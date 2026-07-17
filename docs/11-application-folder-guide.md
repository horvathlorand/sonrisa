# Application Folder Guide

This document explains how the main application folders work together in the MVP alert platform. It focuses on the implemented packages under:

```text
src/main/java/com/example/alerts
```

The code is organized as a modular monolith. Each business capability owns its domain model, application services, and persistence or integration adapters.

```text
alert/
event/
notification/
common/
```

## End-to-End Flow

The main runtime flow is:

```text
WorldEvent received
  -> WorldEventProcessingService.process(...)
  -> WorldEventIngestionService.persistIfNew(...)
  -> EventMatcherRegistry.matcherFor(...)
  -> AlertRepository.findByStatusAndEventTypeAndCategory(...)
  -> EventMatcher.matches(...)
  -> NotificationDeliveryClaimService.createClaim(...)
  -> NotificationChannelRegistry.channelFor(...)
  -> NotificationChannel.send(...)
  -> NotificationDeliveryResultService.recordResult(...)
```

The important design idea is that matching, delivery claiming, external sending, and result persistence are separate responsibilities. This keeps the system easier to test and protects the database from duplicate deliveries.

## Alert Folder

The `alert` package owns alert rules: what a user wants to monitor, which event criteria should trigger it, and which channels should receive notifications.

```text
alert/
  application/
  domain/
  infrastructure/
  presentation/
```

### `alert/domain`

`Alert` is the aggregate that stores the rule:

```text
userId
name
eventType
category
minimumSeverity
status
channels
```

Important behavior:

* `isActive()` checks whether the alert can match events.
* `activate()` and `disable()` change the rule status.
* `addChannel(...)` adds a channel while replacing an equal existing channel.
* `matchesStableCriteria(...)` checks the shared matching criteria: active status, event type, category, and severity threshold.
* `validateInvariants()` prevents persisting an alert without at least one notification channel.

`AlertChannel` is an embeddable value object stored through `@ElementCollection`. It contains:

```text
channelType
target
```

For example, the channel type may be `EMAIL` or `SLACK`, and the target may be an email address or Slack webhook target.

`EventMatcher` is the strategy interface for event-specific matching:

```java
EventType supportedEventType();

boolean matches(Alert alert, WorldEvent event);
```

The current implementations are:

* `BreakingNewsMatcher`
* `MarketMovementMatcher`
* `NaturalDisasterMatcher`

At the moment, all three use the same stable criteria from `Alert.matchesStableCriteria(...)`. They are still separate strategy classes so future event-specific rules can be added without turning the processor into a large conditional block.

### `alert/application`

`EventMatcherRegistry` receives all Spring-managed `EventMatcher` implementations as a list.

When `matcherFor(eventType)` is called, it builds an `EnumMap<EventType, EventMatcher>` and enforces two rules:

* there must be only one matcher per event type
* the requested event type must have a matcher

This makes missing or duplicate matcher wiring fail fast instead of silently choosing the wrong behavior.

### `alert/infrastructure`

`AlertRepository` is the Spring Data JPA repository for `Alert`.

The most important query is:

```java
findByStatusAndEventTypeAndCategory(...)
```

`WorldEventProcessingService` uses this query to load only active candidate alerts for the incoming event's type and category before applying the matcher strategy.

### `alert/presentation`

`AlertAdminController` exposes administrative endpoints under:

```text
/api/admin
```

Implemented endpoints:

* `POST /api/admin/alerts` creates an active alert rule.
* `GET /api/admin/alerts` lists configured alerts.
* `GET /api/admin/deliveries` lists notification delivery audit records.

`CreateAlertRequest` validates the create-alert payload with Jakarta Bean Validation:

* `eventType` is required
* `category` is required
* `severityThreshold` is required
* at least one channel is required
* each channel requires a channel type and non-blank target

`GlobalExceptionHandler` converts common request failures into consistent JSON responses:

* unsupported HTTP method -> `405 Method Not Allowed`
* validation failure -> `400 Bad Request`
* unexpected exception -> `500 Internal Server Error`

## Event Folder

The `event` package owns incoming world events and the orchestration that connects events to alerts and notifications.

```text
event/
  application/
  domain/
  infrastructure/
```

### `event/domain`

`WorldEvent` represents an event received by the platform.

Important fields:

```text
sourceEventId
eventType
category
severity
title
description
occurredAt
metadata
createdAt
```

`sourceEventId` is unique. It is the idempotency key for event ingestion, meaning the same external event should only be persisted once.

`metadata` is stored as JSONB and is reserved for event-specific details that do not belong in global matching criteria.

`Severity` defines the ordering:

```text
LOW < MEDIUM < HIGH < CRITICAL
```

`meetsOrExceeds(minimumSeverity)` uses that ordering to decide whether an event should trigger an alert's severity threshold.

### `event/application`

`WorldEventIngestionService.persistIfNew(...)` stores a world event idempotently.

It first looks up the event by `sourceEventId`. If no row exists, it saves and flushes the new event in a new transaction. If a concurrent request inserts the same source event first, the database unique constraint may raise `DataIntegrityViolationException`; in that case the service opens another new transaction and returns the existing event.

This protects the event table from duplicate external events.

`WorldEventProcessingService.process(...)` is the main application orchestrator.

Step by step:

1. Persist the incoming event or load the existing event with the same `sourceEventId`.
2. Resolve the correct `EventMatcher` for the event type.
3. Load active candidate alerts with the same event type and category.
4. Filter candidates through the matcher.
5. For every matching alert channel, create or claim a delivery.
6. If the claim succeeds, build a `Notification`.
7. Send the notification through the channel registry.
8. Persist the delivery result as `SENT` or `FAILED`.

The send call catches `RuntimeException` and turns it into a failed `NotificationResult`. That prevents one channel failure from crashing the whole processing loop.

### `event/infrastructure`

`WorldEventRepository` is the Spring Data JPA repository for `WorldEvent`.

The key custom lookup is:

```java
findBySourceEventId(...)
```

That lookup supports idempotent ingestion and duplicate event handling.

## Notification Folder

The `notification` package owns delivery records, channel abstraction, channel implementations, retry, and delivery result state.

```text
notification/
  application/
  domain/
  infrastructure/
```

### `notification/domain`

`NotificationDelivery` is the persistent audit and state record for a single attempt to notify a single target about a single alert-event match.

Its logical identity is:

```text
alertId + eventId + channelType + target
```

The database enforces this identity with a unique constraint. This is what prevents duplicate notification deliveries when the same event is processed multiple times or when concurrent workers race.

Important behavior:

* `claim()` marks the delivery as claimed unless it was already sent.
* `markSent()` sets status to `SENT`, records completion time, and clears failure reason.
* `markFailed(reason)` sets status to `FAILED`, records completion time, and stores the reason.
* `resetForRetry()` moves only failed deliveries back to `PENDING`.

`Notification` is the message object passed to channel implementations. It combines delivery identity and event content:

```text
deliveryId
alertId
eventId
channelType
target
title
description
```

`NotificationChannel` is the extension point for delivery providers:

```java
ChannelType channelType();

NotificationResult send(Notification notification);
```

`NotificationResult` represents either success or failure:

```text
sent()
failed(reason)
```

### `notification/application`

`NotificationChannelRegistry` receives every Spring-managed `NotificationChannel`.

When `channelFor(channelType)` is called, it enforces:

* only one channel implementation per `ChannelType`
* a channel must exist for the requested type

This allows the event processor to depend on the interface rather than specific email or Slack classes.

`NotificationDeliveryClaimService` handles ownership of delivery work.

`createClaim(alert, event, channel)` inserts a delivery row using database-level conflict handling:

```text
INSERT ...
ON CONFLICT (alert_id, event_id, channel_type, target) DO NOTHING
RETURNING id
```

If the insert returns an id, the caller owns the delivery and may perform the external send. If no id is returned, another process already created that logical delivery, so this call returns `Optional.empty()`.

`claimFailedForRetry(delivery)` moves an existing failed delivery back into a claimed pending state. It only succeeds if the row is currently `FAILED`.

`NotificationDeliveryResultService.recordResult(...)` persists the result in a separate new transaction:

* successful result -> `markSent()`
* failed result -> `markFailed(reason)`

`NotificationRetryService.retry(deliveryId)` retries one failed delivery.

Step by step:

1. Load the existing delivery.
2. Claim it for retry only if it is currently failed.
3. Load the original world event.
4. Rebuild the `Notification`.
5. Send through the channel registry.
6. Record the new result.
7. Return `true` if retry work happened, otherwise `false`.

### `notification/infrastructure`

`EmailNotificationChannel` and `SlackNotificationChannel` are current provider adapters. In the MVP they return `NotificationResult.sent()` and do not call real external providers yet. They are disabled under the `test` profile so tests can provide controlled channel implementations.

`NotificationDeliveryRepository` is the Spring Data JPA repository for `NotificationDelivery`.

`NotificationDeliveryClaimRepository` uses `JdbcTemplate` for SQL operations that need precise database behavior:

* insert-on-conflict claim creation
* conditional failed-delivery retry claiming

JPA is still used for normal entity loading and state transitions.

## Common Folder

`TransactionCutter` centralizes short `REQUIRES_NEW` transaction blocks.

It is used to make sure database claim/result operations are committed separately from external notification sending. The intended flow is:

```text
short DB transaction: create or claim delivery
external send: no open DB transaction
short DB transaction: record result
```

This avoids holding a database connection open while waiting for an email, Slack, or future provider call.

## Adding a New Notification Channel

To add a new channel:

1. Add the new value to `ChannelType`.
2. Create a class that implements `NotificationChannel`.
3. Return the new channel type from `channelType()`.
4. Implement `send(Notification notification)`.
5. Register it as a Spring bean, usually with `@Component`.

The core event processing flow does not need to change because `WorldEventProcessingService` sends through `NotificationChannelRegistry`.

## Adding a New Event Type

To add a new event type:

1. Add the new value to `EventType`.
2. Create a new `EventMatcher` implementation.
3. Return the new type from `supportedEventType()`.
4. Implement event-specific matching in `matches(...)`.
5. Register it as a Spring bean, usually with `@Component`.

The processor does not need a new `if` or `switch` branch because it resolves matchers through `EventMatcherRegistry`.

## Failure and Idempotency Summary

The application protects against duplicate work at two levels:

* `WorldEvent.sourceEventId` prevents duplicate event records.
* `NotificationDelivery(alertId, eventId, channelType, target)` prevents duplicate delivery records.

Each notification channel is independent. If one channel fails, other channels can still be sent and recorded as successful.

Retries operate on existing failed delivery rows. They do not create a second delivery for the same alert, event, channel, and target.
