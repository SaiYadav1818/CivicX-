package com.janseva.outbox;

public enum OutboxStatus {
    PENDING,
    SENT,
    PROCESSED,
    FAILED
}
