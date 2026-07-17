# `docs/04-architecture.md`

# Architecture

## Architectural Style

The system is implemented as a modular monolith.

```text
REST API
   ↓
Application Layer
   ↓
Domain Layer
   ↓
Ports
   ↓
Infrastructure Adapters
```

The system is modular by domain capability rather than only by technical layer.

---

## High-Level Flow

```text
World Event
    ↓
Event Processing
    ↓
Find Candidate Alerts
    ↓
Event Matcher
    ↓
Matching Alerts
    ↓
Create / Claim Notification Deliveries
    ↓
Notification Channel Registry
    ↓
Email / Slack
    ↓
Persist Delivery Result
```

---

## Suggested Package Structure

```text
com.example.alerts
├── alert
│   ├── domain
│   ├── application
│   └── infrastructure
│
├── event
│   ├── domain
│   ├── application
│   └── infrastructure
│
├── notification
│   ├── domain
│   ├── application
│   └── infrastructure
│
└── admin
    ├── application
    └── infrastructure
```

---

## Core Abstractions

### Notification Channel

```java
public interface NotificationChannel {

    ChannelType channelType();

    NotificationResult send(Notification notification);
}
```

Implementations:

```text
EmailNotificationChannel
SlackNotificationChannel
```

Future implementations:

```text
SmsNotificationChannel
PushNotificationChannel
WebhookNotificationChannel
```

The notification orchestration depends on the interface.

---

### Event Matcher

```java
public interface EventMatcher {

    EventType supportedEventType();

    boolean matches(Alert alert, WorldEvent event);
}
```

Implementations:

```text
BreakingNewsMatcher
MarketMovementMatcher
NaturalDisasterMatcher
```

The system avoids a central conditional chain containing every event type.

---

## Dependency Direction

```text
Controller
    ↓
Application Service
    ↓
Domain Abstraction
    ↑
Infrastructure Adapter
```

The core business logic must not depend on:

* HTTP clients
* Slack SDKs
* email provider SDKs
* JPA implementation details

---

## Explicit Domain Criteria vs JSONB

The system distinguishes between stable business concepts and event-specific extension data.

### Explicitly Modelled

Criteria that participate in global matching semantics should be explicit.

Examples:

* event type
* category
* severity
* priority

These should be represented through:

* typed Java fields
* explicit domain concepts
* relational database columns
* appropriate indexes where query patterns justify them

### JSONB

JSONB is reserved for optional event-specific metadata that varies naturally between event types.

Examples:

```text
Market movement:
{
    "symbols": ["BTC", "ETH"],
    "exchange": "NASDAQ"
}
```

```text
Natural disaster:
{
    "latitude": 46.25,
    "longitude": 20.15,
    "affectedArea": "..."
}
```

The system must not place globally meaningful filtering logic into opaque JSONB merely to avoid designing the domain.

---

## Idempotency

The logical delivery identity is:

```text
alertId + eventId + channelType + target
```

The database must enforce uniqueness.

Conceptually:

```sql
UNIQUE (
    alert_id,
    event_id,
    channel_type,
    target
)
```

Application-level checks may reduce unnecessary work but cannot be the sole correctness mechanism.

This protects against concurrent processing where two workers simultaneously observe that a delivery does not yet exist.

---

## Concurrency

Event processing must assume concurrent execution.

Avoid:

```java
if (!exists()) {
    create();
}
```

as the sole protection against duplicates.

Prefer a database-backed idempotent workflow:

1. Attempt to create or claim the delivery.
2. Let the database enforce uniqueness.
3. Treat an existing delivery as an already-processed or already-owned operation.
4. Only the execution that successfully owns the delivery should perform the external side effect.
5. Persist the result of the external operation.

The implementation must avoid shared mutable global state.

---

## External Network Calls and Transaction Boundaries

External network calls must not execute inside active Spring `@Transactional` boundaries.

This includes:

* email provider calls
* Slack webhook calls
* Slack API calls
* any other external HTTP request

The implementation must not hold a database transaction open while waiting for an external provider.

This is important because a slow or unavailable external provider could otherwise:

```text
hold DB connection
        ↓
block transaction
        ↓
consume connection pool capacity
        ↓
cause pool starvation under load
```

The preferred high-level flow is:

```text
Database Transaction
    ↓
Create / claim delivery
    ↓
Commit
    ↓
External Network Call
    ↓
Short Database Transaction
    ↓
Persist SENT / FAILED
```

This creates an important consistency trade-off:

```text
Database state
        +
External side effect
```

cannot be made atomically consistent with a normal database transaction.

The system must therefore explicitly handle possible uncertain outcomes.

For example:

```text
Delivery claimed
    ↓
Slack request sent
    ↓
Network timeout
```

The system may not know whether Slack accepted the message.

This must not be incorrectly treated as guaranteed failure without considering duplicate delivery implications during retry.

For production-grade guarantees, an asynchronous delivery architecture with an outbox, durable workers, provider idempotency keys, or provider-specific deduplication mechanisms may be required.

---

## Event Delivery Semantics

If the MVP uses in-process asynchronous event processing:

```text
Event accepted
    ↓
ApplicationEventPublisher
    ↓
@Async
    ↓
In-memory executor
```

the event is not durably queued.

If the JVM crashes before processing completes, the event may be lost.

Therefore:

> In-memory asynchronous processing provides at-most-once processing semantics.

This is an explicit MVP limitation.

---

## Transactional Outbox Evolution

The production evolution path is:

```text
HTTP Request
    ↓
Database Transaction
    ├── Persist WorldEvent
    └── Persist OutboxMessage
             ↓
          Commit
             ↓
       Background Worker
             ↓
       Process Event
```

The outbox record is persisted atomically with event ingestion.

A worker can safely retry processing after application restarts.

The outbox itself must also be processed idempotently.

---

## Delivery Failure Model

Each channel is processed independently.

Example:

```text
Event
 └── Alert
      ├── Email Delivery → SENT
      └── Slack Delivery → FAILED
```

One failed channel must not invalidate successful deliveries through other channels.

Delivery status is persisted independently.

---

## Retry Semantics

Retrying a failed delivery must be idempotent.

A retry must:

* operate on the existing delivery
* not create an unrelated duplicate delivery
* update the delivery state based on the result
* preserve failure information when retry fails
* account for uncertain external outcomes where applicable

Future production improvements may include:

* exponential backoff
* maximum retry attempts
* dead-letter handling
* asynchronous workers
* outbox pattern
* provider-level idempotency keys

These are not required for the MVP unless implementation time allows.

---

## Database Design

Core tables:

```text
alerts
alert_channels
world_events
notification_deliveries
```

Stable matching concepts should be explicit columns where they participate in common query logic.

Event-specific extension attributes may use JSONB.

The database must enforce:

* foreign keys
* required fields
* valid uniqueness invariants

---

## SOLID Application

### Single Responsibility

A component should have one reason to change.

Examples:

* matching logic should not send notifications
* notification dispatch should not parse HTTP requests
* persistence adapters should not own business rules

### Open/Closed

New channels and event matchers should be added through new implementations where possible.

### Liskov Substitution

All `NotificationChannel` implementations must obey the same contract.

### Interface Segregation

Interfaces should expose only the behaviour required by their clients.

### Dependency Inversion

Application logic depends on abstractions rather than infrastructure implementations.

---

## Design Patterns

Patterns are applied only where they solve a real problem.

Used or considered:

* Strategy Pattern for event matching.
* Registry Pattern for resolving notification channels and matchers.
* Ports and Adapters for external integrations.
* Repository abstraction for persistence.
* Adapter Pattern for provider-specific APIs.
* Transactional Outbox for future durable asynchronous processing.

Avoid:

* generic factory hierarchies without variation
* unnecessary abstract base classes
* speculative plugin frameworks
* pattern-driven complexity

---

## Architecture Trade-Offs

### Modular Monolith vs Microservices

Chosen: Modular Monolith.

Reason:

* smaller operational footprint
* simpler local development
* easier transactions
* no current independent scaling requirement

### Explicit Criteria vs JSONB

Chosen: Explicit relational modelling for stable global filtering concepts.

JSONB is limited to genuinely event-specific extension data.

Reason:

* stronger type safety
* simpler queries
* better indexing options
* clearer domain model

Trade-off:

* adding a new globally meaningful filter may require schema evolution

### Synchronous vs Asynchronous Delivery

Chosen for MVP: Synchronous application flow with persisted delivery state, provided external network calls execute outside database transactions.

Reason:

* easier to demonstrate
* fewer moving parts
* sufficient for a small MVP

Known limitation:

* application crash windows can create uncertain or lost processing outcomes depending on the exact point of failure

Production evolution:

```text
Event
 ↓
Persist Event + Outbox
 ↓
Worker
 ↓
Notification Provider
```

---

## Future Evolution

If scale or operational requirements increase, the system could evolve toward:

```text
Event Ingestion
       ↓
Event Store
       ↓
Transactional Outbox
       ↓
Message Broker
       ↓
Event Processing Workers
       ↓
Delivery Workers
       ↓
Notification Providers
```

This is deliberately not implemented before real requirements justify it.
