package com.av19.netanalyzer.discovery;

import android.util.Log;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.utils.CancellationToken;
import com.av19.netanalyzer.utils.NetUtils;
import com.av19.netanalyzer.utils.ProgressCallback;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ICMPDiscovery implements DiscoveryMethod {

    private static final String TAG = "IcmpDiscovery";

    @Override
    public String getName() {
        return "ICMP";
    }

    @Override
    public List<DeviceInfo> discover(NetworkInfo network, CancellationToken token, ProgressCallback callback) {
        List<DeviceInfo> devices = new ArrayList<>();

        int networkInt = NetUtils.ipToInt(network.getNetworkAddress());
        int mask = NetUtils.ipToInt(network.getNetmask());
        int first = networkInt + 1;
        int last = (networkInt | ~mask) - 1;
        int totalHosts = last - first + 1;

        if (totalHosts <= 0) return devices;

        AtomicInteger scanned = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalHosts);
        ExecutorService executor = Executors.newFixedThreadPool(30);

        for (int host = first; host <= last && !token.isCancelled(); host++) {
            final int currentHost = host;
            executor.execute(() -> {
                if (token.isCancelled()) {
                    latch.countDown();
                    return;
                }

                String ip = NetUtils.intToIp(currentHost);
                try {
                    PingResult result = pingHostWithTTL(ip, 1, 700);
                    if (result.success) {
                        DeviceInfo device = new DeviceInfo(ip, null, null, null);
                        device.setTtl(result.ttl);
                        synchronized (devices) {
                            devices.add(device);
                        }
                        if (callback != null) callback.onDeviceFound(device);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error al hacer ping a " + ip, e);
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
            Log.e(TAG, "Escaneo ICMP interrumpido");
        }
        executor.shutdown();

        return devices;
    }

    private PingResult pingHostWithTTL(String ip, int count, int timeoutMs) {
        try {
            int timeoutSec = Math.max(1, timeoutMs / 1000);
            String command = "ping -c " + count + " -W " + timeoutSec + " " + ip;
            Process process = Runtime.getRuntime().exec(command);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Pattern ttlPattern = Pattern.compile("ttl=(\\d+)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = ttlPattern.matcher(output.toString());
                if (matcher.find()) {
                    int ttl = Integer.parseInt(matcher.group(1));
                    return new PingResult(true, ttl);
                }
                return new PingResult(true, null);
            } else {
                return new PingResult(false, null);
            }
        } catch (Exception e) {
            return new PingResult(false, null);
        }
    }

    private static class PingResult {
        boolean success;
        Integer ttl;

        PingResult(boolean success, Integer ttl) {
            this.success = success;
            this.ttl = ttl;
        }
    }
}