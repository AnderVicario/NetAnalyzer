package com.av19.netanalyzer.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.av19.netanalyzer.data.ScanState;
import com.av19.netanalyzer.repository.ScanRepository;

public class ScanViewModel extends AndroidViewModel {
    private final ScanRepository repository;
    private long scanStartTime = 0;

    public ScanViewModel(Application application) {
        super(application);
        repository = ScanRepository.getInstance();
    }

    public LiveData<ScanState> getScanState() {
        return repository.getScanState();
    }

    public void resetScan() {
        repository.reset();
    }

    public long getScanStartTime() {
        return scanStartTime;
    }

    public void setScanStartTime(long time) {
        scanStartTime = time;
    }
}