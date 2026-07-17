package com.example.alerts.alert.application;

import com.example.alerts.alert.domain.EventMatcher;
import com.example.alerts.event.domain.EventType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventMatcherRegistry {

    private final List<EventMatcher> matchers;

    public EventMatcher matcherFor(EventType eventType) {
        Map<EventType, EventMatcher> byType = new EnumMap<>(EventType.class);
        for (EventMatcher matcher : matchers) {
            EventMatcher previous = byType.put(matcher.supportedEventType(), matcher);
            if (previous != null) {
                throw new IllegalStateException("Duplicate matcher for event type " + matcher.supportedEventType());
            }
        }
        EventMatcher matcher = byType.get(eventType);
        if (matcher == null) {
            throw new IllegalArgumentException("No matcher registered for event type " + eventType);
        }
        return matcher;
    }
}
