package com.av19.netanalyzer;

import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private TextView output;
    private TextView progressText;
    private ProgressBar progressBar;
    private Button scanButton;

    private ExecutorService executor;
    private final int[] ports = {80, 443, 22, 445, 8080, 21, 23, 3389};
    private boolean isScanning = false;
    private AtomicInteger scannedHosts = new AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configurar edge-to-edge para Android moderno
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }

        setContentView(R.layout.activity_main);

        // Inicializar vistas
        output = findViewById(R.id.output);
        progressText = findViewById(R.id.progressText);
        progressBar = findViewById(R.id.progressBar);
        scanButton = findViewById(R.id.scanButton);

        // Configurar window insets para Android moderno
        setupWindowInsets();

        // Configurar botón de escaneo
        scanButton.setOnClickListener(v -> startScan());

        // Mostrar información inicial
        int api = Build.VERSION.SDK_INT;
        append("📱 Android API: " + api);
        append("━━━━━━━━━━━━━━━━━━━━━━");

        if (api < Build.VERSION_CODES.Q) {
            append("✓ Modo ARP disponible (Android ≤9)");
        } else {
            append("⚠ Modo limitado (Android ≥10, sin acceso ARP)");
        }
        append("━━━━━━━━━━━━━━━━━━━━━━\n");
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Aplicar padding solo en los lados necesarios
            v.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    0 // No padding abajo para aprovechar todo el espacio
            );

            return insets;
        });
    }

    private void startScan() {
        if (isScanning) {
            append("⚠ Escaneo ya en progreso...\n");
            return;
        }

        isScanning = true;
        scannedHosts.set(0);

        // Limpiar output anterior
        runOnUiThread(() -> {
            output.setText("");
            scanButton.setEnabled(false);
            scanButton.setText("Escaneando...");
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            progressText.setText("Iniciando escaneo...");
        });

        // Crear nuevo executor
        executor = Executors.newFixedThreadPool(30);

        int api = Build.VERSION.SDK_INT;

        if (api < Build.VERSION_CODES.Q) {
            append("🔍 Iniciando escaneo ARP...\n");
            scanWithArp();
        } else {
            append("🔍 Iniciando escaneo TCP...\n");
            scanWithTcp();
        }
    }

    // =======================
    // ANDROID ≤ 9  (ARP)
    // =======================
    private void scanWithArp() {
        executor.execute(() -> {
            try {
                String subnet = getSubnet();
                append("📡 Red: " + subnet + ".0/24\n");

                updateProgress(0, "Poblando tabla ARP...");

                // Poblar ARP
                for (int i = 1; i < 255; i++) {
                    final int current = i;
                    InetAddress.getByName(subnet + "." + i).isReachable(100);
                    updateProgress(current, "Escaneando: " + subnet + "." + current);
                }

                updateProgress(0, "Leyendo tabla ARP...");

                BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"));
                br.readLine(); // header
                String line;
                int hostsFound = 0;

                while ((line = br.readLine()) != null) {
                    String[] p = line.split("\\s+");
                    if (p.length >= 4 && !p[3].equals("00:00:00:00:00:00")) {
                        hostsFound++;
                        append("\n💻 Host encontrado: " + p[0]);
                        append("   MAC: " + p[3]);
                        scanPorts(p[0]);
                    }
                }
                br.close();

                finishScan(hostsFound);

            } catch (Exception e) {
                append("\n❌ Error ARP: " + e.getMessage());
                finishScan(0);
            }
        });
    }

    // =======================
    // ANDROID ≥ 10  (TCP)
    // =======================
    private void scanWithTcp() {
        String subnet = getSubnet();
        append("📡 Red: " + subnet + ".0/24\n");

        final AtomicInteger hostsFound = new AtomicInteger(0);

        for (int i = 1; i < 255; i++) {
            final int current = i;
            String host = subnet + "." + i;

            executor.execute(() -> {
                updateProgress(current, "Escaneando: " + subnet + "." + current);

                if (isAlive(host)) {
                    hostsFound.incrementAndGet();
                    append("\n💻 Host activo: " + host);
                    scanPorts(host);
                }

                if (scannedHosts.incrementAndGet() >= 254) {
                    finishScan(hostsFound.get());
                }
            });
        }
    }

    // =======================
    // UTILIDADES
    // =======================
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
            append(openPorts.toString());
        } else {
            append("   Sin puertos abiertos conocidos");
        }
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

    private void append(String text) {
        runOnUiThread(() -> output.append(text + "\n"));
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

            append("\n━━━━━━━━━━━━━━━━━━━━━━");
            append("✓ Escaneo finalizado");
            append("📊 Hosts encontrados: " + hostsFound);
            append("━━━━━━━━━━━━━━━━━━━━━━");
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