# Phase 7 — Test cases & automation (web and mobile separate)

## Test-case document
`docs/test-cases.md` + `docs/test-cases.xlsx` (exceljs). Columns:
ID · Module · Platform (Web/Android/Both) · Type (Positive/Negative/Edge) · Preconditions · Steps · Test data · Expected result · Priority · Automated (Y/N + file).
- Every REQ: ≥ 1 positive, ≥ 1 negative, ≥ 1 edge.
- Always include: navigation, forms/validation, auth + account deletion, offline/slow network, orientation change, Android back button, deep links, empty/very long/unicode/emoji/RTL input, session expiry, ad load failure (layout intact), consent accept/reject, dark mode, accessibility, 320px & wide screens, app kill/resume, low storage, permission denied.

## Unit tests
Vitest for packages and web logic; Jest (jest-expo) + React Native Testing Library for mobile components. ≥ 80% coverage of `packages/*`.

## Web automation — `apps/web/tests/e2e/`
Playwright + TypeScript, Page Object Model. Projects: Chromium, Firefox, WebKit, Pixel 7, iPhone 14. axe-core accessibility checks on every page, visual snapshots of key screens, offline tests. Base URL = Vercel preview URL in CI.

## Mobile automation — `apps/mobile/.maestro/`
Maestro flows (YAML) with `testID`s on all interactive elements. Cover launch, onboarding, each REQ, offline, native features, back button, consent accept/reject, ads with TestIds, account deletion, kill/resume. Use `takeScreenshot` to produce Play screenshots too.
If no local emulator (`adb` missing), still write the flows and run them in CI.

## CI (extend, don't replace, the existing `.github/workflows/ci.yml`)
- `ci.yml` (PRs to main): install → lint → typecheck → unit tests (all workspaces via turbo) → web build → Playwright against the Vercel preview.
- `mobile.yml` (PRs touching apps/mobile, and manual): build a preview APK (EAS or local Gradle) → run Maestro on an Android emulator (reactivecircus/android-emulator-runner) or EAS Workflows Maestro job.
- Run each suite 3 times; fix flaky tests before marking done.
