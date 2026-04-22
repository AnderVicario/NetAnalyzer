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
    private static final int TIMEOUT_MS = 1000;      // aumentado para redes lentas
    private static final int THREAD_POOL_SIZE = 30;  // reducido para evitar saturación

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
                    } else if (result != null && result.hostname == null) {
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

    /**
     * Realiza una petición Node Status (NBSTAT) a una IP.
     */
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

            Log.d(TAG, "Recibida respuesta de " + response.getAddress().getHostAddress() + ", longitud=" + response.getLength());
            logHexDump("Respuesta NBSTAT", response.getData(), response.getLength());

            return parseNBSTATResponse(response.getData(), response.getLength());
        } catch (SocketTimeoutException e) {
            Log.v(TAG, "Timeout NetBIOS para " + ipAddress);
        } catch (IOException e) {
            Log.w(TAG, "IOException en NBSTAT a " + ipAddress + ": " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
        return null;
    }

    /**
     * Construye el paquete NBSTAT request (RFC 1002).
     */
    private byte[] buildNBSTATRequest() {
        ByteBuffer buffer = ByteBuffer.allocate(50);
        Random random = new Random();

        short transactionId = (short) random.nextInt(0xFFFF);
        buffer.putShort(transactionId);
        buffer.putShort((short) 0x0110);  // Flags: query, recursion deseada
        buffer.putShort((short) 1);       // Questions
        buffer.putShort((short) 0);       // Answer RRs
        buffer.putShort((short) 0);       // Authority RRs
        buffer.putShort((short) 0);       // Additional RRs

        // Nombre: "*" + 14 espacios (consulta NBSTAT)
        String name = "*" + "               ".substring(1); // 15 chars
        byte[] encodedName = encodeNetBIOSName(name, (byte) 0x00);
        buffer.put(encodedName);

        buffer.putShort((short) 0x0021);  // Tipo: NBSTAT
        buffer.putShort((short) 0x0001);  // Clase: IN

        byte[] result = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(result);
        Log.v(TAG, "Request NBSTAT construido, transactionId=0x" + Integer.toHexString(transactionId & 0xFFFF));
        logHexDump("Request NBSTAT", result, result.length);
        return result;
    }

    /**
     * Codifica un nombre NetBIOS de 15 caracteres + sufijo en 32 bytes (RFC 1002).
     */
    private byte[] encodeNetBIOSName(String name, byte suffix) {
        StringBuilder padded = new StringBuilder(name);
        while (padded.length() < 15) padded.append(' ');
        String fullName = padded.toString() + (char) (suffix & 0xFF);

        byte[] encoded = new byte[32];
        for (int i = 0; i < 16; i++) {
            char c = fullName.charAt(i);
            int high = (c >> 4) & 0x0F;
            int low = c & 0x0F;
            encoded[i * 2] = (byte) (high + 0x41);
            encoded[i * 2 + 1] = (byte) (low + 0x41);
        }
        return encoded;
    }

    /**
     * Parsea la respuesta NBSTAT extrayendo nombre NetBIOS y MAC.
     * Soporta nombres comprimidos (punteros) en la sección de respuesta.
     */
    private NetBIOSResult parseNBSTATResponse(byte[] data, int length) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data, 0, length));

            // Cabecera DNS/NBNS
            short transactionId = dis.readShort();
            short flags = dis.readShort();
            short questions = dis.readShort();
            short answerRRs = dis.readShort();
            short authorityRRs = dis.readShort();
            short additionalRRs = dis.readShort();

            Log.v(TAG, "Header: flags=0x" + Integer.toHexString(flags & 0xFFFF) +
                    ", answers=" + answerRRs + ", auth=" + authorityRRs + ", add=" + additionalRRs);

            // Verificar que es respuesta exitosa
            if ((flags & 0x8000) == 0) {
                Log.w(TAG, "No es respuesta (QR=0)");
                return null;
            }
            int rcode = flags & 0x000F;
            if (rcode != 0) {
                Log.w(TAG, "RCODE != 0: " + rcode);
                return null;
            }

            // Saltar la pregunta (nombre + tipo + clase)
            // El nombre puede ser de 32 bytes o un puntero. Como es la pregunta, siempre son 32 bytes.
            dis.skipBytes(32);
            short qType = dis.readShort();
            short qClass = dis.readShort();
            Log.v(TAG, "Pregunta: type=" + qType + ", class=" + qClass);

            // Procesar cada RR de respuesta
            for (int i = 0; i < answerRRs; i++) {
                // Leer el nombre del RR (puede ser puntero o nombre completo)
                String rrName = readNameField(dis);
                Log.v(TAG, "RR " + i + " nombre leído: " + rrName);

                short type = dis.readShort();
                short rrClass = dis.readShort();
                int ttl = dis.readInt();
                short dataLen = dis.readShort();

                Log.v(TAG, "RR tipo=0x" + Integer.toHexString(type & 0xFFFF) + ", clase=" + rrClass +
                        ", ttl=" + ttl + ", dataLen=" + dataLen);

                if (type == 0x0021 && dataLen > 0) {
                    byte[] rrData = new byte[dataLen];
                    dis.readFully(rrData);
                    ByteArrayInputStream bais = new ByteArrayInputStream(rrData);

                    int numNames = bais.read(); // número de nombres en la tabla
                    Log.v(TAG, "Número de nombres en tabla: " + numNames);
                    String hostname = null;
                    for (int n = 0; n < numNames; n++) {
                        byte[] rawName = new byte[15];
                        bais.read(rawName);
                        int suffix = bais.read();
                        int nameFlags = bais.read();
                        String decodedName = new String(rawName, "US-ASCII").trim();
                        Log.v(TAG, "Nombre " + n + ": '" + decodedName + "', suffix=0x" + Integer.toHexString(suffix) +
                                ", flags=0x" + Integer.toHexString(nameFlags & 0xFF));
                        // Buscar nombre único (bit7 de flags) y suffix 0x00 (workstation)
                        if ((nameFlags & 0x80) != 0 && suffix == 0x00 && !decodedName.isEmpty()) {
                            hostname = decodedName;
                            Log.d(TAG, "Hostname candidato encontrado: " + hostname);
                        }
                    }
                    // Los últimos 6 bytes son la dirección MAC (si están)
                    String mac = null;
                    if (bais.available() >= 6) {
                        byte[] macBytes = new byte[6];
                        bais.read(macBytes);
                        mac = bytesToMAC(macBytes);
                        Log.v(TAG, "MAC extraída: " + mac);
                    } else {
                        Log.w(TAG, "No hay suficientes bytes para MAC, disponibles=" + bais.available());
                    }
                    if (hostname != null) {
                        return new NetBIOSResult(hostname, mac);
                    } else {
                        Log.w(TAG, "No se encontró hostname en la respuesta");
                    }
                    break; // Solo procesamos el primer RR NBSTAT
                } else {
                    dis.skipBytes(dataLen);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parseando respuesta NBSTAT", e);
        }
        return null;
    }

    /**
     * Lee un campo "NAME" según RFC 1002, que puede ser:
     * - 32 bytes de nombre codificado (si el primer byte no es puntero)
     * - 2 bytes de puntero (si los dos primeros bits son 0xC0)
     * Devuelve una representación legible del nombre o el offset del puntero.
     */
    private String readNameField(DataInputStream dis) throws IOException {
        dis.mark(2);
        int firstByte = dis.readUnsignedByte();
        dis.reset();
        if ((firstByte & 0xC0) == 0xC0) {
            // Es un puntero: leer 2 bytes
            int pointer = dis.readUnsignedShort();
            int offset = pointer & 0x3FFF;
            return "[POINTER 0x" + Integer.toHexString(offset) + "]";
        } else {
            // Nombre completo de 32 bytes
            byte[] nameBytes = new byte[32];
            dis.readFully(nameBytes);
            return "[FULL NAME]";
        }
    }

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
        StringBuilder sb = new StringBuilder(prefix + " (" + len + " bytes): ");
        for (int i = 0; i < Math.min(len, 128); i++) {
            sb.append(String.format("%02X ", data[i] & 0xFF));
            if ((i + 1) % 16 == 0) sb.append("\n   ");
        }
        Log.v(TAG, sb.toString());
    }

    private static class NetBIOSResult {
        String hostname;
        String mac;
        NetBIOSResult(String hostname, String mac) {
            this.hostname = hostname;
            this.mac = mac;
        }
    }
}