package com.example.alerts.notification.application;

import com.example.alerts.alert.domain.Alert;
import com.example.alerts.alert.domain.AlertChannel;
import com.example.alerts.common.application.TransactionCutter;
import com.example.alerts.event.domain.WorldEvent;
import com.example.alerts.notification.domain.DeliveryStatus;
import com.example.alerts.notification.domain.NotificationDelivery;
import com.example.alerts.notification.infrastructure.NotificationDeliveryClaimRepository;
import com.example.alerts.notification.infrastructure.NotificationDeliveryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryClaimService {

    private final TransactionCutter transactionCutter;
    private final NotificationDeliveryClaimRepository claimRepository;
    private final NotificationDeliveryRepository deliveryRepository;

    public Optional<NotificationDelivery> createClaim(Alert alert, WorldEvent event, AlertChannel channel) {
        return transactionCutter.inNewTransaction(() ->
            claimRepository.createClaim(
                    alert.getId(),
                    event.getId(),
                    channel.getChannelType(),
                    channel.getTarget()
                )
                .flatMap(deliveryRepository::findById)
        );
    }

    public Optional<NotificationDelivery> claimFailedForRetry(NotificationDelivery delivery) {
        return transactionCutter.inNewTransaction(() -> {
            if (delivery.getStatus() != DeliveryStatus.FAILED) {
                return Optional.empty();
            }
            NotificationDelivery managed = deliveryRepository.findById(delivery.getId())
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + delivery.getId()));
            managed.resetForRetry();
            managed.claim();
            return Optional.of(managed);
        });
    }
}
