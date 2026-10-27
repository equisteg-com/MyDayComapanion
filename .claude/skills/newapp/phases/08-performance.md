# Phase 8 — Performance & load testing

- **Web:** Lighthouse CI (`@lhci/cli`) with budgets in `apps/web/lighthouserc.json`: mobile ≥ 90 in Performance, Accessibility, Best Practices, SEO on key pages; added to CI. Bundle analysis (`@next/bundle-analyzer`), < 200 KB gzipped JS per route where feasible, next/image (AVIF/WebP), next/font, cache headers.
- **Load (k6) — `tests/load/`:** smoke (1–5 VUs), load (expected peak), stress (2–3× peak), spike, soak (30 min) against the web route handlers and `apps/api` if present. Thresholds: p95 < 500 ms, error rate < 1%. Only against PREVIEW, at modest levels (Vercel fair use; don't trip our own firewall). If k6 isn't installed, use the grafana/k6 Docker image or a CI job.
- **Mobile:** production-profile build; cold start < 2 s on a mid-range emulator profile; no ANR; stable memory over a 10-minute Maestro loop; JS bundle size check; FlashList for long lists; images cached (expo-image).
- `docs/performance-report.md`: results, bottlenecks, fixes, before/after numbers.
