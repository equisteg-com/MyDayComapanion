# Phase 10 — Self-review, fix loop, Pull Request, final report

1. Update HLD/LLD to match the final code; re-export .docx.
2. Re-read docs/product-brief.md, CLAUDE.md and every phase file. Build `docs/compliance-matrix.md`:
   Item · Status (✅ / ❌ / NEEDS HUMAN) · Evidence (command output, file, URL).
   Cover: every REQ; zero-cost check (every service on a free tier, limits and headroom); payments/no-signup tests (Phase 4b); Play policies (Minimum Functionality, data safety, account deletion, target API, permissions); AdSense & AdMob placement/content rules; consent on web and mobile; ads.txt/app-ads.txt; originality vs existingProducts; SEO/AI items; security; firewall; test counts pass/fail (functional and non-functional sheets); every NFR has a passing row; performance numbers; open bugs in `tests/bugs/bug-log.xlsx` (none S1/S2); production self-healing (phase 9b game day passed, canary watch, rollback, kill switches, auto-debug secrets or NEEDS HUMAN); category rules (`docs/standards/product-categories.md`, every rule of the product's category with evidence); repository security (`docs/standards/repo-security.md` §5 checks green, §3 setup done or NEEDS HUMAN); `docs/risk-register.md` complete — every risk has how we tackle it, done now, automatic response and residual (`docs/standards/risks-and-issues.md`).
3. **Specialist panel.** Run every reviewer in `.claude/agents/` except `agent:incident-investigator` and `agent:release-gate`, in parallel, on `origin/development...HEAD` (briefs per `docs/standards/agent-reviews.md` §3). Add each S1–S3 finding to the compliance matrix as ❌ with its finding ID. Save the merged table to `docs/reviews/<date>-<branch>.md`.
4. Fix every ❌; re-run affected tests and the reviewers that said NEEDS WORK; repeat until none remain or three review rounds have run (then it goes to the bug log or risk register). Then reduce every risk in `docs/risk-register.md` in the order eliminate → reduce → detect and auto-recover; only a founder may accept one. No residual High may remain.
5. **Release gate.** Brief `agent:release-gate` with the compliance matrix, the review file and the latest test evidence. Its verdict is the *production readiness* line in the final report; anything that "runs on staging" keeps it at NOT READY until that evidence exists.
6. `docs/CHANGELOG-vs-prototype.md`: every addition/removal vs the prototype and why.
7. Root `README.md`: replace "Getting started" with real per-app dev/test/build commands for this product (keep the company Workflow section as is).
8. Push the user's current branch (permission prompt) and open a PR into `development` with `gh pr create` (without `gh`, see Proven practice below), filling every section of `.github/PULL_REQUEST_TEMPLATE.md`, linking the compliance matrix, the review file and NEEDS-HUMAN list. Do NOT merge.
9. Promotion `development → staging → production` (PRs a founder opens and merges) and Play submission only after the user explicitly confirms.

## Final report (print to user)
1. Product name, one-line pitch, web preview URL, APK preview link, PR link
2. Scope delivered: prototype REQs, parity features, differentiators
3. How it differs from existing products
4. Compliance summary (Play, Minimum Functionality, AdSense, AdMob, consent)
5. Security / firewall / tests / performance — pass counts and key numbers; specialist reviewer verdicts and the release-gate verdict
6. NEEDS HUMAN list (release/NEEDS-HUMAN.md)
7. Risks and issues — table from `docs/risk-register.md`: what, severity, how we tackle it, done now, automatic response in production, residual, needs a person? (never a bare list)
8. Self-healing status — what is automatic in production and the one-time NEEDS HUMAN setup left
9. Next improvements

## Proven practice (from the first product)
- **Before pushing:** clean `npm ci` (proves the lock file CI will use), `npm run check`, then the web build and `npm run seo:audit` **twice** — once as production and once with the dev/staging variables `deploy.yml` sets (`NEXT_PUBLIC_NOINDEX=1`). A check that only ever ran in one mode is not evidence for the other.
- **No `gh` CLI on the machine:** push the branch, then give the user the compare link (`https://github.com/<owner>/<repo>/compare/development...<branch>?expand=1`) and the PR title and body to paste (**NEEDS HUMAN**), or ask them to install the free GitHub CLI and run `gh auth login`. Never read stored credentials (`git credential fill`, `gh auth token`): the secrets guard blocks it (`docs/standards/repo-security.md`).
- **After the PR merges, read the deploy run.** The phase is done when the dev deploy is green and the dev URL answers, not when the PR is open. If the deploy fails, fix it on the user's next branch and say so in the report.
- **Evidence that can only exist after staging** (Android/Maestro, ZAP, MobSF, payment journey, canary drill) is listed in the compliance matrix as "runs on staging" with the workflow that produces it — not marked ✅.
