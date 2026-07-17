package com.example.alerts.notification.infrastructure;

import com.example.alerts.alert.domain.ChannelType;
import com.example.alerts.notification.domain.Notification;
import com.example.alerts.notification.domain.NotificationChannel;
import com.example.alerts.notification.domain.NotificationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class EmailNotificationChannel implements NotificationChannel {

    @Override
    public ChannelType channelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public NotificationResult send(Notification notification) {
        return NotificationResult.sent();
    }
}
