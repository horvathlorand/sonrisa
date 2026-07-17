# `docs/01-problem-framing.md`

# Problem Framing

## Original Brief

> We want users to be able to set up alerts so they get notified when something important happens in the world — like breaking news, market movements, natural disasters, that kind of thing. Should work for both email and Slack. Make it flexible enough that we can add more channels later. We need an admin view too.

## Problem Interpretation

The system allows users to define alert subscriptions for selected world events. When an event is received, the system evaluates enabled alerts, creates notification deliveries for matching alerts, and dispatches notifications through configured channels.

The system must support Email and Slack initially while allowing additional channels to be introduced without modifying the core alert evaluation workflow.

## Core User Capabilities

* Create an alert.
* Configure event type and matching criteria.
* Configure one or more notification channels.
* Enable or disable an alert.
* Update an alert.
* Delete an alert.
* View owned alerts.

## Core System Capabilities

* Accept world events from an event source.
* Evaluate events against enabled alerts.
* Create notification deliveries for matching alerts.
* Dispatch notifications through configured channels.
* Track delivery status and failures.
* Prevent duplicate deliveries for the same alert, event, and channel.
* Support retrying failed deliveries.

## Admin Capabilities

The MVP interprets "admin view" as an administrative API rather than a frontend application.

Administrators should be able to:

* View alerts.
* View received events.
* View notification deliveries.
* Inspect failed deliveries.
* Retry failed deliveries.

## Primary Domain Concepts

* `Alert`
* `AlertCriteria`
* `WorldEvent`
* `NotificationDelivery`
* `NotificationChannel`
* `EventMatcher`

## Key Architectural Principle

The core business flow must not depend on concrete notification providers.

The notification system must be extensible through abstractions such as:

```java
public interface NotificationChannel {

    ChannelType channelType();

    NotificationResult send(Notification notification);
}
```

Adding a new channel should primarily require adding a new implementation rather than modifying the central orchestration logic.

## Success Criteria

The MVP is successful when:

1. A user can create an alert.
2. A world event can be received.
3. Matching alerts are identified.
4. Notification deliveries are created.
5. Email and Slack channels can be selected.
6. Delivery status is tracked.
7. Duplicate event processing does not create duplicate deliveries.
8. Failed deliveries can be identified and retried.
9. The core design allows future channels and event types.
10. The implementation is covered by meaningful automated tests.
11. Architectural decisions and AI-assisted development decisions are documented.

## Non-Goals

The MVP does not attempt to implement:

* Real-world event provider integrations.
* A complete news aggregation platform.
* A market data platform.
* A disaster detection platform.
* Authentication and authorization infrastructure.
* A frontend admin dashboard.
* Kafka or other distributed messaging infrastructure.
* Microservice deployment.
* Multi-region infrastructure.
* A general-purpose rule engine.
* Complex scheduling and notification throttling.
* Production-grade provider OAuth flows.

These can be introduced later when real requirements justify them.
