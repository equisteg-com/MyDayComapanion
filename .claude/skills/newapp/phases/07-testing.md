# Phase 7 — Test cases & automation (web and mobile separate)

All test assets live under `tests/`; nothing test-related goes inside `apps/` except unit tests next to the code.

```
tests/
├── cases/       test-cases.xlsx (+ README.md with the column rules)
├── bugs/        bug-log.xlsx — every failure found on staging/dev (filled by /test)
├── tools/       collect-results.mjs (merges all results) + bug_log.py (bug log + test-case status)
├── web/         Playwright + TypeScript (Page Object Model): specs/, pages/, playwright.config.ts
├── mobile/      Maestro flows (YAML): flows/, config.yaml
├── testdata/    web/, mobile/, shared/ — every input value, file, fixture and expected output
└── load/        npm run perf / npm run load (Lighthouse config + one k6 script with 5 profiles)
```

## 1. Test-case sheet — `tests/cases/test-cases.xlsx`
Start from the template already in `tests/cases/test-cases.xlsx`. **One row per step**, so every step has its own expected result. Columns:
TC ID · Module · REQ ID · Platform (Web/Android/Both) · Type (Positive/Negative/Edge) · Title · Preconditions · Test data (ref to `tests/testdata/...`) · Step No · Test step · Expected result (for this step) · Priority (P1–P3) · Automated (Y/N) · Automation ref (file :: test name) · Status · Remarks.
- The first row of a test case carries TC ID, Module, REQ, Platform, Type, Title, Preconditions, Test data; following step rows repeat the TC ID and fill Step No, Test step, Expected result.
- Steps are concrete user actions ("Enter `abc@` in Email and tap Pay"), expected results are observable ("Error 'Enter a valid email' shows below Email; Pay stays disabled").
- Every REQ: ≥ 1 positive, ≥ 1 negative, ≥ 1 edge case. Separate sheets: `Web`, `Mobile`, `API`, `Payments`, `Non-functional`, `Summary` (counts per type and platform, formulas).
- **End to end:** besides per-screen cases, write journey cases that cross the whole product — first visit → consent → use free feature → hit the paid limit → pay → premium works → access expires → back to free; same on Android incl. restore on a second install. One TC per journey, one row per step.
- **Non-functional sheet** (`TC-N-xxx`, columns: Category, NFR ID, Environment, Measured value): ≥ 1 row per NFR in `docs/product-brief.md` with a measurable threshold as the expected result. Always cover: performance (Lighthouse ≥ 90 per key page, mobile cold start < 2 s), load (k6 p95 < 500 ms, errors < 1%), security (ZAP 0 High/Medium, headers, rate limit → 429, WAF blocks), accessibility (axe 0 serious/critical, keyboard only, screen-reader labels, 4.5:1 contrast, 200% zoom), compatibility (Chromium/Firefox/WebKit, Pixel 7/iPhone 14 viewports, Android API levels min…target), reliability (offline, kill during payment, retry after 5xx, cron expiry), compliance (consent before ads/analytics, paid user loads no ad scripts, data deletion), privacy (no PII in logs/URLs), SEO (robots, sitemap, JSON-LD valid), localization (RTL/long text).
- Always include: navigation, forms/validation, payments and restore (Phase 4b list), offline/slow network, orientation change, Android back button, deep links, empty/very long/unicode/emoji/RTL input, token expiry, ad load failure (layout intact), consent accept/reject, paid user sees no ads, dark mode, accessibility, 320px & wide screens, app kill/resume, low storage, permission denied.
- Also export `docs/test-cases.md` (generated from the sheet) for PR review.
- **Traceability for automation:** every Playwright test title and Maestro flow name starts with its TC ID (`test('TC-W-001 buys a pack', …)`, `flows/TC-M-001-offline.yaml`); non-functional Playwright specs also carry `@nfr`. The Automation ref column points at the exact file :: test. This is how failures land on the right test case and bug.

## 2. Unit tests
Vitest for packages, web logic and the Worker (`@cloudflare/vitest-pool-workers`); Jest (jest-expo) + React Native Testing Library for mobile components. ≥ 80% coverage of `packages/*` and `apps/api/src/payments/*`.

## 3. Web automation — `tests/web/`
Playwright + TypeScript, Page Object Model. Projects: Chromium, Firefox, WebKit, Pixel 7, iPhone 14 (CI runs Chromium + Pixel 7 only, to save free minutes; the full matrix runs before a production release). axe-core accessibility checks on every page (`@axe-core/playwright`, `specs/a11y.spec.ts`, tagged `@nfr`), a shared fixture that fails any test when the page logs a console error or warning (audit A6), `specs/links.spec.ts` that opens every URL in the sitemap and checks every internal link and button target answers 200 (audit B2), visual snapshots of key screens, offline tests, security-header and rate-limit checks. Data from `tests/testdata/web/`. `BASE_URL` = the dev or staging Workers URL.

## 4. Mobile automation — `tests/mobile/`
Maestro flows (YAML) with `testID`s on all interactive elements. Cover launch, onboarding, each REQ, offline, native features, back button, consent accept/reject, ads with TestIds, purchase with Play Billing test cards, restore, kill/resume. Data from `tests/testdata/mobile/`. Use `takeScreenshot` to produce Play screenshots too.
If no local emulator (`adb` missing), still write the flows and run them in CI.

## 5. CI (already in `.github/workflows/`; extend, don't replace)
- `ci.yml` (PRs into development/staging/production): install → lint → typecheck → unit tests → build → security scans.
- `staging-tests.yml` (called by `deploy.yml` after every staging deploy; also manual for dev/staging, never production): Android via `mobile.yml` (APK against the environment's API + Maestro), Playwright functional + `@nfr` specs, `npm run perf` (Lighthouse + k6 smoke), OWASP ZAP baseline → merged report (artifact `staging-test-report`, job summary) → a `test-failure` issue when anything fails.
- `mobile.yml` (PRs touching apps/mobile or tests/mobile into staging, called by `staging-tests.yml`, and manual): build a release-variant APK with Gradle (JS bundled, no Metro needed) pointed at the environment via `EXPO_PUBLIC_API_URL` → run Maestro on an Android emulator (reactivecircus/android-emulator-runner) → JUnit results (artifact `results-mobile`).
- Run each suite 3 times; fix flaky tests before marking done.

## 6. Staging run, bug log and report
After the first staging deploy (and after every later one) the founder runs `/test` (`.claude/skills/test/SKILL.md`): it collects the staging results, re-runs failures to separate flaky from real, logs each failure in `tests/bugs/bug-log.xlsx` (severity, steps, expected/actual, evidence, TC/REQ), writes Pass/Fail into the test-case sheet, writes `docs/test-reports/<date>-staging-<run>.md` with a release recommendation, then **asks which bugs to fix** and fixes the chosen ones with regression tests and a PR. During `/newapp`, check the pipeline with `node tests/tools/collect-results.mjs` and `python tests/tools/bug_log.py ingest` on a local run against the dev URL.
