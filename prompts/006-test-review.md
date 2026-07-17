# `prompts/006-test-review.md`

Act as a senior test engineer and Java architect.

Review the complete test suite.

Determine whether the tests provide real confidence in behaviour.

Check:

* domain behaviour
* alert matching
* disabled alerts
* unsupported event types
* channel resolution
* persistence
* database constraints
* duplicate processing
* concurrent processing
* independent channel failures
* retry behaviour
* API validation
* transaction boundaries
* external network calls outside transaction boundaries

Identify tests that:

* test implementation details
* overuse mocks
* provide false confidence
* fail to test important invariants
* are unnecessarily brittle
* do not test concurrency assumptions
* fail to verify database-level idempotency

Recommend the smallest set of additional tests that materially improves confidence.

At minimum, consider whether the suite should prove:

1. The same event cannot create duplicate deliveries for the same alert and channel.
2. Concurrent processing cannot bypass the uniqueness invariant.
3. A failed Slack delivery does not incorrectly roll back a successful Email delivery.
4. External provider failures are persisted as delivery failures.
5. Retry operates on the existing delivery rather than creating an unrelated duplicate.
6. Slow external providers do not execute inside an active database transaction.

Do not optimize for coverage percentage.

Optimize for behavioural confidence and correctness under failure.
