package com.av19.netanalyzer.discovery;

import android.util.Log;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.utils.CancellationToken;
import com.av19.netanalyzer.utils.FingerprintManager;
import com.av19.netanalyzer.utils.NetApiClient;
import com.av19.netanalyzer.utils.NetUtils;
import com.av19.netanalyzer.utils.ProgressCallback;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ARPDiscovery implements DiscoveryMethod {

    private static final String TAG = "ArpDiscovery";

    @Override
    public String getName() {
        return "ARP";
    }

    @Override
    public List<DeviceInfo> discover(NetworkInfo network, CancellationToken token, ProgressCallback callback) {
        List<DeviceInfo> devices = new ArrayList<>();

        int networkInt = NetUtils.ipToInt(network.getNetworkAddress());
        int mask = NetUtils.ipToInt(network.getNetmask());
        int first = networkInt + 1;
        int last = (networkInt | ~mask) - 1;
        int total = last - first + 1;

        if (total <= 0) return devices;

        // Fase 1: escaneo activo (0‑50%)
        AtomicInteger scanned = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(total);
        ExecutorService executor = Executors.newFixedThreadPool(50);

        for (int host = first; host <= last && !token.isCancelled(); host++) {
            final int currentHost = host;
            executor.execute(() -> {
                if (token.isCancelled()) {
                    latch.countDown();
                    return;
                }
                try {
                    String ip = NetUtils.intToIp(currentHost);
                    InetAddress.getByName(ip).isReachable(300); // forzar entrada en ARP
                } catch (Exception e) {
                    // ignorar errores individuales
                } finally {
                    int progress = (int) (scanned.incrementAndGet() * 50.0 / total);
                    if (callback != null) callback.onProgress(progress, null);
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Escaneo ARP interrumpido");
        }
        executor.shutdown();

        if (token.isCancelled()) return devices;

        // Fase 2: leer tabla ARP (50‑100%)
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"))) {
            br.readLine(); // saltar cabecera
            String line;
            int count = 0;
            int totalArp = 0;
            // Primero contamos líneas para el progreso (opcional, podemos hacerlo más simple)
            // Pero para un progreso suave, podemos usar el número de entradas encontradas.
            List<String[]> arpEntries = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 4 && !parts[3].equals("00:00:00:00:00:00")) {
                    arpEntries.add(parts);
                }
            }
            totalArp = arpEntries.size();
            for (String[] parts : arpEntries) {
                if (token.isCancelled()) break;
                String ip = parts[0];
                String mac = parts[3];
                String vendor = NetApiClient.getMacVendorSync(mac);
                DeviceInfo device = new DeviceInfo(ip, mac, vendor, null);
                devices.add(device);
                if (callback != null) callback.onDeviceFound(device);
                int progress = 50 + (int) ((++count * 50.0) / totalArp);
                if (callback != null) callback.onProgress(progress, ip);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error leyendo /proc/net/arp", e);
        }

        return devices;
    }
}