package com.av19.netanalyzer.discovery;

import android.util.Log;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.utils.CancellationToken;
import com.av19.netanalyzer.utils.ProgressCallback;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SSDPDiscovery implements DiscoveryMethod {

    private static final String TAG = "SSDPDiscovery";
    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int TIMEOUT_MS = 5000;          // 5 segundos de escucha
    private static final int SO_TIMEOUT_MS = 1500;       // timeout por iteración

    @Override
    public String getName() {
        return "SSDP";
    }

    @Override
    public List<DeviceInfo> discover(NetworkInfo network, CancellationToken token, ProgressCallback callback) {
        Log.d(TAG, "Starting SSDP discovery...");
        Map<String, DeviceInfo> deviceMap = new ConcurrentHashMap<>();

        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket();
            socket.setSoTimeout(SO_TIMEOUT_MS);
            socket.setReuseAddress(true);

            // Enviar la consulta M-SEARCH
            String search = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: ssdp:all\r\n" +
                    "\r\n";

            byte[] sendData = search.getBytes("UTF-8");
            InetAddress group = InetAddress.getByName(SSDP_ADDR);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, group, SSDP_PORT);
            socket.send(sendPacket);
            Log.d(TAG, "M-SEARCH sent");

            // Preparar buffer de recepción
            byte[] buffer = new byte[8192];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);

            long startTime = System.currentTimeMillis();
            int responseCount = 0;

            while (!token.isCancelled() && (System.currentTimeMillis() - startTime) < TIMEOUT_MS) {
                try {
                    socket.receive(receivePacket);
                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength(), "UTF-8");
                    String sourceIp = receivePacket.getAddress().getHostAddress();

                    // Parsear headers relevantes
                    String server = extractHeader(response, "SERVER");
                    String location = extractHeader(response, "LOCATION");
                    String usn = extractHeader(response, "USN");
                    String st = extractHeader(response, "ST");

                    Log.d(TAG, "SSDP response from " + sourceIp + " - SERVER: " + server + " LOCATION: " + location + " USN: " + usn + " ST: " + st);

                    // Si ya tenemos el dispositivo, actualizar; si no, crear nuevo
                    // Dentro del bucle de recepción, después de obtener server, location, usn, st:

                    // Obtener o crear DeviceInfo para esta IP
                    DeviceInfo device = deviceMap.get(sourceIp);
                    boolean isNew = false;
                    if (device == null) {
                        device = new DeviceInfo(sourceIp, null, null, new ArrayList<>());
                        deviceMap.put(sourceIp, device);
                        isNew = true;
                    }

                    // Guardar TODOS los campos en extraDetails (sobrescribiendo el último valor)
                    if (server != null) {
                        device.addDetail("SERVER", server);
                    }
                    if (location != null) {
                        device.addDetail("LOCATION", location);
                    }
                    if (usn != null) {
                        device.addDetail("USN", usn);
                    }
                    if (st != null) {
                        device.addDetail("ST", st);
                    }

                    // Opcional: si quieres conservar múltiples valores para la misma clave (por ejemplo, múltiples ST),
                    // podrías acumularlos en una lista separada por comas, pero por simplicidad lo dejamos así.

                    // Notificar solo cuando es un dispositivo nuevo (evita spam)
                    if (isNew && callback != null) {
                        callback.onDeviceFound(device);
                        responseCount++;
                    }

                } catch (SocketTimeoutException e) {
                    // Timeout esperado, continuar el bucle para seguir escuchando hasta el tiempo global
                    continue;
                } catch (IOException e) {
                    Log.e(TAG, "Error receiving SSDP response", e);
                }
            }

            Log.i(TAG, "SSDP discovery finished. Found " + responseCount + " devices");

        } catch (Exception e) {
            Log.e(TAG, "SSDP discovery error", e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }

        return new ArrayList<>(deviceMap.values());
    }

    /**
     * Extrae un header HTTP de la respuesta SSDP.
     */

    private String extractHeader(String response, String headerName) {
        String regex = "(?i)" + Pattern.quote(headerName) + ":\\s*(.*?)(?:\r?\n|$)";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String value = matcher.group(1).trim();
            return value.isEmpty() ? null : value;
        }

        return null;
    }

    /**
     * Intenta extraer un nombre de host (o el segmento de host) de una URL LOCATION.
     * Ejemplo: http://192.168.1.10:8080/description.xml -> null (o la IP)
     * Pero si la URL contiene un nombre DNS, lo devuelve.
     * Por simplicidad, extraemos la parte de la autoridad (host:puerto) y si no es IP,
     * tomamos el hostname.
     */
    private String extractHostnameFromLocation(String location) {
        if (location == null) return null;
        try {
            java.net.URL url = new java.net.URL(location);
            String host = url.getHost();
            // Si parece una IP, no lo consideramos un hostname útil
            if (host != null && !host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                return host;
            }
        } catch (Exception e) {
            // Si no es una URL válida, ignorar
        }
        return null;
    }
}
