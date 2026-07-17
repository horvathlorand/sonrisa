package com.example.alerts.alert.domain;

import com.example.alerts.event.domain.EventType;
import com.example.alerts.event.domain.WorldEvent;
import org.springframework.stereotype.Component;

@Component
public class BreakingNewsMatcher implements EventMatcher {

    @Override
    public EventType supportedEventType() {
        return EventType.BREAKING_NEWS;
    }

    @Override
    public boolean matches(Alert alert, WorldEvent event) {
        return alert.matchesStableCriteria(event.getEventType(), event.getCategory(), event.getSeverity());
    }
}
