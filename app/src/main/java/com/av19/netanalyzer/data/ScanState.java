package com.av19.netanalyzer.data;

import java.util.ArrayList;
import java.util.List;

public class ScanState {
    public enum Status {
        IDLE,
        SCANNING,
        COMPLETED,
        ERROR
    }

    private final Status status;
    private final int progress;          // 0-100
    private final String currentHost;    // optional, for UI feedback
    private final List<DeviceInfo> devices;
    private final NetworkInfo networkInfo;
    private final String errorMessage;

    public ScanState(Status status, int progress, String currentHost, List<DeviceInfo> devices, NetworkInfo networkInfo, String errorMessage) {
        this.status = status;
        this.progress = progress;
        this.currentHost = currentHost;
        this.devices = new ArrayList<>(devices);
        this.networkInfo = networkInfo;
        this.errorMessage = errorMessage;
    }

    public Status getStatus() {
        return status;
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

    public static ScanState idle() {
        return new ScanState(Status.IDLE, 0, null, new ArrayList<>(), null, null);
    }

    public static ScanState scanning(int progress, String currentHost,
                                     List<DeviceInfo> devices, NetworkInfo networkInfo) {
        return new ScanState(Status.SCANNING, progress, currentHost, devices, networkInfo, null);
    }

    public static ScanState completed(List<DeviceInfo> devices, NetworkInfo networkInfo) {
        return new ScanState(Status.COMPLETED, 100, null, devices, networkInfo, null);
    }

    public static ScanState error(String error) {
        return new ScanState(Status.ERROR, 0, null, new ArrayList<>(), null, error);
    }
}