# Phase 9b — Production self-healing & automated debugging

Read `docs/standards/self-healing.md` (design) and `docs/standards/risks-and-issues.md` (rule) first. Build everything below; nothing here may need a paid service. Workflows `auto-debug.yml`, `auto-rollback.yml` and the canary job in `deploy.yml` already exist in the template — wire the product to them.

## 1. API Worker — `apps/api/src/ops/`
| File | Builds |
|---|---|
| `request-log.ts` | Hono middleware: `x-request-id` (generate if absent, return it), one JSON log line per request `{t, rid, route, status, ms, release}` — no IPs, emails, tokens or bodies; increments D1 `request_stats` (route, minute, count, errors5xx, p95 bucket) |
| `errors.ts` | `app.onError` + `POST /api/v1/errors` (web/mobile reports; zod-validated, 8 KB cap, rate-limited 30/min per IP hash, PII scrubber for emails/phones/tokens/card-like numbers). Fingerprint = sha256(platform + error name + first app stack frame + route). Upsert D1 `error_events (fingerprint, platform, release, feature, first_seen, last_seen, count, sample_rid, sample_stack)` |
| `health.ts` | `GET /api/v1/health`: D1 `select 1`, KV read, platform service binding ping, payment provider reachability (cached 60 s); 200 or 503 with which check failed (no internals) |
| `flags.ts` | `isOn(env, 'feature:<name>')` from KV (cached 30 s) + `GET /api/v1/config` (public flags for web/mobile: which features are on, minimum app version, message) |
| `resilience.ts` | `withTimeout`, `retry` (3 tries, backoff + jitter, only idempotent calls), circuit breaker in KV `breaker:<svc>`, D1 `outbox` for webhooks/emails with cron retry |
| `watchdog.ts` | runs every 5 min from `scheduled()`: evaluates the signals in self-healing.md §1 with thresholds from `ops.config.ts`; actions in §2 (kill switch, `rollback-needed` label); records D1 `incidents`; opens/updates/closes GitHub issues; never throws (logs its own failure and alerts) |
| `github.ts` | create/update/close issue, add labels, dedupe by fingerprint (search open issues with `fp:<fingerprint>` in body); uses secret `GITHUB_ISSUES_TOKEN` (fine-grained PAT, this repo, Issues read/write only) and var `GITHUB_REPO` |
| `summary.ts` | `GET /api/v1/ops/summary` (Bearer `OPS_TOKEN`, read-only). Contract used by the canary job in `deploy.yml` and the agent — keep it exactly: `{ release, windows: { "5m"\|"15m"\|"60m": { requests, errors5xx, p95ms } }, newFingerprints: [{ fingerprint, platform, route, count, firstSeen, release }], health: { ok, failed: [] }, incidents: [], killSwitches: {}, breakers: {}, jobs: { watchdog: lastSuccessIso, <daily job>: lastSuccessIso }, usage: { <limit>: percent } }` (type in `packages/shared-types`). `POST /api/v1/ops/run-watchdog` (same token) — staging/game day only |
| `faults.ts` | staging/dev only: KV `fault:<name>` injects 500 / 2 s delay / throw in a named route. Hard-disabled when `ENVIRONMENT === 'production'` (unit test proves it) |
| `ops.config.ts` | thresholds, which features are `autoDisable: true` (never payment verification or entitlement checks — those fail closed), ignore list |

- D1 migration `00xx_ops.sql`: `request_stats`, `error_events`, `incidents`, `job_runs`, `outbox` with indexes on time columns; cleanup in the daily job (keep 14 days).
- `wrangler.jsonc` production: `"triggers": { "crons": ["*/5 * * * *"] }` (one trigger: watchdog every run, daily jobs when `controller.cron` time is 01:00 UTC); vars `GITHUB_REPO`, `RELEASE` (set by deploy: `--var RELEASE:<sha>`); secrets `OPS_TOKEN`, `GITHUB_ISSUES_TOKEN`.
- Unit tests (Vitest, `@cloudflare/vitest-pool-workers`): fingerprinting is stable, scrubber removes PII, each watchdog rule fires/doesn't at the threshold, kill switch never touches payment code, faults are off in production, issue dedupe.
- Security tests (add to the `Non-functional` sheet, category Security): `/api/v1/ops/*` without or with a wrong `OPS_TOKEN` → 401; `/api/v1/errors` over 8 KB → 413, over the rate limit → 429, emails/tokens in a report are scrubbed before storage; `/api/v1/config` exposes no secrets.

## 2. Web — `apps/web`
Global `error` + `unhandledrejection` listeners and a React error boundary per route → `POST /api/v1/errors` (sampled 100 % for new fingerprints, 10 % after 50 hits/hour; `sendBeacon`). Read `/api/v1/config` at load and hide features that are off with a friendly message. Pages stay static, so the site keeps working when the API is down (cached HTML from Workers Static Assets).

## 3. Mobile — `apps/mobile`
Root `ErrorBoundary` + `ErrorUtils.setGlobalHandler` → queue in storage, send to `/api/v1/errors` when online (with app version, build, OS). Fetch `/api/v1/config` on start and on resume: hide features that are off; show "please update" if below the minimum version. Release with Play **staged rollout** (start 20 %) so a crash spike reaches few users; Play's halt-rollout is the store-side kill switch (**NEEDS HUMAN**, click steps in the runbook). OTA updates (EAS Update) only if its free tier fits — verify at build time, otherwise skip.

## 4. GitHub (one-time per product — NEEDS HUMAN, click-by-click in `release/NEEDS-HUMAN.md`)
1. Labels: `incident`, `incident-staging`, `sev1`, `sev2`, `sev3`, `rollback-needed`, `auto-fix`, `needs-founder` (or let `auto-debug.yml` create them on first run).
2. Secrets: `OPS_TOKEN` (one random value, `openssl rand -hex 32`, set as this GitHub secret and as the Worker secret in both staging and production), `CLAUDE_CODE_OAUTH_TOKEN` (`claude setup-token` on a founder's machine — uses the existing Claude subscription; if Anthropic rejects it in CI, the fallback in self-healing.md §3.4 applies — an API key costs money and needs a founder's yes).
3. Secret `AUTOFIX_TOKEN`: fine-grained PAT (or a free GitHub App token) for this repo only with Contents, Pull requests and Issues = read/write. Needed because pushes, PRs and labels made with the built-in `GITHUB_TOKEN` do not trigger other workflows, so the fix PR would get no CI.
4. Variable `OPS_BOT_LOGIN` = the GitHub account whose token the Worker uses (only it or a `RELEASE_APPROVERS` founder may trigger a rollback by label).
5. Fine-grained PAT for the Worker: repo = this product only, permission Issues: read and write, expiry 1 year, calendar reminder to rotate → `npx wrangler secret put GITHUB_ISSUES_TOKEN --env production`.
6. Shared uptime: add the product's `/api/v1/health` to `equisteg-monitor` (docs/standards/cron-jobs.md §3).

## 5. Runbooks — `docs/runbooks/`
One page each: `bad-deploy.md`, `feature-failing.md`, `api-down.md`, `payments.md`, `third-party-outage.md`, `db-errors.md`, `slow.md`, `cron-missed.md`, `free-tier-limit.md`, `mobile-crash-spike.md`, `ads-or-consent-broken.md`. Each: signal → automatic action → what the agent does → the rare human step (click by click) → how to confirm it's fixed. The agent updates the runbook after every incident.

## 6. Prove it (game day on staging)
`tests/web/specs/self-healing.nfr.spec.ts` (`@nfr`) + Non-functional rows (category Reliability, `TC-N-1xx`): fault on → `run-watchdog` → incident issue with `incident-staging` → kill switch off → UI fallback → fault off → watchdog recovers and closes. Plus a canary test: the canary job in `deploy.yml` also watches **staging** after each staging deploy — turn on the staging fault `fault:health=500`, run the staging deploy (`workflow_dispatch`), and confirm the canary rolls staging back and opens an `incident-staging` issue. Record results in `docs/test-reports/`.

## 7. Risk register and report
Create `docs/risk-register.md` from `docs/standards/risks-and-issues.md` §5 plus product risks; every row has how we tackle it, what was done now, automatic response and residual. Mark the phase ✅ only when: every check above has evidence, the game day passed, no risk has residual High, and every Medium has a founder decision.

## Proven practice (from the first product)
- **Start from the reference implementation** in `.claude/skills/newapp/templates/reference/` (`apps/api/src/ops/*`, `lib/github-issues.ts`, `migrations/00xx_ops.sql`, `test/ops.test.ts`, `apps/web/lib/error-report.ts`, `apps/mobile/src/ops.ts`, `tests/web/specs/self-healing.nfr.spec.ts`) and follow the wiring steps in its `README.md`. It was built and game-day tested; adapt the feature ids, routes and provider names, do not redesign it. File names differ slightly from the table in §1 (`stats.ts` = request log + stats, `killswitch.ts` = flags, `routes.ts` = summary/config/errors routes) — the contract in §1 is what matters.
- **Write budget:** request stats are buffered in memory and flushed per minute bucket, never one D1 write per request (free-tier write limit).
- **Kill switches live in one KV key** (`killswitches` → JSON map) so a read is one KV get and the watchdog cannot leave half-written state. Only features listed in `AUTO_DISABLE` can be switched off automatically; payments, restore, entitlement checks and the core flow are never in that list.
- **Third parties make health `degraded`, not `down`:** the canary must not roll back a good deploy because the payment provider is slow.
- **Details that were wrong the first time:** an error fingerprint counts as "active" only if seen in the last 10 minutes (otherwise an old incident never closes); attribute an error to the real request path when the matched route pattern contains `*` (middleware errors); create the GitHub issue first and add labels in a separate call (a missing label must not lose the issue); the watchdog's own final write is guarded so it can never throw.
- **Game day locally:** `POST /api/v1/ops/run-watchdog?at=<iso>` lets the test move the clock instead of waiting 5 minutes; `POST /api/v1/ops/fault` sets a fault. Fault a non-core route so parallel functional tests keep passing. Local token in `apps/api/.dev.vars` (`OPS_TOKEN=…`), never committed.
- **Still to run on staging after the first deploy** (record in `docs/test-reports/`): the canary rollback drill and the `incident-staging` issue round trip — they need the deployed Worker and the GitHub token.
