package com.av19.netanalyzer.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceInfo {
    private String ip;
    private Boolean isCurrent;
    private Boolean isGateway;
    private Boolean isDNS;
    private String mac;
    private String vendor;
    private PriorityValue os;
    private List<Integer> openPorts;
    private Integer ttl;
    private PriorityValue model;
    private PriorityValue hostname;
    private final Map<String, String> extraDetails;

    public DeviceInfo(String ip, String mac, String vendor, List<Integer> openPorts) {
        this.ip = ip;
        this.isCurrent = false;
        this.isGateway = false;
        this.isDNS = false;
        this.mac = mac;
        this.vendor = vendor;
        this.os = null;
        this.openPorts = openPorts;
        this.ttl = null;
        this.model = null;
        this.hostname = null;
        this.extraDetails = new HashMap<>();
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

    public PriorityValue getOs() {
        return os;
    }

    public List<Integer> getOpenPorts() {
        return openPorts;
    }

    public Integer getTtl() {
        return ttl;
    }

    public PriorityValue getModel() {
        return model;
    }

    public PriorityValue getHostname() {
        return hostname;
    }

    public Map<String, String> getExtraDetails() {
        return extraDetails;
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

    public void setOs(PriorityValue os) {
        this.os = os;
    }

    public void setOpenPorts(List<Integer> openPorts) {
        this.openPorts = openPorts;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public void setModel(PriorityValue model) {
        this.model = model;
    }

    public void setHostname(PriorityValue hostname) {
        this.hostname = hostname;
    }

    public void addDetail(String key, String value) {
        if (value != null) extraDetails.put(key, value);
    }

    public static class PriorityValue {
        private int priority;
        private String value;

        public PriorityValue(int priority, String value) {
            this.priority = priority;
            this.value = value;
        }

        public int getPriority() {
            return priority;
        }

        public String getValue() {
            return value;
        }
    }
}
