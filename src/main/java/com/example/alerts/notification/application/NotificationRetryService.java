package com.example.alerts.notification.application;

import com.example.alerts.event.infrastructure.WorldEventRepository;
import com.example.alerts.notification.domain.Notification;
import com.example.alerts.notification.domain.NotificationResult;
import com.example.alerts.notification.infrastructure.NotificationDeliveryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final WorldEventRepository worldEventRepository;
    private final NotificationDeliveryClaimService claimService;
    private final NotificationChannelRegistry channelRegistry;
    private final NotificationDeliveryResultService resultService;

    public boolean retry(UUID deliveryId) {
        var existingDelivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));

        return claimService.claimFailedForRetry(existingDelivery)
            .map(delivery -> {
                var event = worldEventRepository.findById(delivery.getEventId())
                    .orElseThrow(() -> new IllegalStateException("Event not found for delivery " + delivery.getId()));
                NotificationResult result = send(Notification.from(delivery, event));
                resultService.recordResult(delivery.getId(), result);
                return true;
            })
            .orElse(false);
    }

    private NotificationResult send(Notification notification) {
        try {
            return channelRegistry.channelFor(notification.channelType()).send(notification);
        } catch (RuntimeException exception) {
            return NotificationResult.failed(exception.getMessage());
        }
    }
}
