package com.example.alerts.alert.infrastructure;

import com.example.alerts.alert.domain.Alert;
import com.example.alerts.alert.domain.AlertStatus;
import com.example.alerts.event.domain.EventCategory;
import com.example.alerts.event.domain.EventType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByStatusAndEventTypeAndCategory(
        AlertStatus status,
        EventType eventType,
        EventCategory category
    );
}
