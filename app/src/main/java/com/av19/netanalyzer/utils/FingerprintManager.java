package com.av19.netanalyzer.utils;

import androidx.annotation.NonNull;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;

import java.util.List;

public class FingerprintManager {

    private static FingerprintManager instance = null;

    private FingerprintManager() {
    }

    public static FingerprintManager getInstance() {
        if (instance == null) {
            instance = new FingerprintManager();
        }
        return instance;
    }

    @NonNull
    public DeviceInfo getDeviceInfo(NetworkInfo network, String ip, String mac, String vendor, List<Integer> openPorts, Integer ttl) {
        DeviceInfo device = new DeviceInfo(ip, mac, vendor, openPorts);
        device.setTtl(ttl);
        if (device.getOs() == null) {
            device.setOs(new DeviceInfo.PriorityValue(-1, guessOsFromTtl(ttl)));
        }
        if (ip.equals(network.getIp())) {
            device.setIsCurrent(true);
        } else if (ip.equals(network.getGateway())) {
            device.setIsGateway(true);
        } else if (ip.equals(network.getDns().get(0))) {
            device.setIsDNS(true);
        }
        return device;
    }

    public String guessOsFromTtl(Integer ttl) {
        if (ttl == null || ttl <= 0) return "Desconocido";
        if (ttl <= 64) {
            return "Linux / Android / macOS / iOS";
        } else if (ttl <= 128) {
            return "Windows";
        } else if (ttl <= 255) {
            return "Solaris / AIX / Cisco";
        }
        return "Desconocido";
    }
}
