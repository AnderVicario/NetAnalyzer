package com.av19.netanalyzer.discovery;

import android.content.Context;
import android.util.Log;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.utils.CancellationToken;
import com.av19.netanalyzer.utils.NetUtils;
import com.av19.netanalyzer.utils.PreferencesManager;
import com.av19.netanalyzer.utils.ProgressCallback;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TCPDiscovery implements DiscoveryMethod {

    private static final String TAG = "TcpDiscovery";
    private final Context context;

    public TCPDiscovery(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String getName() {
        return "TCP";
    }

    @Override
    public List<DeviceInfo> discover(NetworkInfo network, CancellationToken token, ProgressCallback callback) {
        List<DeviceInfo> devices = new ArrayList<>();
        PreferencesManager pm = new PreferencesManager(context);

        // Leer número de hilos desde preferencias (con valores por defecto desde integers.xml)
        int defaultThreads = context.getResources().getInteger(R.integer.tcp_threads_default);
        int minThreads = context.getResources().getInteger(R.integer.tcp_threads_min);
        int maxThreads = context.getResources().getInteger(R.integer.tcp_threads_max);
        String threadsStr = pm.getMethodParam("tcp", "threads", String.valueOf(defaultThreads));
        int threadCount = NetUtils.parseIntOrDefault(threadsStr, defaultThreads, minThreads, maxThreads);

        // Leer lista de puertos + timeouts desde preferencias (formato JSON)
        String portsJson = pm.getMethodParam("tcp", "ports", null);
        List<PortTimeoutPair> pairs = new ArrayList<>();
        if (portsJson != null && !portsJson.isEmpty()) {
            try {
                Type type = new TypeToken<List<PortTimeoutPair>>() {
                }.getType();
                pairs = new Gson().fromJson(portsJson, type);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing TCP ports JSON", e);
            }
        }
        // Si no hay configuración válida, usar un par por defecto (puerto 80, timeout 200 ms)
        if (pairs.isEmpty()) {
            int defaultPort = context.getResources().getInteger(R.integer.tcp_default_port);
            int defaultTimeout = context.getResources().getInteger(R.integer.tcp_default_timeout);
            pairs.add(new PortTimeoutPair(defaultPort, defaultTimeout));
        }

        // Calcular rango de IPs
        int networkInt = NetUtils.ipToInt(network.getNetworkAddress());
        int mask = NetUtils.ipToInt(network.getNetmask());
        int first = networkInt + 1;
        int last = (networkInt | ~mask) - 1;
        int totalHosts = last - first + 1;

        if (totalHosts <= 0) return devices;

        AtomicInteger scanned = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalHosts);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int host = first; host <= last && !token.isCancelled(); host++) {
            final int currentHost = host;
            List<PortTimeoutPair> finalPairs = pairs;
            executor.execute(() -> {
                if (token.isCancelled()) {
                    latch.countDown();
                    return;
                }

                String ip = NetUtils.intToIp(currentHost);
                try {
                    if (isAlive(ip, finalPairs)) {
                        DeviceInfo device = new DeviceInfo(ip, null, null, null);
                        synchronized (devices) {
                            devices.add(device);
                        }
                        if (callback != null) callback.onDeviceFound(device);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error al verificar " + ip, e);
                } finally {
                    int progress = (int) (scanned.incrementAndGet() * 100.0 / totalHosts);
                    if (callback != null) callback.onProgress(progress, ip);
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Escaneo TCP interrumpido");
        }

        return devices;
    }

    private boolean isAlive(String host, List<PortTimeoutPair> pairs) {
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (PortTimeoutPair pair : pairs) {
            tasks.add(() -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, pair.port), pair.timeout);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
        }
        try {
            // invokeAny devuelve el primer resultado true, o lanza excepción si todas fallan
            return executor.invokeAny(tasks, 3000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    private static class PortTimeoutPair {
        int port;
        int timeout;

        PortTimeoutPair(int port, int timeout) {
            this.port = port;
            this.timeout = timeout;
        }
    }
}