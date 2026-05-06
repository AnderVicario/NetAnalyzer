package com.av19.netanalyzer.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListDeviceInfoTest {

    private ListDeviceInfo listDeviceInfo;

    @Before
    public void setUp() {
        listDeviceInfo = new ListDeviceInfo();
    }

    // ==================== addOrUpdate – casos básicos ====================
    @Test
    public void addOrUpdate_addsNewDevice() {
        DeviceInfo device = createDevice("192.168.1.1", "host1", null, null, null, null, null, null, null);
        DeviceInfo result = listDeviceInfo.addOrUpdate(device);

        assertEquals(1, listDeviceInfo.size());
        assertSame(device, result);
        assertEquals("host1", listDeviceInfo.getDevices().get(0).getHostname().getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addOrUpdate_throwsWhenIpIsNull() {
        DeviceInfo device = new DeviceInfo();
        device.setIp(null);
        listDeviceInfo.addOrUpdate(device);
    }

    // ==================== merge – hostname (prioridad) ====================
    @Test
    public void merge_hostname_higherPriorityWins() {
        DeviceInfo existing = createDevice("192.168.1.1", "old", 1, null, null, null, null, null, null);
        DeviceInfo newDevice = createDevice("192.168.1.1", "new", 2, null, null, null, null, null, null);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals("new", result.getHostname().getValue());
        assertEquals(2, result.getHostname().getPriority());
    }

    @Test
    public void merge_hostname_lowerPriorityDoesNotOverride() {
        DeviceInfo existing = createDevice("192.168.1.1", "old", 2, null, null, null, null, null, null);
        DeviceInfo newDevice = createDevice("192.168.1.1", "new", 1, null, null, null, null, null, null);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals("old", result.getHostname().getValue());
        assertEquals(2, result.getHostname().getPriority());
    }

    // ==================== merge – MAC y vendor ====================
    @Test
    public void merge_mac_setsMacAndVendorWhenExistingEmpty() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);
        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, "AA:BB:CC", "VendorX", null, null, null, null);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals("AA:BB:CC", result.getMac());
        assertEquals("VendorX", result.getVendor());
    }

    @Test
    public void merge_mac_doesNotOverrideExistingMac() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, "AA:BB:CC", "OldVendor", null, null, null, null);
        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, "DD:EE:FF", "NewVendor", null, null, null, null);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals("AA:BB:CC", result.getMac()); // no se sobrescribe
        assertEquals("OldVendor", result.getVendor());
    }

    @Test
    public void merge_vendor_setsOnlyVendorWhenMacEmptyAndExistingVendorEmpty() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);
        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, null, "VendorY", null, null, null, null);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals("VendorY", result.getVendor());
        assertNull(result.getMac());
    }

    // ==================== merge – OS (prioridad) ====================
    @Test
    public void merge_os_higherPriorityWins() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, createOs("Linux", 1), null, null, null);
        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, null, null, createOs("Windows", 2), null, null, null);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals("Windows", result.getOs().getValue());
        assertEquals(2, result.getOs().getPriority());
    }

    // ==================== merge – TTL (el menor) ====================
    @Test
    public void merge_ttl_takesMinimum() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, null, 128, null, null);
        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, null, null, null, 64, null, null);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals(64, result.getTtl().intValue());
    }

    @Test
    public void merge_ttl_takesNewWhenExistingNull() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);
        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, null, null, null, 64, null, null);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals(64, result.getTtl().intValue());
    }

    // ==================== merge – modelo (prioridad) ====================
    @Test
    public void merge_model_higherPriorityWins() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, null, null, createModel("ModelA", 1), null);
        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, null, null, null, null, createModel("ModelB", 2), null);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals("ModelB", result.getModel().getValue());
        assertEquals(2, result.getModel().getPriority());
    }

    // ==================== merge – puertos (unión sin duplicados, ordenados) ====================
    @Test
    public void merge_ports_unionWithoutDuplicatesAndSorted() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, null, null, null, Arrays.asList(80, 443));
        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, null, null, null, null, null, Arrays.asList(443, 22, 8080));

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals(Arrays.asList(22, 80, 443, 8080), result.getOpenPorts());
    }

    @Test
    public void merge_ports_ignoresWhenNewPortsNull() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, null, null, null, List.of(80));
        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals(List.of(80), result.getOpenPorts());
    }

    // ==================== merge – flags booleanos (OR) ====================
    @Test
    public void merge_booleanFlags_truePersists() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);
        existing.setIsCurrent(false);
        existing.setIsGateway(false);
        existing.setIsDNS(false);

        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);
        newDevice.setIsCurrent(true);
        newDevice.setIsGateway(true);
        newDevice.setIsDNS(true);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertTrue(result.getIsCurrent());
        assertTrue(result.getIsGateway());
        assertTrue(result.getIsDNS());
    }

    @Test
    public void merge_booleanFlags_falseDoesNotOverrideTrue() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);
        existing.setIsCurrent(true);
        existing.setIsGateway(true);
        existing.setIsDNS(true);

        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);
        newDevice.setIsCurrent(false);
        newDevice.setIsGateway(false);
        newDevice.setIsDNS(false);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertTrue(result.getIsCurrent());
        assertTrue(result.getIsGateway());
        assertTrue(result.getIsDNS());
    }

    // ==================== merge – extraDetails (fusión de mapas) ====================
    @Test
    public void merge_extraDetails_mergesAllEntries() {
        DeviceInfo existing = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);
        Map<String, String> details1 = new HashMap<>();
        details1.put("key1", "value1");
        details1.put("key2", "value2");
        existing.getExtraDetails().putAll(details1);

        DeviceInfo newDevice = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);
        Map<String, String> details2 = new HashMap<>();
        details2.put("key2", "newValue2");
        details2.put("key3", "value3");
        newDevice.getExtraDetails().putAll(details2);

        listDeviceInfo.addOrUpdate(existing);
        listDeviceInfo.addOrUpdate(newDevice);

        DeviceInfo result = listDeviceInfo.getDevices().get(0);
        assertEquals(3, result.getExtraDetails().size());
        assertEquals("value1", result.getExtraDetails().get("key1"));
        assertEquals("newValue2", result.getExtraDetails().get("key2")); // sobrescrito
        assertEquals("value3", result.getExtraDetails().get("key3"));
    }

    // ==================== getDevices – devuelve copia ====================
    @Test
    public void getDevices_returnsCopy_notAffectedByExternalModification() {
        DeviceInfo device = createDevice("192.168.1.1", null, null, null, null, null, null, null, null);
        listDeviceInfo.addOrUpdate(device);

        List<DeviceInfo> devicesCopy = listDeviceInfo.getDevices();
        assertEquals(1, devicesCopy.size());

        // Modificar la copia no afecta al mapa interno
        devicesCopy.clear();
        assertEquals(1, listDeviceInfo.size());
    }

    // ==================== clear y size ====================
    @Test
    public void clear_removesAllDevices() {
        listDeviceInfo.addOrUpdate(createDevice("192.168.1.1", null, null, null, null, null, null, null, null));
        listDeviceInfo.addOrUpdate(createDevice("192.168.1.2", null, null, null, null, null, null, null, null));
        assertEquals(2, listDeviceInfo.size());

        listDeviceInfo.clear();
        assertEquals(0, listDeviceInfo.size());
        assertTrue(listDeviceInfo.getDevices().isEmpty());
    }

    // ==================== Helper methods ====================
    private DeviceInfo createDevice(String ip, String hostname, Integer hostnamePriority,
                                    String mac, String vendor,
                                    DeviceInfo.PriorityValue os,
                                    Integer ttl,
                                    DeviceInfo.PriorityValue model,
                                    List<Integer> ports) {
        DeviceInfo device = new DeviceInfo();
        device.setIp(ip);
        if (hostname != null) {
            device.setHostname(new DeviceInfo.PriorityValue(hostnamePriority != null ? hostnamePriority : 0, hostname));
        }
        if (mac != null) device.setMac(mac);
        if (vendor != null) device.setVendor(vendor);
        if (os != null) device.setOs(os);
        if (ttl != null) device.setTtl(ttl);
        if (model != null) device.setModel(model);
        if (ports != null) device.setOpenPorts(ports);
        return device;
    }

    private DeviceInfo.PriorityValue createOs(String value, int priority) {
        return new DeviceInfo.PriorityValue(priority, value);
    }

    private DeviceInfo.PriorityValue createModel(String value, int priority) {
        return new DeviceInfo.PriorityValue(priority, value);
    }
}