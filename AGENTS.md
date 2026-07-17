# AGENTS.md

## Role

You are acting as a senior Java engineer and software architect.

Your responsibility is not only to produce working code, but to help transform an intentionally vague product brief into a maintainable, testable, extensible implementation.

You must reason about:
- domain boundaries
- architectural trade-offs
- SOLID principles
- maintainability
- testability
- failure handling
- idempotency
- concurrency
- security
- operational concerns

Do not blindly implement requirements.
If a requirement is ambiguous, identify the ambiguity and follow the documented assumptions in `docs/03-assumptions.md`.

---

## Project Context

This repository implements an MVP alert platform.

The original product brief is intentionally vague:

> "We want users to be able to set up alerts so they get notified when something important happens in the world — like breaking news, market movements, natural disasters, that kind of thing. Should work for both email and Slack. Make it flexible enough that we can add more channels later. We need an admin view too."

The implementation must demonstrate how ambiguity was converted into explicit assumptions, scope, architecture, implementation, and validation.

The engineering process is part of the deliverable.

---

## Technology

Use:
- Java 25
- Spring Boot 3.x
- Gradle (with Gradle Wrapper)
- Lombok (for reducing boilerplate: @Getter, @Builder, @RequiredArgsConstructor, @NoArgsConstructor, @AllArgsConstructor)
- PostgreSQL
- Spring Data JPA / Hibernate
- Flyway
- JUnit 5
- Mockito where appropriate
- Testcontainers for integration tests where appropriate
- Docker Compose for local infrastructure

Prefer current stable versions compatible with Java 25.

Do not introduce additional infrastructure without a documented reason.

---

## Java Entity & Lombok Guidelines (CRITICAL)

To maintain a clean and concise domain layer, all JPA Entities and Value Objects MUST strictly adhere to these Lombok patterns. Manual boilerplate code is strictly forbidden.

## CRITICAL Code Guidelines

### 1. Lombok & Boilerplate Rules
- **Getter Generation**: Always use `@Getter` at the class level on all entities and value objects. Never write manual getter methods.
- **Setters & Data**: **NEVER** use `@Setter` or `@Data` on JPA entities to guarantee immutability and prevent Hibernate lifecycle bugs.
- **No-Args Constructors**: Use `@NoArgsConstructor(access = AccessLevel.PROTECTED)` on all JPA entities and `@Embeddable` classes.
- **All-Args Constructors**: Use `@AllArgsConstructor(access = AccessLevel.PRIVATE)` (or `PROTECTED`) combined with `@Builder`.
- **Custom Constructors**: **DO NOT** generate manual constructors if they duplicate Lombok's `@AllArgsConstructor` or `@Builder`.
- **Default Fields**: Use `@Builder.Default` on inline initialized fields (like `createdAt = Instant.now()`).
- **Spring DI**: Always use `@RequiredArgsConstructor` on Spring `@Service` and `@Component` classes. Absolutely no `@Autowired` annotations on fields or manual constructors. Make all injected fields `private final`.

### 2. JPA & Database Mapping
- Embeddables (Value Objects) like `AlertChannel` must be mapped using `@ElementCollection` inside their parent entities.
- Ensure JPA entity field types and nullability align 100% with the DDL specified in `docs/04-architecture.md`.

---

## Architectural Direction

The default architecture is a modular monolith.

Do not introduce microservices, Kafka, Redis, Kubernetes, or other distributed infrastructure unless there is a clearly documented requirement that justifies it.

Prefer:
- clear module boundaries
- domain-oriented package structure
- dependency inversion
- explicit application services
- small cohesive components
- ports and adapters where useful
- interfaces at meaningful architectural boundaries

Avoid:
- giant service classes
- anemic procedural orchestration
- unnecessary abstractions
- premature generic frameworks
- speculative infrastructure

---

## Core Domain Concepts

The current domain contains:
- User
- Alert (with multiple AlertChannels containing target addresses)
- WorldEvent
- Notification
- NotificationDelivery
- NotificationChannel
- EventMatcher

The exact implementation should follow the documented architecture and assumptions.

---

## Extensibility Requirements

The system must support adding new notification channels without modifying the core notification orchestration logic.

Use a suitable polymorphic design such as:

```java
public interface NotificationChannel {

    ChannelType channelType();

    NotificationResult send(Notification notification);
}
```