package com.example.alerts.notification.domain;

import com.example.alerts.alert.domain.ChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "notification_deliveries",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_notification_deliveries_identity",
        columnNames = {"alert_id", "event_id", "channel_type", "target"}
    )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationDelivery {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID alertId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 50, updatable = false)
    private ChannelType channelType;

    @Column(nullable = false, length = 320, updatable = false)
    private String target;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 50)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    public void claim() {
        if (status == DeliveryStatus.SENT) {
            throw new IllegalStateException("Sent delivery cannot be claimed again.");
        }
        claimedAt = Instant.now();
    }

    public void markSent() {
        status = DeliveryStatus.SENT;
        completedAt = Instant.now();
        failureReason = null;
    }

    public void markFailed(String reason) {
        status = DeliveryStatus.FAILED;
        completedAt = Instant.now();
        failureReason = reason;
    }

    public void resetForRetry() {
        if (status != DeliveryStatus.FAILED) {
            throw new IllegalStateException("Only failed deliveries can be retried.");
        }
        status = DeliveryStatus.PENDING;
        claimedAt = null;
        completedAt = null;
    }
}
