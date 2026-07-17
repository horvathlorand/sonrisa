# `docs/02-plan.md`

# Implementation Plan

## Objective

Transform the intentionally vague product brief into a small but coherent, testable, extensible alert platform.

The implementation prioritizes correctness, explicit assumptions, idempotency, failure handling, and maintainability over feature volume.

---

## Phase 1 — Problem Framing

### Deliverables

* Problem definition.
* Explicit assumptions.
* Scope and non-goals.
* Open questions.
* Success criteria.

### Reason

Implementation should not begin before ambiguity has been converted into explicit engineering assumptions.

---

## Phase 2 — Architecture

### Deliverables

* Modular monolith architecture.
* Domain boundaries.
* Application flow.
* Persistence model.
* Extension points.
* Failure and idempotency strategy.

### Decision

Use a modular monolith.

### Reason

The MVP does not contain independently scaling or independently deployable components. A modular monolith provides clear boundaries without introducing distributed-system complexity prematurely.

---

## Phase 3 — Domain Model

Define:

* `Alert`
* `AlertCriteria`
* `WorldEvent`
* `NotificationDelivery`
* `NotificationChannel`
* `EventMatcher`

Establish:

* ownership boundaries
* invariants
* lifecycle states
* persistence relationships

Avoid creating abstractions without a concrete responsibility.

---

## Phase 4 — First Vertical Slice

Implement:

```text
World Event
    ↓
Event Matching
    ↓
Matching Alert
    ↓
Notification Delivery
    ↓
Notification Channel
```

The first vertical slice must work end-to-end before adding secondary features.

---

## Phase 5 — Notification Extensibility

Implement:

* Email channel.
* Slack channel.
* Channel registry.

The notification orchestration must depend on the `NotificationChannel` abstraction rather than concrete implementations.

Adding a future channel must not require modifying the core notification workflow.

---

## Phase 6 — Persistence and Correctness

Implement:

* PostgreSQL.
* Flyway migrations.
* Foreign keys.
* Appropriate indexes.
* Database-level uniqueness constraints.

Idempotency must not depend solely on application-level existence checks.

The database must enforce critical uniqueness invariants.

---

## Phase 7 — Admin API

Implement administrative read and operational endpoints for:

* alerts
* events
* notification deliveries
* failed deliveries
* retry operations

The MVP does not include a frontend unless time remains after the core system is complete.

---

## Phase 8 — Validation

Run:

```text
./gradlew clean build
```

Validate:

* compilation
* unit tests
* integration tests
* database migrations
* API behaviour
* duplicate processing
* failure handling
* retry behaviour

Inspect the actual diff and generated code.

Do not consider the implementation correct merely because it compiles.

---

## Phase 9 — Adversarial Review

Review the implementation for:

* race conditions
* duplicate delivery
* broken idempotency
* incorrect transaction boundaries
* partial failure handling
* poor exception handling
* SOLID violations
* excessive coupling
* leaky abstractions
* over-engineering
* under-engineering
* misleading tests

Apply corrections only after validating that the finding is real.

---

## Phase 10 — Documentation

Document:

* architectural decisions
* rejected alternatives
* AI-generated defects
* human corrections
* validation evidence
* known limitations
* future improvements

The development process is part of the deliverable.
