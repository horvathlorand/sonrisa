package com.example.alerts.notification.application;

import com.example.alerts.common.application.TransactionCutter;
import com.example.alerts.notification.domain.NotificationResult;
import com.example.alerts.notification.infrastructure.NotificationDeliveryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryResultService {

    private final TransactionCutter transactionCutter;
    private final NotificationDeliveryRepository deliveryRepository;

    public void recordResult(UUID deliveryId, NotificationResult result) {
        transactionCutter.inNewTransaction(() -> {
            var delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));

            if (result.successful()) {
                delivery.markSent();
            } else {
                delivery.markFailed(result.failureReason());
            }
        });
    }
}
