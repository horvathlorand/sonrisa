package com.example.alerts.alert.domain;

import com.example.alerts.event.domain.EventCategory;
import com.example.alerts.event.domain.EventType;
import com.example.alerts.event.domain.Severity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "alerts")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EventCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "minimum_severity", nullable = false, length = 50)
    private Severity minimumSeverity;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 50)
    private AlertStatus status = AlertStatus.ACTIVE;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "alert_channels", joinColumns = @JoinColumn(name = "alert_id"))
    @Builder.Default
    private Set<AlertChannel> channels = new LinkedHashSet<>();

    public boolean isActive() {
        return status == AlertStatus.ACTIVE;
    }

    public void activate() {
        status = AlertStatus.ACTIVE;
    }

    public void disable() {
        status = AlertStatus.DISABLED;
    }

    public void addChannel(AlertChannel channel) {
        channels.remove(channel);
        channels.add(channel);
    }

    public boolean matchesStableCriteria(EventType candidateEventType, EventCategory candidateCategory, Severity candidateSeverity) {
        return isActive()
            && eventType == candidateEventType
            && category == candidateCategory
            && candidateSeverity.meetsOrExceeds(minimumSeverity);
    }

    @PrePersist
    void validateInvariants() {
        if (channels == null || channels.isEmpty()) {
            throw new IllegalStateException("Alert must have at least one notification channel.");
        }
    }
}
