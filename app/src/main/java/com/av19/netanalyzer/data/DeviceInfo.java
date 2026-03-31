package com.av19.netanalyzer.data;

import java.util.List;

public class DeviceInfo {
    private String ip;
    private Boolean isCurrent;
    private Boolean isGateway;
    private Boolean isDNS;
    private String mac;
    private String vendor;
    private List<Integer> openPorts;

    public DeviceInfo(String ip, String mac, String vendor, List<Integer> openPorts) {
        this.ip = ip;
        this.isCurrent = false;
        this.isGateway = false;
        this.isDNS = false;
        this.mac = mac;
        this.vendor = vendor;
        this.openPorts = openPorts;
    }

    public String getIp() { return ip; }
    public Boolean getIsCurrent() { return isCurrent; }
    public Boolean getIsGateway() { return isGateway; }
    public Boolean getIsDNS() { return isDNS; }
    public String getMac() { return mac; }
    public String getVendor() { return vendor; }
    public List<Integer> getOpenPorts() { return openPorts; }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setIsCurrent(Boolean isCurrent) {
        this.isCurrent = isCurrent;
    }

    public void setIsGateway(Boolean isGateway) {
        this.isGateway = isGateway;
    }

    public void setIsDNS(Boolean isDNS) {
        this.isDNS = isDNS;
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
