package com.example.alerts.event.infrastructure;

import com.example.alerts.event.domain.WorldEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldEventRepository extends JpaRepository<WorldEvent, UUID> {

    Optional<WorldEvent> findBySourceEventId(String sourceEventId);
}
