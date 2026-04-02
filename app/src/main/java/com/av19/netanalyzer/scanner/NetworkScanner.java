package com.av19.netanalyzer.scanner;

import android.util.Log;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.ListDeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.discovery.DiscoveryMethod;
import com.av19.netanalyzer.utils.CancellationToken;
import com.av19.netanalyzer.utils.ProgressCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class NetworkScanner {

    private static final String TAG = "NetworkScanner";

    private final List<DiscoveryMethod> methods = new ArrayList<>();
    private final NetworkInfo networkInfo;
    private final int[] ports;
    private final NioPortScanner portScanner;
    private CancellationToken token;
    private Callback callback;

    public interface Callback {
        void onDiscoveryProgress(String methodName, int progressPercent);
        void onDeviceFound(DeviceInfo device);
        void onPortScanProgress(int current, int total, String currentIp, List<DeviceInfo> currentDevices);
        void onComplete(List<DeviceInfo> devices);
        void onCancelled();
    }

    public NetworkScanner(NetworkInfo networkInfo, int[] ports) {
        this.networkInfo = networkInfo;
        this.ports = ports;
        this.portScanner = new NioPortScanner(ports);
    }

    public void addMethod(DiscoveryMethod method) {
        methods.add(method);
    }

    public void start(CancellationToken token, Callback callback) {
        this.token = token;
        this.callback = callback;

        new Thread(() -> {
            // Descubrimiento
            ListDeviceInfo deviceList = discoverAll();

            if (token.isCancelled()) {
                callback.onCancelled();
                return;
            }

            List<DeviceInfo> allDevices = deviceList.getDevices();
            if (allDevices.isEmpty()) {
                callback.onComplete(allDevices);
                return;
            }

            // Escaneo de puertos
            scanPorts(allDevices);
        }).start();
    }

    private ListDeviceInfo discoverAll() {
        ListDeviceInfo deviceList = new ListDeviceInfo();
        int totalMethods = methods.size();
        CountDownLatch latch = new CountDownLatch(totalMethods);

        for (DiscoveryMethod method : methods) {
            new Thread(() -> {
                if (token.isCancelled()) {
                    latch.countDown();
                    return;
                }

                // Ejecutar el método de descubrimiento
                List<DeviceInfo> result = method.discover(networkInfo, token, new ProgressCallback() {
                    @Override
                    public void onProgress(int percent, String currentIp) {
                        if (callback != null) {
                            callback.onDiscoveryProgress(method.getName(), percent);
                            Log.d(TAG, "Progress: " + method.getName() + " " + percent + "%");
                        }
                    }

                    @Override
                    public void onDeviceFound(DeviceInfo device) {
                        // Añadir o fusionar el dispositivo y obtener la versión actualizada
                        DeviceInfo mergedDevice = deviceList.addOrUpdate(device);
                        if (callback != null) {
                            callback.onDeviceFound(mergedDevice);
                        }
                    }
                });

                // Procesar dispositivos devueltos por el método (por si no se reportaron individualmente)
                for (DeviceInfo d : result) {
                    deviceList.addOrUpdate(d);
                }

                latch.countDown();
            }).start();
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Espera de descubrimiento interrumpida");
        }
        return deviceList;
    }

    private void scanPorts(List<DeviceInfo> devices) {
        int total = devices.size();
        AtomicInteger completed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(total);

        for (DeviceInfo device : devices) {
            new Thread(() -> {
                if (token.isCancelled()) {
                    latch.countDown();
                    return;
                }
                try {
                    List<Integer> openPorts = portScanner.scan(device.getIp(), token);
                    device.setOpenPorts(openPorts);
                } catch (Exception e) {
                    Log.e(TAG, "Error scanning ports for " + device.getIp(), e);
                } finally {
                    int done = completed.incrementAndGet();
                    if (callback != null) {
                        // Pasamos la lista actualizada (puede haber cambiado por fusiones, pero aquí es estable)
                        callback.onPortScanProgress(done, total, device.getIp(), devices);
                    }
                    latch.countDown();
                }
            }).start();
        }

        try {
            latch.await();
            if (!token.isCancelled()) {
                callback.onComplete(devices);
            } else {
                callback.onCancelled();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.onCancelled();
        }
    }
}