package com.example.alerts.alert.domain;

import com.example.alerts.event.domain.EventType;
import com.example.alerts.event.domain.WorldEvent;

public class MarketMovementMatcher implements EventMatcher {

    @Override
    public EventType supportedEventType() {
        return EventType.MARKET_MOVEMENT;
    }

    @Override
    public boolean matches(Alert alert, WorldEvent event) {
        return alert.matchesStableCriteria(event.getEventType(), event.getCategory(), event.getSeverity());
    }
}
