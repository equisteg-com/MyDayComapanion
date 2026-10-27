# Phase 10 — Self-review, fix loop, Pull Request, final report

1. Update HLD/LLD to match the final code; re-export .docx.
2. Re-read docs/product-brief.md, CLAUDE.md and every phase file. Build `docs/compliance-matrix.md`:
   Item · Status (✅ / ❌ / NEEDS HUMAN) · Evidence (command output, file, URL).
   Cover: every REQ; Play policies (Minimum Functionality, data safety, account deletion, target API, permissions); AdSense & AdMob placement/content rules; consent on web and mobile; ads.txt/app-ads.txt; originality vs existingProducts; SEO/AI items; security; firewall; test counts pass/fail; performance numbers.
3. Fix every ❌; re-run affected tests; repeat until none remain.
4. `docs/CHANGELOG-vs-prototype.md`: every addition/removal vs the prototype and why.
5. Root `README.md`: replace "Getting started" with real per-app dev/test/build commands for this product (keep the company Workflow section as is).
6. Push `feature/<slug>` (permission prompt) and open a PR into `main` with `gh pr create`, filling every section of `.github/PULL_REQUEST_TEMPLATE.md`, linking the compliance matrix and NEEDS-HUMAN list. Do NOT merge.
7. Production deploy and Play submission only after the user explicitly confirms (and after the PR is merged by a code owner).

## Final report (print to user)
1. Product name, one-line pitch, web preview URL, APK preview link, PR link
2. Scope delivered: prototype REQs, parity features, differentiators
3. How it differs from existing products
4. Compliance summary (Play, Minimum Functionality, AdSense, AdMob, consent)
5. Security / firewall / tests / performance — pass counts and key numbers
6. NEEDS HUMAN list (release/NEEDS-HUMAN.md)
7. Risks and next improvements
