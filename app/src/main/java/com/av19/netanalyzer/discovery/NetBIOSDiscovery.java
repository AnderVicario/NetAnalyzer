package com.av19.netanalyzer.discovery;

import android.util.Log;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.utils.CancellationToken;
import com.av19.netanalyzer.utils.NetUtils;
import com.av19.netanalyzer.utils.ProgressCallback;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NetBIOSDiscovery implements DiscoveryMethod {

    private static final String TAG = "NetBIOSDiscovery";
    private static final int NETBIOS_PORT = 137;
    private static final int TIMEOUT_MS = 8000;
    private static final int THREAD_POOL_SIZE = 40;

    @Override
    public String getName() {
        return "NetBIOS";
    }

    @Override
    public List<DeviceInfo> discover(NetworkInfo network, CancellationToken token, ProgressCallback callback) {
        List<DeviceInfo> devices = new ArrayList<>();
        Log.d(TAG, "Iniciando escaneo NetBIOS en red: " + network.getNetworkAddress() + "/" + network.getNetmask());

        int networkInt = NetUtils.ipToInt(network.getNetworkAddress());
        int mask = NetUtils.ipToInt(network.getNetmask());
        int first = networkInt + 1;
        int last = (networkInt | ~mask) - 1;
        int totalHosts = last - first + 1;

        if (totalHosts <= 0) return devices;
        Log.d(TAG, "Rango de IPs: " + NetUtils.intToIp(first) + " - " + NetUtils.intToIp(last) + " (" + totalHosts + " hosts)");

        AtomicInteger scanned = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalHosts);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        for (int host = first; host <= last && !token.isCancelled(); host++) {
            final String ip = NetUtils.intToIp(host);
            executor.execute(() -> {
                if (token.isCancelled()) {
                    latch.countDown();
                    return;
                }

                try {
                    NetBIOSResult result = queryNodeStatus(ip, TIMEOUT_MS);
                    if (result != null && result.hostname != null && !result.hostname.isEmpty()) {
                        DeviceInfo device = createDeviceInfo(ip, result);
                        synchronized (devices) {
                            devices.add(device);
                        }
                        if (callback != null) callback.onDeviceFound(device);
                        Log.i(TAG, "NetBIOS detectado: " + ip + " -> " + result.hostname + " [" + result.mac + "]");
                    } else if (result != null) {
                        Log.d(TAG, "Respuesta NetBIOS desde " + ip + " pero sin hostname útil");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error crítico en NetBIOS a " + ip, e);
                } finally {
                    int progress = (int) (scanned.incrementAndGet() * 100.0 / totalHosts);
                    if (callback != null) callback.onProgress(progress, ip);
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Escaneo NetBIOS interrumpido");
        }
        executor.shutdown();
        Log.d(TAG, "Escaneo NetBIOS finalizado. Dispositivos encontrados: " + devices.size());
        return devices;
    }

    private DeviceInfo createDeviceInfo(String ip, NetBIOSResult result) {
        DeviceInfo device = new DeviceInfo(ip, result.mac, null, null);
        if (result.hostname != null) {
            device.setHostname(new DeviceInfo.PriorityValue(1, result.hostname));
        }
        if (result.mac != null) {
            device.addDetail("mac", result.mac);
        }
        return device;
    }

    // -------------------------------------------------------------------------
    // queryNodeStatus
    // -------------------------------------------------------------------------

    private NetBIOSResult queryNodeStatus(String ipAddress, int timeoutMs) {
        DatagramSocket socket = null;
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            socket = new DatagramSocket();
            socket.setSoTimeout(timeoutMs);

            byte[] request = buildNBSTATRequest();
            Log.v(TAG, "Enviando NBSTAT a " + ipAddress + ":" + NETBIOS_PORT + ", tamaño=" + request.length);
            DatagramPacket packet = new DatagramPacket(request, request.length, address, NETBIOS_PORT);
            socket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);

            Log.d(TAG, "Recibida respuesta de " + response.getAddress().getHostAddress()
                    + ", longitud=" + response.getLength());
            logHexDump("Respuesta NBSTAT", response.getData(), response.getLength());

            return parseNBSTATResponse(response.getData(), response.getLength());

        } catch (SocketTimeoutException e) {
            Log.v(TAG, "Timeout NetBIOS para " + ipAddress);
        } catch (IOException e) {
            Log.w(TAG, "IOException en NBSTAT a " + ipAddress + ": " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // buildNBSTATRequest — FIX 1: añadir byte de longitud (0x20) y terminador (0x00)
    //                      FIX 2: nombre correcto "*" + 15 espacios
    // -------------------------------------------------------------------------

    private byte[] buildNBSTATRequest() {
        ByteBuffer buffer = ByteBuffer.allocate(50);
        Random random = new Random();

        short transactionId = (short) random.nextInt(0xFFFF);
        buffer.putShort(transactionId);
        buffer.putShort((short) 0x0000);  // Flags
        buffer.putShort((short) 1);       // Questions
        buffer.putShort((short) 0);       // Answer RRs
        buffer.putShort((short) 0);       // Authority RRs
        buffer.putShort((short) 0);       // Additional RRs

        byte[] encodedName = new byte[32];

        // 1. Codificar el '*' (0x2A) -> 0x43, 0x4B ('CK')
        encodedName[0] = (byte) 'C';
        encodedName[1] = (byte) 'K';

        // 2. Rellenar con 15 caracteres nulos (0x00) -> 0x41, 0x41 ('AA')
        // Esto es lo que nmblookup hace y lo que el estándar espera para NBSTAT
        for (int i = 1; i < 16; i++) {
            encodedName[i * 2] = (byte) 'A';
            encodedName[i * 2 + 1] = (byte) 'A';
        }

        buffer.put((byte) 0x20);   // Longitud 32
        buffer.put(encodedName);   // Los 32 bytes codificados
        buffer.put((byte) 0x00);   // Terminador del nombre

        buffer.putShort((short) 0x0021);  // Tipo: NBSTAT (33)
        buffer.putShort((short) 0x0001);  // Clase: IN

        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        Log.v(TAG, "Request NBSTAT construido, transactionId=0x"
                + Integer.toHexString(transactionId & 0xFFFF) + ", tamaño=" + result.length);
        logHexDump("Request NBSTAT", result, result.length);
        return result;
    }

    // -------------------------------------------------------------------------
    // encodeNetBIOSName — sin cambios, era correcto
    // -------------------------------------------------------------------------

    private byte[] encodeNetBIOSName(String name, byte suffix) {
        StringBuilder padded = new StringBuilder(name);
        while (padded.length() < 15) padded.append(' ');
        // NO concatenar el suffix como char — tratarlo como byte directamente

        byte[] encoded = new byte[32];
        for (int i = 0; i < 15; i++) {
            char c = padded.charAt(i);
            int high = (c >> 4) & 0x0F;
            int low = c & 0x0F;
            encoded[i * 2] = (byte) (high + 0x41);
            encoded[i * 2 + 1] = (byte) (low + 0x41);
        }
        // El sufijo se codifica por separado como byte, no como char
        int high = (suffix >> 4) & 0x0F;
        int low = suffix & 0x0F;
        encoded[30] = (byte) (high + 0x41);
        encoded[31] = (byte) (low + 0x41);

        return encoded;
    }

    // -------------------------------------------------------------------------
    // parseNBSTATResponse
    //   FIX 3: skipBytes de la pregunta pasa de 32 a 34 (1 length + 32 encoded + 1 terminator)
    //   FIX 4: nameFlags lee 2 bytes en vez de 1
    //   FIX 5: skipBytes del RR también usa readNameField para soportar punteros
    // -------------------------------------------------------------------------

    private NetBIOSResult parseNBSTATResponse(byte[] data, int length) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data, 0, length));

            // --- Cabecera (12 bytes) ---
            short transactionId = dis.readShort();
            short flags = dis.readShort();
            short questions = dis.readShort();
            short answerRRs = dis.readShort();
            short authorityRRs = dis.readShort();
            short additionalRRs = dis.readShort();

            Log.v(TAG, "Header: flags=0x" + Integer.toHexString(flags & 0xFFFF)
                    + ", questions=" + questions + ", answers=" + answerRRs);

            if ((flags & 0x8000) == 0) {
                Log.w(TAG, "No es respuesta (QR=0)");
                return null;
            }
            if ((flags & 0x000F) != 0) {
                Log.w(TAG, "RCODE=" + (flags & 0x000F));
                return null;
            }

            // 🔧 FIX 1: Saltar preguntas solo si las hay
            for (int i = 0; i < questions; i++) {
                skipNameField(dis);
                dis.readShort(); // qType
                dis.readShort(); // qClass
            }

            // --- Procesar RRs de respuesta ---
            for (int i = 0; i < answerRRs; i++) {
                skipNameField(dis);

                short type = dis.readShort();
                short rrClass = dis.readShort();
                int ttl = dis.readInt();
                short dataLen = dis.readShort();

                if (dataLen > dis.available()) {
                    Log.w(TAG, "dataLen (" + dataLen + ") excede el buffer disponible");
                    break;
                }

                Log.v(TAG, "RR tipo=0x" + Integer.toHexString(type & 0xFFFF) + ", dataLen=" + dataLen);

                if (type == 0x0021 && dataLen > 0) {
                    byte[] rrData = new byte[dataLen];
                    dis.readFully(rrData);
                    ByteArrayInputStream bais = new ByteArrayInputStream(rrData);

                    int numNames = bais.read();
                    String hostname = null;

                    for (int n = 0; n < numNames; n++) {
                        byte[] rawName = new byte[15];
                        bais.read(rawName);
                        int suffix = bais.read();
                        int nameFlags = (bais.read() << 8) | bais.read();

                        String decodedName = new String(rawName, StandardCharsets.US_ASCII).trim();

                        // 🔧 FIX 2: Nombre ÚNICO (bit15 == 0) con sufijo 0x00
                        if ((nameFlags & 0x8000) == 0 && suffix == 0x00 && !decodedName.isEmpty()) {
                            hostname = decodedName;
                        }
                    }

                    // MAC
                    String mac = null;
                    if (bais.available() >= 6) {
                        byte[] macBytes = new byte[6];
                        bais.read(macBytes);
                        mac = bytesToMAC(macBytes);
                    }

                    if (hostname != null) return new NetBIOSResult(hostname, mac);
                    Log.w(TAG, "Sin hostname en la respuesta");
                    break;

                } else {
                    skipFully(dis, dataLen);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parseando respuesta NBSTAT", e);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // skipNameField — salta el campo NAME del wire format NBNS
    //   Si empieza con 0xC0 es un puntero de 2 bytes.
    //   Si no, es un nombre con byte de longitud (0x20) + 32 bytes + 0x00 = 34 bytes.
    // -------------------------------------------------------------------------

    private void skipNameField(DataInputStream dis) throws IOException {
        while (true) {
            int len = dis.readUnsignedByte();
            if (len == 0) {
                // Fin del nombre (terminador 0x00)
                break;
            }
            if ((len & 0xC0) == 0xC0) {
                // Es un puntero (2 bytes en total).
                // Ya leímos el primero (len), leemos el segundo y terminamos.
                dis.readUnsignedByte();
                break;
            } else {
                // Es un segmento de nombre normal, saltamos su longitud
                skipFully(dis, len);
            }
        }
    }

    // -------------------------------------------------------------------------
    // skipFully — skipBytes garantizado (DataInputStream.skipBytes puede saltar menos)
    // FIX 5: reemplaza todos los dis.skipBytes() sueltos
    // -------------------------------------------------------------------------

    private void skipFully(DataInputStream dis, int n) throws IOException {
        if (n <= 0) return;
        int remaining = n;
        while (remaining > 0) {
            // Usamos read() en lugar de skip() porque skip() en ByteArrayInputStream
            // a veces no se comporta como esperamos en streams envueltos.
            long skipped = dis.skip(remaining);
            if (skipped <= 0) {
                // Si skip no funciona, leemos físicamente los bytes para descartarlos
                dis.readByte();
                remaining--;
            } else {
                remaining -= (int) skipped;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private String bytesToMAC(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X", mac[i] & 0xFF));
            if (i < mac.length - 1) sb.append(":");
        }
        return sb.toString();
    }

    private void logHexDump(String prefix, byte[] data, int len) {
        if (!Log.isLoggable(TAG, Log.VERBOSE)) return;
        StringBuilder sb = new StringBuilder(prefix + " (" + len + " bytes):\n   ");
        for (int i = 0; i < Math.min(len, 128); i++) {
            sb.append(String.format("%02X ", data[i] & 0xFF));
            if ((i + 1) % 16 == 0) sb.append("\n   ");
        }
        Log.v(TAG, sb.toString());
    }

    // -------------------------------------------------------------------------
    // Modelo interno
    // -------------------------------------------------------------------------

    private static class NetBIOSResult {
        String hostname;
        String mac;

        NetBIOSResult(String hostname, String mac) {
            this.hostname = hostname;
            this.mac = mac;
        }
    }
}