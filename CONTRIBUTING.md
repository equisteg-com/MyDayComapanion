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

## Build team: Claude commands
You can ask Claude to work on a PR or an issue by commenting one of these (a founder adds you to the allowed list first):

| Comment | What happens |
|---|---|
| `/review` | Specialist reviewers check the PR and reply with verdicts and findings |
| `/test` | Claude reads the PR's lint, typecheck, unit test and build results and fixes what fails |
| `/change <what to change>` | Claude makes the change, with tests, and reviews it |
| `/update <new feature>` | Claude builds the feature, with tests, and reviews it |
| `/newapp` | Claude builds a whole new product from the prototype in `inputs/` (see below) |

Claude reacts with 👀, works for a few minutes inside GitHub, then replies. Code changes come on a branch `claude/<number>-<run>`; the reply shows the two commands to pull it into your branch. Read the change, run `npm run check`, then push it to your PR. There is a daily limit per repo. If a result is held back by a safety check, a founder is told and follows up.

**Building a new product with `/newapp`:** a founder creates the product repo from the template and gives you access. Put the prototype (HTML, or a Claude artifact saved as `.html`/`.jsx`/`.tsx`, plus screenshots) in `inputs/` through a PR from your fork, then comment `/newapp` on that PR (or on an issue, if the prototype is already on `development`). Claude works on branch `claude/newapp-<number>`. Each run takes up to about two hours and builds as much as it can, then replies with what is done, what is next and any questions. Answer and continue with `/newapp <your answers>`; a plain `/newapp` just continues. When the product is finished and reviewed, Claude opens the PR into `development` for a founder to merge. Questions about money, domains, store or payment accounts go to a founder.

## Founders
Branch in this repo (`git switch development && git pull && git switch -c feature/<name>`), push and open a PR into `development`. Merge with **Squash and merge**. Promote with PRs `development → staging` and `staging → production`. Your Claude setup comes from `npm run kit`.

## Rules
- Never push directly to `development`, `staging` or `production`. On GitHub Free this is enforced by the `guard` CI job (it opens an incident issue), not by GitHub.
- Never commit secrets: local secrets go in `.env` / `.dev.vars` (ignored), server secrets with `npx wrangler secret put`. The pre-commit check and CI stop secret files and live keys.
- No paid services (see `docs/standards/zero-cost.md`).
- Every change updates `tests/cases/test-cases.xlsx` (functional and, where it applies, `Non-functional` rows) and the automation in `tests/`.
- Test data only in `tests/testdata/`.
