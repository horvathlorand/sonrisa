# `prompts/003-domain-design.md`

Act as a senior Java domain architect.

Based on the repository documentation, design the MVP domain model.

Do not implement the full application yet.

Define:

* entities
* value objects
* enums
* aggregate boundaries
* invariants
* state transitions
* repository boundaries
* application service responsibilities

Pay special attention to:

* Alert
* AlertCriteria
* WorldEvent
* NotificationDelivery
* NotificationChannel
* EventMatcher

For every proposed abstraction explain:

* why it exists
* what responsibility it owns
* what it must not own
* how it contributes to testability

The design must:

* follow SOLID principles
* be concurrency-safe
* support idempotent event processing
* support independent channel failures
* allow future event types
* allow future notification channels

Avoid:

* anemic procedural design
* giant services
* speculative abstractions
* unnecessary inheritance
* generic frameworks created only for extensibility

Do not use design patterns unless they solve a concrete problem.
