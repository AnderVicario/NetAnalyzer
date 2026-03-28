package com.av19.netanalyzer.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.av19.netanalyzer.ApiClient;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.ui.main.MainActivity;
import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.ScanState;
import com.av19.netanalyzer.repository.ScanRepository;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class ScanService extends Service {
    private static final String CHANNEL_ID = "scan_channel";
    private static final int NOTIFICATION_ID = 1;

    private ScanRepository repository;
    private ExecutorService mainExecutor;      // ejecutor principal del servicio
    private ExecutorService discoveryExecutor; // para descubrimiento
    private ExecutorService portScanExecutor;  // para escaneo de puertos
    private Handler mainHandler;
    private boolean isScanning = false;
    private NetworkInfo currentNetworkInfo;

    private int networkInt;
    private int mask;
    private int[] ports;
    private String currentScanMethod = "AUTO";
    private String currentScanLevel = "100";

    @Override
    public void onCreate() {
        super.onCreate();
        repository = ScanRepository.getInstance();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // 1. Manejar acción de parada
            if ("STOP_SCAN".equals(intent.getAction())) {
                stopScan();
                stopSelf();
                return START_NOT_STICKY;
            }

            // 2. Extraer parámetros del Intent o SharedPreferences (Fallback)
            currentScanLevel = intent.getStringExtra("SCAN_LEVEL");
            currentScanMethod = intent.getStringExtra("SCAN_METHOD");

            if (currentScanLevel == null || currentScanMethod == null) {
                android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
                if (currentScanLevel == null) currentScanLevel = prefs.getString("scan_level", "100");
                if (currentScanMethod == null) currentScanMethod = prefs.getString("scan_method", "AUTO");
            }

            // 3. Cargar los puertos según el nivel seleccionado
            String fileName = "top" + currentScanLevel + ".txt";
            this.ports = loadPortsFromAssets(fileName);

            Log.d("ScanService", "Config: Method=" + currentScanMethod + ", Level=" + currentScanLevel);
        }

        // 4. Iniciar el servicio en primer plano y ejecutar escaneo
        startForeground(NOTIFICATION_ID, createNotification("Escaneando red en modo " + currentScanMethod + "..."));
        startScan();

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startScan() {
        if (isScanning) return;
        isScanning = true;

        mainExecutor = Executors.newSingleThreadExecutor();
        mainExecutor.execute(() -> {
            try {
                repository.reset();
                getNetworkDetails();
                currentNetworkInfo = collectNetworkInfo();

                // 1. Parsear métodos de descubrimiento
                List<String> discoveryMethods = parseDiscoveryMethods(currentScanMethod);
                if (discoveryMethods.contains("AUTO")) {
                    discoveryMethods.clear();
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        discoveryMethods.add("ARP");
                        discoveryMethods.add("ICMP");
                    } else {
                        discoveryMethods.add("TCP");
                        discoveryMethods.add("ICMP");
                    }
                }
                Log.d("ScanService", "Discovery methods: " + discoveryMethods);

                // 2. Descubrir hosts
                List<DeviceInfo> discoveredDevices = performDiscovery(discoveryMethods);

                // 3. Escanear puertos
                List<DeviceInfo> finalDevices = scanPortsForDevices(discoveredDevices);

                // 4. Finalizar
                repository.setCompleted(finalDevices, currentNetworkInfo);

            } catch (Exception e) {
                repository.setError(e.getMessage());
            } finally {
                isScanning = false;
                stopForeground(true);
                stopSelf();
                if (mainExecutor != null) mainExecutor.shutdownNow();
            }
        });
    }

    private List<String> parseDiscoveryMethods(String method) {
        List<String> methods = new ArrayList<>();
        if (method == null || method.isEmpty()) {
            methods.add("AUTO");
            return methods;
        }
        if (method.equals("AUTO")) {
            methods.add("AUTO");
            return methods;
        }
        String[] parts = method.split("[+,]");
        for (String part : parts) {
            String trimmed = part.trim().toUpperCase();
            if (trimmed.equals("ARP") || trimmed.equals("ICMP") || trimmed.equals("TCP")) {
                methods.add(trimmed);
            }
        }
        if (methods.isEmpty()) methods.add("AUTO");
        return methods;
    }


    private void stopScan() {
        isScanning = false;
        if (discoveryExecutor != null && !discoveryExecutor.isShutdown()) {
            discoveryExecutor.shutdownNow();
        }
        if (portScanExecutor != null && !portScanExecutor.isShutdown()) {
            portScanExecutor.shutdownNow();
        }
        if (mainExecutor != null && !mainExecutor.isShutdown()) {
            mainExecutor.shutdownNow();
        }
        repository.setError("Escaneo cancelado");
    }

    private List<DeviceInfo> performDiscovery(List<String> methods) {
        Map<String, DeviceInfo> deviceMap = new HashMap<>();
        int totalMethods = methods.size();
        int currentIdx = 0;

        for (String method : methods) {
            if (!isScanning) break; // cancelado

            List<DeviceInfo> result = null;
            switch (method) {
                case "ARP":
                    result = discoverWithArp();
                    break;
                case "ICMP":
                    result = discoverWithIcmp();
                    break;
                case "TCP":
                    result = discoverWithTcp();
                    break;
            }
            if (result != null) {
                for (DeviceInfo dev : result) {
                    String ip = dev.getIp();
                    if (deviceMap.containsKey(ip)) {
                        DeviceInfo existing = deviceMap.get(ip);
                        // Si el nuevo tiene MAC y el actual no, lo actualizamos
                        if ((existing.getMac() == null || existing.getMac().isEmpty()) &&
                                dev.getMac() != null && !dev.getMac().isEmpty()) {
                            existing.setMac(dev.getMac());
                            existing.setVendor(dev.getVendor());
                        }
                    } else {
                        deviceMap.put(ip, dev);
                    }
                }
            }
            currentIdx++;
            int progress = (int) ((currentIdx / (float) totalMethods) * 100);
            repository.setScanning(progress, "Descubrimiento con " + method,
                    new ArrayList<>(deviceMap.values()), currentNetworkInfo);
        }
        return new ArrayList<>(deviceMap.values());
    }

    @SuppressLint("DefaultLocale")
    private NetworkInfo collectNetworkInfo() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return null;

        LinkProperties lp = cm.getLinkProperties(network);
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (lp == null || caps == null) return null;

        String ip = null, netmask = null, networkAddr = null, gateway = null, dns = null;
        int prefix = 0;
        String connectionType = "Unknown";
        boolean hasInternet = false, validated = false, metered = true;
        int downstream = 0, upstream = 0;
        String ssid = null, bssid = null;
        int rssi = 0, linkSpeed = 0, frequency = 0;

        // --- IP and network details ---
        for (LinkAddress la : lp.getLinkAddresses()) {
            InetAddress addr = la.getAddress();
            if (addr instanceof Inet4Address) {
                ip = addr.getHostAddress();
                prefix = la.getPrefixLength();
                int maskInt = 0xffffffff << (32 - prefix);
                netmask = String.format("%d.%d.%d.%d",
                        (maskInt >> 24) & 0xff, (maskInt >> 16) & 0xff,
                        (maskInt >> 8) & 0xff, maskInt & 0xff);
                byte[] bytes = addr.getAddress();
                int ipInt = ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) |
                        ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
                int networkInt = ipInt & maskInt;
                networkAddr = String.format("%d.%d.%d.%d",
                        (networkInt >> 24) & 0xff, (networkInt >> 16) & 0xff,
                        (networkInt >> 8) & 0xff, networkInt & 0xff);
                break;
            }
        }

        // --- Gateway ---
        for (RouteInfo route : lp.getRoutes()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (route.hasGateway()) {
                    gateway = route.getGateway().getHostAddress();
                    break;
                }
            } else {
                InetAddress gw = route.getGateway();
                if (gw != null && !gw.isAnyLocalAddress()) {
                    gateway = gw.getHostAddress();
                    break;
                }
            }
        }

        // --- DNS servers ---
        List<InetAddress> dnsList = lp.getDnsServers();
        if (!dnsList.isEmpty()) {
            dns = dnsList.get(0).getHostAddress();
        }

        // --- Connection type and capabilities ---
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            connectionType = "WiFi";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            connectionType = "Cellular";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            connectionType = "VPN";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            connectionType = "Ethernet";
        }

        hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        downstream = caps.getLinkDownstreamBandwidthKbps();
        upstream = caps.getLinkUpstreamBandwidthKbps();

        // --- WiFi specific info (only if WiFi and permissions granted) ---
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Need ACCESS_FINE_LOCATION permission for SSID/BSSID
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    if (wifiInfo != null) {
                        ssid = wifiInfo.getSSID();
                        bssid = wifiInfo.getBSSID();
                        rssi = wifiInfo.getRssi();
                        linkSpeed = wifiInfo.getLinkSpeed();
                        frequency = wifiInfo.getFrequency();
                    }
                }
            } else {
                // Older versions: no location permission needed for SSID
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    ssid = wifiInfo.getSSID();
                    bssid = wifiInfo.getBSSID();
                    rssi = wifiInfo.getRssi();
                    linkSpeed = wifiInfo.getLinkSpeed();
                    frequency = wifiInfo.getFrequency();
                }
            }
        }
        String TAG = "NetworkAnalyzer";

        Log.d(TAG, "=== NETWORK ANALYSIS RESULT ===");
        Log.d(TAG, "IP: " + ip);
        Log.d(TAG, "Netmask: " + netmask + " /" + prefix);
        Log.d(TAG, "Network: " + networkAddr);
        Log.d(TAG, "Gateway: " + gateway);
        Log.d(TAG, "DNS: " + dns);
        Log.d(TAG, "Type: " + connectionType);
        Log.d(TAG, "Internet: " + hasInternet + " | Validated: " + validated);
        Log.d(TAG, "Metered: " + metered);
        Log.d(TAG, "Downstream: " + downstream + " kbps");
        Log.d(TAG, "Upstream: " + upstream + " kbps");

        if ("WiFi".equals(connectionType)) {
            Log.d(TAG, "--- WIFI INFO ---");
            Log.d(TAG, "SSID: " + ssid);
            Log.d(TAG, "BSSID: " + bssid);
            Log.d(TAG, "RSSI: " + rssi + " dBm");
            Log.d(TAG, "LinkSpeed: " + linkSpeed + " Mbps");
            Log.d(TAG, "Frequency: " + frequency + " MHz");
        }

        return new NetworkInfo(ip, netmask, prefix, networkAddr, gateway, dns,
                connectionType, hasInternet, validated, metered,
                downstream, upstream, ssid, bssid, rssi, linkSpeed, frequency);
    }

    private void getNetworkDetails() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) {
            throw new RuntimeException("No active network");
        }
        LinkProperties lp = cm.getLinkProperties(network);
        if (lp == null) {
            throw new RuntimeException("No link properties");
        }
        for (LinkAddress la : lp.getLinkAddresses()) {
            InetAddress ip = la.getAddress();
            int prefix = la.getPrefixLength();
            if (ip instanceof Inet4Address) {
                byte[] addr = ip.getAddress();
                int ipInt = ((addr[0] & 0xff) << 24) |
                        ((addr[1] & 0xff) << 16) |
                        ((addr[2] & 0xff) << 8) |
                        (addr[3] & 0xff);
                mask = 0xffffffff << (32 - prefix);
                networkInt = ipInt & mask;
                break;
            }
        }
    }

    private List<DeviceInfo> discoverWithIcmp() {
        List<DeviceInfo> devices = new ArrayList<>();
        try {
            int first = networkInt + 1;
            int last = (networkInt | ~mask) - 1;
            int totalHosts = last - first + 1;
            AtomicInteger scanned = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(totalHosts);
            discoveryExecutor = Executors.newFixedThreadPool(30);

            for (int host = first; host <= last; host++) {
                final int currentHost = host;
                discoveryExecutor.execute(() -> {
                    try {
                        @SuppressLint("DefaultLocale") String ip = String.format("%d.%d.%d.%d",
                                (currentHost >> 24) & 0xff,
                                (currentHost >> 16) & 0xff,
                                (currentHost >> 8) & 0xff,
                                currentHost & 0xff);
                        if (pingHost(ip, 1, 700)) {
                            synchronized (devices) {
                                devices.add(new DeviceInfo(ip, null, null, new ArrayList<>()));
                            }
                        }
                        int progress = (int) (scanned.incrementAndGet() * 100.0 / totalHosts);
                        repository.setScanning(progress, ip, devices, currentNetworkInfo);
                    } catch (Exception e) {
                        scanned.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            if (discoveryExecutor != null) discoveryExecutor.shutdown();

        } catch (Exception e) {
            Log.e("ScanService", "Error en ICMP discovery", e);
        }
        return devices;
    }

    private boolean pingHost(String ip, int count, int timeoutMs) {
        try {
            int timeoutSec = Math.max(1, timeoutMs / 1000);

            String command = "ping -c " + count + " -W " + timeoutSec + " " + ip;

            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            return exitCode == 0;   // 0 = dispositivo respondió

        } catch (Exception e) {
            return false;
        }
    }

    private List<DeviceInfo> discoverWithArp() {
        List<DeviceInfo> devices = new ArrayList<>();
        try {
            int first = networkInt + 1;
            int last = (networkInt | ~mask) - 1;
            int total = last - first + 1;
            AtomicInteger current = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(total);
            discoveryExecutor = Executors.newFixedThreadPool(50);

            for (int host = first; host <= last; host++) {
                final int currentHost = host;
                discoveryExecutor.execute(() -> {
                    try {
                        @SuppressLint("DefaultLocale") String ip = String.format("%d.%d.%d.%d",
                                (currentHost >> 24) & 0xff,
                                (currentHost >> 16) & 0xff,
                                (currentHost >> 8) & 0xff,
                                currentHost & 0xff);
                        InetAddress.getByName(ip).isReachable(300);
                        int progress = (int) (current.incrementAndGet() * 100.0 / total);
                        repository.setScanning(progress, ip, devices, currentNetworkInfo);
                    } catch (Exception e) {
                        current.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            if (discoveryExecutor != null) discoveryExecutor.shutdown();

            // Leer tabla ARP
            BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"));
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 4 && !parts[3].equals("00:00:00:00:00:00")) {
                    String ip = parts[0];
                    String mac = parts[3];
                    String vendor = ApiClient.getMacVendorSync(mac);
                    devices.add(new DeviceInfo(ip, mac, vendor, new ArrayList<>()));
                }
            }
            br.close();

        } catch (Exception e) {
            Log.e("ScanService", "Error en ARP discovery", e);
        }
        return devices;
    }

    private List<DeviceInfo> discoverWithTcp() {
        List<DeviceInfo> devices = new ArrayList<>();
        try {
            int first = networkInt + 1;
            int last = (networkInt | ~mask) - 1;
            int total = last - first + 1;
            AtomicInteger scanned = new AtomicInteger(0);
            discoveryExecutor = Executors.newFixedThreadPool(20);

            for (int host = first; host <= last; host++) {
                final int currentHost = host;
                discoveryExecutor.execute(() -> {
                    @SuppressLint("DefaultLocale") String ip = String.format("%d.%d.%d.%d",
                            (currentHost >> 24) & 0xff,
                            (currentHost >> 16) & 0xff,
                            (currentHost >> 8) & 0xff,
                            currentHost & 0xff);
                    if (isAlive(ip)) {
                        synchronized (devices) {
                            devices.add(new DeviceInfo(ip, "", "", new ArrayList<>()));
                        }
                    }
                    int progress = (int) (scanned.incrementAndGet() * 100.0 / total);
                    repository.setScanning(progress, ip, devices, currentNetworkInfo);
                });
            }

            discoveryExecutor.shutdown();
            discoveryExecutor.awaitTermination(5, TimeUnit.MINUTES);

        } catch (Exception e) {
            Log.e("ScanService", "Error en TCP discovery", e);
        }
        return devices;
    }

    private List<DeviceInfo> scanPortsForDevices(List<DeviceInfo> devices) {
        List<DeviceInfo> result = new ArrayList<>(devices);
        int total = result.size();
        AtomicInteger completed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(total);
        portScanExecutor = Executors.newFixedThreadPool(10);

        for (DeviceInfo device : result) {
            portScanExecutor.execute(() -> {
                try {
                    List<Integer> openPorts = scanPortsNio(device.getIp());
                    device.setOpenPorts(openPorts);
                } catch (Exception e) {
                    Log.e("ScanService", "Error escaneando puertos de " + device.getIp(), e);
                } finally {
                    int done = completed.incrementAndGet();
                    int progress = (int) ((done / (float) total) * 100);
                    // Pasamos una copia de la lista para que el UI vea los cambios
                    List<DeviceInfo> snapshot = new ArrayList<>(result);
                    repository.setScanning(progress, device.getIp(), snapshot, currentNetworkInfo);
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (portScanExecutor != null) portScanExecutor.shutdownNow();
        return result;
    }

    private List<Integer> scanPortsNio(String host) {
        List<Integer> openPorts = new ArrayList<>();
        // Mapa para rastrear cuándo se registró cada canal
        Map<SelectionKey, Long> timestamps = new HashMap<>();
        long timeoutMillis = 1000; // Tiempo máximo de espera por puerto

        try (Selector selector = Selector.open()) {
            InetAddress addr = InetAddress.getByName(host);
            int maxInflight = 50; // Bajamos esto para no saturar el socket del móvil
            int inflight = 0;
            int index = 0;

            while (index < ports.length || inflight > 0) {
                // 1. Lanzar nuevas conexiones si hay hueco
                while (index < ports.length && inflight < maxInflight) {
                    int port = ports[index++];
                    try {
                        SocketChannel ch = SocketChannel.open();
                        ch.configureBlocking(false);
                        ch.connect(new InetSocketAddress(addr, port));
                        SelectionKey key = ch.register(selector, SelectionKey.OP_CONNECT, port);
                        timestamps.put(key, System.currentTimeMillis());
                        inflight++;
                    } catch (IOException e) { /* puerto local ocupado o error inmediato */ }
                }

                // 2. Esperar eventos (bloqueo corto)
                if (selector.select(200) > 0) {
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();
                        SocketChannel ch = (SocketChannel) key.channel();
                        try {
                            if (ch.finishConnect()) {
                                openPorts.add((Integer) key.attachment());
                            }
                        } catch (Exception ignored) {
                        } finally {
                            timestamps.remove(key);
                            ch.close();
                            inflight--;
                        }
                    }
                }

                // 3. LIMPIEZA DE ZOMBIS (Crucial para que no se quede al 99%)
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<SelectionKey, Long>> it = timestamps.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<SelectionKey, Long> entry = it.next();
                    if (now - entry.getValue() > timeoutMillis) {
                        SelectionKey key = entry.getKey();
                        try {
                            key.channel().close();
                        } catch (IOException ignored) {}
                        key.cancel();
                        it.remove();
                        inflight--; // Liberamos el hueco
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ScanService", "Error en scanPorts para " + host, e);
        }
        return openPorts;
    }

    private boolean isAlive(String host) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, 80), 200);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Notification createNotification(String content) {
        Intent stopIntent = new Intent(this, ScanService.class);
        stopIntent.setAction("STOP_SCAN");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Network Scanner")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_notification)
                .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Scan Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private int[] loadPortsFromAssets(String fileName) {
        List<Integer> portList = new ArrayList<>();
        // Regex: busca dígitos seguidos de /tcp
        Pattern pattern = Pattern.compile("(\\d+)/tcp");

        try (InputStream is = getAssets().open(fileName);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {

            String line;
            while ((line = br.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    // El grupo 1 es el número (\d+)
                    portList.add(Integer.parseInt(matcher.group(1)));
                }
            }
        } catch (IOException e) {
            Log.e("ScanService", "Error cargando puertos: " + e.getMessage());
            // Backup por si falla la lectura
            return new int[]{80, 23, 443, 21, 22, 25, 3389, 110, 445, 139, 143, 53, 135};
        }
        portList.add(25565);
        // Convertir List<Integer> a int[]
        return portList.stream().mapToInt(i -> i).toArray();
    }
}