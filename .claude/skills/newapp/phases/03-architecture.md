# Phase 3 — HLD & LLD (write before coding, update after Phase 4)

## docs/HLD.md
Purpose & scope · context diagram · monorepo component diagram (Mermaid: apps/web, apps/mobile, apps/api, packages, equisteg-platform) · data flow · third-party services (AdSense, AdMob, UMP, Cloudflare Web Analytics, Sentry if used, payment provider, Google Play Billing, Resend, D1/KV/R2) · deployment topology (one Cloudflare Worker serving apps/web static assets + apps/api, with dev/staging/production environments mapped to the development/staging/production branches; EAS Build → Play for apps/mobile) · environments & env vars · security architecture · scalability & caching · availability targets · free-tier limits used and headroom (must total ₹0/month) · cron jobs · payment/entitlement sequence · risks.

## docs/LLD.md
Workspace-by-workspace folder structure · every module/screen/component with responsibility · shared-types (zod schemas) and api-client contracts · D1 schema and migrations (including Phase 4b tables) · API contracts (route, method, auth, request, response, error codes) · state management per app · offline strategy (web service worker + IndexedDB; mobile SQLite/MMKV + NetInfo) · validation rules · sequence diagrams (Mermaid) for key flows · error handling & logging · Expo config plugins and native modules used, with permission justification.

## Export
Render Mermaid to PNG (`npx @mermaid-js/mermaid-cli`), then pandoc → `docs/HLD.docx`, `docs/LLD.docx` (title page, version, date, TOC). If pandoc is missing, use the `docx` npm package. Both .docx files are required deliverables; re-export them after every /change or /update that alters the design.
