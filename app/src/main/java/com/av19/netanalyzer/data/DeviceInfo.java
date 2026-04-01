package com.av19.netanalyzer.data;

import java.util.List;

public class DeviceInfo {
    private String ip;
    private Boolean isCurrent;
    private Boolean isGateway;
    private Boolean isDNS;
    private String mac;
    private String vendor;
    private String os;
    private List<Integer> openPorts;
    private Integer ttl;
    private String model;
    private String hostname;

    public DeviceInfo(String ip, String mac, String vendor, List<Integer> openPorts) {
        this.ip = ip;
        this.isCurrent = false;
        this.isGateway = false;
        this.isDNS = false;
        this.mac = mac;
        this.vendor = vendor;
        this.openPorts = openPorts;
        this.ttl = null;
        this.model = null;
        this.hostname = null;
    }

    // Getters
    public String getIp() {
        return ip;
    }

    public Boolean getIsCurrent() {
        return isCurrent;
    }

    public Boolean getIsGateway() {
        return isGateway;
    }

    public Boolean getIsDNS() {
        return isDNS;
    }

    public String getMac() {
        return mac;
    }

    public String getVendor() {
        return vendor;
    }

    public String getOs() {
        return os;
    }

    public List<Integer> getOpenPorts() {
        return openPorts;
    }

    public Integer getTtl() {
        return ttl;
    }

    public String getModel() {
        return model;
    }

    public String getHostname() {
        return hostname;
    }

    // Setters
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

    public void setOs(String os) {
        this.os = os;
    }

    public void setOpenPorts(List<Integer> openPorts) {
        this.openPorts = openPorts;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
}