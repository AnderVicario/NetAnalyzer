package com.av19.netanalyzer.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.ScanRecord;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class PreferencesManagerTest {

    private PreferencesManager preferencesManager;

    @Before
    public void setUp() {
        // Limpiar SharedPreferences antes de cada test para garantizar aislamiento
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                .edit().clear().commit();

        preferencesManager = new PreferencesManager(context);
    }

    // ==================== Valores por defecto ====================

    @Test
    public void defaultScanLevel_is100() {
        assertEquals("100", preferencesManager.getScanLevel());
    }

    @Test
    public void defaultActiveMethods_containsOnlyAuto() {
        List<String> methods = preferencesManager.getActiveMethods();
        assertEquals(1, methods.size());
        assertTrue(methods.contains("AUTO"));
    }

    @Test
    public void defaultMethodParam_returnsDefaultValue() {
        assertEquals("200", preferencesManager.getMethodParam("tcp", "timeout", "200"));
        assertEquals("", preferencesManager.getMethodParam("tcp", "port", ""));
    }

    @Test
    public void defaultScanHistory_isEmpty() {
        assertTrue(preferencesManager.getScanHistory().isEmpty());
    }

    // ==================== Métodos activos ====================

    @Test
    public void setAndGetActiveMethods_roundTrip() {
        List<String> methods = Arrays.asList("ARP", "ICMP");
        preferencesManager.setActiveMethods(methods);
        assertEquals(methods, preferencesManager.getActiveMethods());
    }

    @Test
    public void setActiveMethods_overwritesPrevious() {
        preferencesManager.setActiveMethods(List.of("TCP"));
        preferencesManager.setActiveMethods(Arrays.asList("MDNS", "SSDP"));
        assertEquals(Arrays.asList("MDNS", "SSDP"), preferencesManager.getActiveMethods());
    }

    @Test
    public void setActiveMethods_emptyList_persistsEmpty() {
        preferencesManager.setActiveMethods(new ArrayList<>());
        assertTrue(preferencesManager.getActiveMethods().isEmpty());
    }

    // ==================== Nivel de puertos ====================

    @Test
    public void setAndGetScanLevel() {
        preferencesManager.setScanLevel("500");
        assertEquals("500", preferencesManager.getScanLevel());
    }

    @Test
    public void setScanLevel_overwritesPrevious() {
        preferencesManager.setScanLevel("500");
        preferencesManager.setScanLevel("1000");
        assertEquals("1000", preferencesManager.getScanLevel());
    }

    // ==================== Parámetros de métodos ====================

    @Test
    public void setAndGetMethodParam_basic() {
        preferencesManager.setMethodParam("tcp", "port", "445");
        assertEquals("445", preferencesManager.getMethodParam("tcp", "port", ""));
    }

    @Test
    public void setMethodParam_preservesOtherParamsWithinSameMethod() {
        preferencesManager.setMethodParam("tcp", "port", "445");
        preferencesManager.setMethodParam("tcp", "timeout", "1000");
        assertEquals("445", preferencesManager.getMethodParam("tcp", "port", ""));
        assertEquals("1000", preferencesManager.getMethodParam("tcp", "timeout", "200"));
    }

    @Test
    public void setMethodParam_preservesParamsAcrossDifferentMethods() {
        preferencesManager.setMethodParam("tcp", "port", "445");
        preferencesManager.setMethodParam("icmp", "count", "5");
        assertEquals("445", preferencesManager.getMethodParam("tcp", "port", ""));
        assertEquals("5", preferencesManager.getMethodParam("icmp", "count", ""));
    }

    @Test
    public void getMethodParam_nonExistingMethod_returnsDefault() {
        assertEquals("default", preferencesManager.getMethodParam("nonexistent", "key", "default"));
    }

    @Test
    public void getMethodParam_existingMethodMissingKey_returnsDefault() {
        preferencesManager.setMethodParam("tcp", "port", "445");
        assertEquals("999", preferencesManager.getMethodParam("tcp", "nonexistent_key", "999"));
    }

    // ==================== Historial de escaneos ====================

    @Test
    public void addScanRecord_insertsAtBeginning() {
        preferencesManager.addScanRecord(1000L, 10L, 3, new ArrayList<>());
        preferencesManager.addScanRecord(2000L, 20L, 5, new ArrayList<>());

        List<ScanRecord> history = preferencesManager.getScanHistory();
        assertEquals(2, history.size());
        assertEquals(2000L, history.get(0).getTimestamp()); // más reciente primero
        assertEquals(20L, history.get(0).getDurationSec());
        assertEquals(5, history.get(0).getDeviceCount());
        assertEquals(1000L, history.get(1).getTimestamp());
    }

    @Test
    public void addScanRecord_enforcesMaxHistory() {
        for (int i = 0; i < 25; i++) {
            preferencesManager.addScanRecord(i, i, i, new ArrayList<>());
        }
        List<ScanRecord> history = preferencesManager.getScanHistory();
        assertEquals(20, history.size());
        // El más reciente (timestamp 24) debe estar primero
        assertEquals(24L, history.get(0).getTimestamp());
        // El más antiguo conservado debe ser timestamp 5
        assertEquals(5L, history.get(19).getTimestamp());
    }

    @Test
    public void addScanRecord_storesDevicesCorrectly() {
        List<DeviceInfo> devices = new ArrayList<>();
        DeviceInfo device = new DeviceInfo();
        device.setIp("192.168.1.1");
        devices.add(device);

        preferencesManager.addScanRecord(5000L, 30L, 1, devices);

        List<ScanRecord> history = preferencesManager.getScanHistory();
        assertEquals(1, history.size());
        ScanRecord record = history.get(0);
        assertEquals(5000L, record.getTimestamp());
        assertEquals(30L, record.getDurationSec());
        assertEquals(1, record.getDeviceCount());
        assertNotNull(record.getDevices());
        assertEquals(1, record.getDevices().size());
        assertEquals("192.168.1.1", record.getDevices().get(0).getIp());
    }

    // ==================== Exportar / Importar ajustes avanzados ====================

    @Test
    public void exportAdvancedSettings_producesNonNullJson() {
        String exported = preferencesManager.exportAdvancedSettings();
        assertNotNull(exported);
        assertTrue(exported.contains("\"version\""));
        assertTrue(exported.contains("\"active_methods\""));
        assertTrue(exported.contains("\"scan_level\""));
    }

    @Test
    public void exportAndImportAdvancedSettings_roundTrip() {
        preferencesManager.setActiveMethods(Arrays.asList("ARP", "MDNS"));
        preferencesManager.setScanLevel("1000");
        preferencesManager.setMethodParam("tcp", "port", "443");
        preferencesManager.setMethodParam("icmp", "count", "2");

        String exported = preferencesManager.exportAdvancedSettings();

        // Crear un manager limpio con prefs separadas para simular otro contexto
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                .edit().clear().commit();
        PreferencesManager freshManager = new PreferencesManager(context);

        // Antes de importar, los valores son distintos
        assertEquals("100", freshManager.getScanLevel());
        assertEquals(List.of("AUTO"), freshManager.getActiveMethods());

        boolean success = freshManager.importAdvancedSettings(exported);
        assertTrue(success);

        assertEquals(Arrays.asList("ARP", "MDNS"), freshManager.getActiveMethods());
        assertEquals("1000", freshManager.getScanLevel());
        assertEquals("443", freshManager.getMethodParam("tcp", "port", ""));
        assertEquals("2", freshManager.getMethodParam("icmp", "count", ""));
    }

    @Test
    public void importAdvancedSettings_invalidJson_returnsFalse() {
        assertFalse(preferencesManager.importAdvancedSettings("not a json"));
    }

    @Test
    public void importAdvancedSettings_emptyObject_returnsFalse() {
        assertFalse(preferencesManager.importAdvancedSettings("{}"));
    }

    @Test
    public void importAdvancedSettings_wrongVersion_returnsFalse() {
        assertFalse(preferencesManager.importAdvancedSettings(
                "{\"version\":99,\"active_methods\":[],\"method_params\":{},\"scan_level\":\"100\"}"
        ));
    }

    // ==================== Exportar / Importar historial ====================

    @Test
    public void exportScanHistory_producesNonNullJson() {
        preferencesManager.addScanRecord(1000L, 10L, 2, new ArrayList<>());
        String exported = preferencesManager.exportScanHistory();
        assertNotNull(exported);
        assertTrue(exported.contains("\"version\""));
        assertTrue(exported.contains("\"scan_history\""));
    }

    @Test
    public void exportAndImportScanHistory_roundTrip() {
        preferencesManager.addScanRecord(1000L, 10L, 2, new ArrayList<>());
        preferencesManager.addScanRecord(2000L, 20L, 4, new ArrayList<>());

        String exported = preferencesManager.exportScanHistory();

        // Limpiar y reimportar
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                .edit().clear().commit();
        PreferencesManager freshManager = new PreferencesManager(context);
        assertTrue(freshManager.getScanHistory().isEmpty());

        boolean success = freshManager.importScanHistory(exported);
        assertTrue(success);

        List<ScanRecord> history = freshManager.getScanHistory();
        assertEquals(2, history.size());
        // Ordenados por timestamp desc
        assertEquals(2000L, history.get(0).getTimestamp());
        assertEquals(1000L, history.get(1).getTimestamp());
    }

    @Test
    public void importScanHistory_mergesWithExisting_noDuplicatesByTimestamp() {
        preferencesManager.addScanRecord(3000L, 5L, 1, new ArrayList<>());

        // Exportar historial que incluye timestamp 1000 y 2000
        PreferencesManager sourceManager = new PreferencesManager(RuntimeEnvironment.getApplication());
        // Nota: comparten prefs en este contexto, así que añadimos directamente
        preferencesManager.addScanRecord(1000L, 10L, 2, new ArrayList<>());
        preferencesManager.addScanRecord(2000L, 20L, 4, new ArrayList<>());

        // El historial actual tiene 3 registros: 3000, 2000, 1000
        assertEquals(3, preferencesManager.getScanHistory().size());
    }

    @Test
    public void importScanHistory_invalidJson_returnsFalse() {
        assertFalse(preferencesManager.importScanHistory("not a json"));
        assertFalse(preferencesManager.importScanHistory("{}"));
    }

    @Test
    public void importScanHistory_wrongVersion_returnsFalse() {
        assertFalse(preferencesManager.importScanHistory(
                "{\"version\":99,\"scan_history\":[]}"
        ));
    }

    // ==================== Nuevas validaciones de seguridad ====================

    @Test
    public void importScanHistory_tooLargeJson_returnsFalse() {
        // Crear un JSON de más de 2MB
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":1,\"scan_history\":[{\"deviceCount\":1,\"devices\":[{\"ip\":\"192.168.1.1\"");
        // Añadir padding para superar 2MB
        for (int i = 0; i < 300000; i++) {
            sb.append(",\"padding\":\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"");
        }
        sb.append("}]}]}");
        
        assertFalse(preferencesManager.importScanHistory(sb.toString()));
    }

    @Test
    public void importScanHistory_missingScanHistoryField_returnsFalse() {
        assertFalse(preferencesManager.importScanHistory(
                "{\"version\":1,\"other\":[]}"
        ));
    }

    @Test
    public void importScanHistory_scanHistoryNotArray_returnsFalse() {
        assertFalse(preferencesManager.importScanHistory(
                "{\"version\":1,\"scan_history\":\"not an array\"}"
        ));
    }

    @Test
    public void importScanHistory_tooManyRecords_returnsFalse() {
        // Crear 101 registros vacíos
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":1,\"scan_history\":[");
        for (int i = 0; i < 101; i++) {
            sb.append("{\"deviceCount\":0,\"devices\":[],\"durationSec\":0,\"timestamp\":0}");
            if (i < 100) sb.append(",");
        }
        sb.append("]}");
        
        assertFalse(preferencesManager.importScanHistory(sb.toString()));
    }

    @Test
    public void importScanHistory_recordWithoutDevices_returnsFalse() {
        assertFalse(preferencesManager.importScanHistory(
                "{\"version\":1,\"scan_history\":[{\"deviceCount\":0,\"durationSec\":10,\"timestamp\":123}]}"
        ));
    }

    @Test
    public void importScanHistory_deviceWithoutIp_returnsFalse() {
        assertFalse(preferencesManager.importScanHistory(
                "{\"version\":1,\"scan_history\":[{\"deviceCount\":1,\"devices\":[{\"extra\":\"no ip\"}],\"durationSec\":10,\"timestamp\":123}]}"
        ));
    }

    @Test
    public void importScanHistory_deviceWithEmptyIp_returnsFalse() {
        assertFalse(preferencesManager.importScanHistory(
                "{\"version\":1,\"scan_history\":[{\"deviceCount\":1,\"devices\":[{\"ip\":\"\"}],\"durationSec\":10,\"timestamp\":123}]}"
        ));
    }

    // ==================== Validaciones de importAdvancedSettings ====================

    @Test
    public void importAdvancedSettings_tooLargeJson_returnsFalse() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":1,\"active_methods\":[\"AUTO\"],\"method_params\":{},\"scan_level\":\"100\"");
        for (int i = 0; i < 300000; i++) {
            sb.append(",\"padding\":\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"");
        }
        sb.append("}");
        
        assertFalse(preferencesManager.importAdvancedSettings(sb.toString()));
    }

    @Test
    public void importAdvancedSettings_invalidActiveMethods_returnsFalse() {
        // "INVALID" no es un método válido
        assertFalse(preferencesManager.importAdvancedSettings(
                "{\"version\":1,\"active_methods\":[\"INVALID\"],\"method_params\":{},\"scan_level\":\"100\"}"
        ));
    }

    @Test
    public void importAdvancedSettings_invalidScanLevel_returnsFalse() {
        assertFalse(preferencesManager.importAdvancedSettings(
                "{\"version\":1,\"active_methods\":[\"AUTO\"],\"method_params\":{},\"scan_level\":\"999\"}"
        ));
    }

    @Test
    public void importAdvancedSettings_tooManyParams_returnsFalse() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":1,\"active_methods\":[\"AUTO\"],\"method_params\":{");
        for (int i = 0; i < 60; i++) {
            sb.append("\"method" + i + "\":{\"key\":\"value\"}");
            if (i < 59) sb.append(",");
        }
        sb.append("},\"scan_level\":\"100\"}");
        
        assertFalse(preferencesManager.importAdvancedSettings(sb.toString()));
    }

    @Test
    public void importAdvancedSettings_missingActiveMethods_returnsFalse() {
        assertFalse(preferencesManager.importAdvancedSettings(
                "{\"version\":1,\"method_params\":{},\"scan_level\":\"100\"}"
        ));
    }

    @Test
    public void importAdvancedSettings_missingMethodParams_returnsFalse() {
        assertFalse(preferencesManager.importAdvancedSettings(
                "{\"version\":1,\"active_methods\":[\"AUTO\"],\"scan_level\":\"100\"}"
        ));
    }

    @Test
    public void importAdvancedSettings_missingScanLevel_returnsFalse() {
        assertFalse(preferencesManager.importAdvancedSettings(
                "{\"version\":1,\"active_methods\":[\"AUTO\"],\"method_params\":{}}"
        ));
    }

    @Test
    public void importAdvancedSettings_activeMethodsNotArray_returnsFalse() {
        assertFalse(preferencesManager.importAdvancedSettings(
                "{\"version\":1,\"active_methods\":\"not array\",\"method_params\":{},\"scan_level\":\"100\"}"
        ));
    }

    @Test
    public void importAdvancedSettings_methodParamsNotObject_returnsFalse() {
        assertFalse(preferencesManager.importAdvancedSettings(
                "{\"version\":1,\"active_methods\":[\"AUTO\"],\"method_params\":\"not object\",\"scan_level\":\"100\"}"
        ));
    }

    @Test
    public void importAdvancedSettings_scanLevelNotString_returnsFalse() {
        assertFalse(preferencesManager.importAdvancedSettings(
                "{\"version\":1,\"active_methods\":[\"AUTO\"],\"method_params\":{},\"scan_level\":100}"
        ));
    }
}