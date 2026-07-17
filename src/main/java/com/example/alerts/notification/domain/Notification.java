package com.example.alerts.notification.domain;

import com.example.alerts.alert.domain.ChannelType;
import com.example.alerts.event.domain.WorldEvent;
import java.util.UUID;
import lombok.Builder;

@Builder
public record Notification(
    UUID deliveryId,
    UUID alertId,
    UUID eventId,
    ChannelType channelType,
    String target,
    String title,
    String description
) {

    public static Notification from(NotificationDelivery delivery, WorldEvent event) {
        return Notification.builder()
            .deliveryId(delivery.getId())
            .alertId(delivery.getAlertId())
            .eventId(delivery.getEventId())
            .channelType(delivery.getChannelType())
            .target(delivery.getTarget())
            .title(event.getTitle())
            .description(event.getDescription())
            .build();
    }
}
