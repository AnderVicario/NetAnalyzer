package com.av19.netanalyzer.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetUtils {
    private static final Map<String, CacheEntry> cache = new HashMap<>();

    public static int ipToInt(String ip) {
        String[] parts = ip.split("\\.");
        return (Integer.parseInt(parts[0]) << 24) |
                (Integer.parseInt(parts[1]) << 16) |
                (Integer.parseInt(parts[2]) << 8) |
                Integer.parseInt(parts[3]);
    }

    @SuppressLint("DefaultLocale")
    public static String intToIp(int ip) {
        return String.format("%d.%d.%d.%d",
                (ip >> 24) & 0xff,
                (ip >> 16) & 0xff,
                (ip >> 8) & 0xff,
                ip & 0xff);
    }

    public static int[] loadPortsFromAssets(Context context, String fileName) {
        CacheEntry entry = loadIfNeeded(context, fileName);
        return entry.ports;
    }

    public static String getPortServiceName(Context context, String fileName, int port) {
        CacheEntry entry = loadIfNeeded(context, fileName);
        return entry.serviceNames.get(port);
    }

    private static synchronized CacheEntry loadIfNeeded(Context context, String fileName) {
        // Si ya está en caché, devolverlo
        if (cache.containsKey(fileName)) {
            return cache.get(fileName);
        }

        List<Integer> ports = new ArrayList<>();
        Map<Integer, String> names = new HashMap<>();
        Pattern pattern = Pattern.compile("^(\\S+)\\s+(\\d+)/tcp");

        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    String service = m.group(1).toUpperCase();
                    int port = Integer.parseInt(m.group(2));
                    ports.add(port);
                    names.putIfAbsent(port, service);
                }
            }
        } catch (IOException e) {
            Log.e("NetUtils", "Error cargando puertos desde " + fileName, e);
            // Fallback por si falla la carga
            int[] fallbackPorts = {80, 23, 443, 21, 22, 25, 3389, 110, 445, 139, 143, 53, 135, 25565};
            Map<Integer, String> fallbackNames = new HashMap<>();
            for (int p : fallbackPorts) fallbackNames.put(p, null);
            CacheEntry fallbackEntry = new CacheEntry(fallbackPorts, fallbackNames);
            cache.put(fileName, fallbackEntry);
            return fallbackEntry;
        }

        // Añadir puerto 25565 (Minecraft) si no estaba
        if (!ports.contains(25565)) {
            ports.add(25565);
            names.putIfAbsent(25565, "MINECRAFT");
        }

        int[] portsArray = ports.stream().mapToInt(i -> i).toArray();
        CacheEntry entry = new CacheEntry(portsArray, names);
        cache.put(fileName, entry);
        return entry;
    }

    public static int compareIps(String ip1, String ip2) {
        if (ip1 == null) return (ip2 == null) ? 0 : -1;
        if (ip2 == null) return 1;

        String[] parts1 = ip1.split("\\.");
        String[] parts2 = ip2.split("\\.");

        // Comparamos octeto por octeto
        for (int i = 0; i < Math.min(parts1.length, parts2.length); i++) {
            try {
                int n1 = Integer.parseInt(parts1[i]);
                int n2 = Integer.parseInt(parts2[i]);
                if (n1 != n2) return Integer.compare(n1, n2);
            } catch (NumberFormatException e) {
                // Si no es un número (ej. IPv6 o mal formato), comparamos como string
                int res = parts1[i].compareTo(parts2[i]);
                if (res != 0) return res;
            }
        }
        return Integer.compare(parts1.length, parts2.length);
    }

    private static class CacheEntry {
        final int[] ports;
        final Map<Integer, String> serviceNames;

        CacheEntry(int[] ports, Map<Integer, String> serviceNames) {
            this.ports = ports;
            this.serviceNames = serviceNames;
        }
    }
}