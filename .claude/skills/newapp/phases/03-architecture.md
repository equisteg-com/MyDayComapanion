# Phase 3 — HLD & LLD (write before coding, update after Phase 4)

## docs/HLD.md
Purpose & scope · context diagram · monorepo component diagram (Mermaid: apps/web, apps/mobile, apps/api?, packages) · data flow · third-party services (AdSense, AdMob, UMP, analytics, Sentry if used, auth, DB) · deployment topology (Vercel project for apps/web with preview/prod; EAS Build → Play for apps/mobile; api host if any) · environments & env vars · security architecture · scalability & caching · availability targets · monthly cost estimate · risks.

## docs/LLD.md
Workspace-by-workspace folder structure · every module/screen/component with responsibility · shared-types (zod schemas) and api-client contracts · DB schema (if any) · API contracts (route, method, auth, request, response, error codes) · state management per app · offline strategy (web service worker + IndexedDB; mobile SQLite/MMKV + NetInfo) · validation rules · sequence diagrams (Mermaid) for key flows · error handling & logging · Expo config plugins and native modules used, with permission justification.

## Export
Render Mermaid to PNG (`npx @mermaid-js/mermaid-cli`), then pandoc → `docs/HLD.docx`, `docs/LLD.docx` (title page, version, date, TOC). If pandoc is missing, use the `docx` npm package.
