package com.av19.netanalyzer.discovery;

import android.util.Log;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
            List<DeviceInfo> allDevices = discoverAll();

            if (token.isCancelled()) {
                callback.onCancelled();
                return;
            }

            if (allDevices.isEmpty()) {
                callback.onComplete(allDevices);
                return;
            }

            // Escaneo de puertos
            scanPorts(allDevices);
        }).start();
    }

    private List<DeviceInfo> discoverAll() {
        // Mapa para evitar duplicados (clave: IP)
        ConcurrentHashMap<String, DeviceInfo> deviceMap = new ConcurrentHashMap<>();
        int totalMethods = methods.size();
        CountDownLatch latch = new CountDownLatch(totalMethods);

        for (DiscoveryMethod method : methods) {
            new Thread(() -> {
                if (token.isCancelled()) {
                    latch.countDown();
                    return;
                }

                List<DeviceInfo> result = method.discover(networkInfo, token, new ProgressCallback() {
                    @Override
                    public void onProgress(int percent, String currentIp) {
                        // Progreso dentro de un método (opcional, se puede usar para UI)
                        if (callback != null) {
                            callback.onDiscoveryProgress(method.getName(), percent);
                        }
                    }

                    @Override
                    public void onDeviceFound(DeviceInfo device) {
                        // Unificar por IP
                        deviceMap.merge(device.getIp(), device, (existing, newDevice) -> {
                            // Si el nuevo tiene MAC y el actual no, actualizar
                            if ((existing.getMac() == null || existing.getMac().isEmpty()) &&
                                    newDevice.getMac() != null && !newDevice.getMac().isEmpty()) {
                                existing.setMac(newDevice.getMac());
                                existing.setVendor(newDevice.getVendor());
                            }
                            // Actualizar TTL si no lo tenía
                            if (existing.getTtl() == null && newDevice.getTtl() != null) {
                                existing.setTtl(newDevice.getTtl());
                                existing.setOs(newDevice.getOs());
                            }
                            return existing;
                        });
                        if (callback != null) {
                            callback.onDeviceFound(device);
                        }
                    }
                });

                // Añadir los dispositivos que quizás no se reportaron individualmente
                for (DeviceInfo d : result) {
                    deviceMap.merge(d.getIp(), d, (existing, newDevice) -> {
                        if ((existing.getMac() == null || existing.getMac().isEmpty()) &&
                                newDevice.getMac() != null && !newDevice.getMac().isEmpty()) {
                            existing.setMac(newDevice.getMac());
                            existing.setVendor(newDevice.getVendor());
                        }
                        if (existing.getTtl() == null && newDevice.getTtl() != null) {
                            existing.setTtl(newDevice.getTtl());
                            existing.setOs(newDevice.getOs());
                        }
                        return existing;
                    });
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
        return new ArrayList<>(deviceMap.values());
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
                    // clasificación...
                } catch (Exception e) {
                    Log.e(TAG, "Error scanning ports for " + device.getIp(), e);
                } finally {
                    int done = completed.incrementAndGet();
                    if (callback != null) {
                        callback.onPortScanProgress(done, total, device.getIp(), devices); // <<< PASAMOS devices
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