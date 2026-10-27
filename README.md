# Equisteg Product Template

One private repository per product, created from this template. Web (Next.js), Android (React Native + Expo) and API (Cloudflare Worker) in one monorepo, built by Claude Code from a prototype, deployed for ₹0 on Cloudflare.

## The flow in one picture

```
Idea → HTML / Claude artifact  →  inputs/  →  /newapp  →  PR into development
                                                  │
        development ──PR──▶ staging ──PR──▶ production      (each merge deploys automatically)
             dev env            staging env         product's own domain
After staging deploys: /test  → bug log + report → "which bugs should I fix?"
Later:  /change <plain English>   ·   /update <plain English>
```

## Structure

```
product/
├── apps/
│   ├── web/        → Next.js + TypeScript, static export (SEO-ready HTML)
│   ├── mobile/     → React Native + Expo (Android first)
│   └── api/        → Cloudflare Worker (Hono) + D1/KV — payments, entitlement, cron
├── packages/
│   ├── shared-types/  → zod schemas + types shared by web, mobile, api
│   ├── api-client/    → typed fetch client
│   └── design-tokens/ → colors/spacing/type (created per product)
├── tests/
│   ├── cases/      → test-cases.xlsx (one row per step + expected result; functional + Non-functional)
│   ├── bugs/       → bug-log.xlsx (every failure found on staging, status, fix PR)
│   ├── tools/      → result collector + bug-log helper
│   ├── web/        → Playwright
│   ├── mobile/     → Maestro
│   ├── testdata/   → web/, mobile/, shared/
│   └── load/       → k6
├── docs/standards/ → zero-cost limits, cron jobs
├── infra/          → wrangler.example.jsonc, env templates
├── inputs/         → drop the prototype here
├── prompts/        → AUDIT_PROMPT.md for existing products
├── .claude/        → /newapp, /change, /update, /test + permissions
├── .github/        → CI, deploy, mobile, scheduled workflows, templates
├── CLAUDE.md       → rules Claude follows in every session
└── publisher.config.json → company details (filled once)
```

## Start a new product in VS Code (step by step)

One-time on each laptop:
1. Install **Git**, **Node.js 22 LTS**, **VS Code** and the **GitHub CLI** (`gh auth login`).
2. In VS Code open Extensions (Ctrl+Shift+X), search **Claude Code** (publisher Anthropic) and install it; sign in when asked. The other recommended extensions are suggested automatically from `.vscode/extensions.json`.

Each product:
1. GitHub → `equisteg-com/Repo-Template-equisteg-com` → **Use this template → Create a new repository** → owner `equisteg-com`, name = product, **Private**.
2. VS Code → Command Palette (Ctrl+Shift+P) → **Git: Clone** → pick the new repo → open it.
3. Terminal (Ctrl+`) → `npm install`.
4. Copy the prototype file (`.html`, or the Claude artifact downloaded as `.html/.jsx/.tsx`) into `inputs/`.
5. Open Claude Code (the Claude icon in the side bar, or Ctrl+Esc) and type **`/newapp`**. Use **`/newapp review`** to approve scope, preview and the PR step by step.
6. Before `/newapp`, create your branch: `git switch development && git pull && git switch -c feature/<name>` (Claude works only on the branch you created and never makes its own). Claude asks one question (the stack, with a recommended option), then builds on your branch, runs every check and opens a PR into `development`.
7. Merge the PR → `dev` deploys. Open a PR `development → staging` → merge → staging deploys and the full staging test suite runs (web: Playwright functional + accessibility, Lighthouse, k6 smoke, OWASP ZAP; Android: APK built against staging + Maestro on an emulator). `/test` first checks what is installed and configured, shows what is missing with the free fix, and asks for the details it needs (staging URL, test-mode payments, Play licence tester). Then type **`/test`** — Claude logs every failure in `tests/bugs/bug-log.xlsx`, writes a report in `docs/test-reports/`, and asks which bugs to fix. Open `staging → production` → a founder listed in `RELEASE_APPROVERS` merges → production deploys, is health-checked, tagged, or rolled back automatically.

Later changes, from the same Claude Code chat:
- **`/change`** followed by the change in plain English, e.g. `/change make the result card show 2 decimals and move Share below it`.
- **`/update`** followed by the new feature, e.g. `/update add a history screen that keeps the last 20 conversions offline`.
- **`npm run perf`** (page speed + small load check) and **`npm run load -- stress`** (or `load`, `spike`, `soak`) — performance and load testing on staging in one command; see [tests/load/README.md](./tests/load/README.md).
- **`/test`** after a staging deploy (or `/test dev`, `/test fresh`, `/test retest BUG-0004`): runs or collects the automation, updates the bug log and test-case statuses, gives a release recommendation, and asks what to fix.

Once live, production looks after itself ([self-healing](./docs/standards/self-healing.md)): a watchdog checks every 5 minutes; a bad release is rolled back automatically; a failing feature is switched off automatically; every incident becomes a GitHub issue that Claude debugs on its own and answers with a tested fix PR. Your only job is to merge that PR. Every risk or issue Claude reports comes with how it is tackled and what is left ([rule](./docs/standards/risks-and-issues.md)).

Long build? When Claude says so, type `/clear` then `/newapp` — it resumes from `PROGRESS.md`.
Existing products: open that repo and paste `prompts/AUDIT_PROMPT.md` into Claude Code.

## One-time company setup
1. Mark this repository as a template: Settings → General → **Template repository**.
2. GitHub org `equisteg-com` → Teams → create `build-team`, add the founders.
3. Cloudflare (free account) → Workers & Pages → create an API token with the **Edit Cloudflare Workers** template + D1 Edit, scoped to the Equisteg account.
4. In each product repo: Settings → Secrets and variables → Actions → secrets `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`; variables `RELEASE_APPROVERS`, `DEV_URL`, `STAGING_URL`, `PRODUCTION_URL`.
5. Verify `publisher.config.json` (publisher IDs, emails, package prefix, payment provider).

GitHub organization + Cloudflare: yes, it works. Deploys run from GitHub Actions with the API token, so the org needs nothing extra. If you prefer Cloudflare's own Git builds (Workers Builds, 3,000 free build minutes/month), an org owner must install the **Cloudflare Workers and Pages** GitHub app on `equisteg-com` and limit it to the product repos.

## Rules
See [CLAUDE.md](./CLAUDE.md) and [CONTRIBUTING.md](./CONTRIBUTING.md). Zero-cost limits: [docs/standards/zero-cost.md](./docs/standards/zero-cost.md). Cron jobs: [docs/standards/cron-jobs.md](./docs/standards/cron-jobs.md).
