---
name: update
description: Adds a new feature to an existing Equisteg product from a plain-English request — requirement, design, build on web and mobile, tests, test cases, docs and a PR. Run only when the user types /update.
argument-hint: "<the new feature, in plain English>"
---

# /update — add a new feature

Request: `$ARGUMENTS`

If the request is empty, reply with exactly one line: "Type /update followed by the new feature, in plain English." and stop.
If the request only modifies existing behaviour, reply in one line suggesting `/change` instead and stop.
New features follow phase 9b too: error capture, a kill switch if the feature can fail at runtime (`autoDisable` unless it is payment/entitlement), runbook line, risk-register rows (`docs/standards/risks-and-issues.md`).

## Steps
1. **Brief.** Read `CLAUDE.md`, `PROGRESS.md`, `docs/product-brief.md`, `docs/HLD.md`, `docs/LLD.md`. Write a short feature brief: user problem, behaviour on web and mobile, free vs paid (does it sit behind the entitlement check?), data stored, new API routes, SEO impact. Ask ONE question with options and a recommendation only if the answer changes what gets built; otherwise record the assumption.
2. **Zero-cost and policy check.** Any new service, SDK or API must be free (see `docs/standards/zero-cost.md`) and allowed by Play/AdSense policy. If not, STOP and report the cost and a free alternative.
3. **Branch.** Work on the branch the user created (`git branch --show-current`); never create or switch branches. If it is `main`, `development`, `staging` or `production`, stop and ask the user to create theirs: `git switch development && git pull && git switch -c update/<name>`.
4. **Requirements.** Add `REQ-xxx` rows (next numbers) to `docs/product-brief.md` with source "update: <date>", and `NFR-xxx` rows if the feature brings its own performance, load, security, accessibility or compliance needs.
5. **Design.** Update `docs/HLD.md` and `docs/LLD.md` (components, API contracts, DB migrations, sequence diagram) before coding; re-export `.docx`.
6. **Build** on web, mobile and api as the brief says. Shared types and validation in `packages/shared-types`; D1 schema changes as a new migration in `apps/api/migrations/`, backward compatible for one release. Premium features call the platform entitlement check on the server.
7. **Tests.**
   - Unit tests for new logic (≥ 80% of new package code).
   - `tests/cases/test-cases.xlsx`: positive, negative and edge cases for every new REQ, one row per step with its own expected result.
   - An end-to-end journey case that uses the feature from entry point to result (and through payment, if premium).
   - `Non-functional` sheet rows for the feature: Lighthouse budget for new pages, k6 smoke for new API routes, axe on new screens, security cases for new routes (authz 403, validation 400, rate limit 429), offline behaviour.
   - Playwright specs in `tests/web/` (titles start with the TC ID; non-functional ones tagged `@nfr`), Maestro flows in `tests/mobile/`, k6 scripts in `tests/load/`, data in `tests/testdata/`.
   - Run lint, typecheck, unit tests, affected E2E suites and the affected non-functional checks until green.
8. **SEO / AI search** (web features with public pages): metadata, JSON-LD, sitemap entry, `llms.txt` line.
9. **Security.** gitleaks + Semgrep clean; authz and input validation on every new route.
10. **Specialist review.** Run `/review fix` steps 2–5: `agent:code-reviewer` and `agent:scope-guard` (judged against the new REQs), `agent:test-traceability-auditor`, and the specialists the changed paths call for (security, payments for anything premium, UX/accessibility for new screens, SEO for new public pages, store policy for new SDKs or permissions). Fix S1–S3, at most three rounds (`docs/standards/agent-reviews.md`).
11. **Docs and PR.** `CHANGELOG.md` entry `UPD-xxx`, user manual section, push the branch (permission prompt), PR into `development` with the *Specialist review* section filled. Do not merge.

## Report (print to user)
UPD-ID, REQs/NFRs added, what was built per platform, test results (functional and non-functional), reviewer verdicts, test-case rows added, PR link, NEEDS HUMAN items. Remind the user to run `/test` once this reaches staging.
