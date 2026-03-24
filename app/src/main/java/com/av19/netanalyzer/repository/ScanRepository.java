package com.av19.netanalyzer.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.data.ScanState;

import java.util.List;

public class ScanRepository {
    private static volatile ScanRepository instance;
    private final MutableLiveData<ScanState> state = new MutableLiveData<>();

    private ScanRepository() {
        state.setValue(ScanState.idle());
    }

    public static ScanRepository getInstance() {
        if (instance == null) {
            synchronized (ScanRepository.class) {
                if (instance == null) {
                    instance = new ScanRepository();
                }
            }
        }
        return instance;
    }

    public LiveData<ScanState> getScanState() {
        return state;
    }

    // Called by ScanService to update state
    public void updateState(ScanState newState) {
        state.postValue(newState);
    }

    // Convenience methods for the service
    public void setScanning(int progress, String currentHost, List<DeviceInfo> devices) {
        updateState(ScanState.scanning(progress, currentHost, devices));
    }

    public void setCompleted(List<DeviceInfo> devices) {
        updateState(ScanState.completed(devices));
    }

    public void setError(String error) {
        updateState(ScanState.error(error));
    }

    public void reset() {
        updateState(ScanState.idle());
    }
}