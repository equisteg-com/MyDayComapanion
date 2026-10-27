# Equisteg Product Template

One private repository per product, created from this template. Web (Next.js), Android (React Native + Expo) and API (Cloudflare Worker) in one monorepo, deployed for ₹0 on Cloudflare.

## Branches and deploys

```
feature/* ──PR──▶ development ──PR──▶ staging ──PR──▶ production      (each merge deploys automatically)
                    dev env            staging env       product's own domain
```

## Structure

```
product/
├── apps/
│   ├── web/        → Next.js + TypeScript, static export (SEO-ready HTML)
│   ├── mobile/     → React Native + Expo (Android first)
│   └── api/        → Cloudflare Worker (Hono) + D1/KV
├── packages/
│   ├── shared-types/  → zod schemas + types shared by web, mobile, api
│   ├── api-client/    → typed fetch client
│   └── design-tokens/ → colors/spacing/type (created per product)
├── tests/
│   ├── cases/      → test-cases.xlsx (one row per step + expected result; functional + Non-functional)
│   ├── bugs/       → bug-log.xlsx
│   ├── tools/      → result collector, bug-log helper, secret check
│   ├── web/        → Playwright
│   ├── mobile/     → Maestro
│   ├── testdata/   → web/, mobile/, shared/
│   └── load/       → k6
├── docs/standards/ → zero-cost limits, cron jobs
├── docs/test-reports/, docs/reviews/, docs/marketing/
├── infra/          → wrangler.example.jsonc, env templates
├── inputs/         → the product prototype
├── .githooks/      → pre-commit secret check (enabled by npm install)
├── .github/        → CI, deploy, mobile, scheduled workflows, templates
└── publisher.config.json → company details
```

## Build and test (everyone)

One-time on each laptop: install **Git**, **Node.js 22 LTS** and **VS Code**.

```
npm install                 # also enables the pre-commit secret check
npm run check               # lint, typecheck, unit tests, build across all workspaces
npm run test:web            # Playwright (web)
npm run test:mobile         # Maestro (Android emulator)
npm run check:secrets       # no secret files or live keys in tracked files
```

Performance and load on staging: `npm run perf` and `npm run load -- <smoke|load|stress|spike|soak>` ([tests/load/README.md](./tests/load/README.md)). Never against production.

How to send a change (build team: through a fork): [CONTRIBUTING.md](./CONTRIBUTING.md).

## Founders
Founders also run `npm run kit` once per checkout. It installs the founders' private kit from a separate repo; the files are git-ignored and never committed here. Without access it prints a note and changes nothing. Setup, commands and company settings are in the kit.

## Keeping code and secrets safe
Repositories are private, secrets never enter git (a pre-commit check and Gitleaks in CI), CI actions are pinned and Dependabot watches dependencies. Build-team members contribute through forks, and GitHub gives workflows from fork PRs no secrets. To report a problem: [SECURITY.md](./SECURITY.md).

## Rules
[CONTRIBUTING.md](./CONTRIBUTING.md). Zero-cost limits: [docs/standards/zero-cost.md](./docs/standards/zero-cost.md). Cron jobs: [docs/standards/cron-jobs.md](./docs/standards/cron-jobs.md).
