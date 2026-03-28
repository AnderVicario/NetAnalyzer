package com.av19.netanalyzer.data;

import java.util.List;

public class DeviceInfo {
    private String ip;
    private String mac;
    private String vendor;
    private List<Integer> openPorts;

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

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public void setOpenPorts(List<Integer> openPorts) {
        this.openPorts = openPorts;
    }
}
