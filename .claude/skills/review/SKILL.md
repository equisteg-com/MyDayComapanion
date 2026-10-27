---
name: review
description: Runs the specialist reviewer agents on the current branch (or a named area), merges their verdicts, fixes what they find in up to three rounds, and saves the review. Run only when the user types /review.
argument-hint: "[all | security | payments | ux | seo | policy | category | marketing | tests | release] [fix | report]"
---

# /review — a second pair of eyes on the current branch

Arguments: `$ARGUMENTS`

Rules for briefing reviewers, their verdict format and the fix loop: `docs/standards/agent-reviews.md`. Risks and issues are reported with how we tackle them (`docs/standards/risks-and-issues.md`).

## 1. Scope
1. `git branch --show-current`. On `main`, `development`, `staging` or `production` you may review and report, but before writing or fixing anything stop and ask the user to create their branch (`git switch development && git pull && git switch -c fix/<name>`). Never create or switch branches yourself.
2. `git fetch origin development`, then base = `origin/development`. Changed files: `git diff --name-only origin/development...HEAD`. If nothing changed, say so in one line and stop (unless the argument is `release`, which looks at `origin/staging`).
3. Mode: `report` = only report; `fix` or no mode = report, then fix S1–S3.

## 2. Pick the reviewers
Named area → that reviewer only (`security` → `agent:security-auditor`, `payments` → `agent:payments-auditor`, `ux` → `agent:ux-a11y-reviewer`, `seo` → `agent:seo-ai-reviewer`, `policy` → `agent:store-policy-reviewer`, `category` → `agent:category-reviewer`, `marketing` → `agent:marketing-claims-reviewer`, `tests` → `agent:test-traceability-auditor`, `release` → `agent:release-gate`).

Otherwise (`all` or empty) always run `agent:code-reviewer` and `agent:scope-guard`, then add by changed paths:

| Changed paths | Add |
|---|---|
| `apps/api/**`, `infra/**`, `**/_headers`, `**/package.json`, env examples, auth/cookie/token code, AI/LLM calls | `agent:security-auditor` |
| checkout, webhook, entitlement, licence, magic link, device, credits, usage, refund, Play Billing, `skus.ts`, `migrations/*payments*` | `agent:payments-auditor` (and `agent:security-auditor`) |
| `apps/web/**` components or pages, `apps/mobile/**` screens, `packages/design-tokens/**` | `agent:ux-a11y-reviewer` |
| public pages, content, `robots`, `sitemap`, `llms*.txt`, `lib/seo.ts` | `agent:seo-ai-reviewer` |
| ads, consent, `app.config.ts` permissions, SDK added to `apps/mobile/package.json`, `release/**`, Privacy/Terms/Refund/deletion pages | `agent:store-policy-reviewer` |
| `tests/**`, `docs/product-brief.md`, any new REQ/NFR | `agent:test-traceability-auditor` |
| any `apps/**` or `packages/**` change when `docs/product-brief.md` §1b names a category | `agent:category-reviewer` |
| `docs/marketing/**`, `release/listing/**`, landing or pricing copy | `agent:marketing-claims-reviewer` |
| `.claude/settings.json`, `.claude/hooks/**`, `.githooks/**`, `.github/workflows/**`, `tests/tools/secret-patterns.mjs` | `agent:security-auditor` (guard and CI changes) |

Tell the user in one line which reviewers will run and why.

## 3. Run
Start the chosen agents **in parallel** (one message, several Agent calls). Each brief follows `docs/standards/agent-reviews.md` §3: goal, base…head and changed files, only the context files it needs, evidence paths (latest test output, CI run link), `Round: 1`.

## 4. Merge
One table, S1 → S4, duplicates merged (keep the stricter severity and both IDs), each row with the reviewer's evidence. Reject any PASS that has no *SCOPE CHECKED* and re-ask that reviewer once.

## 5. Fix (skip in `report` mode)
- Fix every S1–S3 on the current branch, smallest change first, with a test that would have caught it where a test is possible. Follow `/change` steps 5–7 for tests, security scans and docs.
- Re-run only the reviewers that said NEEDS WORK, with `Round: 2` and what changed. Stop after round 3: what is still open goes to `tests/bugs/bug-log.xlsx` (bugs) or `docs/risk-register.md` (risks) with how we tackle it.
- Commit `fix(<scope>): address review findings (<IDs>)`.

## 6. Save and report
- Write `docs/reviews/<YYYY-MM-DD>-<branch with / as ->.md`: reviewers run, final verdict of each, the merged table with status (fixed in `<sha>` / open → BUG or RSK id / S4 noted), and NOT CHECKED items. Commit it with `docs: review <branch>`.
- If a PR for this branch exists, update its **Specialist review** section (permission prompt). Otherwise remind the user that the next PR carries it.
- Print: reviewers and verdicts, counts per severity found and fixed, what is still open and how it is tackled, NEEDS HUMAN items. Never print secrets or customer data.
