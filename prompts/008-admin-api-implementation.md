`prompts/008-admin-api-swagger-ui-implementation.md`

## Context & Objectives
During the final senior engineering review of Phase 007, a critical gap was identified between the documented MVP scope (which promised administrative alert and delivery management capabilities) and the initial backend-only database engine.

To transition the system into a production-ready MVP and eliminate all remaining high-severity BLOCKER findings, this phase delivers:
1. **Administrative REST API**: A clean, validated controller exposing alert creation, rule listing, and delivery audit logs.
2. **Interactive Swagger UI (OpenAPI v3)**: A fully functional web-based "Admin View" allowing visual exploration and manual invocation of the system without custom frontend builds.
3. **Local Docker Compose Alignment**: Resolution of Gradle development bootstrap issues.

---

## Technical Design Decisions

### 1. Unified REST Endpoint: `AlertAdminController`
Instead of separating admin features across multiple service domains, a single administrative controller under the path `/api/admin` was created to minimize infrastructure footprint while keeping deployment simple.
* **POST `/api/admin/alerts`**: Registers a new proactive alert rule mapping specific event metadata (`EventType`, `EventCategory`, `Severity`) to multi-channel communication endpoints.
* **GET `/api/admin/alerts`**: Allows operators to review active notification triggers.
* **GET `/api/admin/deliveries`**: Exposes the complete audit trail of notification deliveries (including `PENDING`, `SENT`, `FAILED` states, failure messages, and retry counters) to satisfy auditing compliance.

### 2. Explicit OpenAPI v3 Metadata & Examples
To maximize usability for testing and evaluation teams, the API leverages Springdoc OpenAPI annotations:
* Grouped under a dedicated `Admin Operations` section to distinguish infrastructure management from downstream ingestion endpoints.
* Equipped with comprehensive, pre-populated schema JSON payloads (e.g., automatically suggesting functional Slack webhook structures and valid system events).

---

## Implementation Details

### Springdoc Integration
Added to the build path to auto-generate schema definitions directly from JPA Entities and Presentation Records:
* **UI Endpoint**: `http://localhost:8080/swagger-ui.html`

### Configuration (`application.yml`)
Configured to baseline Flyway schema migrations, validate Hibernate models, disable intrusive local Docker Compose auto-rebuild routines during standard `./gradlew bootRun` executions, and route the Swagger UI correctly:

```yaml
spring:
  docker:
    compose:
      enabled: false
  datasource:
    url: jdbc:postgresql://localhost:5432/alerts
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    baseline-on-migrate: true

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```