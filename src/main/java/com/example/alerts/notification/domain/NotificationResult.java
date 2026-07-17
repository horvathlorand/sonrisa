package com.example.alerts.notification.domain;

public record NotificationResult(boolean successful, String failureReason) {

    public static NotificationResult sent() {
        return new NotificationResult(true, null);
    }

    public static NotificationResult failed(String failureReason) {
        return new NotificationResult(false, failureReason);
    }
}
