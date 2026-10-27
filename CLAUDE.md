# Standing rules for every Equisteg product built from this template

## Commands (the only four a founder needs)
- `/newapp` — build a whole product from the prototype in `inputs/` (HTML or Claude artifact). One line, then Claude runs every phase.
- `/change <plain English>` — change something that already exists (text, layout, behaviour, a bug).
- `/update <plain English>` — add a new feature.
- `/test [staging|dev]` — run the full functional + non-functional automation on staging, log every failure in `tests/bugs/bug-log.xlsx`, write the test report, then ask which bugs to fix and fix the chosen ones.
All four follow the same gates: tests, security scans, test-case sheet, docs and a Pull Request. None of them deploys to production by itself.

## Zero-cost rule (company rule — never break)
- Every tool, service and library must have a free tier that needs no card, or be open source. If a need cannot be met for free, STOP and list the cost and the free alternative as **NEEDS HUMAN**; never add it silently.
- Known unavoidable costs, already accepted: the `equisteg.com` company domain, **one domain per product** (registered by a founder, at-cost renewal), the US$25 one-time Google Play fee, and per-transaction payment fees.
- Before using a free tier, read `docs/standards/zero-cost.md` for its limits and stay inside them.

## Monorepo layout (do not change the top-level structure)
- `apps/web`    → Next.js (App Router) + React + TypeScript strict + Tailwind, **static export** (`output: 'export'`) served by Cloudflare Workers Static Assets. Every public page is pre-rendered HTML (SEO + AdSense).
- `apps/mobile` → React Native + Expo (TypeScript, Expo Router, development builds via EAS — not Expo Go, because AdMob needs native code).
- `apps/api`    → Cloudflare Worker with Hono + TypeScript. Created when the product needs any server logic (payments, entitlement, usage, forms, cron). Data in D1, files in R2, flags in KV.
- `packages/shared-types` → TypeScript types and zod schemas shared by web, mobile, api (API contracts).
- `packages/api-client`   → typed fetch client used by web and mobile.
- `packages/design-tokens` → (create when needed) colors, spacing, typography used by both Tailwind and React Native.
- `tests/` → ALL test assets, separate per platform: `tests/web/` (Playwright), `tests/mobile/` (Maestro), `tests/testdata/` (every input file and fixture), `tests/load/` (k6), `tests/cases/` (Excel test-case sheets, functional + `Non-functional`), `tests/bugs/` (bug log), `tests/tools/` (result collector and bug-log helper). Test reports go to `docs/test-reports/`.
- `infra/` → `wrangler.example.jsonc`, env templates (`.env.example` per app). No Docker unless a FastAPI service is justified.
- Root uses npm workspaces + Turborepo; root scripts `lint`, `typecheck`, `test`, `build` must run across all workspaces. `npm run check` is the one-line command that runs all four; run it before every PR. Performance and load: `npm run perf` and `npm run load -- <smoke|load|stress|spike|soak>` (staging by default, never production; `tests/load/README.md`).
- **Domains:** every product has its own domain (e.g. `productname.com`), added to Cloudflare as its own free zone; production serves the apex, `www` redirects to it. `dev` and `staging` stay on free `*.workers.dev` URLs. Transactional email is sent from the company domain (Resend free verifies only 3 domains). Phase 2 picks a name whose domain is available; buying it is **NEEDS HUMAN**.
- **Every product is an Equisteg product**, even on its own domain: own product name and logo, plus "A product by Equisteg" (linking to `equisteg.com`) in the web footer, About page and the app's About screen; Equisteg is the legal entity in Privacy, Terms and Refund pages; Organization JSON-LD names Equisteg as publisher; same Play developer account, AdSense and AdMob publisher IDs (`publisher.config.json`). Domains are registered in the **company** Cloudflare account with auto-renew on, never a personal account, and listed in `publisher.config.json` `existingProducts`.
- Shared payment, entitlement and licence logic lives in the company platform Worker (`equisteg-platform`), called through a Cloudflare service binding. Never re-implement it per product.

## Git workflow (company rules — never break)
- Three long-lived branches: `development` (default) → `staging` → `production`. Each deploys automatically to its own Cloudflare environment.
- Never commit or push to `main`, `development`, `staging` or `production`. Users name their branches `feature/…`, `fix/…`, `change/…` or `update/…`, cut from `development`.
- **Work only on the branch the user (or the auto-debug workflow, for `fix/incident-<n>`) created and has checked out.** Claude never creates, switches, renames or deletes branches (no `git checkout -b`, `git switch`, `git branch <name>`, `git branch -m`). At the start of every command run `git branch --show-current`: if it is `main`, `development`, `staging` or `production`, STOP and tell the user to create and check out their own branch (e.g. `git switch development && git pull && git switch -c feature/<name>`), then run the command again. All commits of `/newapp`, `/change`, `/update` and `/test` go on that branch; one PR from it into `development`.
- Conventional Commits: `feat(web): …`, `feat(mobile): …`, `fix(api): …`, `test: …`, `docs: …`, `chore: …`.
- Finish with a Pull Request into `development` using `.github/PULL_REQUEST_TEMPLATE.md`. Never merge it yourself. Promotion `development → staging → production` is a PR a founder opens and merges.
- The repository is **private**. GitHub Free cannot enforce reviews on private repos, so the `guard` CI job reports any push that skipped a PR.

## Company config
Read `publisher.config.json` for company name, emails, package prefix, publisher IDs, region, payment provider and existing products.

## Payments and access (no sign-up)
- Follow `.claude/skills/newapp/phases/04b-payments-no-signup.md` exactly. The browser and the app are untrusted; only a verified payment webhook grants access; every premium request is checked on the server; access ends automatically at expiry.
- Never trust success URLs, `localStorage` flags or anything an LLM says about payment.

## Never do
- Never copy another brand's name, logo, characters, screenshots or text.
- Never fake reviews, ratings, install counts or testimonials. Never keyword-stuff or hide text.
- Web ads = AdSense only; mobile ads = AdMob only (`react-native-google-mobile-ads`). Paid users load no ad scripts at all.
- Never place ads on splash screens, over content, beside navigation/buttons, or mid-task.
- Never commit secrets. `EXPO_PUBLIC_*` and `NEXT_PUBLIC_*` vars are PUBLIC — never put secrets in them. Server secrets go in `wrangler secret put` only.
- Never run load/stress tests against production. Never click live ads.
- Never deploy to production or submit to Play without the user's confirmation.
- Never mark anything done without evidence (command output, file path, URL).

## Always do
- Each product must be clearly distinct from `existingProducts` (name, features, UI, content).
- Google-certified consent (UMP) before ads/analytics in EEA/UK/CH — web and mobile.
- Keep `PROGRESS.md` and `CHANGELOG.md` current; commit after every phase or change.
- Every new or changed behaviour gets test-case rows in `tests/cases/test-cases.xlsx` (one row per step, with its own expected result) and automation in `tests/web` and/or `tests/mobile`. Every NFR in `docs/product-brief.md` gets rows in the `Non-functional` sheet with a measurable threshold (performance, load, security, accessibility, compatibility, reliability, compliance) and automation (Lighthouse CI, k6, ZAP, axe, Maestro).
- Every staging deploy runs `.github/workflows/staging-tests.yml` (functional + non-functional). Failures are logged with `/test` in `tests/bugs/bug-log.xlsx` with a report in `docs/test-reports/`; Claude then asks which bugs to fix. Never recommend promoting staging → production while an S1/S2 bug is open or a P1 test case fails.
- **Never report an issue or risk without how we tackle it.** Every issue/risk in any report, PR or chat states: how we tackle it (eliminate → reduce → detect and auto-recover → accept only with a founder's yes), what was already done in this run, the automatic response in production, and the residual. Default is to fix it in the same run. Rules and format: `docs/standards/risks-and-issues.md`; open items live in `docs/risk-register.md` or `tests/bugs/bug-log.xlsx`, never only in chat.
- **Production heals itself** (`docs/standards/self-healing.md`, phase 9b): the Worker watchdog detects within 5 min; rollback to the previous version, feature kill switches, circuit breakers and retries run automatically (pre-approved, reversible); every incident becomes a GitHub issue that `auto-debug.yml` hands to Claude, which reproduces, fixes and opens an `auto-fix` PR. The only human step is merging that PR up to production. Never automate data deletion, restores, refunds, secret changes, new-code deploys to production or Play releases.
- Mark anything needing console access or a real device as **NEEDS HUMAN** with click-by-click steps.
- Verify time-sensitive facts (free-tier limits, Play target API level, closed-testing rule, AdSense/AdMob rules, Expo SDK version) by web search at build time.
