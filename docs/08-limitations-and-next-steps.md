# `docs/08-limitations-and-next-steps.md`

# Limitations and Next Steps

## Current Limitations

The MVP does not provide:

* real external event provider integrations
* authentication
* authorization
* frontend administration UI
* production Slack OAuth
* production email provider integration
* durable asynchronous event processing
* transactional outbox
* distributed event processing
* advanced retry scheduling
* exponential backoff
* dead-letter queues
* rate limiting
* notification aggregation
* deduplication across semantically identical external events

---

## In-Memory Event Processing Limitation

If the MVP uses asynchronous in-process event handling, such as:

```text
ApplicationEventPublisher
        ↓
@Async
        ↓
In-memory executor
```

the event is not durably persisted before processing.

A JVM crash, process termination, deployment, or infrastructure failure can cause an accepted event to be lost before processing completes.

Therefore, the MVP provides at-most-once processing semantics for this processing path.

This is an explicit limitation of the MVP and not a guaranteed production-grade delivery model.

---

## Transactional Outbox as the Production Evolution

A production-grade ingestion flow should use:

```text
Incoming Event
      ↓
Database Transaction
      ├── Persist Event
      └── Persist Outbox Record
              ↓
           Commit
              ↓
        Background Worker
              ↓
        Process Event
```

The event and its corresponding outbox record are persisted atomically.

If the application crashes after the transaction commits, the worker can continue processing the outbox record.

The worker must still be idempotent.

---

## External Network Calls

External network calls must execute outside active database transactions.

This includes:

* email sending
* Slack webhook calls
* Slack API calls

The reason is to avoid holding database connections while waiting for potentially slow or unavailable external systems.

The preferred flow is:

```text
Claim Delivery
      ↓
Commit Database Transaction
      ↓
Call External Provider
      ↓
Persist Result
```

This introduces an unavoidable consistency window.

For example:

```text
Provider accepts message
      ↓
Network timeout
      ↓
Application cannot determine result
```

A retry may create a duplicate notification unless the provider supports idempotency keys or equivalent deduplication.

Production systems should consider:

* provider idempotency keys
* durable delivery workers
* retry policies
* exponential backoff
* dead-letter queues
* delivery reconciliation
* provider-specific status queries

---

## Production Evolution

### External Event Sources

Introduce provider adapters:

```text
NewsProvider
MarketDataProvider
DisasterDataProvider
```

Normalize external data into:

```text
WorldEvent
```

The domain should not depend directly on provider-specific payloads.

---

### Reliable Asynchronous Delivery

A production architecture could evolve toward:

```text
Event
  ↓
Persist Event + Outbox
  ↓
Message Broker
  ↓
Worker
  ↓
Notification Provider
```

---

### Retry Policy

Introduce:

* maximum attempts
* exponential backoff
* retry scheduling
* dead-letter handling
* provider-specific retry classification

---

### Security

Introduce:

* authentication
* role-based authorization
* tenant isolation
* secret management
* provider credential encryption

---

### Observability

Introduce:

* structured logging
* metrics
* distributed tracing
* delivery latency metrics
* failure-rate monitoring
* queue depth monitoring

---

## Principle

Future complexity should be introduced in response to real requirements, scale, or operational needs.

The MVP intentionally avoids speculative infrastructure while documenting the evolution path for durability, scale, and stronger delivery guarantees.
