package com.av19.netanalyzer.discovery;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.NetworkInfo;

import java.util.List;

public interface DiscoveryMethod {
    String getName();

    List<DeviceInfo> discover(NetworkInfo network, CancellationToken token, ProgressCallback callback);
}