# Product Monorepo

Standard monorepo for web, mobile, and backend — built once, deployed everywhere.

## Structure

```
product/
├── apps/
│   ├── web/        → Next.js (React + TypeScript) on Vercel — SSR for SEO/AdSense
│   ├── mobile/     → React Native + Expo (mobile app)
│   └── api/        → Node.js backend (NestJS/Express) — only if the product needs one
├── packages/
│   ├── shared-types/ → Shared TypeScript interfaces (API contracts)
│   ├── api-client/   → Shared fetch/API request logic
│   └── design-tokens/ → Shared colors/spacing/type (created per product)
├── infra/          → Docker configs, environment templates
├── inputs/         → Drop the product prototype here
├── prompts/        → AUDIT_PROMPT.md for existing products
├── .claude/        → /newapp skill + Claude Code permissions
├── CLAUDE.md       → Rules Claude follows in every session
├── publisher.config.json → Company-wide details (filled once)
└── .github/
    └── workflows/  → CI/CD pipeline definitions
```

## Getting started

1. Clone the repo
2. `npm install` at the root
3. See each app's own README (once added) for local dev instructions

## Build a new product with Claude Code (`/newapp`)

1. GitHub → **Use this template** → new repo → clone → open in VS Code.
2. Put the prototype (HTML, or Claude artifact downloaded as .html/.jsx/.tsx) in `inputs/` — or drag/@-mention it into the Claude Code chat.
3. In Claude Code type **`/newapp`** (or `/newapp review` to approve scope, preview and final PR step by step).

Claude derives the product brief from the prototype, researches competitors, builds `apps/web` + `apps/mobile` (+ `apps/api` only if needed), and does SEO, security, firewall, tests, docs and the Play Store pack — on a `feature/<product>` branch, ending with a PR into `main` for code-owner approval.
Long build? When Claude says so, type `/clear` then `/newapp` — it resumes from `PROGRESS.md`.

**Existing products:** open that repo in VS Code and paste `prompts/AUDIT_PROMPT.md` into Claude Code.

**One-time company setup:** verify `publisher.config.json` (publisher IDs, emails, package prefix) in this template repo.

## Workflow

- **Never push directly to `main`.** Always work on a feature branch.
- Branch naming: `feature/xxx`, `fix/xxx`, `chore/xxx`
- Open a PR into `main` — CI must pass and a code owner must approve before merge
- See [CONTRIBUTING.md](./CONTRIBUTING.md) for full details

## Stack

See the internal Product Architecture & Tools Setup guides for full stack details, deployment pipeline, and access-control policy.
