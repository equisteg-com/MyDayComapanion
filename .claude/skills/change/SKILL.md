---
name: change
description: Changes existing behaviour, text, layout or a bug in an Equisteg product from a plain-English request, with tests, test cases, docs and a PR. Run only when the user types /change.
argument-hint: "<what to change, in plain English>"
---

# /change — modify something that already exists

Request: `$ARGUMENTS`

If the request is empty, reply with exactly one line: "Type /change followed by what should change, in plain English." and stop.
If the request adds something new rather than changing what exists, reply in one line suggesting `/update` instead and stop.
If the request is "fix incident #<n>", follow `prompts/INCIDENT_PROMPT.md` on the current branch (it starts with `agent:incident-investigator` before any code is changed).
Risks and issues you find are reported with how we tackle them, what was done now and the residual (`docs/standards/risks-and-issues.md`).

## Steps
1. **Understand.** Read `CLAUDE.md`, `PROGRESS.md`, `docs/product-brief.md`, `docs/LLD.md`. Find every file, screen, API route and test affected (web, mobile, api, packages). Write a 3–6 line brief: what changes, where, on which platforms, risks. If the request is ambiguous in a way that changes the result, ask ONE question with options and a recommendation; otherwise state the assumption and continue.
2. **Branch.** Work on the branch the user created (`git branch --show-current`); never create or switch branches. If it is `main`, `development`, `staging` or `production`, stop and ask the user to create theirs: `git switch development && git pull && git switch -c change/<name>`.
3. **Trace.** Give the change an ID `CHG-xxx` (next number in `CHANGELOG.md`) and link it to the REQ-xxx it modifies in `docs/product-brief.md`.
4. **Implement** on every affected platform so web and mobile stay consistent. Keep shared logic in `packages/`. Do not touch unrelated code.
5. **Tests.**
   - Update or add unit tests next to the code.
   - Update `tests/cases/test-cases.xlsx`: edit affected rows and add positive, negative and edge cases for the change, one row per step with its own expected result (format in `tests/cases/README.md`).
   - If the change can affect an NFR (page weight, speed, API latency, accessibility, security headers, consent/ads, offline), update or add rows in the `Non-functional` sheet and re-run that check (Lighthouse for changed pages, axe, k6 smoke for changed routes, security specs).
   - If the change fixes a logged bug, put its BUG ID in the commit and PR, add a regression test, and set it to `In PR` with `python tests/tools/bug_log.py set BUG-xxxx "Status=In PR" "Fix branch / PR=<url>"`.
   - Update automation in `tests/web/` (Playwright) and `tests/mobile/` (Maestro); test titles start with the TC ID; test data only in `tests/testdata/`.
   - Run `npm run lint && npm run typecheck && npm test`, then the affected Playwright specs. Fix until green.
6. **Security and cost check.** Run gitleaks and Semgrep (`npm run security` if present). Confirm no new paid dependency (zero-cost rule).
7. **Docs.** Update `docs/LLD.md` (and HLD if architecture moved), re-export `.docx`, add a `CHANGELOG.md` entry, update the user manual if users will notice.
8. **Specialist review.** Run `/review fix` steps 2–5 on this branch: `agent:code-reviewer` and `agent:scope-guard` always, the specialists the changed paths call for, in parallel. Fix S1–S3, re-run, at most three rounds (`docs/standards/agent-reviews.md`).
9. **Commit and PR.** Conventional Commit `fix(...)` or `refactor(...)`/`feat(...)` as fits, push the branch (permission prompt), open a PR into `development` filling every section of the template, including *Specialist review*. Do not merge.

## Report (print to user)
CHG-ID, what changed per platform, files touched, test results (counts, functional and non-functional), reviewer verdicts, test-case rows added/edited, bugs fixed, PR link, anything NEEDS HUMAN. Remind the user to run `/test` once this reaches staging.
