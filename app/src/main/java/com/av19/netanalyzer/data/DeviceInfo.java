package com.av19.netanalyzer.data;

import java.util.List;

public class DeviceInfo {
    private final String ip;
    private final String mac;
    private final String vendor;
    private final List<Integer> openPorts;

    public DeviceInfo(String ip, String mac, String vendor, List<Integer> openPorts) {
        this.ip = ip;
        this.mac = mac;
        this.vendor = vendor;
        this.openPorts = openPorts;
    }

    public String getIp() { return ip; }
    public String getMac() { return mac; }
    public String getVendor() { return vendor; }
    public List<Integer> getOpenPorts() { return openPorts; }
}