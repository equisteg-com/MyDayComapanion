# Contributing

## Branches
| Branch | Deploys to | Accepts PRs from | Who merges |
|---|---|---|---|
| `development` (default) | dev environment | `feature/*`, `fix/*`, `change/*`, `update/*` | any founder after CI is green |
| `staging` | staging environment + staging tests (functional + non-functional) | `development` only | any founder |
| `production` | production + health check + tag | `staging` only | a founder in `RELEASE_APPROVERS` |

## Workflow
1. Update and branch from `development`:
   ```
   git switch development
   git pull origin development
   git switch -c feature/short-description
   ```
   Then open Claude Code on that branch. Claude works only on the branch you checked out — it never creates or switches branches; if you are on `development`, `staging`, `production` or `main` it stops and asks you to create your branch first.
2. Commit with [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).
3. Push and open a PR into `development` using the template, with the *Specialist review* section filled (Claude does this; run `/review` yourself for work done without Claude). CI (agent lint, lint, typecheck, tests, build, Gitleaks, Semgrep, OSV) must be green.
4. Merge with **Squash and merge**. Promote with PRs `development → staging` and `staging → production`.

## Rules
- Never push directly to `development`, `staging` or `production`. The repo is private on GitHub Free, so this is enforced by the `guard` job (it opens an incident issue) and by Claude's denied commands, not by GitHub.
- Never commit secrets — local secrets in `.env` / `.dev.vars` (ignored), server secrets with `npx wrangler secret put`.
- No paid services (see `docs/standards/zero-cost.md`).
- Every change updates `tests/cases/test-cases.xlsx` (functional and, where it applies, `Non-functional` rows) and the automation in `tests/`.
- After a staging deploy, run `/test`: failures go into `tests/bugs/bug-log.xlsx` and a report into `docs/test-reports/`. Promote to production only when the report says *Ready* (no open S1/S2 bug, all P1 test cases pass).
- Rotate credentials if a team member leaves.

## MyDayCompanion (native Android)
- The product is a native Kotlin/Compose Android app at the repo root (`app/`, `domain/`, `data/`), not the template's Expo app. Read the product override at the top of `CLAUDE.md`.
- Local check before every PR: `./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest lintDebug :app:assembleDebug`. CI runs it in `.github/workflows/android.yml`.
- Security, privacy, export/delete and audit tests may never be weakened. A new permission, dependency, network path or cost needs a founder decision first (`docs/prebuild/12-open-questions.md`).
