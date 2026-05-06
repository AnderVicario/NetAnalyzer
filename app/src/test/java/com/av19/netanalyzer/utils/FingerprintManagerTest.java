package com.av19.netanalyzer.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FingerprintManagerTest {

    private FingerprintManager manager;

    @Before
    public void setUp() {
        manager = FingerprintManager.getInstance();
    }

    // ==================== guessOsFromTtl ====================
    @Test
    public void guessOsFromTtl_returnsDesconocido_forNullOrZeroOrNegative() {
        assertEquals("Desconocido", manager.guessOsFromTtl(null));
        assertEquals("Desconocido", manager.guessOsFromTtl(0));
        assertEquals("Desconocido", manager.guessOsFromTtl(-1));
    }

    @Test
    public void guessOsFromTtl_returnsLinux_forTtlUpTo64() {
        assertEquals("Linux / Android / macOS / iOS", manager.guessOsFromTtl(1));
        assertEquals("Linux / Android / macOS / iOS", manager.guessOsFromTtl(64));
    }

    @Test
    public void guessOsFromTtl_returnsWindows_forTtlBetween65And128() {
        assertEquals("Windows", manager.guessOsFromTtl(65));
        assertEquals("Windows", manager.guessOsFromTtl(128));
    }

    @Test
    public void guessOsFromTtl_returnsSolaris_forTtlBetween129And255() {
        assertEquals("Solaris / AIX / Cisco", manager.guessOsFromTtl(129));
        assertEquals("Solaris / AIX / Cisco", manager.guessOsFromTtl(255));
    }

    @Test
    public void guessOsFromTtl_returnsDesconocido_forTtlAbove255() {
        assertEquals("Desconocido", manager.guessOsFromTtl(256));
        assertEquals("Desconocido", manager.guessOsFromTtl(1000));
    }

    // ==================== enrichDevice ====================
    @Test
    public void enrichDevice_marksCurrentDevice() {
        NetworkInfo network = createNetworkInfo("192.168.1.10", "192.168.1.1", List.of("8.8.8.8"));
        DeviceInfo device = new DeviceInfo();
        device.setIp("192.168.1.10");

        manager.enrichDevice(device, network);

        assertTrue(device.getIsCurrent());
        assertNull(device.getIsGateway());
        assertNull(device.getIsDNS());
    }

    @Test
    public void enrichDevice_marksGateway() {
        NetworkInfo network = createNetworkInfo("192.168.1.20", "192.168.1.1", List.of("8.8.8.8"));
        DeviceInfo device = new DeviceInfo();
        device.setIp("192.168.1.1");

        manager.enrichDevice(device, network);

        assertNull(device.getIsCurrent());
        assertTrue(device.getIsGateway());
        assertNull(device.getIsDNS());
    }

    @Test
    public void enrichDevice_marksFirstDnsAsDNS() {
        NetworkInfo network = createNetworkInfo("192.168.1.20", "192.168.1.1", Arrays.asList("192.168.1.5", "8.8.8.8"));
        DeviceInfo device = new DeviceInfo();
        device.setIp("192.168.1.5");

        manager.enrichDevice(device, network);

        assertNull(device.getIsCurrent());
        assertNull(device.getIsGateway());
        assertTrue(device.getIsDNS());
    }

    @Test
    public void enrichDevice_doesNotMarkDNS_ifDnsListEmpty() {
        NetworkInfo network = createNetworkInfo("192.168.1.20", "192.168.1.1", new ArrayList<>());
        DeviceInfo device = new DeviceInfo();
        device.setIp("8.8.8.8");

        manager.enrichDevice(device, network);

        assertNull(device.getIsDNS());
    }

    @Test
    public void enrichDevice_guessesOsFromTtl_whenOsIsNull() {
        NetworkInfo network = createNetworkInfo("192.168.1.10", "192.168.1.1", List.of("8.8.8.8"));
        DeviceInfo device = new DeviceInfo();
        device.setIp("192.168.1.20");
        device.setTtl(64);

        manager.enrichDevice(device, network);

        assertNotNull(device.getOs());
        assertEquals("Linux / Android / macOS / iOS", device.getOs().getValue());
        assertEquals(-1, device.getOs().getPriority());
    }

    @Test
    public void enrichDevice_doesNotOverrideOs_withHigherPriority() {
        NetworkInfo network = createNetworkInfo("192.168.1.10", "192.168.1.1", List.of("8.8.8.8"));
        DeviceInfo device = new DeviceInfo();
        device.setIp("192.168.1.20");
        device.setTtl(64);
        device.setOs(new DeviceInfo.PriorityValue(10, "Custom OS"));

        manager.enrichDevice(device, network);

        assertEquals("Custom OS", device.getOs().getValue());
        assertEquals(10, device.getOs().getPriority());
    }

    @Test
    public void enrichDevice_doesNotGuessOs_whenTtlNull() {
        NetworkInfo network = createNetworkInfo("192.168.1.10", "192.168.1.1", List.of("8.8.8.8"));
        DeviceInfo device = new DeviceInfo();
        device.setIp("192.168.1.20");
        device.setTtl(null);

        manager.enrichDevice(device, network);

        assertNull(device.getOs());
    }

    // ==================== getDeviceInfo ====================
    @Test
    public void getDeviceInfo_createsDeviceWithGivenFieldsAndEnriches() {
        NetworkInfo network = createNetworkInfo("192.168.1.10", "192.168.1.1", List.of("8.8.8.8"));
        String ip = "192.168.1.20";
        String mac = "AA:BB:CC:DD:EE:FF";
        String vendor = "TestVendor";
        List<Integer> ports = Arrays.asList(80, 443);
        Integer ttl = 64;

        DeviceInfo device = manager.getDeviceInfo(network, ip, mac, vendor, ports, ttl);

        assertEquals(ip, device.getIp());
        assertEquals(mac, device.getMac());
        assertEquals(vendor, device.getVendor());
        assertEquals(ports, device.getOpenPorts());
        assertEquals(ttl, device.getTtl());
        assertFalse(device.getIsCurrent()); // IP no coincide con network IP
        assertFalse(device.getIsGateway()); // IP no es gateway
        assertFalse(device.getIsDNS());     // IP no es DNS
        assertNotNull(device.getOs());
        assertEquals("Linux / Android / macOS / iOS", device.getOs().getValue());
    }

    // ==================== Helper ====================
    private NetworkInfo createNetworkInfo(String myIp, String gateway, List<String> dns) {
        // Constructor: ip, netmask, prefix, networkAddress, gateway, dns, connectionType,
        // hasInternet, validated, metered, downstream, upstream, ssid, bssid, rssi, linkSpeed, frequency
        // Valores por defecto para campos no relevantes en estos tests.
        return new NetworkInfo(
                myIp,               // ip
                "255.255.255.0",    // netmask
                24,                 // prefix
                "192.168.1.0",      // networkAddress (simplificado)
                gateway,            // gateway
                new ArrayList<>(dns), // dns
                "WiFi",             // connectionType
                true,               // hasInternet
                true,               // validated
                false,              // metered
                100000,             // downstreamBandwidth
                10000,              // upstreamBandwidth
                "TestSSID",         // ssid
                "00:11:22:33:44:55",// bssid
                -50,                // rssi
                300,                // linkSpeed
                2400                // frequency
        );
    }
}