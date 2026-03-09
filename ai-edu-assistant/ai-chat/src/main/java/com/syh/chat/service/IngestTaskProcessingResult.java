package com.syh.chat.service;

public enum IngestTaskProcessingResult {
    SUCCEEDED(true),
    RETRY(false),
    DEAD(true),
    NOT_DUE(false),
    BUSY(false),
    SKIPPED(true);

    private final boolean shouldAck;

    IngestTaskProcessingResult(boolean shouldAck) {
        this.shouldAck = shouldAck;
    }

    public boolean shouldAck() {
        return shouldAck;
    }
}
