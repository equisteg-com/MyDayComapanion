# Phase 4 — Build

## Workspace setup
- Add Turborepo; root scripts `lint`, `typecheck`, `test`, `build` run across all workspaces (replace the placeholder echo scripts in root package.json).
- Shared ESLint/Prettier/TS config; TypeScript strict everywhere.
- Per-app `.env.example`; copies in `infra/env/`.

## packages/
- `shared-types`: domain types + zod schemas (single source of validation for web, mobile, api).
- `api-client`: typed client (fetch) with error handling and retries; used by web and mobile.
- `design-tokens`: from Phase 2.

## apps/web — Next.js on Vercel
- `create-next-app` (App Router, TS, Tailwind, ESLint) inside `apps/web`.
- Implement every REQ and differentiator; port the prototype's look and interactions, then apply the design system.
- PWA (manifest + service worker via Serwist or current recommended plugin), offline page.
- Required pages: Home · feature pages · About · Contact/Support (developerEmail) · Privacy Policy (lists every SDK on web AND mobile) · Terms · Disclaimer if relevant · Account deletion page (public URL, required by Play if accounts exist) · FAQ/Help · 404 / error.
- Content: 15–30 original, genuinely useful pages (guides, how-tos, calculators/tools) around the brief keywords. Needed for AdSense approval and SEO. No filler; nothing duplicated from existingProducts.
- AdSense via `next/script` only after consent (Google-certified CMP); `public/ads.txt` with adsensePublisherId; `AdSlot` reserves space.
- Server logic (if any) in route handlers under `app/api/`.

## apps/mobile — React Native + Expo
- `create-expo-app` (TypeScript, Expo Router) inside `apps/mobile`; web-search the current Expo SDK and use it.
- `app.config.ts`: name, slug, `android.package = <androidPackagePrefix>.<slug>`, versionCode 1, icons/adaptive icon/splash from Phase 2, minimal permissions (justify each in LLD), `android.blockedPermissions` for anything a library adds that we don't need.
- Implement the same REQs with native UI (not a WebView of the website).
- Minimum Functionality: works offline (local storage + NetInfo UI), ≥ 3 meaningful native features (e.g. expo-sharing, expo-notifications local reminders, expo-haptics, expo-file-system export, expo-local-authentication, home-screen quick actions), Android back behaviour correct on every screen, no dead links, no hand-off to the browser for core features.
- Ads: `react-native-google-mobile-ads` config plugin with AdMob app ID; UMP consent (`AdsConsent`) before loading ads; Google TestIds in development; production unit IDs from EAS env; banner + frequency-capped interstitial per Phase 2 plan.
- `eas.json` with `development`, `preview` (APK) and `production` (AAB) profiles. EAS manages the upload keystore (no key files in repo).
- Set `targetSdkVersion` to the current Play requirement (web-search it; configure via expo-build-properties if the SDK default is lower).

## apps/api (only if Phase 0 decided it's needed)
Express (or NestJS) + TypeScript, zod validation from shared-types, helmet, CORS allow-list, rate limiting, structured logging, health endpoint. Deploy target per `publisher.config.json` hosting.api; Dockerfile in `infra/` only if not on Vercel.

## Preview
- Push `feature/<slug>` (permission prompt) → Vercel creates a preview for apps/web.
  If the Vercel project doesn't exist → NEEDS HUMAN: "Vercel (Pro team) → Add New → Project → import repo → Root Directory = apps/web → Deploy".
- Mobile preview: `eas build --profile preview --platform android` (permission prompt) → installable APK link. If `eas` isn't logged in → NEEDS HUMAN: `npx eas login` then `npx eas init`.
- If mode is `review`: show preview URL + APK link + REQ checklist and wait.
