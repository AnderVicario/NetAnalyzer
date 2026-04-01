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
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.data.ScanState;
import com.av19.netanalyzer.discovery.ARPDiscovery;
import com.av19.netanalyzer.discovery.CancellationToken;
import com.av19.netanalyzer.discovery.ICMPDiscovery;
import com.av19.netanalyzer.discovery.NetworkScanner;
import com.av19.netanalyzer.discovery.TCPDiscovery;
import com.av19.netanalyzer.discovery.mDNSDiscovery;
import com.av19.netanalyzer.repository.ScanRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanService extends Service {
    private static final String CHANNEL_ID = "scan_channel";
    private static final int NOTIFICATION_ID = 1;
    private final List<DeviceInfo> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
    private ScanRepository repository;
    private CancellationToken cancellationToken;
    private NetworkScanner networkScanner;
    private Handler mainHandler;
    private long lastNotificationUpdate = 0;
    private static final long NOTIFICATION_THROTTLE_MS = 500;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = ScanRepository.getInstance();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_SCAN".equals(intent.getAction())) {
            cancelScan();
            stopSelf();
            return START_NOT_STICKY;
        }

        String scanMethod = getScanMethod(intent);
        String scanLevel = getScanLevel(intent);
        int[] ports = loadPortsFromAssets("top" + scanLevel + ".txt");

        startForeground(NOTIFICATION_ID, createNotification("Iniciando escaneo..."));
        startScan(scanMethod, ports);

        return START_STICKY;
    }

    private void startScan(String scanMethod, int[] ports) {
        cancellationToken = new CancellationToken();

        NetworkInfo networkInfo = collectNetworkInfo();
        if (networkInfo == null) {
            repository.setError("No se pudo obtener información de red", ScanState.Phase.DISCOVERY, "Network Analysis");
            stopSelf();
            return;
        }

        networkScanner = new NetworkScanner(networkInfo, ports);

        // Añadir métodos según configuración
        List<String> methods = parseMethods(scanMethod);
        for (String m : methods) {
            switch (m) {
                case "ARP":
                    networkScanner.addMethod(new ARPDiscovery());
                    break;
                case "ICMP":
                    networkScanner.addMethod(new ICMPDiscovery());
                    break;
                case "TCP":
                    networkScanner.addMethod(new TCPDiscovery());
                    break;
                case "mDNS":
                    networkScanner.addMethod(new mDNSDiscovery(this));
                    break;
                // case "MDNS": networkScanner.addMethod(new MdnsDiscovery(this)); break;
            }
        }

        networkScanner.start(cancellationToken, new NetworkScanner.Callback() {
            @Override
            public void onDiscoveryProgress(String methodName, int progressPercent) {
                // Actualizar la barra de progreso y la lista de dispositivos en el repositorio
                // Esto restaura el comportamiento original: cada vez que se avanza en el descubrimiento,
                // se actualiza la UI con el porcentaje y la lista actual de dispositivos.
                // Hacemos una copia de la lista para evitar problemas de concurrencia.
                List<DeviceInfo> snapshot = new ArrayList<>(discoveredDevices);
                repository.setScanning(progressPercent, ScanState.Phase.DISCOVERY, methodName, null, snapshot, networkInfo);
                updateNotification("Descubrimiento " + methodName + ": " + progressPercent + "%");
            }

            @Override
            public void onDeviceFound(DeviceInfo device) {
                // Añadir dispositivo a la lista acumulada
                discoveredDevices.add(device);
                // No actualizamos el repositorio aquí porque ya lo hará onDiscoveryProgress con el progreso actual.
                // Pero si quieres que aparezca inmediatamente el dispositivo, podrías llamar a setScanning
                // con el progreso actual y la lista actualizada. Lo dejamos así para evitar llamadas redundantes.
                updateNotification("Dispositivo encontrado: " + device.getIp());
            }

            @Override
            public void onPortScanProgress(int current, int total, String currentIp, List<DeviceInfo> currentDevices) {
                int percent = (int) ((current / (float) total) * 100);
                updateNotification("Escaneando puertos: " + currentIp + " (" + percent + "%)");
                repository.setScanning(percent, ScanState.Phase.PORT_SCAN, "NIO", currentIp, currentDevices, networkInfo);
            }

            @Override
            public void onComplete(List<DeviceInfo> devices) {
                repository.setCompleted(devices, networkInfo);
                updateNotification("Escaneo completado: " + devices.size() + " dispositivos");
                stopSelf();
            }

            @Override
            public void onCancelled() {
                repository.setError("Escaneo cancelado", ScanState.Phase.NONE, null);
                stopSelf();
            }
        });
    }

    private void cancelScan() {
        if (cancellationToken != null) {
            cancellationToken.cancel();
        }
    }

    // ---------- Métodos auxiliares (extraídos del original) ----------

    @SuppressLint("DefaultLocale")
    private NetworkInfo collectNetworkInfo() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null) return null;

        LinkProperties lp = cm.getLinkProperties(network);
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (lp == null || caps == null) return null;

        String ip = null, netmask = null, networkAddr = null, gateway = null;
        ArrayList<String> dnsServers = new ArrayList<>();
        int prefix = 0;
        String connectionType = "Unknown";
        boolean hasInternet = false, validated = false, metered = true;
        int downstream = 0, upstream = 0;
        String ssid = null, bssid = null;
        int rssi = 0, linkSpeed = 0, frequency = 0;

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

        List<InetAddress> dnsList = lp.getDnsServers();
        for (InetAddress dnsAddr : dnsList) {
            dnsServers.add(dnsAddr.getHostAddress());
        }

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

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

        return new NetworkInfo(ip, netmask, prefix, networkAddr, gateway, dnsServers,
                connectionType, hasInternet, validated, metered,
                downstream, upstream, ssid, bssid, rssi, linkSpeed, frequency);
    }

    private List<String> parseMethods(String method) {
        List<String> methods = new ArrayList<>();
        if (method == null || method.isEmpty() || method.equals("AUTO")) {
            // Por defecto: ICMP y ARP
            methods.add("ICMP");
            methods.add("mDNS");
            if (android.os.Build.VERSION.SDK_INT < 29) {
                methods.add("ARP");
            }
            return methods;
        }
        String[] parts = method.split("[+,]");
        for (String part : parts) {
            String trimmed = part.trim().toUpperCase();
            if (trimmed.equals("ARP") || trimmed.equals("ICMP") || trimmed.equals("TCP") || trimmed.equals("MDNS")) {
                methods.add(trimmed);
            }
        }
        if (methods.isEmpty()) {
            methods.add("ICMP");
            methods.add("ARP");
            methods.add("MDNS");
        }
        return methods;
    }

    private String getScanMethod(Intent intent) {
        if (intent != null && intent.getStringExtra("SCAN_METHOD") != null) {
            return intent.getStringExtra("SCAN_METHOD");
        }
        return getSharedPreferences("app_settings", MODE_PRIVATE)
                .getString("scan_method", "AUTO");
    }

    private String getScanLevel(Intent intent) {
        if (intent != null && intent.getStringExtra("SCAN_LEVEL") != null) {
            return intent.getStringExtra("SCAN_LEVEL");
        }
        return getSharedPreferences("app_settings", MODE_PRIVATE)
                .getString("scan_level", "100");
    }

    private int[] loadPortsFromAssets(String fileName) {
        List<Integer> portList = new ArrayList<>();
        Pattern pattern = Pattern.compile("(\\d+)/tcp");

        try (InputStream is = getAssets().open(fileName);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    portList.add(Integer.parseInt(matcher.group(1)));
                }
            }
        } catch (IOException e) {
            Log.e("ScanService", "Error cargando puertos: " + e.getMessage());
            return new int[]{80, 23, 443, 21, 22, 25, 3389, 110, 445, 139, 143, 53, 135};
        }
        portList.add(25565);
        return portList.stream().mapToInt(i -> i).toArray();
    }

    private void updateNotification(String text) {
        long now = System.currentTimeMillis();
        if (now - lastNotificationUpdate < NOTIFICATION_THROTTLE_MS) {
            return; // no actualizar tan seguido
        }
        lastNotificationUpdate = now;
        mainHandler.post(() -> {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID, createNotification(text));
        });
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}