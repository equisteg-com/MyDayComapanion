# Phase 10 — Self-review, fix loop, Pull Request, final report

1. Update HLD/LLD to match the final code; re-export .docx.
2. Re-read docs/product-brief.md, CLAUDE.md and every phase file. Build `docs/compliance-matrix.md`:
   Item · Status (✅ / ❌ / NEEDS HUMAN) · Evidence (command output, file, URL).
   Cover: every REQ; zero-cost check (every service on a free tier, limits and headroom); payments/no-signup tests (Phase 4b); Play policies (Minimum Functionality, data safety, account deletion, target API, permissions); AdSense & AdMob placement/content rules; consent on web and mobile; ads.txt/app-ads.txt; originality vs existingProducts; SEO/AI items; security; firewall; test counts pass/fail (functional and non-functional sheets); every NFR has a passing row; performance numbers; open bugs in `tests/bugs/bug-log.xlsx` (none S1/S2); production self-healing (phase 9b game day passed, canary watch, rollback, kill switches, auto-debug secrets or NEEDS HUMAN); `docs/risk-register.md` complete — every risk has how we tackle it, done now, automatic response and residual (`docs/standards/risks-and-issues.md`).
3. Fix every ❌; re-run affected tests; repeat until none remain. Then reduce every risk in `docs/risk-register.md` in the order eliminate → reduce → detect and auto-recover; only a founder may accept one. No residual High may remain.
4. `docs/CHANGELOG-vs-prototype.md`: every addition/removal vs the prototype and why.
5. Root `README.md`: replace "Getting started" with real per-app dev/test/build commands for this product (keep the company Workflow section as is).
6. Push the user's current branch (permission prompt) and open a PR into `development` with `gh pr create`, filling every section of `.github/PULL_REQUEST_TEMPLATE.md`, linking the compliance matrix and NEEDS-HUMAN list. Do NOT merge.
7. Promotion `development → staging → production` (PRs a founder opens and merges) and Play submission only after the user explicitly confirms.

## Final report (print to user)
1. Product name, one-line pitch, web preview URL, APK preview link, PR link
2. Scope delivered: prototype REQs, parity features, differentiators
3. How it differs from existing products
4. Compliance summary (Play, Minimum Functionality, AdSense, AdMob, consent)
5. Security / firewall / tests / performance — pass counts and key numbers
6. NEEDS HUMAN list (release/NEEDS-HUMAN.md)
7. Risks and issues — table from `docs/risk-register.md`: what, severity, how we tackle it, done now, automatic response in production, residual, needs a person? (never a bare list)
8. Self-healing status — what is automatic in production and the one-time NEEDS HUMAN setup left
9. Next improvements
