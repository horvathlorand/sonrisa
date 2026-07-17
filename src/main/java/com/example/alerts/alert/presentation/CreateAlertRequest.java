package com.example.alerts.alert.presentation;

import com.example.alerts.alert.domain.ChannelType;
import com.example.alerts.event.domain.EventCategory;
import com.example.alerts.event.domain.EventType;
import com.example.alerts.event.domain.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Payload representing a new administrative alert rule registration.")
public record CreateAlertRequest(
    
    @NotNull
    @Schema(description = "The type of event to filter on.", example = "BREAKING_NEWS", requiredMode = Schema.RequiredMode.REQUIRED)
    EventType eventType,
    
    @NotNull
    @Schema(description = "General category of the event.", example = "DISASTER", requiredMode = Schema.RequiredMode.REQUIRED)
    EventCategory category,
    
    @NotNull
    @Schema(description = "Minimum severity required to trigger the notification.", example = "HIGH", requiredMode = Schema.RequiredMode.REQUIRED)
    Severity severityThreshold,
    
    @NotEmpty
    @Schema(description = "Destination channels and endpoints for notifications triggered by this alert.", requiredMode = Schema.RequiredMode.REQUIRED)
    List<@Valid ChannelRequest> channels
) {

    @Schema(description = "Target destination detail for an alert channel.")
    public record ChannelRequest(
        
        @NotNull
        @Schema(description = "Delivery provider medium.", example = "SLACK", requiredMode = Schema.RequiredMode.REQUIRED)
        ChannelType channelType,
        
        @NotBlank
        @Schema(description = "Destination recipient (e.g., Slack webhook URL or an email address).", example = "https://hooks.slack.com/services/T00/B00/X00", requiredMode = Schema.RequiredMode.REQUIRED)
        String target
    ) {
    }
}