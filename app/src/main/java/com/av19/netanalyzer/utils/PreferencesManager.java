package com.av19.netanalyzer.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.ScanRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreferencesManager {
    private static final String PREFS_NAME = "app_preferences";
    private static final String KEY_ACTIVE_METHODS = "active_methods";
    private static final String KEY_SCAN_LEVEL = "scan_level";
    private static final String KEY_METHOD_PARAMS = "method_params";
    private static final String KEY_SCAN_HISTORY = "scan_history";
    private static final int MAX_HISTORY = 20;

    private final SharedPreferences prefs;
    private final Gson gson;

    public PreferencesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new GsonBuilder().create();
    }

    // --- Nivel de puertos ---
    public String getScanLevel() {
        return prefs.getString(KEY_SCAN_LEVEL, "100");
    }

    public void setScanLevel(String level) {
        prefs.edit().putString(KEY_SCAN_LEVEL, level).apply();
    }

    public List<String> getActiveMethods() {
        String json = prefs.getString(KEY_ACTIVE_METHODS, null);
        if (json == null) {
            List<String> defaults = new ArrayList<>();
            defaults.add("AUTO");
            return defaults;
        }
        Type listType = new TypeToken<List<String>>() {
        }.getType();
        return gson.fromJson(json, listType);
    }

    // --- Preset de métodos activos ---
    public void setActiveMethods(List<String> methods) {
        prefs.edit().putString(KEY_ACTIVE_METHODS, gson.toJson(methods)).apply();
    }

    // --- Parámetros de métodos (ej. tcp_port) ---
    public void setMethodParam(String method, String paramKey, String value) {
        Map<String, Map<String, String>> allParams = getAllMethodParams();
        Map<String, String> methodParams = allParams.getOrDefault(method, new HashMap<>());
        methodParams.put(paramKey, value);
        allParams.put(method, methodParams);
        saveAllMethodParams(allParams);
    }

    public String getMethodParam(String method, String paramKey, String defaultValue) {
        Map<String, Map<String, String>> allParams = getAllMethodParams();
        Map<String, String> methodParams = allParams.get(method);
        if (methodParams == null) return defaultValue;
        return methodParams.getOrDefault(paramKey, defaultValue);
    }

    private Map<String, Map<String, String>> getAllMethodParams() {
        String json = prefs.getString(KEY_METHOD_PARAMS, null);
        if (json == null) return new HashMap<>();
        Type mapType = new TypeToken<Map<String, Map<String, String>>>() {
        }.getType();
        return gson.fromJson(json, mapType);
    }

    private void saveAllMethodParams(Map<String, Map<String, String>> params) {
        prefs.edit().putString(KEY_METHOD_PARAMS, gson.toJson(params)).apply();
    }

    // --- Historial de escaneos ---
    public void addScanRecord(long timestamp, long durationSec, int deviceCount, List<DeviceInfo> devices) {
        List<ScanRecord> history = getScanHistory();
        history.add(0, new ScanRecord(timestamp, durationSec, deviceCount, devices));
        if (history.size() > MAX_HISTORY) {
            history = history.subList(0, MAX_HISTORY);
        }
        saveScanHistory(history);
    }

    public List<ScanRecord> getScanHistory() {
        String json = prefs.getString(KEY_SCAN_HISTORY, null);
        if (json == null) return new ArrayList<>();
        Type listType = new TypeToken<List<ScanRecord>>() {
        }.getType();
        return gson.fromJson(json, listType);
    }

    private void saveScanHistory(List<ScanRecord> history) {
        prefs.edit().putString(KEY_SCAN_HISTORY, gson.toJson(history)).apply();
    }

    // --- Exportar / Importar SOLO ajustes avanzados ---
    public String exportAdvancedSettings() {
        Map<String, Object> root = new HashMap<>();
        root.put("version", 1);
        root.put("active_methods", getActiveMethods());
        root.put("method_params", getAllMethodParams());
        root.put("scan_level", getScanLevel());
        return gson.toJson(root);
    }

    public boolean importAdvancedSettings(String jsonString) {
        try {
            // Limitar el tamaño máximo del JSON para evitar DoS
            if (jsonString == null || jsonString.length() > 100000) { // 100KB máximo
                return false;
            }

            JsonObject root = gson.fromJson(jsonString, JsonObject.class);
            
            // Validar versión
            if (!root.has("version") || root.get("version").getAsInt() != 1) {
                return false;
            }

            // Validar y parsear active_methods
            if (!root.has("active_methods") || !root.get("active_methods").isJsonArray()) {
                return false;
            }
            List<String> activeMethods = gson.fromJson(root.get("active_methods"),
                    new TypeToken<List<String>>() {
                    }.getType());
            // Validar que los métodos sean válidos
            List<String> validMethods = Arrays.asList("AUTO", "ARP", "TCP", "ICMP", "MDNS", "SSDP", "NETBIOS");
            for (String method : activeMethods) {
                if (!validMethods.contains(method)) {
                    return false;
                }
            }
            setActiveMethods(activeMethods);

            // Validar y parsear method_params
            if (!root.has("method_params") || !root.get("method_params").isJsonObject()) {
                return false;
            }
            Map<String, Map<String, String>> params = gson.fromJson(root.get("method_params"),
                    new TypeToken<Map<String, Map<String, String>>>() {
                    }.getType());
            // Limitar el número de parámetros
            if (params.size() > 50) {
                return false;
            }
            saveAllMethodParams(params);

            // Validar y parsear scan_level
            if (!root.has("scan_level")) {
                return false;
            }
            // Verificar que sea un string (no un número)
            if (!root.get("scan_level").isJsonPrimitive() || !root.get("scan_level").getAsJsonPrimitive().isString()) {
                return false;
            }
            String scanLevel = root.get("scan_level").getAsString();
            // Validar que scan_level sea uno de los valores permitidos
            if (!Arrays.asList("100", "500", "1000").contains(scanLevel)) {
                return false;
            }
            setScanLevel(scanLevel);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String exportScanHistory() {
        Map<String, Object> root = new HashMap<>();
        root.put("version", 1);
        root.put("scan_history", getScanHistory());
        return gson.toJson(root);
    }

    public boolean importScanHistory(String jsonString) {
        return importScanHistory(jsonString, null);
    }

    public boolean importScanHistory(String jsonString, ScanRecord[] outImported) {
        try {
            // Limitar el tamaño máximo del JSON para evitar DoS
            if (jsonString == null || jsonString.length() > 100000) { // 100KB máximo
                return false;
            }

            JsonObject root = gson.fromJson(jsonString, JsonObject.class);

            // Validar versión
            if (!root.has("version") || root.get("version").getAsInt() != 1) {
                return false;
            }

            // Validar y parsear scan_history
            if (!root.has("scan_history") || !root.get("scan_history").isJsonArray()) {
                return false;
            }
            List<ScanRecord> imported = gson.fromJson(root.get("scan_history"),
                    new TypeToken<List<ScanRecord>>() {
                    }.getType());

            // Validar que la lista no sea demasiado grande
            // Permite hasta 100 escaneos importados, cada uno con hasta 5000 dispositivos
            if (imported.size() > 100) {
                return false;
            }

            // Validar cada registro individual
            for (ScanRecord record : imported) {
                if (record.getDevices() == null) {
                    return false;
                }
                // Validar que cada dispositivo tenga campos mínimos
                for (DeviceInfo device : record.getDevices()) {
                    if (device.getIp() == null || device.getIp().isEmpty()) {
                        return false;
                    }
                }
            }

            List<ScanRecord> current = getScanHistory();
            // Fusionar: añadir los registros importados al principio
            for (ScanRecord record : imported) {
                // Opcional: evitar duplicados exactos por timestamp
                current.add(0, record);
            }
            // Ordenar por timestamp descendente
            current.sort((r1, r2) -> Long.compare(r2.getTimestamp(), r1.getTimestamp()));
            if (current.size() > MAX_HISTORY) {
                current = current.subList(0, MAX_HISTORY);
            }
            saveScanHistory(current);

            // Devolver el primer registro importado si se solicita
            if (outImported != null && !imported.isEmpty()) {
                outImported[0] = imported.get(0);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void println() {
        android.util.Log.d("PreferencesManager", "\n" + getFormattedPreferences());
    }

    private String getFormattedPreferences() {
        StringBuilder sb = new StringBuilder();
        sb.append("══════════════════════════════════════════\n");
        sb.append("       PREFERENCES MANAGER - DUMP         \n");
        sb.append("══════════════════════════════════════════\n\n");

        // 1. Nivel de puertos
        sb.append("📊 SCAN LEVEL: ").append(getScanLevel()).append(" ports\n\n");

        // 2. Métodos activos
        sb.append("🔍 ACTIVE METHODS:\n");
        List<String> activeMethods = getActiveMethods();
        if (activeMethods.isEmpty()) {
            sb.append("   (none)\n");
        } else {
            for (String method : activeMethods) {
                sb.append("   • ").append(method).append("\n");
            }
        }
        sb.append("\n");

        // 3. Parámetros por método
        sb.append("⚙️  METHOD PARAMETERS:\n");
        Map<String, Map<String, String>> allParams = getAllMethodParams();
        if (allParams.isEmpty()) {
            sb.append("   (none)\n");
        } else {
            for (Map.Entry<String, Map<String, String>> methodEntry : allParams.entrySet()) {
                sb.append("   ▸ ").append(methodEntry.getKey().toUpperCase()).append("\n");
                Map<String, String> params = methodEntry.getValue();
                if (params.isEmpty()) {
                    sb.append("      (no params)\n");
                } else {
                    for (Map.Entry<String, String> param : params.entrySet()) {
                        sb.append("      • ").append(param.getKey()).append(" = ").append(param.getValue()).append("\n");
                    }
                }
            }
        }
        sb.append("\n");

        // 4. Historial de escaneos
        sb.append("📜 SCAN HISTORY (").append(MAX_HISTORY).append(" max):\n");
        List<ScanRecord> history = getScanHistory();
        if (history.isEmpty()) {
            sb.append("   (empty)\n");
        } else {
            for (int i = 0; i < history.size(); i++) {
                ScanRecord record = history.get(i);
                sb.append("   #").append(i + 1).append(" | ");
                sb.append(new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date(record.getTimestamp())));
                sb.append(" | ").append(record.getDeviceCount()).append(" devices");
                sb.append(" | ").append(record.getDurationSec()).append("s\n");

                // Mostrar dispositivos si hay
                List<DeviceInfo> devices = record.getDevices();
                if (devices != null && !devices.isEmpty()) {
                    for (DeviceInfo device : devices) {
                        sb.append("        📱 ").append(device.getIp());
                        if (device.getHostname() != null && !device.getHostname().getValue().isEmpty()) {
                            sb.append(" (").append(device.getHostname().getValue()).append(")");
                        }
                        if (device.getMac() != null && !device.getMac().isEmpty()) {
                            sb.append(" MAC:").append(device.getMac());
                        }
                        sb.append("\n");
                    }
                }
            }
        }

        sb.append("\n══════════════════════════════════════════\n");
        return sb.toString();
    }
}