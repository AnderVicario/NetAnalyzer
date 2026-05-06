package com.av19.netanalyzer.data;

import java.util.ArrayList;
import java.util.List;

public class ScanState {
    private final Status status;
    private final Phase phase;
    private final String currentMethod;
    private final int progress;
    private final String currentHost;
    private final List<DeviceInfo> devices;
    private final NetworkInfo networkInfo;
    private final String errorMessage;

    public ScanState(Status status, Phase phase, String currentMethod, int progress, String currentHost, List<DeviceInfo> devices, NetworkInfo networkInfo, String errorMessage) {
        this.status = status;
        this.phase = phase;
        this.currentMethod = currentMethod;
        this.progress = progress;
        this.currentHost = currentHost;
        this.devices = new ArrayList<>(devices);
        this.networkInfo = networkInfo;
        this.errorMessage = errorMessage;
    }

    public static ScanState idle() {
        return new ScanState(Status.IDLE, Phase.NONE, null, 0, null, new ArrayList<>(), null, null);
    }

    public static ScanState scanning(int progress, Phase phase, String currentMethod, String currentHost,
                                     List<DeviceInfo> devices, NetworkInfo networkInfo) {
        return new ScanState(Status.SCANNING, phase, currentMethod, progress, currentHost, devices, networkInfo, null);
    }

    public static ScanState completed(List<DeviceInfo> devices, NetworkInfo networkInfo) {
        return new ScanState(Status.COMPLETED, Phase.NONE, null, 100, null, devices, networkInfo, null);
    }

    public static ScanState error(String error, Phase phase, String currentMethod) {
        return new ScanState(Status.ERROR, phase, currentMethod, 100, null, new ArrayList<>(), null, error);
    }

    public Status getStatus() {
        return status;
    }

    public Phase getPhase() {
        return phase;
    }

    public String getCurrentMethod() {
        return currentMethod;
    }

    public int getProgress() {
        return progress;
    }

    public String getCurrentHost() {
        return currentHost;
    }

    public List<DeviceInfo> getDevices() {
        return devices;
    }

    public NetworkInfo getNetworkInfo() {
        return networkInfo;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public enum Status {
        IDLE,
        SCANNING,
        COMPLETED,
        ERROR
    }

    public enum Phase {
        NONE,
        DISCOVERY,
        PORT_SCAN
    }
}