# `prompts/001-problem-analysis.md`

You are a senior software architect reviewing an intentionally vague product brief.

Do not write code.

Analyze:

1. Ambiguities.
2. Missing requirements.
3. Assumptions that must be made.
4. Possible domain concepts.
5. MVP scope.
6. Non-goals.
7. Risks.
8. Questions that would normally be asked to the product manager.
9. Success criteria.
10. Potential concurrency and idempotency concerns.

Do not over-engineer the solution.

Prefer a modular monolith unless there is a clear, evidence-based reason for distributed architecture.

The output should be an engineering analysis, not a generic summary.

Product brief:

"We want users to be able to set up alerts so they get notified when something important happens in the world — like breaking news, market movements, natural disasters, that kind of thing. Should work for both email and Slack. Make it flexible enough that we can add more channels later. We need an admin view too."
