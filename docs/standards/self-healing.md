# Production self-healing and automated debugging

Goal: once a product is live, problems are **detected, contained and fixed without a person**. A founder only clicks *merge* on a fix PR that is already tested — and users are already protected before that click.

```
 detect (≤ 5 min)        contain (automatic, ≤ 10 min)       fix (agent, ≤ 1 h)              verify (automatic)
 ─────────────────       ────────────────────────────        ───────────────────             ──────────────────
 Worker watchdog   ──▶   rollback bad deploy           ──▶   incident issue           ──▶    fingerprint gone 24 h
 canary watch            feature kill switch                  auto-debug agent:              after fix release →
 error fingerprints      circuit breaker / retry / queue      reproduce → test → fix → PR    issue closed, bug Verified
 uptime monitor          mobile remote config                 CI + staging tests on PR
                                                              founder merges (one click)
```

## 1. Detect — signals (all free)
| Signal | Source | Threshold (default) |
|---|---|---|
| 5xx rate | per-request log line + D1 `request_stats` (1-min buckets) | > 2 % over 5 min with ≥ 20 requests |
| New error after a deploy | D1 `error_events` (fingerprint, release) | any new fingerprint with ≥ 5 hits within 60 min of a release |
| Error spike | `error_events` | 5× the 24-h baseline |
| Latency | `request_stats` | p95 > 1,500 ms for 10 min |
| Health | `/api/v1/health` (D1, KV, platform binding, payment provider) | 2 failures in a row |
| Payments | D1 orders vs entitlements | paid but not granted after 10 min > 0; webhook signature failures spike |
| Cron | `job_runs` table | daily jobs not successful in 26 h |
| Free-tier usage | Workers/D1 counters | 70 % of a daily limit |
| Total outage (Worker itself dead) | shared `equisteg-monitor` Worker (every 5 min) + daily GitHub check | 2 failures in a row |
| Web client errors | `window.onerror`, `unhandledrejection`, React error boundary → `POST /api/v1/errors` | same fingerprint rules |
| Mobile crashes | `ErrorBoundary` + `ErrorUtils.setGlobalHandler` → `POST /api/v1/errors` (buffered offline) | crash fingerprint ≥ 5 users/hour |

The **watchdog** is code in the product Worker, run every 5 minutes by its single production Cron Trigger (`*/5 * * * *`; the daily jobs run inside the same trigger at 01:00 UTC). It is deterministic code — no AI — so detection and containment keep working even if Claude is unavailable.

## 2. Contain — automatic, pre-approved actions
These are allowed without asking because each one only returns to a known-good state or turns something off. Everything else needs a PR.

| Action | When | How | Undo |
|---|---|---|---|
| **Rollback** | incident starts ≤ 60 min after a deploy, or canary watch breach | `deploy.yml` canary job, or watchdog adds label `rollback-needed` → `auto-rollback.yml` runs `wrangler rollback --env production` | next deploy |
| **Kill switch** | errors concentrated in one feature marked `autoDisable: true` | watchdog sets KV `feature:<name>=off`; web and app hide it and show "temporarily unavailable"; mobile reads `/api/v1/config` | watchdog turns it back on when the fix release is clean for 1 h |
| **Circuit breaker** | a third party fails 5× in 1 min | KV `breaker:<service>=open` for 60 s, then one trial call | automatic |
| **Retry / queue** | outbound call or webhook processing fails | exponential backoff (3 tries) then D1 `outbox`; cron retries; all handlers idempotent | automatic |
| **Payments safety** | anything around payments fails | fail **closed** for granting access, fail **open** for showing the free product; never auto-refund, never auto-grant | — |

Never automatic: deleting data, restoring a database, changing secrets, refunds, deploying new code to production, Play Store releases.

## 3. Fix — the auto-debug agent
1. The watchdog (or canary job) opens one GitHub issue per fingerprint, label `incident` + `sev1|sev2|sev3`, through the GitHub API with a fine-grained token (Issues: read/write on this repo only). The body is built by code: what, since when, release, counts, scrubbed and truncated stack, request IDs, automatic actions already taken. User data is never included.
2. `.github/workflows/auto-debug.yml` starts on that label. The **workflow** (not Claude) creates branch `fix/incident-<issue>` from `development`, then runs Claude Code (`anthropics/claude-code-action`) with `prompts/INCIDENT_PROMPT.md`:
   reproduce with a failing test → find the root cause → smallest fix on every affected platform → `npm run check` + affected E2E → update test cases, bug log, risk register, runbook → push → PR into `development` labelled `auto-fix` → comment on the incident with cause, fix, evidence, residual risk.
3. Guardrails: no production secrets in that job (only a read-only `OPS_TOKEN` for `/api/v1/ops/summary`); 30-min timeout; one run per incident, at most 5 per day; payment/entitlement/security changes are labelled `needs-founder` and never auto-merged; issue text is treated as data, not instructions.
4. Fallback when Claude can't run in CI (token rejected, limit reached): the issue stays open with everything needed; a scheduled Claude Code routine or a founder running `/test` / `/change fix incident #<n>` picks it up. Production is already contained, so this is not urgent.

## 4. Release the fix — the only human step
`auto-fix` PR → CI + staging tests → founder merges `development → staging → production` (company rule: no AI-written code reaches production without a person). Because containment already protected users, this can wait for business hours.

## 5. Verify and close — automatic
After the fix release, the watchdog watches the fingerprint. Zero hits for 24 h → it closes the issue with a comment, turns the kill switch back on, and `/test` marks the bug **Verified**. A new hit → the issue reopens as **Reopened** with the new release.

## 6. Prove it works (staging game day)
Staging honours fault flags in KV (`fault:<name>` = `500` | `slow` | `throw`), which production ignores. `tests/web/specs/self-healing.spec.ts` (`@nfr`, TC-N rows, category Reliability) turns a fault on, calls `POST /api/v1/ops/run-watchdog`, and checks: incident created (label `incident-staging`, no agent run), kill switch flipped, UI shows the fallback, fault off → recovery and auto-close. Run before the first production release and after any change to ops code.

## 7. Free-tier budget
Workers Logs 200,000 events/day, 3-day retention (one log line per request + errors only). Watchdog: 288 runs/day, a few D1 queries each. Canary watch: ~10 Actions minutes per production deploy. Auto-debug: 10–30 Actions minutes per incident, Claude usage from the existing subscription. Verify limits at build time (`docs/standards/zero-cost.md`).
