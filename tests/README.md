# Tests — everything test-related lives here, separate per platform

| Folder | What | Tool |
|---|---|---|
| `cases/` | `test-cases.xlsx` — functional (Web, Mobile, API, Payments) and `Non-functional` test cases, one row per step with its own expected result | Excel / LibreOffice |
| `bugs/` | `bug-log.xlsx` — every failure found on staging/dev, its status and fix PR | Excel / LibreOffice |
| `tools/` | `collect-results.mjs` merges every result file; `bug_log.py` updates the bug log and test-case statuses | Node / Python + openpyxl |
| `web/` | Web automation (`specs/`, `pages/` page objects, `playwright.config.ts`) | Playwright + TypeScript |
| `mobile/` | Android automation (`flows/*.yaml`) | Maestro |
| `testdata/` | `web/`, `mobile/`, `shared/` — every input value, file and fixture used by tests | JSON / CSV / files |
| `load/` | Performance and load: `npm run perf`, `npm run load -- <profile>` (see `load/README.md`) | Lighthouse CI + k6 |

Unit tests stay next to the code they test (`*.test.ts`).

## Staging run
`.github/workflows/staging-tests.yml` runs after every staging deploy: Playwright (functional + `@nfr` specs), Lighthouse CI, k6 smoke, OWASP ZAP baseline. Android runs in the same staging run (its `android` job calls `mobile.yml`: APK built against staging, Maestro on an emulator), so one report covers web and Android.

Before testing, `node tests/tools/preflight.mjs` lists every tool, repo setting and test asset needed, what is missing, and the free fix. Then `/test` logs failures in `bugs/bug-log.xlsx`, writes `docs/test-reports/…`, and asks which bugs to fix.

Naming: every test title or flow name starts with its TC ID (`TC-W-001 …`, `TC-N-004 … @nfr`) so results map to test cases and bugs.
Never put real customer data, live keys or production URLs in `testdata/`.
