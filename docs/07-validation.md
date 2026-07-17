# `docs/07-validation.md`

# Validation Strategy

## Build Validation

```bash
./gradlew clean test
```

The build must pass before a milestone is considered complete.

---

## Unit Tests

Test business behaviour such as:

* matching event criteria
* disabled alerts being ignored
* supported channel resolution
* unsupported channel rejection
* delivery state transitions

Avoid testing implementation details unnecessarily.

---

## Integration Tests

Validate:

* Flyway migrations
* persistence
* foreign keys
* unique constraints
* important API flows

Prefer Testcontainers for database integration tests.

---

## Idempotency Tests

At minimum:

```text
process event
    ↓
delivery created

process same event again
    ↓
no duplicate delivery
```

Also test concurrent processing where practical.

---

## Failure Tests

Validate:

```text
Email succeeds
Slack fails
```

Expected:

```text
Email  → SENT
Slack  → FAILED
```

The entire operation must not incorrectly appear successful or fail as an all-or-nothing transaction.

---

## Retry Tests

Validate:

```text
FAILED
  ↓
RETRY
  ↓
SENT
```

Also validate retry failure.

---

## API Validation

Check:

* validation errors
* HTTP status codes
* invalid identifiers
* missing required fields
* unsupported enum values
* unauthorized access when authentication exists

---

## Manual Review

After AI implementation:

```bash
git diff
git status
git log --oneline
```

Review:

* unnecessary files
* accidental complexity
* generated boilerplate
* swallowed exceptions
* incorrect transaction annotations
* entity exposure through controllers
* missing constraints
* misleading tests

---

## Adversarial Questions

* What happens if the same event is processed twice?
* What happens if two workers process the same event concurrently?
* What happens if the provider succeeds but the database update fails?
* What happens if one channel fails?
* Can retry create a duplicate?
* Can an inactive alert receive a notification?
* Can a user access another user's alert?
* Is the database enforcing important invariants?
* Are tests proving behaviour or merely mocking implementation details?
* Does adding a channel require modifying core orchestration logic?
