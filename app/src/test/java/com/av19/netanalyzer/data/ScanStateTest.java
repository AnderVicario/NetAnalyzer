package com.av19.netanalyzer.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ScanStateTest {

    // ==================== idle ====================
    @Test
    public void idle_returnsCorrectDefaults() {
        ScanState state = ScanState.idle();

        assertEquals(ScanState.Status.IDLE, state.getStatus());
        assertEquals(ScanState.Phase.NONE, state.getPhase());
        assertNull(state.getCurrentMethod());
        assertEquals(0, state.getProgress());
        assertNull(state.getCurrentHost());
        assertTrue(state.getDevices().isEmpty());
        assertNull(state.getNetworkInfo());
        assertNull(state.getErrorMessage());
    }

    // ==================== scanning ====================
    @Test
    public void scanning_returnsGivenValues() {
        List<DeviceInfo> devices = new ArrayList<>();
        devices.add(new DeviceInfo("192.168.1.1", null, null, null));
        NetworkInfo network = createDummyNetwork();

        ScanState state = ScanState.scanning(
                75,
                ScanState.Phase.DISCOVERY,
                "ARP",
                "192.168.1.100",
                devices,
                network
        );

        assertEquals(ScanState.Status.SCANNING, state.getStatus());
        assertEquals(ScanState.Phase.DISCOVERY, state.getPhase());
        assertEquals("ARP", state.getCurrentMethod());
        assertEquals(75, state.getProgress());
        assertEquals("192.168.1.100", state.getCurrentHost());
        assertEquals(devices, state.getDevices()); // equals por contenido
        assertSame(network, state.getNetworkInfo());
        assertNull(state.getErrorMessage());
    }

    @Test
    public void scanning_devicesIsDefensiveCopy() {
        List<DeviceInfo> original = new ArrayList<>();
        original.add(new DeviceInfo("10.0.0.1", null, null, null));
        ScanState state = ScanState.scanning(0, ScanState.Phase.NONE, null, null, original, null);

        // Modificar la lista original no afecta al estado
        original.clear();
        assertEquals(1, state.getDevices().size());
    }

    // ==================== completed ====================
    @Test
    public void completed_returnsCorrectValues() {
        List<DeviceInfo> devices = new ArrayList<>();
        devices.add(new DeviceInfo("10.0.0.1", null, null, null));
        NetworkInfo network = createDummyNetwork();

        ScanState state = ScanState.completed(devices, network);

        assertEquals(ScanState.Status.COMPLETED, state.getStatus());
        assertEquals(ScanState.Phase.NONE, state.getPhase());
        assertNull(state.getCurrentMethod());
        assertEquals(100, state.getProgress());
        assertNull(state.getCurrentHost());
        assertEquals(devices, state.getDevices());
        assertSame(network, state.getNetworkInfo());
        assertNull(state.getErrorMessage());
    }

    @Test
    public void completed_devicesIsDefensiveCopy() {
        List<DeviceInfo> original = new ArrayList<>();
        original.add(new DeviceInfo("10.0.0.1", null, null, null));
        ScanState state = ScanState.completed(original, null);

        original.clear();
        assertFalse(state.getDevices().isEmpty());
    }

    // ==================== error ====================
    @Test
    public void error_returnsCorrectValues() {
        ScanState state = ScanState.error("Fallo de red", ScanState.Phase.PORT_SCAN, "NIO");

        assertEquals(ScanState.Status.ERROR, state.getStatus());
        assertEquals(ScanState.Phase.PORT_SCAN, state.getPhase());
        assertEquals("NIO", state.getCurrentMethod());
        assertEquals(100, state.getProgress());
        assertNull(state.getCurrentHost());
        assertTrue(state.getDevices().isEmpty());
        assertNull(state.getNetworkInfo());
        assertEquals("Fallo de red", state.getErrorMessage());
    }

    @Test
    public void error_withNullPhaseAndMethod() {
        ScanState state = ScanState.error("msg", null, null);
        assertEquals(ScanState.Status.ERROR, state.getStatus());
        assertNull(state.getPhase());
        assertNull(state.getCurrentMethod());
        assertEquals("msg", state.getErrorMessage());
    }

    // ==================== Helper ====================
    private NetworkInfo createDummyNetwork() {
        return new NetworkInfo(
                "192.168.1.1",          // ip
                "255.255.255.0",         // netmask
                24,                      // prefix
                "192.168.1.0",           // networkAddress
                "192.168.1.254",         // gateway
                new ArrayList<>(),       // dns
                "WiFi",                  // connectionType
                true,                    // hasInternet
                true,                    // validated
                false,                   // metered
                100000,                  // downstream
                10000,                   // upstream
                "TestSSID",              // ssid
                "AA:BB:CC:DD:EE:FF",     // bssid
                -50,                     // rssi
                300,                     // linkSpeed
                2400                     // frequency
        );
    }
}