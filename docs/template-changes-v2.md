# Template v2 — what changed and why (1 Oct 2026)

| Area | Before | Now | Why |
|---|---|---|---|
| Branches | `main` only | `development` (default) → `staging` → `production`, each auto-deploys | Requested 3-branch flow with environments |
| Commands | `/newapp` | `/newapp`, `/change <plain English>`, `/update <plain English>` | Change and extend products in plain English |
| Stack choice | Fixed, no questions | Phase 0b scores options and asks the user once (TypeScript everywhere recommended) | Checklist step 2 |
| Hosting | Vercel Pro (paid, ~per seat) + Upstash | Cloudflare Workers Free: static Next.js + Hono API + D1/KV, Cron Triggers | Zero-cost rule |
| Payments | Not covered | Phase 4b: no sign-up, verified webhooks, licence key + magic link restore, usage credits, auto-revoke cron, Play Billing on Android | Secure paid access without accounts |
| Cron jobs | Not covered | `docs/standards/cron-jobs.md`, Cloudflare Cron Triggers + `scheduled.yml` | Missing steps |
| Tests | `apps/web/tests/e2e`, `apps/mobile/.maestro`, no data folder | `tests/web`, `tests/mobile`, `tests/testdata`, `tests/load`, `tests/cases/test-cases.xlsx` (one row per step) | Separate folders, per-step expected results |
| CI | Lint/typecheck/test on PR to main | + build, Gitleaks, Semgrep CE, OSV-Scanner, promotion order, guard; deploy with health check, auto-rollback, tag | Private repo on GitHub Free has no branch protection or code scanning |
| AI search | llms.txt, bot allow-list | + Cloudflare AI-bot setting check, Bing Webmaster/IndexNow, entity consistency, daily crawler check | Cloudflare can block AI crawlers by default |
| VS Code | "open in VS Code" | Step-by-step README, `.vscode/extensions.json` + settings committed | Clarity |
| Audit prompt | Vercel checks, no launch or payment section | Cloudflare checks, J Launch Readiness, K Payments | Matches the new standard |
