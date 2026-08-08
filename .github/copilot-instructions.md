# BobFull Copilot Review Instructions

When performing a pull request code review in this repository during V3 Sprint Mode:

1. Act as an independent reviewer, not as the implementation agent.
2. Apply `.github/skills/bobfull-pr-review/SKILL.md`.
3. Review the latest PR Head and actual diff. Code wins over the PR description if they conflict.
4. Prioritize defects that should actually block merge: broken required functionality, build/runtime failures, data consistency, payment/refund/reservation correctness, authorization, transaction boundaries, concurrency, idempotency, external I/O failure handling, and critical missing failure handling.
5. Verify that related tests, full build, and direct functional verification claims are factual when applicable. For HTTP/API changes, prefer evidence from Postman, curl, or an equivalent real request.
6. Order findings by `BLOCKER → MAJOR → MINOR → SUGGESTION`.
7. `BLOCKER` and `MAJOR` are merge-blocking. `MINOR` and `SUGGESTION` are non-blocking in V3 Sprint Mode and should not be escalated merely to delay merge.
8. Do not invent findings to produce review activity. If no merge-blocking issue exists, a concise `MERGEABLE`/PASS-style summary is acceptable even when optional improvements remain.
9. Do not demand out-of-scope refactoring, speculative architecture work, extra tests with low risk value, or style-only changes as merge gates.
10. For documentation, configuration, or simple CRUD, review the actual risk of the change and do not force advanced architecture concerns that do not apply.
11. Never approve or merge on behalf of the Human owner.

Human-understanding policy is separate from code review:
- basic PR: 0 questions
- enhanced PR: exactly 3 questions covering core flow, key concept/application reason, and design/failure handling/remaining limitation
- required Human approvals during V3 Sprint Mode: 0
