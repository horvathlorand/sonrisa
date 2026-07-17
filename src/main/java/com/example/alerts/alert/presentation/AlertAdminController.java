package com.example.alerts.alert.presentation;

import com.example.alerts.alert.domain.Alert;
import com.example.alerts.alert.domain.AlertChannel;
import com.example.alerts.alert.domain.AlertStatus;
import com.example.alerts.alert.infrastructure.AlertRepository;
import com.example.alerts.notification.domain.NotificationDelivery;
import com.example.alerts.notification.infrastructure.NotificationDeliveryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Operations", description = "Administrative endpoints for managing alerts and auditing notification deliveries.")
public class AlertAdminController {

    private static final UUID ADMIN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AlertRepository alertRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;

    @PostMapping("/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create an active Alert Rule",
        description = "Configures a new rule matching specific world event types, categories, and severity thresholds, mapping them to targets."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201", 
            description = "Alert rule successfully registered and activated.",
            content = @Content(schema = @Schema(implementation = Alert.class))
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid payload (validation failed, or missing required fields).", 
            content = @Content
        )
    })
    public Alert createAlert(@Valid @RequestBody CreateAlertRequest request) {
        Alert alert = Alert.builder()
            .userId(ADMIN_USER_ID)
            .name(defaultAlertName(request))
            .eventType(request.eventType())
            .category(request.category())
            .minimumSeverity(request.severityThreshold())
            .status(AlertStatus.ACTIVE)
            .channels(toAlertChannels(request))
            .build();

        return alertRepository.save(alert);
    }

    @GetMapping("/alerts")
    @Operation(
        summary = "Get all configured Alerts",
        description = "Returns a comprehensive list of all system alerts, including their status, criteria, and destination channels."
    )
    @ApiResponse(
        responseCode = "200", 
        description = "List of alerts retrieved successfully."
    )
    public List<Alert> getAlerts() {
        return alertRepository.findAll();
    }

    @GetMapping("/deliveries")
    @Operation(
        summary = "Audit Notification Deliveries",
        description = "Provides read-only administrative access to audit delivery logs, showing states (PENDING, SENT, FAILED), targets, and provider response metadata."
    )
    @ApiResponse(
        responseCode = "200", 
        description = "Delivery logs retrieved successfully."
    )
    public List<NotificationDelivery> getDeliveries() {
        return notificationDeliveryRepository.findAll();
    }

    private LinkedHashSet<AlertChannel> toAlertChannels(CreateAlertRequest request) {
        return request.channels().stream()
            .map(channel -> AlertChannel.builder()
                .channelType(channel.channelType())
                .target(channel.target())
                .build())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String defaultAlertName(CreateAlertRequest request) {
        return "%s %s %s".formatted(
            request.eventType(),
            request.category(),
            request.severityThreshold()
        );
    }
}