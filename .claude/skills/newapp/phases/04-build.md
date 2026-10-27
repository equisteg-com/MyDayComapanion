# Phase 4 — Build (zero cost: Cloudflare + Expo/EAS free tiers)

## Workspace setup
- Add Turborepo; root scripts `lint`, `typecheck`, `test`, `build`, `security` run across all workspaces (replace the placeholder echo scripts in root package.json).
- Shared ESLint/Prettier/TS config; TypeScript strict everywhere.
- Per-app `.env.example`; copies in `infra/env/`. Copy `infra/wrangler.example.jsonc` to `apps/api/wrangler.jsonc` and fill names.

## packages/
- `shared-types`: domain types + zod schemas (single source of validation for web, mobile, api).
- `api-client`: typed client (fetch) with error handling and retries; used by web and mobile.
- `design-tokens`: from Phase 2.

## apps/web — Next.js static export on Cloudflare
- `create-next-app` (App Router, TS, Tailwind, ESLint) inside `apps/web`; set `output: 'export'`, `images.unoptimized: true` (or a free loader), `trailingSlash: true`.
- Every page is pre-rendered at build time (SSG) so its content is in the raw HTML. Dynamic data comes from `apps/api` through `packages/api-client`.
- Implement every REQ and differentiator; port the prototype's look and interactions, then apply the design system.
- Every screen and page has finished loading (skeleton), empty, error and offline states; custom 404 and error pages (audit A5). No console errors or warnings in the browser (audit A6).
- PWA (manifest + service worker via Serwist or the current recommended plugin), offline page.
- Footer on every page: product name · "A product by [Equisteg](https://equisteg.com)" · © year Equisteg · links to Privacy, Terms, Contact. About page explains that Equisteg builds and runs the product.
- Required pages: Home · feature pages · Pricing (if paid) · Restore purchase (if paid) · About · Contact/Support (developerEmail) · Privacy Policy (data controller = Equisteg, `legalEntityCountry` from `publisher.config.json`; lists every SDK on web AND mobile and the payment provider) · Terms · Refund policy (if paid) · Disclaimer if relevant · Account/data deletion page (public URL) · FAQ/Help · 404 / error.
- Content: 15–30 original, genuinely useful pages (guides, how-tos, calculators/tools) around the brief keywords. Needed for AdSense approval, SEO and AI search. No filler; nothing duplicated from existingProducts.
- AdSense via `next/script` only after consent (Google-certified CMP) AND only when `/api/v1/access/me` says the visitor is on the free tier; `public/ads.txt` with adsensePublisherId; `AdSlot` reserves space. The product's own domain is its own AdSense site: `ads.txt` and `app-ads.txt` both live at the root of that domain (`apps/web/public/`).
- Served by the `apps/api` Worker through Workers Static Assets (`assets.directory = ../web/out`), so web and API share one origin and one free Worker. Production origin = the product's own domain (`CANONICAL_HOST`): canonical URLs, sitemap, OG URLs, CORS allow-list and the app's `EXPO_PUBLIC_API_URL` default all use `https://<domain>`; the Worker 301-redirects `www.<domain>` and any other host to it in production.

## apps/api — Cloudflare Worker (Hono + TypeScript)
- Hono app with `/api/v1/*` routes, zod validation from shared-types, secure headers middleware, CORS allow-list (web origin + mobile app scheme), per-IP rate limiting (WAF rule for bursts + a D1/KV counter per route), Turnstile on abusable forms, structured JSON logs, `GET /api/v1/health`.
- Bindings: D1 `DB`, KV `FLAGS`, R2 only if files must be stored server-side, `AI` only if an LLM feature is in scope (through the LLM gateway rules in HLD), service binding `PLATFORM` to `equisteg-platform` when it exists.
- Environments in `wrangler.jsonc`: `dev`, `staging`, `production`, each with its own D1, KV and secrets. Migrations in `apps/api/migrations/`.
- `scheduled()` handler for Cron Triggers (Phase 4b and `docs/standards/cron-jobs.md`).
- A FastAPI service is allowed only if Phase 0b chose Option D; record its free host and limits.

## apps/mobile — React Native + Expo
- `create-expo-app` (TypeScript, Expo Router) inside `apps/mobile`; web-search the current Expo SDK and use it.
- `app.config.ts`: name, slug, `android.package = <androidPackagePrefix>.<slug>`, versionCode 1, icons/adaptive icon/splash from Phase 2, minimal permissions (justify each in LLD), `android.blockedPermissions` for anything a library adds that we don't need.
- Implement the same REQs with native UI (not a WebView of the website).
- Minimum Functionality: works offline (local storage + NetInfo UI), ≥ 3 meaningful native features (e.g. expo-sharing, expo-notifications local reminders, expo-haptics, expo-file-system export, expo-local-authentication, home-screen quick actions), Android back behaviour correct on every screen, no dead links, no hand-off to the browser for core features.
- Ads: `react-native-google-mobile-ads` config plugin with AdMob app ID; UMP consent (`AdsConsent`) before loading ads; Google TestIds in development; production unit IDs from EAS env; banner + frequency-capped interstitial per Phase 2 plan; no ads for entitled users.
- Settings/About screen in the app: "A product by Equisteg" with a link to `https://equisteg.com`, app version, links to the Privacy Policy, Terms and the web data-deletion page, plus an in-app **Delete my data** action (clears local data and, if a purchase exists, calls the deletion endpoint of phase 4b) (audit B3, B4).
- Paid features: Phase 4b (Google Play Billing on Android).
- The app reads its API base URL from `EXPO_PUBLIC_API_URL` (public, not a secret; set per environment at build time — CI builds test APKs against dev or staging with it), defaulting to the production URL.
- `eas.json` with `development`, `preview` (APK) and `production` (AAB) profiles. EAS manages the upload keystore (no key files in repo). EAS Free allows a limited number of builds per month at low priority — build `preview` only from `staging`, `production` only for a release; otherwise build locally with `npx expo run:android` / Gradle (free).
- Set `targetSdkVersion` to the current Play requirement (web-search it; configure via expo-build-properties if the SDK default is lower).

## Preview
- Push the user's current branch (permission prompt) and open the PR into `development`. When it merges, `deploy.yml` deploys the `dev` environment to `<slug>-dev.<account>.workers.dev`.
  If the Cloudflare secrets are missing in GitHub → NEEDS HUMAN: "GitHub repo → Settings → Secrets and variables → Actions → add CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID".
- Mobile preview: `eas build --profile preview --platform android` (permission prompt) → installable APK link. If `eas` isn't logged in → NEEDS HUMAN: `npx eas login` then `npx eas init`.
- If mode is `review`: show preview URL + APK link + REQ checklist and wait.

## Proven practice (from the first product — follow unless the brief says otherwise)
- **Reference code:** `.claude/skills/newapp/templates/reference/` holds working implementations (security middleware, zod config, and the files later phases need). Copy and adapt them; read its `README.md` first.
- **Keep zod and other heavy code out of the first load.** Give `packages/*` extra entry points (`exports` subpaths in `package.json`) that contain only constants, types and formatters and import nothing heavy (e.g. `@<slug>/shared-types/flags`, `/display`). The app shell (layout, header, footer, consent, toasts) imports only those. Create the API client lazily (on first call), and keep a small store (e.g. toasts) in its own module so the shell does not pull the whole state tree.
- **Islands on content pages.** Pricing, guides and other mostly-static pages stay server-rendered; only the interactive part (buy button, calculator) is a client component. Heavy on-device work (WASM, models) starts after first paint or on first interaction, never at module load.
- **Service worker:** precache with one request per URL and `Promise.allSettled`, then verify the core URLs are cached — `cache.addAll` fails the whole install if any URL redirects (e.g. `404.html` with `trailingSlash`). Phase 7 has a test that the worker reaches `activated`.
- **320 px:** visually-hidden (`sr-only`) elements inside a scrollable box widen it; give the box `position: relative`. Check every page at 320 px before phase 7.
- **Worker + static assets:** `run_worker_first: ["/api/*"]` (already in `infra/wrangler.example.jsonc`). Pages are served without running the Worker, so page headers live in `apps/web/public/_headers` and `www → apex` is a zone Redirect Rule, not Worker code.
- **API hygiene from day one:** `hono/body-limit` 64 KB on `/api/*`, the CSRF guard from the reference `security.ts`, user-supplied URLs accepted only when `https:`, zod `jitless` (reference `zod-config.ts`).
- **Mobile:** every interactive element gets a `testID` through one helper (e.g. `tid('pay-button')`) so Maestro flows never select by text; `android.allowBackup: false` and cleartext traffic off in `app.config.ts`.
- **Local secrets:** `apps/api/.dev.vars` (git-ignored) for local values; never in `wrangler.jsonc`.
- **Lock file:** after adding or upgrading any dependency run a clean `npm ci` (delete `node_modules` first, or run it in a fresh clone). If it reports "Missing: <pkg> from lock file", a transitive dependency pins a different version — add a scoped `overrides` entry in the root `package.json` and re-run. CI uses `npm ci`, so a lock that only works with `npm install` breaks every PR.
- **Windows:** stop `wrangler dev` before `npm run build` (it holds `apps/web/out` open → `EBUSY`).
