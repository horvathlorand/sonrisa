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

## Getting Started & Running

The project requires **Docker** to be installed and running on your machine.

### 1. Running the Automated Tests
The integration test suite uses **Testcontainers**. It automatically spins up and tears down isolated PostgreSQL instances. **No manual database setup is required to run the tests.**

```bash
./gradlew clean test
```

### 2. Running locally, if you have docker installed
docker run --name sonrisa-postgres -p 5432:5432 -e POSTGRES_DB=alerts -e POSTGRES_PASSWORD=postgres -e POSTGRES_USER=postgres -d postgres:16

### If postgres database running then we can bootRun
```bash
./gradlew bootRun
```

### 3. Interactive API Exploration (Swagger UI)
Once the application is running, you don't need external tools like Postman to test the endpoints. The project includes built-in **Swagger UI** support.

Simply open your browser and navigate to:
👉 **http://localhost:8080/swagger-ui.html**

From this interactive dashboard, you can:
*   **Explore the Schema**: Review the exact model definitions for `CreateAlertRequest`, `EventType`, `EventCategory`, and `Severity`.
*   **Send Live Requests**: Use the **"Try it out"** button on the `POST /api/admin/alerts` endpoint to send test payloads directly to your running local database.
*   **Audit Deliveries**: Trigger the `GET /api/admin/deliveries` endpoint to instantly see the real-time status of sent, pending, or failed notifications.

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
