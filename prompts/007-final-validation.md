# `prompts/007-final-validation.md`

Perform a final senior engineering review of the repository.

Read:

* `AGENTS.md`
* all `docs/`
* all `prompts/`
* source code
* tests
* migrations
* README

Run the appropriate build and tests.

Evaluate:

1. Does the implementation match the documented scope?
2. Are assumptions explicit?
3. Are architectural decisions consistent?
4. Is the code modular and maintainable?
5. Are SOLID principles applied pragmatically?
6. Is event processing safe under repetition and concurrency?
7. Is idempotency enforced at the correct boundary?
8. Are notification channels extensible?
9. Are partial failures correctly represented?
10. Are retry operations safe?
11. Are database constraints sufficient?
12. Do tests validate behaviour?
13. Is there unnecessary complexity?
14. What would you reject before production?
15. What would be the next engineering priorities?

Separate findings into:

* BLOCKER
* IMPORTANT
* NICE TO HAVE

Do not make changes automatically.

Produce a concise final engineering assessment with evidence from the repository.
