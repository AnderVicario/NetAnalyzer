package com.av19.netanalyzer.discovery;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;
import com.av19.netanalyzer.utils.CancellationToken;
import com.av19.netanalyzer.utils.ProgressCallback;

import java.util.List;

public class FakeDiscovery implements DiscoveryMethod {
    private final List<DeviceInfo> fakeDevices;

    public FakeDiscovery(List<DeviceInfo> devices) {
        this.fakeDevices = devices;
    }

    @Override
    public String getName() {
        return "FAKE";
    }

    @Override
    public List<DeviceInfo> discover(NetworkInfo network, CancellationToken token, ProgressCallback callback) {
        if (callback != null) {
            callback.onProgress(100, "Fake discovery done");
            for (DeviceInfo d : fakeDevices) {
                callback.onDeviceFound(d);
            }
        }
        return fakeDevices;
    }
}