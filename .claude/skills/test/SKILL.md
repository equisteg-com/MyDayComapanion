---
name: test
description: Checks prerequisites, then runs the full functional and non-functional automation (web and Android) against staging (or dev), logs every failure as a bug in tests/bugs/bug-log.xlsx, writes a test report, then asks the user which bugs to fix and fixes the chosen ones with tests and a PR. Run only when the user types /test.
argument-hint: "[staging|dev] [fresh] [load] [retest BUG-xxxx]"
---

# /test — test a deployed environment (web + Android), log bugs, ask what to fix

Arguments: `$ARGUMENTS`

- Environment = `dev` if the arguments say dev, otherwise `staging`. **Never production** — if asked, refuse in one line (load and security scans must not hit production).
- `fresh` = start a new CI run even if one exists for the current commit.
- `load` = also run the bigger load profiles with `npm run load -- load|stress|spike [dev]` (soak only if the user asks; it needs `--yes`). Staging or dev only — see `tests/load/README.md` for what each profile does and its request cost.
- `retest BUG-xxxx` = only re-run the tests linked to those bugs and update their status.

## 0. Preflight — show what is missing and get the details
1. Read `CLAUDE.md`, `PROGRESS.md`, `tests/README.md`, `tests/bugs/README.md`. Check `git branch --show-current`: everything this command writes (bug log, sheets, report, fixes) is committed on that branch, the one the user created. If it is `main`, `development`, `staging` or `production`, testing and reporting may still run, but before writing anything stop and ask the user to create their branch (`git switch development && git pull && git switch -c fix/<name>`). Never create or switch branches yourself; only `git fetch` other branches to read them.
2. Run `node tests/tools/preflight.mjs --env <env>` (and `--json` for yourself). Show the user the table **as is**: every tool, repo setting and test asset, OK / MISSING / optional, what it is needed for, and the free fix.
3. Decide the route from `blocking`:
   - **CI route** (preferred; web and Android both run on GitHub, nothing to install except `gh` + Python/openpyxl) when `blocking.ci` is empty.
   - **Local route** for whatever CI cannot give (no `gh`, `retest`, or the user asks): web needs `blocking.localWeb` empty, Android needs `blocking.localAndroid` empty.
4. If anything blocks the chosen route, ask ONE AskUserQuestion with options such as:
   - "Install the missing tools for me (Recommended)" — then run only the free install commands from the table (`winget`/`brew`/`pip`/`npx playwright install`), each behind its permission prompt; Android Studio, emulator creation, starting Docker and `gh auth login` are click-by-click **NEEDS HUMAN** steps the user does, then re-run preflight;
   - "Use CI only" — skip local runs; Android, k6 and ZAP run on GitHub;
   - "Skip Android this time" / "Skip load and security scans this time" — record them under *Not run* in the report;
   - "Stop — I'll set things up first".
5. Ask for the **details only a person knows** (the `details` list), in one AskUserQuestion where possible: the environment URL if `STAGING_URL`/`DEV_URL` is unset (then `gh variable set STAGING_URL --body <url>` with permission), that payments are in TEST mode and which public test card/UPI id to use, the Play licence-tester account on the device, AdSense/AdMob test IDs, and any test fixture data. Save non-secret answers in `tests/testdata/shared/environment.json` (`{ "env", "baseUrl", "paymentTestCard", "playLicenceTester", "notes" }`). **Never ask for or accept secrets or real customer data in chat** — for tokens give the `gh secret set ...` / `npx wrangler secret put ...` command for the user to run.
6. Missing test assets (no web specs, no Maestro flows, no `lighthouserc.json`, no k6 script) are not blockers: say what will be skipped, and offer to write the missing automation from `tests/cases/test-cases.xlsx` first.
7. Run `python tests/tools/bug_log.py init`.

## 1. Get results
2. Find the deployed commit: `git fetch origin <env-branch>` and `git rev-parse origin/<env-branch>` (`staging` or `development`).
3. **CI first (preferred, free minutes, real environment):**
   - `gh run list --workflow staging-tests.yml --branch <env-branch> --limit 5 --json databaseId,headSha,status,conclusion`
   - If no completed run exists for that commit, or `fresh` was asked: `gh workflow run staging-tests.yml -f environment=<env> --ref <env-branch>`, then `gh run watch <id> --exit-status` (use Monitor; don't poll in a loop).
   - `gh run download <id> -D test-results/ci-<id>` (all `results-*` artifacts incl. `results-mobile`, + `staging-test-report`).
   - The run covers **web and Android together**: its `android` job builds the APK against this environment's API (`EXPO_PUBLIC_API_URL`) and runs every Maestro flow on an emulator (~30 min). To repeat only Android: `gh workflow run mobile.yml -f environment=<env> --ref <env-branch>` and download its `results-mobile`.
4. **Local fallback** (no `gh`, no CI, or `retest`): with `BASE_URL=<env URL>` run into `test-results/local-<timestamp>/`:
   - `npx playwright test -c tests/web/playwright.config.ts --project=chromium --project=pixel7` (add `-g "TC-W-001|TC-N-004"` for retest)
   - Android (when `blocking.localAndroid` is empty): start the emulator if none is running (`emulator -avd <name>`); in `apps/mobile` build against the environment — `EXPO_PUBLIC_API_URL=<env URL> npx expo prebuild --platform android --no-install`, then `cd android && ./gradlew assembleRelease`; `adb install -r android/app/build/outputs/apk/release/app-release.apk`; then `maestro test tests/mobile --format junit --output <dir>/maestro.xml --debug-output <dir>/maestro-debug -e API_URL=<env URL>`. Otherwise use the CI route for Android, or record it under *Not run*.
   - Real-device-only checks (Play Billing purchase with the licence tester, AdMob test ads, UMP consent from an EEA location) → **NEEDS HUMAN** with click-by-click steps; record the user's result as Pass/Fail in the sheet.
   - Performance and load: `npm run perf -- <env> --out <dir>` (Lighthouse + k6 smoke; with `load`: `npm run load -- <profile> <env> --out <dir>`). ZAP baseline as in `.github/workflows/staging-tests.yml` (Docker; if Docker is missing, use the CI run instead).
5. Merge: `node tests/tools/collect-results.mjs <dir> --env <env> --base-url <url> --commit <sha> --run-id <id> --run-url <url>` → `<dir>/summary.json` + `summary.md`.

## 2. Triage before logging
For every failure:
- **Re-run it once** (single spec/flow). Fails again → real. Passes → `flaky` (still logged, S4, so the test gets fixed).
- Decide the cause from the error, trace, screenshot and code: **product bug**, **test bug** (selector, timing, wrong data), or **environment** (staging down, quota, third-party sandbox). Test and environment problems are still logged, with Status `Not a bug` and the reason in Remarks — never hidden.
- If a failure has no TC ID (`unmappedFailures`), find or add its test-case row first, so every bug traces to a TC and a REQ/NFR.

## 3. Log
1. `python tests/tools/bug_log.py ingest <dir>/summary.json --report docs/test-reports/<YYYY-MM-DD>-<env>-<run-id>.md`
   - adds a **Runs** row; one **Bugs** row per failure signature (same failure on several browsers/devices = one bug); repeats update *Last seen / Occurrences*; failures of Fixed/Verified bugs → **Reopened**; Fixed bugs whose tests now pass on staging → **Verified**;
   - writes Pass / Fail / Flaky into the Status column of `tests/cases/test-cases.xlsx`.
2. Improve each new row with `python tests/tools/bug_log.py set BUG-xxxx "Title=..." "Actual result=..." "Severity=S2"`: a plain-English title ("Pay button stays disabled after a valid card"), real reproduction steps, observed vs expected, severity per the Read me sheet (S1 payments/security/data loss, S2 main feature broken, S3 partial/workaround, S4 cosmetic/flaky), evidence paths (trace, screenshot, run URL). Never paste secrets, tokens or customer data.
3. Write the report `docs/test-reports/<YYYY-MM-DD>-<env>-<run-id>.md`:
   - header: environment, URL, commit, CI run link, date, tester = automation + /test;
   - **Summary** table per suite (functional web, functional Android, API, payments, performance, load, security, accessibility, …): total / pass / fail / flaky / skipped;
   - **Non-functional numbers** against their thresholds (Lighthouse scores per page, k6 p95 and error rate, ZAP alert counts, axe violations, cold start) and the change since the previous report;
   - **Bugs**: new, reopened, still open, verified — ID, severity, title, platform, TC;
   - **Environment and prerequisites**: the preflight table (what was available, what was skipped and why);
   - **Not run / NEEDS HUMAN** (e.g. no Android emulator, real-device checks);
   - **Risks and issues**: for every open bug and risk — how we tackle it, what was done now, the automatic response in production (rollback / kill switch / retry / none), residual (`docs/standards/risks-and-issues.md`); update `docs/risk-register.md`;
   - **Release recommendation**: *Ready for production* only if no open S1/S2, every P1 test case passed and no residual High risk; otherwise *Not ready* with the blockers and how each will be tackled. Before writing it, brief `agent:release-gate` (commit, run link, report path) and use its verdict; if you disagree with it, show both and the reason.
   - After ingest, run `agent:test-traceability-auditor` on the results folder: every failure must map to a TC and a bug row.

## 4. Ask what to fix
Print the report summary and the open-bug table (ID · Severity · Title · Platform), then ask with **AskUserQuestion** (one question; multiSelect when there are ≤ 4 open bugs, listing each bug as an option, the S1/S2 ones first and marked "(Recommended)"). With more than 4 open bugs, offer:
- "Fix all S1 and S2 (Recommended)" — list their IDs in the description
- "Fix all open bugs"
- "Only log them — fix nothing now"
- the user can type specific IDs in *Other* (e.g. `BUG-0004, BUG-0007`) or "defer BUG-0005".

If there are no failures, say so, give the release recommendation and stop — don't ask.

Record the answer: chosen bugs → `Decision=Fix now`, `Status=Open`; bugs the user defers → `Decision=Defer`, `Status=Deferred`; the rest stay `New` for next time.

## 5. Fix the chosen bugs
- Fix on the user's current branch. If an S1 bug should ship on its own, say so and suggest the user create a separate branch for it; don't create it.
- For each bug, in severity order: `Status=In progress` → **first make the failing test reproduce it** (Playwright/Maestro/unit/k6/axe, data in `tests/testdata/`) → fix the cause on every affected platform (web, mobile, api, packages) → test passes locally → add or adjust test-case rows (one row per step, positive/negative/edge or NFR threshold) → `Fixed in=<commit sha>`.
- Then follow `/change` steps 5–9 (including the specialist review): lint, typecheck, unit tests, affected E2E, gitleaks + Semgrep, docs (`CHANGELOG.md` entries `FIX-xxx — BUG-xxxx`), Conventional Commit `fix(<scope>): <summary> (BUG-xxxx)`, PR into `development` listing every BUG ID. Set `Status=In PR`, `Fix branch / PR=<url>`. Never merge.
- Status becomes **Fixed** when a founder merges the PR, and **Verified** only when the next `/test` on staging passes the linked tests (the ingest step does this automatically). A bug is closed (`Status=Closed`) only after Verified.

## 6. Commit the log even when nothing is fixed
`tests/bugs/bug-log.xlsx`, `tests/cases/test-cases.xlsx` and the report go in the same PR as the fixes, or — when the user chose "only log" — on the same current branch with a `test: <env> run <id> report` commit and a PR into `development`.

## Report (print to user)
Run ID and link, pass/fail per suite, non-functional numbers vs thresholds, bugs new/reopened/open/verified, what was fixed (BUG → commit), PR link, release recommendation, NEEDS HUMAN items.
