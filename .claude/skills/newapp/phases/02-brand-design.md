# Phase 2 — Brand, logo, icons, design tokens

## Name
- Generate 5 original names (short, pronounceable, ≤ 20 chars). Web-search each + "app" and check Google Play. Drop conflicts and anything similar to `existingProducts`. Pick one, record why.
- Set the product slug; set root `package.json` "name" to the slug. Keep working on the user's current branch (do not rename it).

## Logo (single source of truth)
- Original SVG mark `brand/logo.svg` (simple, legible at 16px, no text in the mark) + `brand/logo-full.svg` (mark + wordmark).
- `scripts/generate-icons.mjs` (uses `sharp`) produces, from that one SVG:
  - **Web (apps/web):** `app/favicon.ico` (16/32/48), `app/icon.svg`, `app/apple-icon.png` (180), PWA manifest icons 192/512 + maskable 512, default OG image 1200×630.
  - **Mobile (apps/mobile/assets):** `icon.png` 1024×1024, `adaptive-icon-foreground.png` + background color, `adaptive-icon-monochrome.png` (Android themed icons), `splash-icon.png`, `notification-icon.png` (white on transparent).
  - **Play Store (release/listing):** icon 512×512, feature graphic 1024×500.
- The same mark appears in the browser address bar, PWA, Android launcher and Play listing.

## Design tokens — `packages/design-tokens`
- Export colors (light + dark, WCAG AA), spacing, radii, font sizes, shadows as TS objects.
- Web consumes them in the Tailwind config; mobile consumes them in a theme provider (or NativeWind).
- Base palette from the prototype, refined for contrast.

## Component set (both platforms)
Button, input, card, dialog/sheet, toast, tabs, skeleton, empty state, error state, bottom tabs (mobile), top nav (web), `AdSlot` (web) / `BannerAd` wrapper (mobile).
Quality bar: modern and consistent, 48dp touch targets, visible focus (web), screen-reader labels, reduced-motion support, 320–1920px responsive web, safe-area aware mobile.

## Ad placement plan
- Web: responsive in-content AdSense units between content blocks, desktop sidebar, optional mobile-web anchor.
- Mobile: adaptive anchored banner at the bottom above the tab bar (never overlapping it), interstitial only at natural breaks with a ≥ 3 min cap, rewarded only when the user opts in.
- Every slot reserves space; on ad failure the space collapses cleanly.
