package com.av19.netanalyzer.utils;

import com.av19.netanalyzer.data.DeviceInfo;

public interface ProgressCallback {
    void onProgress(int percent, String currentIp);

    void onDeviceFound(DeviceInfo device);  // opcional, si quieres reportar en tiempo real
}