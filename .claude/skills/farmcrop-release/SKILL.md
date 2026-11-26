---
name: farmcrop-release
description: Use when a FarmCrop milestone or fix is ready to commit and push - runs the verification gate, secret scan and milestone report.
---

# FarmCrop milestone release gate

Run every step; if one fails, fix it or report it — never skip silently.

1. `git status` — confirm only intended files changed; no unrelated work is being swept in.
2. Backend: `cd backend && ./mvnw -B verify` → read `target/surefire-reports` totals.
3. Frontend: `cd frontend && npm test && npm run build && npm audit --audit-level=high`.
4. Browser: `cd frontend && npm run test:e2e` (backend running if the journey needs the API).
5. Review: run `/code-review` on the diff; run `/security-review` when auth, payments, uploads or permissions changed.
6. Secret / artifact scan on the staged diff:
   - `git diff --cached --name-only` must not include `.env` (other than `.env.example`), `target/`, `dist/`,
     `node_modules/`, `playwright-report/`, `test-results/`, uploads, database dumps, `*.pem`, `*.key`.
   - `git diff --cached | grep -inE "(password|secret|api[_-]?key|token)\s*[:=]\s*['\"]?[A-Za-z0-9/+_-]{8,}"` and
     inspect every hit (test fixtures with obviously fake values are fine).
7. Commit with a Conventional Commit message (`feat(scope): ...`, `fix(...)`, `test(...)`, `docs(...)`),
   ending with the attribution lines required by the session.
8. `git push origin main` (never force). Report the exact error if it fails; claim success only when Git confirms.
9. Milestone report: implemented, files, migrations, APIs, pages connected, security controls, tests run with
   results, build result, known issues, commit hash, push status, manual test steps.
