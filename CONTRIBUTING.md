# Contributing

## Branches
| Branch | Deploys to | Accepts PRs from | Who merges |
|---|---|---|---|
| `development` (default) | dev environment | `feature/*`, `fix/*`, `change/*`, `update/*` (from this repo or a fork) | any founder after CI is green |
| `staging` | staging environment + staging tests (functional + non-functional) | `development` only | any founder |
| `production` | production + health check + tag | `staging` only | a founder in `RELEASE_APPROVERS` |

## Build team: work through a fork
You have read access to this repo. You can clone it, build it, run every test and send changes as pull requests from your own fork.

1. On GitHub, open this repo → **Fork** → owner: your account (the fork stays private).
2. Clone your fork and keep it up to date:
   ```
   git clone https://github.com/<you>/<product>.git
   cd <product>
   git remote add upstream https://github.com/equisteg-com/<product>.git
   git fetch upstream
   git switch -c feature/short-description upstream/development
   ```
3. `npm install`, make the change, then run `npm run check`, `npm run test:web` (and `npm run test:mobile` for Android work).
4. Commit with [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `test:`, `docs:`, `chore:`), push to your fork and open a PR into `equisteg-com/<product>` `development`, filling in the template.
5. CI (lint, typecheck, tests, build, Gitleaks, Semgrep, OSV) runs on the PR after a founder approves the run. A founder reviews and merges.

Staging tests, deploys and production releases are run by founders.

## Founders
Branch in this repo (`git switch development && git pull && git switch -c feature/<name>`), push and open a PR into `development`. Merge with **Squash and merge**. Promote with PRs `development → staging` and `staging → production`. Your Claude setup comes from `npm run kit`.

## Rules
- Never push directly to `development`, `staging` or `production`. On GitHub Free this is enforced by the `guard` CI job (it opens an incident issue), not by GitHub.
- Never commit secrets: local secrets go in `.env` / `.dev.vars` (ignored), server secrets with `npx wrangler secret put`. The pre-commit check and CI stop secret files and live keys.
- No paid services (see `docs/standards/zero-cost.md`).
- Every change updates `tests/cases/test-cases.xlsx` (functional and, where it applies, `Non-functional` rows) and the automation in `tests/`.
- Test data only in `tests/testdata/`.
