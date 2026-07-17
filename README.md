# `README.md`

# Alert Platform

An AI-assisted engineering exercise demonstrating how an intentionally vague product brief can be transformed into an explicit, tested, extensible Java application.

## Product

Users configure alerts for important world events.

When a matching event is received, the system creates and dispatches notification deliveries through configured channels.

Initial channels:

* Email
* Slack

The architecture is designed to support additional channels without modifying core notification orchestration.

## Technology

* Java 25
* Spring Boot
* Gradle
* PostgreSQL
* JPA / Hibernate
* Flyway
* JUnit 5
* Mockito
* Testcontainers
* Docker Compose

## Architecture

The application is a modular monolith.

Core flow:

```text
World Event
    ↓
Event Matching
    ↓
Matching Alerts
    ↓
Notification Deliveries
    ↓
Email / Slack
```

The design explicitly considers:

* SOLID principles
* concurrency
* idempotency
* duplicate event processing
* partial notification failure
* retry behaviour
* database constraints
* testability
* extensibility

## Repository Structure

```text
docs/
    Problem framing
    Plan
    Assumptions
    Architecture
    Decision log
    AI workflow
    Validation
    Limitations

prompts/
    AI prompt history

src/
    Application implementation
```

## Development Process

The project was developed through:

```text
Ambiguous Brief
      ↓
Problem Framing
      ↓
Explicit Assumptions
      ↓
Architecture
      ↓
AI-Assisted Implementation
      ↓
Automated Validation
      ↓
Adversarial Review
      ↓
Human Corrections
      ↓
Documented Decisions
```

AI-generated output was not accepted automatically.

The implementation was reviewed for correctness, maintainability, concurrency safety, idempotency, failure handling, and test quality.

## Running

```bash
docker compose up -d
./gradlew clean test
```

## MVP Scope

The MVP includes:

* alert management
* event processing
* event matching
* Email notification channel
* Slack notification channel
* persistent delivery tracking
* duplicate delivery protection
* failed delivery retry
* administrative API

The MVP does not include:

* real external event providers
* authentication
* frontend UI
* microservices
* Kafka
* distributed deployment

See:

* `docs/01-problem-framing.md`
* `docs/03-assumptions.md`
* `docs/08-limitations-and-next-steps.md`

for the complete scope and rationale.
