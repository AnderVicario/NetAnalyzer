package com.av19.netanalyzer.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetUtilsTest {

    // ==================== ipToInt ====================
    @Test
    public void ipToInt_convertsValidIp() {
        assertEquals(0xC0A80101, NetUtils.ipToInt("192.168.1.1"));
        assertEquals(0x0A000001, NetUtils.ipToInt("10.0.0.1"));
        // 255.255.255.255 en int con signo es -1, pero 0xFFFFFFFF también es -1
        assertEquals(0xFFFFFFFF, NetUtils.ipToInt("255.255.255.255"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipToInt_throwsOnOctetAbove255() {
        NetUtils.ipToInt("256.1.2.3");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipToInt_throwsOnNegativeOctet() {
        NetUtils.ipToInt("-1.0.0.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void ipToInt_throwsOnMissingOctet() {
        NetUtils.ipToInt("192.168.1");
    }

    @Test(expected = NullPointerException.class)
    public void ipToInt_throwsOnNull() {
        NetUtils.ipToInt(null);
    }

    // ==================== intToIp ====================
    @Test
    public void intToIp_convertsValidInt() {
        assertEquals("192.168.1.1", NetUtils.intToIp(0xC0A80101));
        assertEquals("10.0.0.1", NetUtils.intToIp(0x0A000001));
        assertEquals("255.255.255.255", NetUtils.intToIp(0xFFFFFFFF));
        assertEquals("0.0.0.0", NetUtils.intToIp(0));
    }

    @Test
    public void intToIp_convertsNegativeInt() {
        assertEquals("255.255.255.255", NetUtils.intToIp(-1));
        assertEquals("128.0.0.0", NetUtils.intToIp(Integer.MIN_VALUE)); // -2147483648
    }

    @Test
    public void intToIp_isInverseOfIpToInt() {
        int original = 0xC0A80101;
        String ip = NetUtils.intToIp(original);
        int result = NetUtils.ipToInt(ip);
        assertEquals(original, result);
    }

    @Test
    public void roundTrip_worksForVariousIps() {
        String[] ips = {
                "0.0.0.0",
                "127.0.0.1",
                "192.168.1.1",
                "255.255.255.255",
                "10.0.0.1",
                "172.16.0.1"
        };
        for (String ip : ips) {
            int intVal = NetUtils.ipToInt(ip);
            assertEquals(ip, NetUtils.intToIp(intVal));
            assertEquals(intVal, NetUtils.ipToInt(NetUtils.intToIp(intVal)));
        }
    }

    // ==================== compareIps ====================
    @Test
    public void compareIps_returnsZeroForEqualIps() {
        assertEquals(0, NetUtils.compareIps("192.168.1.1", "192.168.1.1"));
        assertEquals(0, NetUtils.compareIps("10.0.0.1", "10.0.0.1"));
    }

    @Test
    public void compareIps_negativeWhenFirstIsSmaller() {
        assertTrue(NetUtils.compareIps("192.168.1.1", "192.168.1.10") < 0);
        assertTrue(NetUtils.compareIps("10.0.0.0", "10.0.0.1") < 0);
        assertTrue(NetUtils.compareIps("192.168.0.1", "192.168.1.1") < 0);
    }

    @Test
    public void compareIps_positiveWhenFirstIsLarger() {
        assertTrue(NetUtils.compareIps("192.168.1.10", "192.168.1.1") > 0);
        assertTrue(NetUtils.compareIps("10.0.0.2", "10.0.0.1") > 0);
    }

    @Test
    public void compareIps_handlesNullGracefully() {
        assertTrue(NetUtils.compareIps(null, "192.168.1.1") < 0);
        assertTrue(NetUtils.compareIps("192.168.1.1", null) > 0);
        assertEquals(0, NetUtils.compareIps(null, null));
    }

    @Test
    public void compareIps_handlesDifferentLengths() {
        assertTrue(NetUtils.compareIps("192.168.1", "192.168.1.1") < 0);
        assertTrue(NetUtils.compareIps("192.168.1.1", "192.168.1") > 0);
    }

    @Test
    public void compareIps_fallsBackToStringComparisonForNonNumeric() {
        assertTrue(NetUtils.compareIps("192.168.a.1", "192.168.b.1") < 0);
        assertEquals(0, NetUtils.compareIps("192.168.a.1", "192.168.a.1"));
    }

    @Test
    public void compareIps_sortingOrderMatchesNumericOrder() {
        String[] ips = {
                "10.0.0.1",
                "10.0.0.10",
                "10.0.0.2",
                "172.16.0.1",
                "192.168.1.1",
                "192.168.1.100"
        };
        String[] expected = {
                "10.0.0.1",
                "10.0.0.2",
                "10.0.0.10",
                "172.16.0.1",
                "192.168.1.1",
                "192.168.1.100"
        };
        java.util.Arrays.sort(ips, NetUtils::compareIps);
        assertArrayEquals(expected, ips);
    }
}