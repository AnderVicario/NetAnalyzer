package com.av19.netanalyzer.data;

import android.os.Parcel;
import android.os.Parcelable;

public class NetworkInfo implements Parcelable {
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
                       boolean hasInternet, boolean isValidated, boolean isMetered,
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
        this.validated = isValidated;
        this.metered = isMetered;
        this.downstreamBandwidth = downstreamBandwidth;
        this.upstreamBandwidth = upstreamBandwidth;
        this.ssid = ssid;
        this.bssid = bssid;
        this.rssi = rssi;
        this.linkSpeed = linkSpeed;
        this.frequency = frequency;
    }

    protected NetworkInfo(Parcel in) {
        ip = in.readString();
        netmask = in.readString();
        prefix = in.readInt();
        networkAddress = in.readString();
        gateway = in.readString();
        dns = in.readString();
        connectionType = in.readString();
        hasInternet = in.readByte() != 0;
        validated = in.readByte() != 0;
        metered = in.readByte() != 0;
        downstreamBandwidth = in.readInt();
        upstreamBandwidth = in.readInt();
        ssid = in.readString();
        bssid = in.readString();
        rssi = in.readInt();
        linkSpeed = in.readInt();
        frequency = in.readInt();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(ip);
        dest.writeString(netmask);
        dest.writeInt(prefix);
        dest.writeString(networkAddress);
        dest.writeString(gateway);
        dest.writeString(dns);
        dest.writeString(connectionType);
        dest.writeByte((byte) (hasInternet ? 1 : 0));
        dest.writeByte((byte) (validated ? 1 : 0));
        dest.writeByte((byte) (metered ? 1 : 0));
        dest.writeInt(downstreamBandwidth);
        dest.writeInt(upstreamBandwidth);
        dest.writeString(ssid);
        dest.writeString(bssid);
        dest.writeInt(rssi);
        dest.writeInt(linkSpeed);
        dest.writeInt(frequency);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<NetworkInfo> CREATOR = new Creator<NetworkInfo>() {
        @Override
        public NetworkInfo createFromParcel(Parcel in) {
            return new NetworkInfo(in);
        }

        @Override
        public NetworkInfo[] newArray(int size) {
            return new NetworkInfo[size];
        }
    };

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