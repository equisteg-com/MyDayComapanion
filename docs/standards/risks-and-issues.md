# Risks and issues — never just list them, remove them

Company rule for every Claude command (`/newapp`, `/change`, `/update`, `/test`, the audit, the auto-debug agent) and every report.

## 1. Order of treatment (try them in this order)
1. **Eliminate** — change the design so the risk cannot happen (e.g. server-side entitlement check instead of trusting the app; static pages instead of a server that can go down).
2. **Reduce** — make it unlikely or small (validation, rate limits, backward-compatible migrations, budgets, tests that block the PR).
3. **Detect and auto-recover** — if it still happens, the system notices within minutes and fixes itself without a person (rollback, kill switch, retry, circuit breaker — see `docs/standards/self-healing.md`).
4. **Accept** — only when 1–3 are impossible or cost money. Needs a founder's explicit yes, recorded with date and reason. Never silent.

## 2. What every reported issue or risk must contain
An issue = something already wrong. A risk = something that could go wrong.

| Field | Meaning |
|---|---|
| ID | `ISS-xxx` (issue) or `RSK-xxx` (risk); bugs keep their `BUG-xxxx` |
| What | one plain-English sentence |
| Impact × Likelihood | High / Medium / Low each → severity S1–S4 (S1 payments, security, data loss, outage) |
| How we tackle it | the concrete steps, using the order above |
| Done now | what was already changed in this run (file, commit, test) — the default is to fix it now, not later |
| Automatic response | what the system does by itself if it happens in production (detector + action), or "none" with why |
| Residual | what is left after the fix: None / Low / Medium / High |
| Needs a person? | No, or **NEEDS HUMAN** with click-by-click steps and why it can't be automated |
| Status | Open / Mitigated / Eliminated / Accepted (founder, date) |

A report that lists a risk without "How we tackle it", "Done now" and "Residual" is incomplete — fix the report before showing it.

## 3. Default: fix it in the same run
- Issues found while working are fixed in the same branch, with a test that would have caught them, unless that is impossible (needs console access, money, or a founder decision).
- Anything left open goes to `docs/risk-register.md` (risks) or `tests/bugs/bug-log.xlsx` (bugs) — never only in chat.
- Target before production: no open S1/S2, and every residual is Low or None, or Accepted by a founder.

## 4. `docs/risk-register.md` (one per product, kept current)
Created in phase 10, updated by every command and by the auto-debug agent:

```
| ID | What | Severity | How we tackle it | Done now | Automatic response | Residual | Needs a person? | Status |
```

Seed it with the standard risks below and add product-specific ones.

## 5. Standard risks every product starts with
| ID | Risk | How we tackle it (eliminate → reduce → auto-recover) | Residual |
|---|---|---|---|
| RSK-001 | Bad deploy breaks production | CI + staging tests block it; post-deploy canary watch auto-rolls back within ~10 min; watchdog rolls back later spikes | Low |
| RSK-002 | A feature starts failing for users | per-feature kill switch turned off automatically by the watchdog; agent writes a fix PR | Low |
| RSK-003 | Payment webhook missed or failing | idempotent handler, provider retries, D1 outbox + cron re-check of paid orders, alert if paid-but-not-granted > 0 | Low |
| RSK-004 | Third-party outage (payment, email, platform Worker) | timeouts, retries with backoff, circuit breaker, queued retry, friendly fallback UI | Low |
| RSK-005 | Free-tier limit reached (requests, D1, logs, Actions) | budgets in `zero-cost.md`, usage check in the watchdog, alert at 70 % | Low |
| RSK-006 | DB migration breaks rollback | migrations backward-compatible for one release; never destructive in the same release | Low |
| RSK-007 | Secrets leaked | Gitleaks in CI + weekly; secrets only in `wrangler secret`/GitHub secrets; rotate on any leak | Low |
| RSK-008 | Auto-debug agent makes a wrong fix | agent only opens a PR; CI, staging tests and a founder merge before production; production stays safe via rollback/kill switch meanwhile | Low |
| RSK-009 | Prompt injection through error text sent to the agent | error text scrubbed, truncated and fenced as data; agent has no production secrets; PR reviewed | Low |
| RSK-010 | Claude subscription token stops working in CI | detection and auto-recovery never depend on Claude; fallback agent = scheduled Claude routine or `/test` locally | Low |
| RSK-011 | Mobile crash spike after a store release | remote kill switches via `/api/v1/config`; staged rollout in Play; crash fingerprints alerted | Medium (store releases can't be rolled back instantly) |
