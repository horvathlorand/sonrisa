package com.example.alerts.alert.domain;

import com.example.alerts.event.domain.EventType;
import com.example.alerts.event.domain.WorldEvent;

public interface EventMatcher {

    EventType supportedEventType();

    boolean matches(Alert alert, WorldEvent event);
}
