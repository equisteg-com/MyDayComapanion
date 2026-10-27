# Phase 2 — Brand, logo, icons, design tokens

## Name
- Generate 5 original names (short, pronounceable, ≤ 20 chars). Web-search each + "app" and check Google Play. Drop conflicts and anything similar to `existingProducts`.
- **Domain:** each product gets its own domain. For every remaining name check availability of `.com` first, then a fitting alternative (`.app`, `.io`, `.in`) with the free RDAP lookup (`curl -s https://rdap.org/domain/<name>.com` — 404 means not registered). Prefer a name whose `.com` is free; no hyphens or digits; never a domain resembling another brand. Pick the name, record name + domain + why in `docs/brand.md` and `PROGRESS.md`.
- Buying the domain is **NEEDS HUMAN** (it costs money), always in the **company (Equisteg) Cloudflare account**, registrant organisation Equisteg, auto-renew on: Cloudflare dashboard → Domain Registration → Register Domains → search the domain → buy (at-cost price) → it becomes a Cloudflare zone automatically. Until it is bought, everything uses the `workers.dev` URLs.
- Set the product slug; set root `package.json` "name" to the slug. Keep working on the user's current branch (do not rename it).

## Company endorsement
The product keeps its own name and logo; Equisteg appears as the maker, never inside the product logo: "A product by Equisteg" line (small wordmark or text) for the web footer, About page and the app's About/Settings screen. Keep it consistent across products.

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
