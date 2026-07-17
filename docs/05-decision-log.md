# `docs/05-decision-log.md`

# Decision Log

## D001 — Use a Modular Monolith

### Decision

Use a modular monolith for the MVP.

### Rationale

No independently scaling or independently deployable component has been identified.

### Rejected Alternative

Microservices.

### Reason Rejected

Would introduce operational and distributed-system complexity without a demonstrated requirement.

---

## D002 — Use Strategy-Based Event Matching

### Decision

Represent event matching through `EventMatcher` implementations.

### Rationale

Different event types have different matching semantics.

### Rejected Alternative

A single large conditional block.

### Reason Rejected

Would create a growing modification point and violate the Open/Closed Principle.

---

## D003 — Use Extensible Notification Channels

### Decision

Use a `NotificationChannel` abstraction with a registry.

### Rationale

Email and Slack are current channels, but future channels are explicitly required.

### Consequence

The core notification orchestration does not need to know concrete channel implementations.

---

## D004 — Enforce Idempotency at the Database Boundary

### Decision

Use a unique database constraint for the logical delivery identity.

### Rationale

Application-level existence checks are vulnerable to concurrent processing.

### Rejected Alternative

Check-then-insert only.

### Reason Rejected

Two concurrent executions can both observe that a delivery does not exist.

---

## D005 — Track Deliveries Independently

### Decision

Each alert-event-channel combination has an independently tracked delivery.

### Rationale

One channel may succeed while another fails.

### Consequence

Partial failure is visible and retryable.

---

## D006 — Treat External Provider Calls as Non-Transactional

### Decision

Do not assume external notification calls participate in database transactions.

### Rationale

A PostgreSQL rollback cannot undo a notification already sent to Slack or an email provider.

### Consequence

The delivery state machine must explicitly handle uncertain outcomes.

---

## D007 — Use PostgreSQL JSONB Selectively

### Decision

Use JSONB only for genuinely event-specific criteria or attributes.

### Rationale

The MVP supports different event types with potentially different attributes.

### Constraint

Stable domain concepts must remain explicitly modelled.

---

## D008 — Interpret Admin View as an API

### Decision

Implement an administrative REST API instead of a frontend.

### Rationale

The brief provides no UI requirements or technology choice.

### Consequence

The administrative capability is testable and demonstrable within the MVP scope.
