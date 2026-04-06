package com.av19.netanalyzer.discovery;

import android.util.Log;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.utils.CancellationToken;
import com.av19.netanalyzer.utils.OpenRouterApiClient;
import com.av19.netanalyzer.utils.ProgressCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SSDPDiscovery implements DiscoveryMethod {

    private static final String TAG = "SSDPDiscovery";
    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int TIMEOUT_MS = 5000;
    private static final int SO_TIMEOUT_MS = 1500;
    private static final int HTTP_TIMEOUT_MS = 3000;

    private static final int LISTEN_PROGRESS_MAX = 20;

    @Override
    public String getName() {
        return "SSDP";
    }

    @Override
    public List<DeviceInfo> discover(NetworkInfo network, CancellationToken token, ProgressCallback callback) {
        Log.d(TAG, "Starting SSDP discovery...");
        Map<String, DeviceInfo> deviceMap = new ConcurrentHashMap<>();
        Map<String, Set<String>> rawServers = new ConcurrentHashMap<>();
        Map<String, Set<String>> rawLocations = new ConcurrentHashMap<>();
        Map<String, Set<String>> rawUsns = new ConcurrentHashMap<>();
        Map<String, Set<String>> rawSts = new ConcurrentHashMap<>();

        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket();
            socket.setSoTimeout(SO_TIMEOUT_MS);
            socket.setReuseAddress(true);

            String search = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: ssdp:all\r\n" +
                    "\r\n";

            byte[] sendData = search.getBytes(StandardCharsets.UTF_8);
            InetAddress group = InetAddress.getByName(SSDP_ADDR);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, group, SSDP_PORT);
            socket.send(sendPacket);
            Log.d(TAG, "M-SEARCH sent");

            byte[] buffer = new byte[8192];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);

            long startTime = System.currentTimeMillis();
            int lastProgress = -1;
            int responseCount = 0;

            // ==================== FASE 1: ESCUCHA SSDP ====================
            while (!token.isCancelled() && (System.currentTimeMillis() - startTime) < TIMEOUT_MS) {
                // Calcular progreso dentro del rango 0..LISTEN_PROGRESS_MAX
                int elapsedPercent = (int) ((System.currentTimeMillis() - startTime) * 100 / TIMEOUT_MS);
                int currentProgress = elapsedPercent * LISTEN_PROGRESS_MAX / 100;
                if (currentProgress != lastProgress && callback != null) {
                    callback.onProgress(currentProgress, null);
                    lastProgress = currentProgress;
                }

                try {
                    socket.receive(receivePacket);
                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
                    String sourceIp = receivePacket.getAddress().getHostAddress();

                    String server = extractHeader(response, "SERVER");
                    String location = extractHeader(response, "LOCATION");
                    String usn = extractHeader(response, "USN");
                    String st = extractHeader(response, "ST");

                    DeviceInfo device = deviceMap.get(sourceIp);
                    boolean isNew = false;
                    if (device == null) {
                        device = new DeviceInfo(sourceIp, null, null, new ArrayList<>());
                        deviceMap.put(sourceIp, device);
                        isNew = true;
                        rawServers.put(sourceIp, new HashSet<>());
                        rawLocations.put(sourceIp, new HashSet<>());
                        rawUsns.put(sourceIp, new HashSet<>());
                        rawSts.put(sourceIp, new HashSet<>());
                    }

                    if (server != null) rawServers.get(sourceIp).add(server);
                    if (location != null) rawLocations.get(sourceIp).add(location);
                    if (usn != null) rawUsns.get(sourceIp).add(usn);
                    if (st != null) rawSts.get(sourceIp).add(st);

                    if (server != null) device.addDetail("SERVER", server);
                    if (location != null) device.addDetail("LOCATION", location);
                    if (usn != null) device.addDetail("USN", usn);
                    if (st != null) device.addDetail("ST", st);

                    if (server != null && device.getOs() == null) {
                        String[] parts = server.split(" ");
                        if (parts.length > 0) device.setOs(new DeviceInfo.PriorityValue(0, parts[0]));
                    }

                    if (isNew && callback != null) {
                        callback.onDeviceFound(device);
                        responseCount++;
                    }

                } catch (SocketTimeoutException e) {
                    // timeout esperado, continuar
                } catch (IOException e) {
                    Log.e(TAG, "Error receiving SSDP response", e);
                }
            }

            // Asegurar que al final de la fase 1 se reporte el progreso máximo de esta fase
            if (callback != null && lastProgress != LISTEN_PROGRESS_MAX) {
                callback.onProgress(LISTEN_PROGRESS_MAX, null);
                lastProgress = LISTEN_PROGRESS_MAX;
            }

            Log.i(TAG, "SSDP discovery finished. Raw devices found: " + responseCount);
            Log.i(TAG, "Is API key available? " + OpenRouterApiClient.hasToken());

            // ==================== FASE 2: ENRIQUECIMIENTO ====================
            if (OpenRouterApiClient.hasToken() && !deviceMap.isEmpty() && !token.isCancelled()) {
                Log.d(TAG, "Enriching devices with OpenRouter...");
                OkHttpClient httpClient = new OkHttpClient.Builder()
                        .connectTimeout(HTTP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .readTimeout(HTTP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .build();

                List<Map.Entry<String, DeviceInfo>> deviceList = new ArrayList<>(deviceMap.entrySet());
                int totalDevices = deviceList.size();
                int enrichedCount = 0;

                for (Map.Entry<String, DeviceInfo> entry : deviceList) {
                    if (token.isCancelled()) break;

                    String ip = entry.getKey();
                    DeviceInfo device = entry.getValue();

                    Set<String> servers = rawServers.get(ip);
                    Set<String> locations = rawLocations.get(ip);
                    Set<String> usns = rawUsns.get(ip);
                    Set<String> sts = rawSts.get(ip);

                    StringBuilder combinedInfo = new StringBuilder();
                    combinedInfo.append("=== SSDP RESPONSES FOR IP ").append(ip).append(" ===\n");
                    if (servers != null && !servers.isEmpty()) {
                        combinedInfo.append("SERVER headers:\n");
                        for (String s : servers) combinedInfo.append("  ").append(s).append("\n");
                    }
                    if (locations != null && !locations.isEmpty()) {
                        combinedInfo.append("LOCATION headers:\n");
                        for (String loc : locations) combinedInfo.append("  ").append(loc).append("\n");
                    }
                    if (usns != null && !usns.isEmpty()) {
                        combinedInfo.append("USN headers:\n");
                        for (String u : usns) combinedInfo.append("  ").append(u).append("\n");
                    }
                    if (sts != null && !sts.isEmpty()) {
                        combinedInfo.append("ST headers:\n");
                        for (String s : sts) combinedInfo.append("  ").append(s).append("\n");
                    }

                    String xmlContent = null;
                    if (locations != null && !locations.isEmpty()) {
                        String firstLocation = locations.iterator().next();
                        xmlContent = fetchXmlFromLocation(firstLocation, httpClient);
                        if (xmlContent != null) {
                            combinedInfo.append("\n=== DEVICE DESCRIPTION XML ===\n");
                            combinedInfo.append(xmlContent);
                        } else {
                            Log.w(TAG, "Could not fetch XML from " + firstLocation + " for IP " + ip);
                        }
                    }

                    if (combinedInfo.length() > 0) {
                        try {
                            JSONObject parsed = OpenRouterApiClient.parseDeviceInfoSync(combinedInfo.toString());
                            if (parsed != null) {
                                JSONObject devObj = parsed.optJSONObject("device");
                                if (devObj != null) {
                                    String mac = devObj.optString("mac", null);
                                    if (!devObj.isNull("mac") && !mac.isEmpty()) device.setMac(mac);

                                    String manufacturer = devObj.optString("manufacturer", null);
                                    if (!devObj.isNull("manufacturer") && !manufacturer.isEmpty()) device.setVendor(manufacturer);

                                    String os = devObj.optString("os", null);
                                    if (!devObj.isNull("os") && !os.isEmpty()) device.setOs(new DeviceInfo.PriorityValue(1, os));

                                    String model = devObj.optString("model", null);
                                    if (!devObj.isNull("model") && !model.isEmpty()) device.setModel(new DeviceInfo.PriorityValue(1, model));

                                    String hostname = devObj.optString("friendly_name", null);
                                    if (!devObj.isNull("friendly_name") && !hostname.isEmpty()) device.setHostname(new DeviceInfo.PriorityValue(1,hostname));

                                    String udn = devObj.optString("udn", null);
                                    if (!devObj.isNull("udn") && !udn.isEmpty()) device.addDetail("udn", udn);

                                    String serial_number = devObj.optString("serial_number", null);
                                    if (!devObj.isNull("serial_number") && !serial_number.isEmpty()) device.addDetail("serial_number", serial_number);
                                }

                                JSONArray services = parsed.optJSONArray("services");
                                if (services != null && services.length() > 0) {
                                    List<Integer> currentPorts = device.getOpenPorts();
                                    if (currentPorts == null) currentPorts = new ArrayList<>();
                                    for (int i = 0; i < services.length(); i++) {
                                        JSONObject svc = services.getJSONObject(i);
                                        int port = svc.optInt("port", 0);
                                        if (port > 0 && !currentPorts.contains(port)) {
                                            currentPorts.add(port);
                                        }
                                    }
                                    device.setOpenPorts(currentPorts);
                                }

                                Log.i(TAG, "Enriched device " + ip + " via OpenRouter: vendor=" + device.getVendor() +
                                        ", os=" + device.getOs() + ", model=" + device.getModel() + ", ports=" + device.getOpenPorts());
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing OpenRouter response for " + ip, e);
                        }
                    }

                    enrichedCount++;
                    // Actualizar progreso global: desde LISTEN_PROGRESS_MAX hasta 100
                    if (callback != null) {
                        int enrichmentProgress = LISTEN_PROGRESS_MAX +
                                (enrichedCount * (100 - LISTEN_PROGRESS_MAX) / totalDevices);
                        if (enrichmentProgress != lastProgress) {
                            callback.onProgress(enrichmentProgress, null);
                            lastProgress = enrichmentProgress;
                        }
                    }
                }
            }

            // Asegurar 100% al finalizar (si no se alcanzó antes)
            if (callback != null && lastProgress != 100) {
                callback.onProgress(100, null);
            }

        } catch (Exception e) {
            Log.e(TAG, "SSDP discovery error", e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }

        return new ArrayList<>(deviceMap.values());
    }

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

    private String fetchXmlFromLocation(String location, OkHttpClient client) {
        Request request = new Request.Builder()
                .url(location)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                Log.w(TAG, "Failed to fetch XML from " + location + " - HTTP " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error fetching XML from " + location, e);
        }
        return null;
    }
}