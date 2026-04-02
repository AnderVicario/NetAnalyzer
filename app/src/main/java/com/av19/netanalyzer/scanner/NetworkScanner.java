package com.av19.netanalyzer.scanner;

import android.util.Log;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.discovery.DiscoveryMethod;
import com.av19.netanalyzer.utils.CancellationToken;
import com.av19.netanalyzer.utils.ProgressCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
                        if (callback != null) {
                            callback.onDiscoveryProgress(method.getName(), percent);
                            Log.d(TAG, "Progress: " + method.getName() + " " + percent + "%");
                        }
                    }

                    @Override
                    public void onDeviceFound(DeviceInfo device) {
                        // Unificar por IP con fusión COMPLETA
                        mergeDeviceInfo(deviceMap, device);
                        if (callback != null) {
                            callback.onDeviceFound(device);
                        }
                    }
                });

                // Añadir los dispositivos que quizás no se reportaron individualmente
                for (DeviceInfo d : result) {
                    mergeDeviceInfo(deviceMap, d);
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

    private void mergeDeviceInfo(ConcurrentHashMap<String, DeviceInfo> deviceMap, DeviceInfo newDevice) {
        deviceMap.merge(newDevice.getIp(), newDevice, (existing, newDeviceInfo) -> {

            // ============================================================
            // 1. INFORMACIÓN DE RED
            // ============================================================

            // Hostname (priorizar el que no esté vacío)
            if ((existing.getHostname() == null || existing.getHostname().isEmpty()) &&
                    newDeviceInfo.getHostname() != null && !newDeviceInfo.getHostname().isEmpty()) {
                existing.setHostname(newDeviceInfo.getHostname());
            }

            // ============================================================
            // 2. INFORMACIÓN DE HARDWARE
            // ============================================================

            // MAC y Vendor (priorizar el que no esté vacío)
            if ((existing.getMac() == null || existing.getMac().isEmpty()) &&
                    newDeviceInfo.getMac() != null && !newDeviceInfo.getMac().isEmpty()) {
                existing.setMac(newDeviceInfo.getMac());
                existing.setVendor(newDeviceInfo.getVendor());
            } else if (existing.getMac() != null && !existing.getMac().isEmpty() &&
                    (existing.getVendor() == null || existing.getVendor().isEmpty()) &&
                    newDeviceInfo.getVendor() != null && !newDeviceInfo.getVendor().isEmpty()) {
                // Si ya teníamos MAC pero no vendor, y el nuevo tiene vendor, lo actualizamos
                existing.setVendor(newDeviceInfo.getVendor());
            }

            // ============================================================
            // 3. INFORMACIÓN DE DISPOSITIVO
            // ============================================================

            // OS (Time To Live inference or actual OS)
            if ((existing.getOs() == null || existing.getOs().isEmpty()) &&
                    newDeviceInfo.getOs() != null && !newDeviceInfo.getOs().isEmpty()) {
                existing.setOs(newDeviceInfo.getOs());
            }

            // TTL (priorizar el que tenga valor)
            if (existing.getTtl() == null && newDeviceInfo.getTtl() != null) {
                existing.setTtl(newDeviceInfo.getTtl());
            } else if (existing.getTtl() != null && newDeviceInfo.getTtl() != null) {
                // Si ambos tienen TTL, usar el menor (más cercano al dispositivo real)
                if (newDeviceInfo.getTtl() < existing.getTtl()) {
                    existing.setTtl(newDeviceInfo.getTtl());
                }
            }

            // Model (de mDNS, etc.)
            if ((existing.getModel() == null || existing.getModel().isEmpty()) &&
                    newDeviceInfo.getModel() != null && !newDeviceInfo.getModel().isEmpty()) {
                existing.setModel(newDeviceInfo.getModel());
            }

            // ============================================================
            // 4. PUERTOS ABIERTOS
            // ============================================================

            // Fusionar puertos sin duplicados
            if (existing.getOpenPorts() == null || existing.getOpenPorts().isEmpty()) {
                if (newDeviceInfo.getOpenPorts() != null && !newDeviceInfo.getOpenPorts().isEmpty()) {
                    existing.setOpenPorts(new ArrayList<>(newDeviceInfo.getOpenPorts()));
                }
            } else if (newDeviceInfo.getOpenPorts() != null && !newDeviceInfo.getOpenPorts().isEmpty()) {
                // Fusionar ambas listas sin duplicados
                List<Integer> mergedPorts = new ArrayList<>(existing.getOpenPorts());
                for (Integer port : newDeviceInfo.getOpenPorts()) {
                    if (!mergedPorts.contains(port)) {
                        mergedPorts.add(port);
                    }
                }
                // Ordenar puertos para mejor visualización
                Collections.sort(mergedPorts);
                existing.setOpenPorts(mergedPorts);
            }

            // ============================================================
            // 5. FLAGS ESPECIALES (Current, Gateway, DNS)
            // ============================================================

            // Estas flags son booleanos, si alguno es true, se mantiene
            if (newDeviceInfo.getIsCurrent() != null && newDeviceInfo.getIsCurrent()) {
                existing.setIsCurrent(true);
            }

            if (newDeviceInfo.getIsGateway() != null && newDeviceInfo.getIsGateway()) {
                existing.setIsGateway(true);
            }

            if (newDeviceInfo.getIsDNS() != null && newDeviceInfo.getIsDNS()) {
                existing.setIsDNS(true);
            }

            return existing;
        });
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