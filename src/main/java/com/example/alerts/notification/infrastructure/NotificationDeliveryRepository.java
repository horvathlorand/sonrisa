package com.example.alerts.notification.infrastructure;

import com.example.alerts.alert.domain.ChannelType;
import com.example.alerts.notification.domain.NotificationDelivery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    Optional<NotificationDelivery> findByAlertIdAndEventIdAndChannelTypeAndTarget(
        UUID alertId,
        UUID eventId,
        ChannelType channelType,
        String target
    );
}
