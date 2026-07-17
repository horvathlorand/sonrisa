# `docs/06-ai-workflow.md`

# AI-Assisted Development Workflow

## Principle

AI is used as an engineering collaborator, not as an unquestioned code generator.

The human engineer remains responsible for:

* scope
* architectural decisions
* correctness
* security
* validation
* accepting or rejecting generated output

---

## Workflow

```text
Problem Framing
      ↓
AI Analysis
      ↓
Human Review
      ↓
Architecture
      ↓
AI Challenge
      ↓
Implementation
      ↓
Automated Validation
      ↓
Adversarial Review
      ↓
Human Correction
      ↓
Decision Documentation
```

---

## AI Responsibilities

AI may assist with:

* ambiguity analysis
* architecture proposals
* implementation
* test generation
* code review
* identifying risks

AI output is not automatically accepted.

---

## Human Validation

Generated output must be checked for:

* correctness
* hidden assumptions
* concurrency
* idempotency
* transaction boundaries
* external failure handling
* security
* maintainability
* test quality

---

## Course Correction Example

### Initial Approach

Application-level duplicate detection:

```text
check whether delivery exists
    ↓
if not found
    ↓
create delivery
```

### Review Finding

Concurrent processing can cause two executions to observe the absence of a delivery simultaneously.

### Correction

Add database-level uniqueness enforcement.

### Result

Correctness no longer depends solely on application-level timing.

---

## Evidence

The repository contains:

* prompt history
* plans
* architectural decisions
* rejected alternatives
* validation notes
* implementation commits
* adversarial review findings

The purpose is to make the engineering process auditable.
