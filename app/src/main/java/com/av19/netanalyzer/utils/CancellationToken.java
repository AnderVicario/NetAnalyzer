package com.av19.netanalyzer.utils;

public class CancellationToken {
    private volatile boolean cancelled = false;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
