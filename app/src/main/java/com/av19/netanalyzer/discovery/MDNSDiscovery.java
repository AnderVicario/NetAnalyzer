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
    private static final String TAG = "mDNSDiscovery";
    private final NsdManager nsdManager;
    private final Map<String, DeviceInfo> foundDevices = new ConcurrentHashMap<>();
    private final Set<String> discoveredServiceTypes = Collections.synchronizedSet(new HashSet<>());
    private final List<NsdManager.DiscoveryListener> activeListeners = new ArrayList<>();

    // Tiempos optimizados para escaneo rápido
    private static final int SERVICE_TYPE_DISCOVERY_TIME = 5000;  // 2 segundos para tipos
    private static final int INSTANCE_DISCOVERY_TIME = 5000;      // 3 segundos para instancias

    public MDNSDiscovery(Context context) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) {
            Log.e(TAG, "NsdManager is null! mDNS discovery will not work.");
        }
    }

    @Override
    public String getName() {
        return "mDNS";
    }

    @Override
    public List<DeviceInfo> discover(NetworkInfo network, CancellationToken token, ProgressCallback callback) {
        Log.d(TAG, "Starting mDNS discovery...");
        foundDevices.clear();
        discoveredServiceTypes.clear();
        activeListeners.clear();

        if (nsdManager == null) {
            Log.e(TAG, "Cannot start discovery: NsdManager is null");
            return new ArrayList<>();
        }

        // Notificar progreso inicial
        if (callback != null) {
            callback.onProgress(0, "Discovering mDNS services...");
        }

        // 1. Descubrir tipos de servicio (2 segundos máximo)
        List<String> serviceTypes = discoverServiceTypes(token, callback);
        Log.i(TAG, "Discovered " + serviceTypes.size() + " service types in " + SERVICE_TYPE_DISCOVERY_TIME + "ms");

        if (token.isCancelled()) {
            stopAllDiscoveries();
            return new ArrayList<>(foundDevices.values());
        }

        if (serviceTypes.isEmpty()) {
            Log.d(TAG, "No service types found, finishing mDNS discovery");
            return new ArrayList<>(foundDevices.values());
        }

        // 2. Descubrir instancias para cada tipo (3 segundos máximo)
        CountDownLatch latch = new CountDownLatch(serviceTypes.size());

        for (String serviceType : serviceTypes) {
            if (token.isCancelled()) break;
            startSubDiscovery(serviceType, token, latch, callback);
        }

        // Esperar a que terminen los descubrimientos o se cumpla el timeout
        try {
            boolean completed = latch.await(INSTANCE_DISCOVERY_TIME, TimeUnit.MILLISECONDS);
            if (!completed) {
                Log.d(TAG, "Instance discovery completed partially after " + INSTANCE_DISCOVERY_TIME + "ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Discovery interrupted");
        } finally {
            stopAllDiscoveries();
        }

        Log.d(TAG, "mDNS discovery finished. Found " + foundDevices.size() + " devices");
        return new ArrayList<>(foundDevices.values());
    }

    /**
     * Descubre tipos de servicio de forma rápida (2 segundos, enviando consultas al inicio y a la mitad)
     */
    private List<String> discoverServiceTypes(CancellationToken token, ProgressCallback callback) {
        List<String> types = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        MulticastSocket socket = MdnsUtils.openMulticastSocket();
        if (socket == null) {
            Log.e(TAG, "Failed to open multicast socket for service type discovery");
            return types;
        }

        try {
            byte[] query = MdnsUtils.buildQuery("_services._dns-sd._udp.local", MdnsUtils.TYPE_PTR);

            // Enviar primera consulta
            MdnsUtils.sendQuery(socket, query);

            long startTime = System.currentTimeMillis();
            byte[] buf = new byte[4096];
            socket.setSoTimeout(500); // Timeout corto para ser reactivo

            // Enviar segunda consulta después de 1 segundo para capturar respuestas tardías
            boolean secondQuerySent = false;

            while (System.currentTimeMillis() - startTime < SERVICE_TYPE_DISCOVERY_TIME && !token.isCancelled()) {
                // Enviar segunda consulta a mitad del tiempo
                if (!secondQuerySent && System.currentTimeMillis() - startTime > SERVICE_TYPE_DISCOVERY_TIME / 2) {
                    MdnsUtils.sendQuery(socket, query);
                    secondQuerySent = true;
                    Log.d(TAG, "Sent second service type query");
                }

                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    MdnsUtils.parseServiceTypeResponse(packet.getData(), packet.getLength(), seen, type -> {
                        if (!types.contains(type)) {
                            types.add(type);
                            Log.d(TAG, "Found service type: " + type);
                            if (callback != null) {
                                callback.onProgress(0, "Found: " + type);
                            }
                        }
                    });
                } catch (java.net.SocketTimeoutException e) {
                    // Timeout esperado, continuar
                } catch (Exception e) {
                    Log.e(TAG, "Error receiving mDNS response", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in service type discovery", e);
        } finally {
            socket.close();
        }

        return types;
    }

    /**
     * Inicia descubrimiento rápido de instancias (3 segundos)
     */
    private void startSubDiscovery(String serviceType, CancellationToken token,
                                   CountDownLatch latch, ProgressCallback callback) {
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicInteger instancesFound = new AtomicInteger(0);

        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (token.isCancelled() || stopped.get()) return;

                instancesFound.incrementAndGet();

                // Resolver inmediatamente para obtener la IP
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
                    latch.countDown();
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                // No necesario para escaneo rápido
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Start failed: " + serviceType + " error=" + errorCode);
                if (!stopped.getAndSet(true)) {
                    latch.countDown();
                }
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop failed: " + serviceType + " error=" + errorCode);
                if (!stopped.getAndSet(true)) {
                    latch.countDown();
                }
            }
        };

        synchronized (activeListeners) {
            activeListeners.add(listener);
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);

        // Detener después del tiempo configurado
        new Thread(() -> {
            try {
                Thread.sleep(INSTANCE_DISCOVERY_TIME);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (!stopped.get() && !token.isCancelled()) {
                    stopDiscovery(listener);
                }
            }
        }).start();
    }

    private void stopDiscovery(NsdManager.DiscoveryListener listener) {
        try {
            nsdManager.stopServiceDiscovery(listener);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping discovery", e);
        }
    }

    private void stopAllDiscoveries() {
        synchronized (activeListeners) {
            for (NsdManager.DiscoveryListener listener : activeListeners) {
                try {
                    nsdManager.stopServiceDiscovery(listener);
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping discovery", e);
                }
            }
            activeListeners.clear();
        }
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

        // Actualizar información del dispositivo
        if (info.getServiceName() != null && (device.getHostname() == null || device.getHostname().isEmpty())) {
            device.setHostname(info.getServiceName());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<String, byte[]> attributes = info.getAttributes();
            if (attributes.containsKey("md") && device.getModel() == null) {
                device.setModel(new String(attributes.get("md")));
            } else if (attributes.containsKey("modelid") && device.getModel() == null) {
                device.setModel(new String(attributes.get("modelid")));
            }
        }

        if (callback != null) {
            callback.onDeviceFound(device);
        }
    }

    // ========================
    // Clase interna con utilidades mDNS
    // ========================
    private static class MdnsUtils {
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
                // IPv6 puede fallar si no está disponible
            }
        }

        private static void writeDnsName(DataOutputStream dos, String name) throws IOException {
            String n = name;
            if (n.endsWith(".")) {
                n = n.substring(0, n.length() - 1);
            }
            for (String label : n.split("\\.")) {
                byte[] bytes = label.getBytes("UTF-8");
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

            if (savedPos != -1) {
                buf.position(savedPos);
            }

            return sb.toString();
        }

        private static void skipDnsName(ByteBuffer buf) {
            while (buf.remaining() > 0) {
                int len = buf.get() & 0xFF;
                if (len == 0) break;
                if ((len & 0xC0) == 0xC0) {
                    buf.get(); // saltar segundo byte
                    break;
                }
                if (buf.remaining() < len) break;
                buf.position(buf.position() + len);
            }
        }

        private static String extractServiceType(String target) {
            if (target == null) return null;
            String t = target;
            if (t.endsWith(".")) {
                t = t.substring(0, t.length() - 1);
            }
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