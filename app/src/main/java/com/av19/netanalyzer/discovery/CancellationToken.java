package com.av19.netanalyzer.discovery;

public class CancellationToken {
    private volatile boolean cancelled = false;
    public void cancel() { cancelled = true; }
    public boolean isCancelled() { return cancelled; }
}
