package com.av19.netanalyzer.data;

import java.util.List;

public class ScanRecord {
    private long timestamp;
    private long durationSec;
    private int deviceCount;
    private List<DeviceInfo> devices;

    public ScanRecord() {} // para Gson

    public ScanRecord(long timestamp, long durationSec, int deviceCount, List<DeviceInfo> devices) {
        this.timestamp = timestamp;
        this.durationSec = durationSec;
        this.deviceCount = deviceCount;
        this.devices = devices;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDurationSec() {
        return durationSec;
    }

    public int getDeviceCount() {
        return deviceCount;
    }

    public List<DeviceInfo> getDevices() {
        return devices;
    }
}
