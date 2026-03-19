package com.av19.netanalyzer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import okhttp3.Call;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private LinearLayout outputContainer;
    private TextView progressText;
    private ProgressBar progressBar;
    private Button scanButton;

    private ExecutorService executor;
    private final int[] ports = IntStream.rangeClosed(1, 1000).toArray();
    private boolean isScanning = false;
    private AtomicInteger scannedHosts = new AtomicInteger(0);
    private int networkInt;
    private int mask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }

        setContentView(R.layout.activity_main);

        outputContainer = findViewById(R.id.outputContainer);
        progressText = findViewById(R.id.progressText);
        progressBar = findViewById(R.id.progressBar);
        scanButton = findViewById(R.id.scanButton);

        setupWindowInsets();
        scanButton.setOnClickListener(v -> startScan());

        int api = Build.VERSION.SDK_INT;
        append("📱 Android API: " + api, R.color.on_terminal);

        if (api < Build.VERSION_CODES.Q) {
            append("✓ Modo ARP disponible (Android ≤9)", R.color.on_terminal);
        } else {
            append("⚠ Modo limitado (Android ≥10, sin acceso ARP)", R.color.on_terminal);
        }
        checkLocationStatus();
        addDivider();
    }

    private void append(String text, @ColorRes int colorRes) {
        runOnUiThread(() -> {
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(13);
            tv.setPadding(0, 4, 0, 4);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextColor(ContextCompat.getColor(this, colorRes));
            outputContainer.addView(tv);
        });
    }

    private void addDivider() {
        runOnUiThread(() -> {
            View divider = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 2);
            params.setMargins(0, 15, 0, 15);
            divider.setLayoutParams(params);
            divider.setBackgroundColor(Color.LTGRAY);
            outputContainer.addView(divider);
        });
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });
    }

    private void checkLocationStatus() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            boolean permissionGranted =
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;

            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            boolean locationEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!permissionGranted) {

                append("⚠ Permiso de ubicación no concedido", R.color.warning);
                append("ℹ Necesario para mostrar SSID y detalles WiFi (requisito de Android)", R.color.warning);

                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        1001
                );

            } else if (!locationEnabled) {

                append("⚠ Ubicación desactivada", R.color.warning);
                append("ℹ Actívala para ver el nombre de la red (SSID)", R.color.warning);

            } else {

                append("✓ Acceso a SSID y detalles WiFi disponible", R.color.on_terminal);
            }
        }
    }

    private void startScan() {
        if (isScanning) {
            append("⚠ Escaneo ya en progreso...", R.color.on_terminal);
            addDivider();
            return;
        }

        isScanning = true;
        scannedHosts.set(0);

        runOnUiThread(() -> {
            outputContainer.removeAllViews();
            scanButton.setEnabled(false);
            scanButton.setText("Escaneando...");
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            progressText.setText("Iniciando escaneo...");
        });

        executor = Executors.newFixedThreadPool(30);
        int api = Build.VERSION.SDK_INT;

        if (api < Build.VERSION_CODES.Q) {
            append("🔍 Iniciando escaneo ARP...", R.color.on_terminal);
            addDivider();
            scanWithArp();
        } else {
            append("🔍 Iniciando escaneo TCP...", R.color.on_terminal);
            addDivider();
            scanWithTcp();
        }
    }

    private void getNetworkDetails(){
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        Network network = cm.getActiveNetwork();
        if (network == null) return;

        LinkProperties lp = cm.getLinkProperties(network);
        if (lp == null) return;

        for (LinkAddress la : lp.getLinkAddresses()) {

            InetAddress ip = la.getAddress();
            int prefix = la.getPrefixLength();   // máscara en formato CIDR

            if (ip instanceof Inet4Address) {

                String ipStr = ip.getHostAddress();

                // convertir /24 → 255.255.255.0
                mask = 0xffffffff << (32 - prefix);
                @SuppressLint("DefaultLocale") String netmask = String.format(
                        "%d.%d.%d.%d",
                        (mask >> 24) & 0xff,
                        (mask >> 16) & 0xff,
                        (mask >> 8) & 0xff,
                        mask & 0xff
                );

                // calcular red
                byte[] addr = ip.getAddress();
                int ipInt =
                        ((addr[0] & 0xff) << 24) |
                                ((addr[1] & 0xff) << 16) |
                                ((addr[2] & 0xff) << 8) |
                                (addr[3] & 0xff);

                networkInt = ipInt & mask;

                @SuppressLint("DefaultLocale") String networkAddr = String.format(
                        "%d.%d.%d.%d",
                        (networkInt >> 24) & 0xff,
                        (networkInt >> 16) & 0xff,
                        (networkInt >> 8) & 0xff,
                        networkInt & 0xff
                );

                append("🌐 IP: " + ipStr, R.color.on_terminal);
                append("🎭 Máscara: " + netmask + " (/" + prefix + ")", R.color.on_terminal);
                append("📡 Red: " + networkAddr + "/" + prefix, R.color.on_terminal);
                addDivider();
            }
        }
    }

    @SuppressLint({"MissingPermission"})
    private void getAdvancedNetworkDetails() {

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        Network network = cm.getActiveNetwork();
        if (network == null) {
            append("❌ Sin red activa", R.color.error);
            return;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        LinkProperties lp = cm.getLinkProperties(network);

        if (caps == null || lp == null) return;

        // 📡 Tipo de red
        String transport = "Desconocido";

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            transport = "WiFi";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            transport = "Datos móviles";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            transport = "VPN";
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            transport = "Ethernet";
        }

        append("📡 Tipo: " + transport, R.color.on_terminal);

        // 🌍 Estado de internet
        boolean hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

        append("🌐 Internet: " + (hasInternet ? "Sí" : "No"), R.color.on_terminal);
        append("✔️ Validada: " + (validated ? "Sí (acceso real)" : "No"), R.color.on_terminal);

        // 🔐 Seguridad (nivel básico)
        boolean notMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        append("💸 Red medida: " + (notMetered ? "No (WiFi usualmente)" : "Sí (datos móviles)"), R.color.on_terminal);

        // ⚡ Velocidad estimada
        append("⚡ Downstream: " + caps.getLinkDownstreamBandwidthKbps() + " kbps", R.color.on_terminal);
        append("⚡ Upstream: " + caps.getLinkUpstreamBandwidthKbps() + " kbps", R.color.on_terminal);

        // 🌐 DNS
        for (InetAddress dns : lp.getDnsServers()) {
            append("🧭 DNS: " + dns.getHostAddress(), R.color.on_terminal);
        }

        // 🚪 Gateway
        for (RouteInfo route : lp.getRoutes()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (route.hasGateway()) {
                    append("🚪 Gateway: " + route.getGateway().getHostAddress(), R.color.on_terminal);
                }
            }
            else {
                InetAddress gateway = route.getGateway();

                if (gateway != null && !gateway.isAnyLocalAddress()) {
                    append("🚪 Gateway: " + gateway.getHostAddress(), R.color.on_terminal);
                }
            }
        }

        // 🌐 IPs
        for (LinkAddress la : lp.getLinkAddresses()) {
            append("🌐 IP: " + la.getAddress().getHostAddress() + "/" + la.getPrefixLength(), R.color.on_terminal);
        }

        // 📶 Info específica de WiFi
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {

            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();

            if (wifiInfo != null) {
                append("📶 SSID: " + wifiInfo.getSSID(), R.color.on_terminal);
                append("📡 BSSID: " + wifiInfo.getBSSID(), R.color.on_terminal);
                append("📊 RSSI: " + wifiInfo.getRssi() + " dBm", R.color.on_terminal);
                append("⚙️ Velocidad enlace: " + wifiInfo.getLinkSpeed() + " Mbps", R.color.on_terminal);
                append("📡 Frecuencia: " + wifiInfo.getFrequency() + " MHz", R.color.on_terminal);
            }
        }

        addDivider();
    }

    private void scanWithArp() {
        getNetworkDetails();
        getAdvancedNetworkDetails();
        executor.execute(() -> {
            try {
                int first = networkInt + 1;
                int last = (networkInt | ~mask) - 1;

                append("📡 Escaneo ARP iniciado", R.color.on_terminal);
                addDivider();

                updateProgress(0, "Poblando tabla ARP...");

                int total = last - first + 1;
                AtomicInteger current = new AtomicInteger(0);
                CountDownLatch latch = new CountDownLatch(total);

                runOnUiThread(() -> progressBar.setMax(total));

                // Ejecutar todos los pings en paralelo
                for (int host = first; host <= last; host++) {
                    final int currentHost = host;
                    executor.execute(() -> {
                        try {
                            @SuppressLint("DefaultLocale") String ip = String.format("%d.%d.%d.%d",
                                    (currentHost >> 24) & 0xff,
                                    (currentHost >> 16) & 0xff,
                                    (currentHost >> 8) & 0xff,
                                    currentHost & 0xff);

                            InetAddress.getByName(ip).isReachable(300);

                            // Actualizar progreso de forma segura
                            int progress = current.incrementAndGet();
                            updateProgress(progress, "Escaneando: " + ip);

                        } catch (Exception e) {
                            // Ignorar errores individuales de ping
                            int progress = current.incrementAndGet();
                            updateProgress(progress, "Ping falló para una IP");
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                // Esperar a que todos los pings terminen
                latch.await();

                updateProgress(total, "Leyendo tabla ARP...");

                BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"));
                br.readLine();
                String line;
                int hostsFound = 0;

                while ((line = br.readLine()) != null) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 4 && !p[3].equals("00:00:00:00:00:00")) {
                        hostsFound++;

                        // 1. Imprimir Host y MAC
                        append("💻 Host encontrado: " + p[0], R.color.on_terminal);
                        append("   MAC: " + p[3], R.color.on_terminal);

                        // 2. Llamada SÍNCRONA (El código espera aquí la respuesta)
                        String vendor = ApiClient.getMacVendorSync(p[3]);

                        // 3. Imprimir Vendor justo debajo
                        append("   Vendor: " + vendor, R.color.on_terminal);

                        // 4. Continuar con el resto

                        long startTime = System.currentTimeMillis();
                        scanPortsNio(p[0]);
                        long endTime = System.currentTimeMillis();
                        long duration = endTime - startTime;
                        append("   Tiempo de escaneo 1: " + duration, R.color.on_terminal);

                        long startTime2 = System.currentTimeMillis();
                        scanPorts(p[0]);
                        long endTime2 = System.currentTimeMillis();
                        long duration2 = endTime2 - startTime2;
                        append("   Tiempo de escaneo 2: " + duration2, R.color.on_terminal);

                        addDivider();
                    }
                }
                br.close();

                finishScan(hostsFound);

            } catch (Exception e) {
                append("❌ Error ARP: " + e.getMessage(), R.color.on_terminal);
                finishScan(0);
            }
        });
    }

    private void scanWithTcp() {
        getNetworkDetails();
        getAdvancedNetworkDetails();
        executor.execute(() -> {
            try {
                int first = networkInt + 1;
                int last = (networkInt | ~mask) - 1;
                int totalHosts = last - first + 1;

                append("📡 Escaneo TCP iniciado", R.color.on_terminal);
                addDivider();

                TreeMap<Integer, String> results = new TreeMap<>();
                AtomicInteger hostsFound = new AtomicInteger(0);
                AtomicInteger scanned = new AtomicInteger(0);

                ExecutorService pool = Executors.newFixedThreadPool(10);

                for (int host = first; host <= last; host++) {

                    final int currentHost = host;

                    pool.execute(() -> {

                        @SuppressLint("DefaultLocale") String ip = String.format("%d.%d.%d.%d",
                                (currentHost >> 24) & 0xff,
                                (currentHost >> 16) & 0xff,
                                (currentHost >> 8) & 0xff,
                                currentHost & 0xff);

                        updateProgress(scanned.get(), "Escaneando: " + ip);

                        if (isAlive(ip)) {

                            hostsFound.incrementAndGet();

                            StringBuilder sb = new StringBuilder();
                            sb.append("💻 Host activo: ").append(ip).append("\n");

                            StringBuilder openPorts = new StringBuilder("   Puertos: ");
                            boolean found = false;

                            for (int port : ports) {
                                try (Socket s = new Socket()) {
                                    s.connect(new InetSocketAddress(ip, port), 150);
                                    openPorts.append(port).append(" ");
                                    found = true;
                                } catch (Exception ignored) {}
                            }

                            if (found) sb.append(openPorts);
                            else sb.append("   Sin puertos abiertos conocidos");

                            synchronized (results) {
                                results.put(currentHost, sb.toString());
                            }
                        }

                        if (scanned.incrementAndGet() >= totalHosts) {

                            runOnUiThread(() -> {
                                for (String res : results.values()) {
                                    String[] lines = res.split("\n");
                                    append(lines[0], R.color.on_terminal);
                                    if (lines.length > 1)
                                        append(lines[1], R.color.on_terminal);
                                    addDivider();
                                }

                                finishScan(hostsFound.get());
                            });

                            pool.shutdown();
                        }
                    });
                }

            } catch (Exception e) {
                append("❌ Error TCP: " + e.getMessage(), R.color.on_terminal);
                finishScan(0);
            }
        });
    }

    private void scanPorts(String host) {
        StringBuilder openPorts = new StringBuilder("   Puertos: ");
        boolean foundPort = false;

        for (int port : ports) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 150);
                openPorts.append(port).append(" ");
                foundPort = true;
            } catch (Exception ignored) {}
        }

        if (foundPort) {
            append(openPorts.toString(), R.color.on_terminal);
        } else {
            append("   Sin puertos abiertos conocidos", R.color.on_terminal);
        }
    }

    private void scanPortsNio(String host) {

        executor.execute(() -> {
            StringBuilder openPorts = new StringBuilder("   Puertos: ");
            boolean found = false;

            try {
                Selector selector = Selector.open();
                InetAddress addr = InetAddress.getByName(host);

                final int MAX_INFLIGHT = 200;
                int inflight = 0;
                int index = 0;

                while (index < ports.length || inflight > 0) {

                    // Lanzar conexiones en paralelo
                    while (index < ports.length && inflight < MAX_INFLIGHT) {
                        int port = ports[index++];

                        SocketChannel ch = SocketChannel.open();
                        ch.configureBlocking(false);
                        ch.socket().setTcpNoDelay(true);
                        ch.connect(new InetSocketAddress(addr, port));
                        ch.register(selector, SelectionKey.OP_CONNECT, port);

                        inflight++;
                    }

                    selector.select(150);

                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        SocketChannel ch = (SocketChannel) key.channel();
                        int port = (int) key.attachment();

                        try {
                            if (ch.finishConnect()) {
                                openPorts.append(port).append(" ");
                                found = true;
                            }
                        } catch (IOException ignored) {
                        } finally {
                            ch.close();
                            inflight--;
                        }
                    }
                }

                selector.close();

            } catch (Exception e) {
                e.printStackTrace();
            }

            if (found) {
                append(openPorts.toString(), R.color.on_terminal);
            } else {
                append("   Sin puertos abiertos conocidos", R.color.on_terminal);
            }
        });
    }

    private boolean isAlive(String host) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, 80), 200);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getSubnet() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        return ip.substring(0, ip.lastIndexOf("."));
    }

    private void updateProgress(int progress, String message) {
        runOnUiThread(() -> {
            progressBar.setProgress(progress);
            progressText.setText(message);
        });
    }

    private void finishScan(int hostsFound) {
        runOnUiThread(() -> {
            isScanning = false;
            scanButton.setEnabled(true);
            scanButton.setText("Iniciar Escaneo");
            progressBar.setVisibility(View.GONE);
            progressText.setText("Escaneo completado");

            append("✓ Escaneo finalizado", R.color.on_terminal);
            append("📊 Hosts encontrados: " + hostsFound, R.color.on_terminal);
            addDivider();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}