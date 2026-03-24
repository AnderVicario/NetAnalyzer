package com.av19.netanalyzer.service;

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
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.text.format.Formatter;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.av19.netanalyzer.ApiClient;
import com.av19.netanalyzer.ui.main.MainActivity;
import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.ScanState;
import com.av19.netanalyzer.repository.ScanRepository;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class ScanService extends Service {
    private static final String CHANNEL_ID = "scan_channel";
    private static final int NOTIFICATION_ID = 1;

    private ScanRepository repository;
    private ExecutorService executor;
    private Handler mainHandler;
    private boolean isScanning = false;

    private int networkInt;
    private int mask;
    private final int[] ports = IntStream.rangeClosed(1, 1000).toArray();

    @Override
    public void onCreate() {
        super.onCreate();
        repository = ScanRepository.getInstance();
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_SCAN".equals(intent.getAction())) {
            stopScan();
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, createNotification("Starting scan..."));
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
        executor.execute(() -> {
            try {
                // Reset state before starting
                repository.reset();

                // Gather network details
                getNetworkDetails();
                // Determine scan method based on Android version
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    scanWithArp();
                } else {
                    scanWithTcp();
                }
            } catch (Exception e) {
                repository.setError(e.getMessage());
                stopSelf();
            } finally {
                isScanning = false;
                stopForeground(false);
                stopSelf();
            }
        });
    }

    private void stopScan() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        isScanning = false;
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

    private void scanWithArp() {
        try {
            int first = networkInt + 1;
            int last = (networkInt | ~mask) - 1;
            int total = last - first + 1;
            AtomicInteger current = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(total);
            List<DeviceInfo> devices = new ArrayList<>();

            // We'll use the same executor for pings, but limit parallelism
            ExecutorService pingPool = Executors.newFixedThreadPool(50);

            for (int host = first; host <= last; host++) {
                final int currentHost = host;
                pingPool.execute(() -> {
                    try {
                        @SuppressLint("DefaultLocale") String ip = String.format("%d.%d.%d.%d",
                                (currentHost >> 24) & 0xff,
                                (currentHost >> 16) & 0xff,
                                (currentHost >> 8) & 0xff,
                                currentHost & 0xff);
                        InetAddress.getByName(ip).isReachable(300);
                        int progress = (int) (current.incrementAndGet() * 100.0 / total);
                        repository.setScanning(progress, ip, devices);
                    } catch (Exception e) {
                        current.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            pingPool.shutdown();

            // Now read ARP table
            BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"));
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 4 && !parts[3].equals("00:00:00:00:00:00")) {
                    String ip = parts[0];
                    String mac = parts[3];
                    String vendor = ApiClient.getMacVendorSync(mac);
                    List<Integer> openPorts = scanPortsNio(ip);
                    devices.add(new DeviceInfo(ip, mac, vendor, openPorts));
                }
            }
            br.close();

            repository.setCompleted(devices);
        } catch (Exception e) {
            repository.setError(e.getMessage());
        }
    }

    private void scanWithTcp() {
        try {
            int first = networkInt + 1;
            int last = (networkInt | ~mask) - 1;
            int total = last - first + 1;
            AtomicInteger scanned = new AtomicInteger(0);
            List<DeviceInfo> devices = new ArrayList<>();
            ExecutorService hostPool = Executors.newFixedThreadPool(20);

            for (int host = first; host <= last; host++) {
                final int currentHost = host;
                hostPool.execute(() -> {
                    @SuppressLint("DefaultLocale") String ip = String.format("%d.%d.%d.%d",
                            (currentHost >> 24) & 0xff,
                            (currentHost >> 16) & 0xff,
                            (currentHost >> 8) & 0xff,
                            currentHost & 0xff);
                    if (isAlive(ip)) {
                        List<Integer> openPorts = scanPortsNio(ip);
                        // We can't get MAC easily on Android 10+, so leave blank
                        synchronized (devices) {
                            devices.add(new DeviceInfo(ip, "", "", openPorts));
                        }
                    }
                    int progress = (int) (scanned.incrementAndGet() * 100.0 / total);
                    repository.setScanning(progress, ip, devices);
                });
            }

            hostPool.shutdown();
            // Wait for all tasks to finish (simplified: use awaitTermination with timeout)
            hostPool.awaitTermination(5, java.util.concurrent.TimeUnit.MINUTES);
            repository.setCompleted(devices);
        } catch (Exception e) {
            repository.setError(e.getMessage());
        }
    }

    private List<Integer> scanPortsNio(String host) {
        List<Integer> openPorts = new ArrayList<>();
        try {
            Selector selector = Selector.open();
            InetAddress addr = InetAddress.getByName(host);
            int maxInflight = 200;
            int inflight = 0;
            int index = 0;
            while (index < ports.length || inflight > 0) {
                while (index < ports.length && inflight < maxInflight) {
                    int port = ports[index++];
                    SocketChannel ch = SocketChannel.open();
                    ch.configureBlocking(false);
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
                            openPorts.add(port);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        ch.close();
                        inflight--;
                    }
                }
            }
            selector.close();
        } catch (Exception e) {
            // ignore
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
}