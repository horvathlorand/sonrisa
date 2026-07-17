# `prompts/004-implementation.md`

You are implementing a production-minded MVP as a senior Java 25 engineer and software architect.

Before modifying files:

1. Read `AGENTS.md`.
2. Read relevant documentation under `docs/`.
3. Inspect the current repository.
4. State the implementation plan.
5. Identify files to create or modify.
6. Identify risks and assumptions.

Implement only the smallest coherent vertical slice:

```text
World Event
    ↓
Event Matching
    ↓
Matching Alert
    ↓
Create / Claim Notification Delivery
    ↓
Commit
    ↓
External Notification Channel
    ↓
Persist Delivery Result
```

## Requirements

* Java 25.
* Spring Boot.
* Gradle (using Gradle Wrapper).
* PostgreSQL.
* Flyway.
* Constructor injection.
* Clear domain boundaries.
* Meaningful validation.
* Extensible event matching.
* Extensible notification channels.
* Persistent delivery state.
* Idempotent processing.
* Meaningful tests.

## Domain Modelling

Stable global filtering concepts must be explicitly modelled using typed domain concepts and relational database columns.

Do not hide core matching logic inside opaque JSONB structures.

Use JSONB only for genuinely event-specific optional metadata whose structure naturally varies between event types.

## Concurrency and Idempotency

The event processing flow must be safe under concurrent execution.

Do not rely solely on:

```text
check
  ↓
if absent
  ↓
insert
```

for correctness.

Use database-level uniqueness constraints to protect the logical delivery identity.

The implementation must ensure that concurrent processing cannot unintentionally create duplicate deliveries for the same:

```text
alert + event + channel + target
```

Application-level checks may optimize the common path but must not be the sole correctness mechanism.

## Transaction Boundaries

External network calls must execute OUTSIDE active Spring `@Transactional` boundaries.

This includes:

* email provider calls
* Slack webhook calls
* Slack API calls
* any other external HTTP request

Do not hold a database transaction or database connection open while waiting for external network I/O.

The preferred flow is:

```text
Short Database Transaction
    ↓
Create or claim delivery
    ↓
Commit
    ↓
External Network Call
    ↓
Short Database Transaction
    ↓
Persist SENT / FAILED
```

This is required to avoid:

* long-running database transactions
* unnecessary connection occupation
* lock contention
* database connection pool starvation under load

Do not assume that an external side effect can be rolled back when a database transaction rolls back.

Explicitly consider uncertain outcomes such as:

```text
External provider accepts request
        ↓
Network timeout
        ↓
Application cannot determine result
```

The retry strategy must account for the possibility of duplicate external notifications.

## Event Processing

If in-memory asynchronous processing is used, explicitly document that it is not durable.

An application crash before processing completes may lose the event.

Do not claim at-least-once delivery unless durable persistence and retry semantics actually support it.

The Transactional Outbox Pattern is the documented production evolution path.

## Infrastructure Constraints

Do not introduce:

* Kafka.
* Redis.
* Microservices.
* Kubernetes.
* Speculative infrastructure.

unless the repository documentation explicitly justifies the addition.

## Testing

Tests must provide behavioural confidence.

Include appropriate tests for:

* event matching
* disabled alerts
* channel resolution
* persistence
* database uniqueness constraints
* duplicate processing
* concurrent processing where practical
* independent channel failures
* retry behaviour
* API validation

Do not optimize for coverage percentage.

## After Implementation

1. Run relevant tests.
2. Run the complete build.
3. Inspect the diff.
4. Review the implementation critically.
5. Identify remaining risks.
6. Document important decisions or compromises.

Do not claim success based only on compilation.
