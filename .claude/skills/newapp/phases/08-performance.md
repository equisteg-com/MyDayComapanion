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
