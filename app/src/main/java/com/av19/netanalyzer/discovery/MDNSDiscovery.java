package com.av19.netanalyzer.discovery;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Log;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.utils.CancellationToken;
import com.av19.netanalyzer.utils.ProgressCallback;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MDNSDiscovery implements DiscoveryMethod {
    private static final String TAG = "MDNSDiscovery";
    private final NsdManager nsdManager;
    private final Map<String, DeviceInfo> foundDevices = new ConcurrentHashMap<>();
    private final Set<String> discoveredServiceTypes = Collections.synchronizedSet(new HashSet<>());
    private final Set<NsdManager.DiscoveryListener> activeListeners = Collections.synchronizedSet(new HashSet<>());

    private static final int SERVICE_TYPE_DISCOVERY_TIME = 5000;
    private static final int INSTANCE_DISCOVERY_TIME = 5000;

    // Porcentajes: fase1 (tipos) 30%, fase2 (instancias) 70%
    private static final int PHASE1_MAX_PROGRESS = 30;

    public MDNSDiscovery(Context context) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) {
            Log.e(TAG, "NsdManager is null! MDNS discovery will not work.");
        }
    }

    @Override
    public String getName() {
        return "MDNS";
    }

    @Override
    public List<DeviceInfo> discover(NetworkInfo network, CancellationToken token, ProgressCallback callback) {
        Log.d(TAG, "Starting MDNS discovery...");
        foundDevices.clear();
        discoveredServiceTypes.clear();
        activeListeners.clear();

        if (nsdManager == null) {
            Log.e(TAG, "Cannot start discovery: NsdManager is null");
            return new ArrayList<>();
        }

        if (callback != null) callback.onProgress(0, "Discovering MDNS service types...");

        // ==================== FASE 1: DESCUBRIR TIPOS DE SERVICIO ====================
        List<String> serviceTypes = discoverServiceTypes(token, callback);
        Log.i(TAG, "Discovered " + serviceTypes.size() + " service types");

        if (token.isCancelled()) {
            return new ArrayList<>(foundDevices.values());
        }

        if (callback != null) {
            callback.onProgress(PHASE1_MAX_PROGRESS, "Found " + serviceTypes.size() + " service types");
        }

        if (serviceTypes.isEmpty()) {
            Log.d(TAG, "No service types found, finishing MDNS discovery");
            if (callback != null) callback.onProgress(100, "No services found");
            return new ArrayList<>(foundDevices.values());
        }

        // ==================== FASE 2: DESCUBRIR INSTANCIAS ====================
        CountDownLatch latch = new CountDownLatch(serviceTypes.size());
        AtomicInteger completedTypes = new AtomicInteger(0);
        int totalTypes = serviceTypes.size();
        int phase2Range = 100 - PHASE1_MAX_PROGRESS;

        for (String serviceType : serviceTypes) {
            if (token.isCancelled()) break;
            startSubDiscovery(serviceType, token, latch, completedTypes, totalTypes, phase2Range, callback);
        }

        // Esperar a que TODOS los tipos terminen (no solo 5 segundos)
        try {
            // Esperar hasta que todos los tipos hayan terminado, con un timeout máximo extendido
            boolean completed = latch.await(INSTANCE_DISCOVERY_TIME + 2000, TimeUnit.MILLISECONDS);
            if (!completed) {
                Log.w(TAG, "Not all service types completed within timeout, forcing stop");
                stopAllDiscoveries();
            } else {
                Log.d(TAG, "All service types completed successfully");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Discovery interrupted");
            stopAllDiscoveries();
        }

        // Asegurar 100% solo después de que todo haya terminado
        if (callback != null && completedTypes.get() >= totalTypes) {
            callback.onProgress(100, "MDNS discovery finished");
        }

        Log.d(TAG, "MDNS discovery finished. Found " + foundDevices.size() + " devices");
        return new ArrayList<>(foundDevices.values());
    }

    private void startSubDiscovery(String serviceType, CancellationToken token,
                                   CountDownLatch latch, AtomicInteger completedTypes,
                                   int totalTypes, int phase2Range, ProgressCallback callback) {
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicInteger instancesFound = new AtomicInteger(0);

        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (token.isCancelled() || stopped.get()) return;
                instancesFound.incrementAndGet();

                nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onServiceResolved(NsdServiceInfo resolvedInfo) {
                        if (token.isCancelled() || stopped.get()) return;
                        processResolvedService(resolvedInfo, callback);
                    }

                    @Override
                    public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                        Log.d(TAG, "Resolve failed for " + info.getServiceName() + ": " + errorCode);
                    }
                });
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Discovering: " + serviceType);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Stopped: " + serviceType + " (" + instancesFound.get() + " instances)");
                if (!stopped.getAndSet(true)) {
                    int completed = completedTypes.incrementAndGet();
                    // Calcular y reportar progreso
                    int progress = PHASE1_MAX_PROGRESS + (completed * phase2Range / totalTypes);
                    if (callback != null) {
                        callback.onProgress(progress, "Completed: " + serviceType);
                    }
                    activeListeners.remove(this);
                    latch.countDown();
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Start failed: " + serviceType + " error=" + errorCode);
                if (!stopped.getAndSet(true)) {
                    int completed = completedTypes.incrementAndGet();
                    int progress = PHASE1_MAX_PROGRESS + (completed * phase2Range / totalTypes);
                    if (callback != null) {
                        callback.onProgress(progress, "Failed: " + serviceType);
                    }
                    activeListeners.remove(this);
                    latch.countDown();
                }
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop failed: " + serviceType + " error=" + errorCode);
                if (!stopped.getAndSet(true)) {
                    int completed = completedTypes.incrementAndGet();
                    int progress = PHASE1_MAX_PROGRESS + (completed * phase2Range / totalTypes);
                    if (callback != null) {
                        callback.onProgress(progress, "Stop failed: " + serviceType);
                    }
                    activeListeners.remove(this);
                    latch.countDown();
                }
            }
        };

        activeListeners.add(listener);
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);

        // Temporizador para detener automáticamente después de INSTANCE_DISCOVERY_TIME
        new Thread(() -> {
            try {
                Thread.sleep(INSTANCE_DISCOVERY_TIME);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (!stopped.get() && !token.isCancelled()) {
                    Log.d(TAG, "Auto-stopping discovery for " + serviceType + " after timeout");
                    stopDiscovery(listener);
                }
            }
        }).start();
    }

    /**
     * Descubre tipos de servicio (0% -> PHASE1_MAX_PROGRESS% basado en tiempo)
     */
    private List<String> discoverServiceTypes(CancellationToken token, ProgressCallback callback) {
        List<String> types = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        MulticastSocket socket = MDNSUtils.openMulticastSocket();
        if (socket == null) {
            Log.e(TAG, "Failed to open multicast socket for service type discovery");
            return types;
        }

        try {
            byte[] query = MDNSUtils.buildQuery("_services._dns-sd._udp.local", MDNSUtils.TYPE_PTR);
            MDNSUtils.sendQuery(socket, query);

            long startTime = System.currentTimeMillis();
            byte[] buf = new byte[4096];
            socket.setSoTimeout(500);
            boolean secondQuerySent = false;
            int lastProgress = -1;

            while (System.currentTimeMillis() - startTime < SERVICE_TYPE_DISCOVERY_TIME && !token.isCancelled()) {
                // Actualizar progreso de fase 1 (0 a PHASE1_MAX_PROGRESS)
                int elapsedPercent = (int) ((System.currentTimeMillis() - startTime) * 100 / SERVICE_TYPE_DISCOVERY_TIME);
                int currentProgress = elapsedPercent * PHASE1_MAX_PROGRESS / 100;
                if (currentProgress != lastProgress && callback != null) {
                    callback.onProgress(currentProgress, "Discovering service types...");
                    lastProgress = currentProgress;
                }

                // Capturar el valor actual de lastProgress para usar en la lambda
                final int progressForLambda = lastProgress;

                if (!secondQuerySent && System.currentTimeMillis() - startTime > SERVICE_TYPE_DISCOVERY_TIME / 2) {
                    MDNSUtils.sendQuery(socket, query);
                    secondQuerySent = true;
                    Log.d(TAG, "Sent second service type query");
                }

                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    MDNSUtils.parseServiceTypeResponse(packet.getData(), packet.getLength(), seen, type -> {
                        if (!types.contains(type)) {
                            types.add(type);
                            Log.d(TAG, "Found service type: " + type);
                            if (callback != null) {
                                // Usar la variable final capturada
                                callback.onProgress(progressForLambda, "Found: " + type);
                            }
                        }
                    });
                } catch (java.net.SocketTimeoutException e) {
                    // esperado
                } catch (Exception e) {
                    Log.e(TAG, "Error receiving MDNS response", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in service type discovery", e);
        } finally {
            socket.close();
        }

        return types;
    }

    private void stopDiscovery(NsdManager.DiscoveryListener listener) {
        try {
            // Solo intentamos detener si el listener aún está en el conjunto activo
            if (activeListeners.contains(listener)) {
                nsdManager.stopServiceDiscovery(listener);
                // No lo removemos aquí porque onDiscoveryStopped o onStopDiscoveryFailed lo harán
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping discovery", e);
        }
    }

    private void stopAllDiscoveries() {
        // Copia para evitar ConcurrentModificationException
        List<NsdManager.DiscoveryListener> listenersCopy;
        synchronized (activeListeners) {
            listenersCopy = new ArrayList<>(activeListeners);
        }
        for (NsdManager.DiscoveryListener listener : listenersCopy) {
            try {
                nsdManager.stopServiceDiscovery(listener);
            } catch (IllegalArgumentException e) {
                // El listener ya no estaba registrado, ignoramos
                Log.d(TAG, "Listener already unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping discovery", e);
            }
        }
        activeListeners.clear();
    }

    private void processResolvedService(NsdServiceInfo info, ProgressCallback callback) {
        String ip = info.getHost().getHostAddress();
        if (ip == null) return;

        Log.d(TAG, "Found device: " + info.getServiceName() + " @ " + ip);

        DeviceInfo device = foundDevices.get(ip);
        if (device == null) {
            device = new DeviceInfo(ip, null, null, new ArrayList<>());
            foundDevices.put(ip, device);
        }

        if (info.getServiceName() != null) {
            device.setHostname(new DeviceInfo.PriorityValue(0, info.getServiceName()));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<String, byte[]> attributes = info.getAttributes();
            if (attributes.containsKey("md") && device.getModel() == null) {
                device.setModel(new DeviceInfo.PriorityValue(0, new String(attributes.get("md"))));
            } else if (attributes.containsKey("modelid") && device.getModel() == null) {
                device.setModel(new DeviceInfo.PriorityValue(0, new String(attributes.get("modelid"))));
            }
        }

        if (callback != null) {
            callback.onDeviceFound(device);
        }
    }

    // ======================== CLASE INTERNA MDNSUtils (sin cambios) ========================
    private static class MDNSUtils {
        private static final String MDNS_IPV4_ADDRESS = "224.0.0.251";
        private static final int MDNS_PORT = 5353;
        public static final int TYPE_PTR = 12;
        public static final int CLASS_IN = 1;

        public static MulticastSocket openMulticastSocket() {
            NetworkInterface ni = findMulticastInterface();
            if (ni == null) {
                Log.e(TAG, "No suitable multicast interface found");
                return null;
            }
            MulticastSocket socket = null;
            try {
                socket = new MulticastSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(MDNS_PORT));
                socket.setNetworkInterface(ni);
                socket.setTimeToLive(255);

                InetAddress ipv4Group = InetAddress.getByName(MDNS_IPV4_ADDRESS);
                socket.joinGroup(new InetSocketAddress(ipv4Group, MDNS_PORT), ni);

                if (hasIPv6(ni)) {
                    InetAddress ipv6Group = InetAddress.getByName("ff02::fb");
                    try {
                        socket.joinGroup(new InetSocketAddress(ipv6Group, MDNS_PORT), ni);
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to join IPv6 multicast: " + e.getMessage());
                    }
                }
                return socket;
            } catch (Exception e) {
                if (socket != null) socket.close();
                Log.e(TAG, "Failed to open multicast socket", e);
                return null;
            }
        }

        private static NetworkInterface findMulticastInterface() {
            try {
                java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    if (ni.isLoopback() || !ni.isUp() || !ni.supportsMulticast()) continue;
                    return ni;
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to enumerate interfaces", e);
            }
            return null;
        }

        private static boolean hasIPv6(NetworkInterface ni) {
            java.util.Enumeration<InetAddress> addrs = ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                if (addrs.nextElement() instanceof Inet6Address) return true;
            }
            return false;
        }

        public static byte[] buildQuery(String name, int type) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                dos.writeShort(0);      // ID
                dos.writeShort(0);      // Flags
                dos.writeShort(1);      // QDCOUNT
                dos.writeShort(0);      // ANCOUNT
                dos.writeShort(0);      // NSCOUNT
                dos.writeShort(0);      // ARCOUNT
                writeDnsName(dos, name);
                dos.writeShort(type);
                dos.writeShort(CLASS_IN);
                dos.flush();
                return baos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Failed to build DNS query", e);
            }
        }

        public static void sendQuery(MulticastSocket socket, byte[] query) {
            try {
                InetAddress ipv4Group = InetAddress.getByName(MDNS_IPV4_ADDRESS);
                socket.send(new DatagramPacket(query, query.length, ipv4Group, MDNS_PORT));
            } catch (IOException e) {
                Log.w(TAG, "Failed to send IPv4 query: " + e.getMessage());
            }
            try {
                InetAddress ipv6Group = InetAddress.getByName("ff02::fb");
                socket.send(new DatagramPacket(query, query.length, ipv6Group, MDNS_PORT));
            } catch (IOException e) {
            }
        }

        private static void writeDnsName(DataOutputStream dos, String name) throws IOException {
            String n = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
            for (String label : n.split("\\.")) {
                byte[] bytes = label.getBytes(StandardCharsets.UTF_8);
                dos.writeByte(bytes.length);
                dos.write(bytes);
            }
            dos.writeByte(0);
        }

        private static String readDnsName(ByteBuffer buf) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            int savedPos = -1;
            while (buf.remaining() > 0) {
                int len = buf.get() & 0xFF;
                if (len == 0) break;
                if ((len & 0xC0) == 0xC0) {
                    if (buf.remaining() < 1) break;
                    int offset = ((len & 0x3F) << 8) | (buf.get() & 0xFF);
                    if (savedPos == -1) savedPos = buf.position();
                    buf.position(offset);
                    continue;
                }
                if (buf.remaining() < len) break;
                if (!first) sb.append('.');
                byte[] labelBytes = new byte[len];
                buf.get(labelBytes);
                sb.append(new String(labelBytes));
                first = false;
            }
            if (savedPos != -1) buf.position(savedPos);
            return sb.toString();
        }

        private static void skipDnsName(ByteBuffer buf) {
            while (buf.remaining() > 0) {
                int len = buf.get() & 0xFF;
                if (len == 0) break;
                if ((len & 0xC0) == 0xC0) {
                    buf.get();
                    break;
                }
                if (buf.remaining() < len) break;
                buf.position(buf.position() + len);
            }
        }

        private static String extractServiceType(String target) {
            if (target == null) return null;
            String t = target.endsWith(".") ? target.substring(0, target.length() - 1) : target;
            String[] parts = t.split("\\.");
            if (parts.length < 2) return null;
            String name = parts[0];
            String proto = parts[1];
            if (!name.startsWith("_")) return null;
            if (!proto.equals("_tcp") && !proto.equals("_udp")) return null;
            return name + "." + proto;
        }

        public static void parseServiceTypeResponse(byte[] data, int length,
                                                    Set<String> seen,
                                                    java.util.function.Consumer<String> callback) {
            if (length < 12) return;
            ByteBuffer buf = ByteBuffer.wrap(data, 0, length);
            buf.getShort(); // ID
            buf.getShort(); // Flags
            int qdCount = buf.getShort() & 0xFFFF;
            int anCount = buf.getShort() & 0xFFFF;
            int nsCount = buf.getShort() & 0xFFFF;
            int arCount = buf.getShort() & 0xFFFF;
            for (int i = 0; i < qdCount; i++) {
                skipDnsName(buf);
                if (buf.remaining() < 4) return;
                buf.getShort(); // type
                buf.getShort(); // class
            }
            int totalRecords = anCount + nsCount + arCount;
            for (int i = 0; i < totalRecords; i++) {
                if (buf.remaining() < 1) return;
                String name = readDnsName(buf);
                if (buf.remaining() < 10) return;
                int type = buf.getShort() & 0xFFFF;
                buf.getShort(); // class
                buf.getInt();   // TTL
                int rdLength = buf.getShort() & 0xFFFF;
                if (buf.remaining() < rdLength) return;
                if (type == TYPE_PTR) {
                    int rdStart = buf.position();
                    String target = readDnsName(buf);
                    buf.position(rdStart + rdLength);
                    String serviceType = extractServiceType(target);
                    if (serviceType != null && !seen.contains(serviceType)) {
                        seen.add(serviceType);
                        callback.accept(serviceType);
                    }
                } else {
                    buf.position(buf.position() + rdLength);
                }
            }
        }
    }
}