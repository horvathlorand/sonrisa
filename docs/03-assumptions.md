# `docs/03-assumptions.md`

# Assumptions and Open Questions

## Product Assumptions

### A1 — Events are provided to the system

The MVP receives normalized `WorldEvent` objects.

The system does not implement external news, market, or disaster data providers.

### A2 — Event types are initially finite

Initial event types:

* `BREAKING_NEWS`
* `MARKET_MOVEMENT`
* `NATURAL_DISASTER`

The design must allow future event types.

### A3 — Alert matching is event-type-specific

Different event types may require different matching logic.

Matching should be extensible through dedicated strategies rather than a single growing conditional block.

### A4 — Stable filtering concepts are explicitly modelled

Core filtering concepts that are part of the global matching model must be represented as typed domain concepts and relational database columns.

Examples may include:

* event category
* severity
* priority

These concepts must not be hidden inside an untyped JSONB structure.

This provides:

* stronger domain modelling
* type safety
* simpler JPA/Hibernate mapping
* clearer queries
* more predictable indexing
* easier database-level optimization

### A5 — JSONB is limited to event-specific extension data

JSONB may be used for optional, event-specific attributes that are not part of the global matching model.

Examples:

```text
Market movement:
- ticker symbols
- exchange-specific metadata

Natural disaster:
- latitude
- longitude
- affected area metadata
```

JSONB must not be used to avoid modelling stable business concepts.

The rule is:

> Stable concepts are explicit. Truly variable event-specific metadata may use JSONB.

### A6 — An alert can have multiple notification channels

A single matching event may create multiple independent notification deliveries.

Example:

```text
Alert
 ├── Email
 └── Slack
```

Each delivery is tracked independently.

### A7 — Channel failures are isolated

If Slack delivery fails but Email succeeds:

```text
Email  → SUCCESS
Slack  → FAILED
```

The successful delivery must not be rolled back because another channel failed.

### A8 — Delivery status is persistent

Notification delivery status must survive application restarts.

The MVP tracks at least:

* `PENDING`
* `SENT`
* `FAILED`

### A9 — Duplicate processing must be safe

The same event may be received more than once.

The system must not unintentionally create duplicate deliveries for the same:

```text
alert + event + channel + target
```

Application-level checks may optimize processing, but correctness must be protected by a database constraint.

### A10 — Retry is explicit

Failed deliveries can be retried through an administrative operation.

Retry behaviour must not create duplicate successful deliveries.

### A11 — Admin view means API

Because no UI technology or wireframes were provided, "admin view" is interpreted as an administrative REST API.

### A12 — External provider integrations are adapters

Email and Slack integrations must be represented behind abstractions.

The core domain must not depend directly on HTTP clients or provider SDKs.

---

## Technical Assumptions

### Java

Java 25 is the baseline.

New language features are used only where they improve clarity.

### Framework

Spring Boot is used for:

* dependency injection
* web layer
* persistence integration
* validation
* testing support

### Database

PostgreSQL is the primary database.

Flyway manages schema evolution.

### Criteria Storage

Stable, globally meaningful matching criteria are represented explicitly in the domain and relational schema.

JSONB is reserved for optional, event-specific extension data whose structure varies naturally between event types.

---

## Concurrency Assumptions

The event processing flow may be executed concurrently.

Therefore:

* application-level existence checks are insufficient for correctness
* database constraints are required for idempotency
* operations must be designed for repeated execution
* shared mutable state must be avoided
* external side effects must be considered separately from database transactions

---

## Transaction and External Side-Effect Assumptions

External network calls, including:

* email provider calls
* Slack webhook calls
* Slack API calls

must not execute while holding an active database transaction.

The application must avoid keeping database transactions open while waiting for external network I/O.

This prevents:

* unnecessary database connection occupation
* connection pool starvation
* long-running transactions
* increased lock contention

The system must also not assume that an external side effect can be rolled back when a database transaction rolls back.

For example:

```text
Database transaction
    ↓
External Slack call succeeds
    ↓
Database transaction rolls back
```

The Slack message cannot automatically be undone.

This limitation must be explicitly represented in the delivery lifecycle and documented as part of the MVP architecture.

---

## Event Delivery Semantics

The MVP may use in-process event processing for simplicity.

If asynchronous in-memory processing is used, such as:

```text
ApplicationEventPublisher
        ↓
@Async
        ↓
In-memory executor
```

the system does not provide durable event delivery.

A JVM crash, process termination, deployment, or infrastructure failure can cause an accepted event to be lost before processing completes.

Therefore, the MVP may provide **at-most-once processing semantics for in-memory asynchronous event handling**.

This is an explicit limitation, not an accidental guarantee.

The production evolution path is a durable event-processing mechanism such as the Transactional Outbox Pattern.

---

## Open Product Questions

The following remain undefined by the original brief:

* Who provides world events?
* What constitutes a breaking news event?
* How are market movements detected?
* What disaster sources are supported?
* Are alerts user-specific or globally visible?
* How are users authenticated?
* What is the authorization model?
* Are Slack channels configured through OAuth?
* Which email provider is used?
* What retry policy is required?
* Should notifications be throttled?
* Should multiple matching events be aggregated?
* What are the rate limits?
* What is the expected event volume?
* What is the required delivery latency?
* What does the admin UI look like?
* Are audit logs required?

These questions are intentionally not invented as requirements.

The MVP documents assumptions and keeps extension points where future requirements are likely to evolve.
