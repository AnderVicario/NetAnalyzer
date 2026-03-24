package com.av19.netanalyzer.data;

public class NetworkInfo {
    private final String ip;
    private final String netmask;
    private final int prefix;
    private final String networkAddress;
    private final String gateway;
    private final String dns;
    private final String connectionType; // WiFi, Cellular, etc.
    private final boolean hasInternet;
    private final boolean validated;
    private final boolean metered;
    private final int downstreamBandwidth;
    private final int upstreamBandwidth;
    private final String ssid;
    private final String bssid;
    private final int rssi;
    private final int linkSpeed;
    private final int frequency;

    // Constructor
    public NetworkInfo(String ip, String netmask, int prefix, String networkAddress,
                       String gateway, String dns, String connectionType,
                       boolean hasInternet, boolean validated, boolean metered,
                       int downstreamBandwidth, int upstreamBandwidth,
                       String ssid, String bssid, int rssi, int linkSpeed, int frequency) {
        this.ip = ip;
        this.netmask = netmask;
        this.prefix = prefix;
        this.networkAddress = networkAddress;
        this.gateway = gateway;
        this.dns = dns;
        this.connectionType = connectionType;
        this.hasInternet = hasInternet;
        this.validated = validated;
        this.metered = metered;
        this.downstreamBandwidth = downstreamBandwidth;
        this.upstreamBandwidth = upstreamBandwidth;
        this.ssid = ssid;
        this.bssid = bssid;
        this.rssi = rssi;
        this.linkSpeed = linkSpeed;
        this.frequency = frequency;
    }

    // Getters
    public String getIp() { return ip; }
    public String getNetmask() { return netmask; }
    public int getPrefix() { return prefix; }
    public String getNetworkAddress() { return networkAddress; }
    public String getGateway() { return gateway; }
    public String getDns() { return dns; }
    public String getConnectionType() { return connectionType; }
    public boolean isHasInternet() { return hasInternet; }
    public boolean isValidated() { return validated; }
    public boolean isMetered() { return metered; }
    public int getDownstreamBandwidth() { return downstreamBandwidth; }
    public int getUpstreamBandwidth() { return upstreamBandwidth; }
    public String getSsid() { return ssid; }
    public String getBssid() { return bssid; }
    public int getRssi() { return rssi; }
    public int getLinkSpeed() { return linkSpeed; }
    public int getFrequency() { return frequency; }
}