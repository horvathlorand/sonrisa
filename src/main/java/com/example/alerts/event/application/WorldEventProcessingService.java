package com.example.alerts.event.application;

import com.example.alerts.alert.application.EventMatcherRegistry;
import com.example.alerts.alert.domain.Alert;
import com.example.alerts.alert.domain.AlertStatus;
import com.example.alerts.alert.infrastructure.AlertRepository;
import com.example.alerts.event.domain.WorldEvent;
import com.example.alerts.notification.application.NotificationChannelRegistry;
import com.example.alerts.notification.application.NotificationDeliveryClaimService;
import com.example.alerts.notification.application.NotificationDeliveryResultService;
import com.example.alerts.notification.domain.Notification;
import com.example.alerts.notification.domain.NotificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorldEventProcessingService {

    private final WorldEventIngestionService ingestionService;
    private final AlertRepository alertRepository;
    private final EventMatcherRegistry matcherRegistry;
    private final NotificationDeliveryClaimService claimService;
    private final NotificationChannelRegistry channelRegistry;
    private final NotificationDeliveryResultService resultService;

    public void process(WorldEvent incomingEvent) {
        WorldEvent event = ingestionService.persistIfNew(incomingEvent);
        var matcher = matcherRegistry.matcherFor(event.getEventType());
        var candidateAlerts = alertRepository.findByStatusAndEventTypeAndCategory(
            AlertStatus.ACTIVE,
            event.getEventType(),
            event.getCategory()
        );

        candidateAlerts.stream()
            .filter(alert -> matcher.matches(alert, event))
            .forEach(alert -> dispatchAlertChannels(alert, event));
    }

    private void dispatchAlertChannels(Alert alert, WorldEvent event) {
        for (var alertChannel : alert.getChannels()) {
            claimService.createClaim(alert, event, alertChannel)
                .ifPresent(delivery -> {
                    var notification = Notification.from(delivery, event);
                    NotificationResult result = send(notification);
                    resultService.recordResult(delivery.getId(), result);
                });
        }
    }

    private NotificationResult send(Notification notification) {
        try {
            return channelRegistry.channelFor(notification.channelType()).send(notification);
        } catch (RuntimeException exception) {
            return NotificationResult.failed(exception.getMessage());
        }
    }
}
