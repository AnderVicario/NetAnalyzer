package com.av19.netanalyzer.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DeviceInfoTest {

    private DeviceInfo device;

    @Before
    public void setUp() {
        device = new DeviceInfo();
    }

    // ==================== Constructores ====================

    @Test
    public void defaultConstructor_initializesExtraDetailsEmpty() {
        assertNotNull(device.getExtraDetails());
        assertTrue(device.getExtraDetails().isEmpty());
    }

    @Test
    public void parameterizedConstructor_setsFieldsCorrectly() {
        List<Integer> ports = Arrays.asList(80, 443);
        DeviceInfo d = new DeviceInfo("192.168.1.1", "AA:BB:CC:DD:EE:FF", "VendorX", ports);

        assertEquals("192.168.1.1", d.getIp());
        assertEquals("AA:BB:CC:DD:EE:FF", d.getMac());
        assertEquals("VendorX", d.getVendor());
        assertEquals(ports, d.getOpenPorts());

        // El constructor parametrizado inicializa los flags a false explícitamente
        assertFalse(d.getIsCurrent());
        assertFalse(d.getIsGateway());
        assertFalse(d.getIsDNS());

        assertNull(d.getOs());
        assertNull(d.getTtl());
        assertNull(d.getModel());
        assertNull(d.getHostname());

        assertNotNull(d.getExtraDetails());
        assertTrue(d.getExtraDetails().isEmpty());
    }

    @Test
    public void parameterizedConstructor_canHandleNullPorts() {
        DeviceInfo d = new DeviceInfo("10.0.0.1", null, null, null);
        assertNull(d.getOpenPorts());
    }

    // ==================== Getters y Setters básicos ====================

    @Test
    public void setAndGetIp() {
        device.setIp("10.0.0.1");
        assertEquals("10.0.0.1", device.getIp());
    }

    @Test
    public void setAndGetMac() {
        device.setMac("00:11:22:33:44:55");
        assertEquals("00:11:22:33:44:55", device.getMac());
    }

    @Test
    public void setAndGetVendor() {
        device.setVendor("Cisco");
        assertEquals("Cisco", device.getVendor());
    }

    @Test
    public void setAndGetTtl() {
        device.setTtl(64);
        assertEquals(Integer.valueOf(64), device.getTtl());
    }

    @Test
    public void setTtl_nullIsValid() {
        device.setTtl(64);
        device.setTtl(null);
        assertNull(device.getTtl());
    }

    @Test
    public void setAndGetOpenPorts() {
        List<Integer> ports = Arrays.asList(22, 80, 443);
        device.setOpenPorts(ports);
        // Verificar contenido, sin depender de aliasing
        assertEquals(3, device.getOpenPorts().size());
        assertEquals(Integer.valueOf(22), device.getOpenPorts().get(0));
        assertEquals(Integer.valueOf(443), device.getOpenPorts().get(2));
    }

    @Test
    public void setOpenPorts_nullIsAllowed() {
        device.setOpenPorts(null);
        assertNull(device.getOpenPorts());
    }

    // ==================== Flags booleanos ====================

    @Test
    public void booleanFlags_defaultToNull_inDefaultConstructor() {
        // El constructor por defecto NO inicializa los flags → null
        assertNull(device.getIsCurrent());
        assertNull(device.getIsGateway());
        assertNull(device.getIsDNS());
    }

    @Test
    public void booleanFlags_defaultToFalse_inParameterizedConstructor() {
        // El constructor parametrizado SÍ inicializa los flags a false
        DeviceInfo d = new DeviceInfo("1.2.3.4", null, null, null);
        assertFalse(d.getIsCurrent());
        assertFalse(d.getIsGateway());
        assertFalse(d.getIsDNS());
    }

    @Test
    public void booleanFlags_setAndGet() {
        device.setIsCurrent(true);
        device.setIsGateway(false);
        device.setIsDNS(true);

        assertTrue(device.getIsCurrent());
        assertFalse(device.getIsGateway());
        assertTrue(device.getIsDNS());
    }

    @Test
    public void booleanFlags_canBeSetBackToNull() {
        device.setIsCurrent(true);
        device.setIsCurrent(null);
        assertNull(device.getIsCurrent());
    }

    // ==================== PriorityValue y objetos anidados ====================

    @Test
    public void setAndGetHostname() {
        DeviceInfo.PriorityValue host = new DeviceInfo.PriorityValue(2, "MyHost");
        device.setHostname(host);
        assertSame(host, device.getHostname());
        assertEquals("MyHost", device.getHostname().getValue());
        assertEquals(2, device.getHostname().getPriority());
    }

    @Test
    public void setAndGetOs() {
        DeviceInfo.PriorityValue os = new DeviceInfo.PriorityValue(3, "Linux");
        device.setOs(os);
        assertEquals("Linux", device.getOs().getValue());
        assertEquals(3, device.getOs().getPriority());
    }

    @Test
    public void setAndGetModel() {
        DeviceInfo.PriorityValue model = new DeviceInfo.PriorityValue(1, "X100");
        device.setModel(model);
        assertEquals("X100", device.getModel().getValue());
    }

    @Test
    public void setHostname_nullIsAllowed() {
        device.setHostname(new DeviceInfo.PriorityValue(1, "Host"));
        device.setHostname(null);
        assertNull(device.getHostname());
    }

    // ==================== addDetail ====================

    @Test
    public void addDetail_addsEntry() {
        device.addDetail("manufacturer", "Apple");
        assertEquals("Apple", device.getExtraDetails().get("manufacturer"));
        assertEquals(1, device.getExtraDetails().size());
    }

    @Test
    public void addDetail_overwritesExistingKey() {
        device.addDetail("key", "old");
        device.addDetail("key", "new");
        assertEquals("new", device.getExtraDetails().get("key"));
        assertEquals(1, device.getExtraDetails().size());
    }

    @Test
    public void addDetail_withNullValue_doesNotAdd() {
        device.addDetail("key", null);
        assertTrue(device.getExtraDetails().isEmpty());
    }

    @Test
    public void addDetail_multipleEntries_nullSkipped() {
        device.addDetail("A", "1");
        device.addDetail("B", "2");
        device.addDetail("C", null); // no se añade
        assertEquals(2, device.getExtraDetails().size());
        assertEquals("1", device.getExtraDetails().get("A"));
        assertEquals("2", device.getExtraDetails().get("B"));
    }

    // ==================== Clase interna PriorityValue ====================

    @Test
    public void priorityValue_parameterizedConstructor() {
        DeviceInfo.PriorityValue pv = new DeviceInfo.PriorityValue(5, "test");
        assertEquals(5, pv.getPriority());
        assertEquals("test", pv.getValue());
    }

    @Test
    public void priorityValue_defaultConstructor_fieldsAreJavaDefaults() {
        DeviceInfo.PriorityValue pv = new DeviceInfo.PriorityValue();
        assertEquals(0, pv.getPriority()); // int por defecto es 0
        assertNull(pv.getValue());         // String por defecto es null
    }

    // ==================== Estado general del constructor por defecto ====================

    @Test
    public void freshlyCreatedDevice_hasNoAttributesSet() {
        DeviceInfo empty = new DeviceInfo();
        assertNull(empty.getIp());
        assertNull(empty.getMac());
        assertNull(empty.getVendor());
        assertNull(empty.getOs());
        assertNull(empty.getTtl());
        assertNull(empty.getModel());
        assertNull(empty.getHostname());
        assertNull(empty.getOpenPorts());
        // CORRECCIÓN: el constructor por defecto NO inicializa los flags → null, no false
        assertNull(empty.getIsCurrent());
        assertNull(empty.getIsGateway());
        assertNull(empty.getIsDNS());
        assertNotNull(empty.getExtraDetails());
        assertTrue(empty.getExtraDetails().isEmpty());
    }
}