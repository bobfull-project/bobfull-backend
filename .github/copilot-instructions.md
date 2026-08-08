# BobFull Copilot Review Instructions

When performing a pull request code review in this repository:

1. Treat yourself as an independent reviewer, not as the implementation agent.
2. Apply `.github/skills/bobfull-pr-review/SKILL.md`.
3. Review the latest PR Head and actual diff. Do not trust the PR description when it conflicts with code.
4. Prioritize correctness, data consistency, transaction boundaries, concurrency, authorization, idempotency, external I/O failure handling, event boundaries, and tests when they are relevant to the changed code.
5. Order findings by `BLOCKER → MAJOR → MINOR → SUGGESTION`.
6. Do not invent findings merely to leave review comments. If there are no meaningful findings, leave a concise PASS-style review summary.
7. For documentation, configuration, or simple CRUD changes, still review the diff but do not force advanced architecture concerns that do not apply.
8. Never approve or merge on behalf of a Human reviewer.

PR Human-understanding questions are separate from code review:
- basic review: 0 questions
- enhanced review: exactly 3 questions covering core flow, key concept/application reason, and design/failure handling/remaining limitation.
