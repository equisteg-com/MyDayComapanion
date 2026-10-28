# Tests — everything test-related lives here, separate per platform

| Folder | What | Tool |
|---|---|---|
| `cases/` | `test-cases.xlsx` — functional (Web, Mobile, API, Payments) and `Non-functional` test cases, one row per step with its own expected result | Excel / LibreOffice |
| `bugs/` | `bug-log.xlsx` — every failure found on staging/dev, its status and fix PR | Excel / LibreOffice |
| `tools/` | `collect-results.mjs` merges every result file; `bug_log.py` updates the bug log and test-case statuses; `export_cases.py` writes `docs/test-cases.md` from the sheet and checks that sheet and automation agree; `mobile-perf.sh` records Android cold start and APK size on the CI emulator; `lint-agents.mjs` checks the reviewer agents, skills and Claude settings (`npm run lint:agents`) | Node / Python + openpyxl / bash |
| `web/` | Web automation (`specs/`, `pages/` page objects, `playwright.config.ts`, `fixtures.ts` — import `test`/`expect` from it: fails a test on any console error or warning) | Playwright + TypeScript |
| `mobile/` | Android automation (`flows/*.yaml`, `config.yaml`; run with `npm run test:mobile`) | Maestro |
| `testdata/` | `web/`, `mobile/`, `shared/` — every input value, file and fixture used by tests | JSON / CSV / files |
| `load/` | Performance and load: `npm run perf`, `npm run load -- <profile>` (see `load/README.md`) | Lighthouse CI + k6 |

Unit tests stay next to the code they test (`*.test.ts`).

## Staging run
`.github/workflows/staging-tests.yml` runs after every staging deploy: Playwright (functional + `@nfr` specs), Lighthouse CI, k6 smoke, OWASP ZAP baseline. Android runs in the same staging run (its `android` job calls `mobile.yml`: APK built against staging, Maestro on an emulator), so one report covers web and Android.

Before testing, `node tests/tools/preflight.mjs` lists every tool, repo setting and test asset needed, what is missing, and the free fix. Then `/test` logs failures in `bugs/bug-log.xlsx`, writes `docs/test-reports/…`, and asks which bugs to fix.

Naming: every test title or flow name starts with its TC ID (`TC-W-001 …`, `TC-N-004 … @nfr`) so results map to test cases and bugs.
Never put real customer data, live keys or production URLs in `testdata/`.

## MyDayCompanion (native Android)

Module tests live next to the code (`*/src/test`). This folder holds what is shared or not code:

| Folder | Contents |
|---|---|
| `testdata/golden/` | Golden AI evaluation datasets with thresholds (run by `domain` `GoldenEvaluationTest`). |
| `cases/` | `test-cases.xlsx` (template sheet; Android rows are `TC-M-1xx` and `TC-N-1xx`) plus manual device cases `TC-M-<n>-*.md`. |
| `bugs/` | `bug-log.xlsx` (template), filled by `/test`. |
| `security/` | Security test plan and manual penetration checklists. |
| `ai-evals/` | Evaluation reports kept for comparison across model/prompt versions. |
| `device/` | Device-matrix results. |
| `tools/` | Test helper scripts. |
