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
        enrichDevice(device, network);
        return device;
    }

    public void enrichDevice(@NonNull DeviceInfo device, @NonNull NetworkInfo network) {
        if (device.getIp() != null) {
            if (device.getIp().equals(network.getIp())) {
                device.setIsCurrent(true);
            } else if (device.getIp().equals(network.getGateway())) {
                device.setIsGateway(true);
            } else if (!network.getDns().isEmpty() && device.getIp().equals(network.getDns().get(0))) {
                device.setIsDNS(true);
            }
        }

        if (device.getOs() == null || device.getOs().getPriority() < 0) {
            Integer ttl = device.getTtl();
            if (ttl != null && ttl > 0) {
                String guessed = guessOsFromTtl(ttl);
                device.setOs(new DeviceInfo.PriorityValue(-1, guessed));
            }
        }
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
