package com.example.alerts.notification.application;

import com.example.alerts.alert.domain.ChannelType;
import com.example.alerts.notification.domain.NotificationChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationChannelRegistry {

    private final List<NotificationChannel> channels;

    public NotificationChannel channelFor(ChannelType channelType) {
        Map<ChannelType, NotificationChannel> byType = new EnumMap<>(ChannelType.class);
        for (NotificationChannel channel : channels) {
            NotificationChannel previous = byType.put(channel.channelType(), channel);
            if (previous != null) {
                throw new IllegalStateException("Duplicate notification channel for type " + channel.channelType());
            }
        }
        NotificationChannel channel = byType.get(channelType);
        if (channel == null) {
            throw new IllegalArgumentException("No notification channel registered for type " + channelType);
        }
        return channel;
    }
}
