package com.example.alerts.notification.domain;

import com.example.alerts.alert.domain.ChannelType;

public interface NotificationChannel {

    ChannelType channelType();

    NotificationResult send(Notification notification);
}
