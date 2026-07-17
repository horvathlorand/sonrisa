package com.example.alerts.event.domain;

public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean meetsOrExceeds(Severity minimumSeverity) {
        return ordinal() >= minimumSeverity.ordinal();
    }
}
