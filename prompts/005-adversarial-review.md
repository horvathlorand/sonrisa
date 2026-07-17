# `prompts/005-adversarial-review.md`

Act as a hostile senior engineer reviewing code written by another AI agent.

Do not assume the implementation is correct.

Inspect the actual source code, migrations, and tests.

Find concrete problems in:

* correctness
* duplicate event processing
* idempotency
* race conditions
* transaction boundaries
* external side effects
* database connection pool usage
* long-running transactions
* partial failures
* retry behaviour
* uncertain external outcomes
* exception handling
* SOLID violations
* coupling
* abstraction quality
* database constraints
* API design
* security
* test quality
* false confidence from mocks

Pay particular attention to this rule:

> External network calls must not execute while an active database transaction is holding a database connection.

Look for:

* `@Transactional` methods that call Slack or email providers directly.
* network calls made before a transaction commits.
* transaction propagation that unintentionally keeps a transaction open.
* external calls inside loops while a transaction remains active.
* code that can cause database connection pool starvation under slow provider responses.

Also inspect idempotency.

Verify whether concurrent processing can cause:

```text
same alert
+
same event
+
same channel
+
same target
=
duplicate delivery
```

For every finding provide:

1. File and location.
2. Concrete problem.
3. Why it matters.
4. Severity.
5. Recommended fix.
6. Whether the fix is required for the MVP.

Also identify:

* over-engineered areas
* under-engineered areas
* code that should be rejected rather than patched
* assumptions that are not documented

Do not modify files yet.

Be specific and critical.
