# `prompts/002-architecture-review.md`

Act as a senior Java software architect and adversarial architecture reviewer.

Read:

* `AGENTS.md`
* `docs/01-problem-framing.md`
* `docs/02-plan.md`
* `docs/03-assumptions.md`
* `docs/04-architecture.md`

Do not implement code.

Challenge the proposed architecture.

Investigate:

* whether the modular monolith is justified
* whether domain boundaries are meaningful
* whether notification channel abstractions are correctly placed
* whether event matching is extensible without over-engineering
* whether JSONB is appropriate for the MVP
* whether idempotency is correctly addressed
* whether concurrent event processing is safe
* whether partial notification failures are correctly modelled
* whether transaction boundaries are sound
* whether retry behaviour is safe
* whether the admin requirement is sufficiently defined
* whether any abstractions are unnecessary

For each concern provide:

1. Problem.
2. Severity: CRITICAL, IMPORTANT, or OPTIONAL.
3. Why it matters.
4. Recommended correction.
5. Trade-off introduced by the correction.

Do not praise the design unless required to explain a trade-off.

Do not silently resolve contradictions.
