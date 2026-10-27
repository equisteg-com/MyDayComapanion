# Phase 8 — Performance & load testing

The process is already built: `npm run perf` and `npm run load -- <profile>` (see `tests/load/README.md`). This phase only fills in the product's pages and routes, tunes the code, and records the numbers.

1. **Configure** (nothing else to write):
   - `tests/load/lighthouserc.json` → `ci.collect.url`: the key pages as paths (home, pricing, the main tool page, one content page).
   - `tests/testdata/shared/load.json` → `routes`: the same pages plus every API route a user hits (GET and POST with a valid test body, expected status); keep `p95Ms: 500`, `maxErrorRate: 0.01` unless the brief's NFRs say otherwise.
2. **Run on dev** after deploying: `npm run perf -- dev`, then `npm run load -- dev`. Fix and re-run until both pass:
   - Web: Lighthouse mobile ≥ 90 in Performance, Accessibility, Best Practices, SEO on every key page. Bundle analysis (`@next/bundle-analyzer`), < 200 KB gzipped JS per route where feasible, next/image (AVIF/WebP), next/font, cache headers.
   - API: p95 < 500 ms, errors < 1% (D1 indexes, KV caching, no N+1 queries).
   - Stress and spike only once, on staging, before the first production release: `npm run load -- stress`, `npm run load -- spike`. Soak only if the brief asks for it (`--yes`). Never against production; keep within the Workers free 100,000 requests/day.
3. **Mobile:** production-profile build; cold start < 2 s on a mid-range emulator profile; no ANR; stable memory over a 10-minute Maestro loop; JS bundle size check; FlashList for long lists; images cached (expo-image). Record as a Maestro flow where possible.
4. **Record:** every budget is a row in the `Non-functional` sheet of `tests/cases/test-cases.xlsx` (Expected = threshold, Measured value = latest number, Automation ref = `lighthouse`, `tests/load/load.js :: <profile>`, or the Maestro flow). `docs/performance-report.md`: results, bottlenecks, fixes, before/after numbers. Misses that can't be fixed now go to `tests/bugs/bug-log.xlsx` via `python tests/tools/bug_log.py ingest <results>/summary.json`.

After every staging deploy `npm run perf` runs automatically in `staging-tests.yml`; `/test` logs any misses.

## Proven practice (from the first product)
- **Measure the real thing locally first:** build the static export and serve it through the Worker (`npx wrangler dev --env dev` in `apps/api`), then `npm run perf -- --url http://localhost:8787`. `next dev` numbers are meaningless.
- **Lighthouse config** (`tests/load/lighthouserc.json`): 3 runs, median asserted; `assertMatrix` lets one heavy tool page carry its own budget (`categories:performance` as `warn`) while the pages the brief names stay `error`. SEO is asserted audit by audit because dev/staging are noindex.
- **Where the first-load weight came from** (fix in this order): a validation library in the shell bundle → zod-free entry points (phase 4); the whole state store imported by the layout → split small stores; the API client created at import → lazy; on-device models warmed at load → after first paint / on first use; client components on content pages → islands.
- **k6:** `tests/testdata/shared/load.json` routes list `expect: [200, 429]` for API routes — the test runs from one IP, so the per-IP rate limit answering 429 is the protection working, not an error. Page routes expect 200 only.
- **Portable k6** (no installer, no admin rights): unzip the release binary anywhere and put it on `PATH`; CI installs it itself.
- **Mobile:** `tests/tools/mobile-perf.sh` (runs in `mobile.yml` on the CI emulator) records cold start and APK size; it reads the app id from `apps/mobile/app.config.ts`.
- **A budget that cannot be met now** is logged in `tests/bugs/bug-log.xlsx` with its severity and the measured number, and in `docs/risk-register.md` with how it is tackled — never silently lowered.
