# Phase 9 — User manual & submission pack

## User manual
`docs/User-Manual.md` + `.docx` (pandoc): introduction · access (web, PWA, Android install) · getting started · every feature with real screenshots · settings · privacy & data deletion · troubleshooting · FAQ · contact.

## release/ folder
- `listing/`: title, short & full description, screenshots (phone, 7", 10"), feature graphic, icon 512.
- `data-safety.md`: for every SDK (AdMob, UMP, analytics, Sentry if used, auth, DB): data type, collected/shared, purpose, optional/required, encrypted in transit, deletion available.
- `content-rating.md`, `target-audience.md` (18+ or 13+; if under-13s are included, apply Families policy and child-directed ad settings), `ads-declaration.md` (= contains ads), `app-access.md` (reviewer test login if needed).
- `app-ads.txt` for developerWebsite (AdMob); `apps/web/public/ads.txt` already done (AdSense).
- `NEEDS-HUMAN.md`, click-by-click, in this order:
  1. GitHub: review and approve the PR (code owner), squash-merge.
  2. Vercel (Pro team): project exists with Root Directory `apps/web`; add production env vars; confirm production deploy; add custom domain.
  3. AdSense: add site, confirm ads.txt, request review only after content pages are live.
  4. AdMob: create app + ad units; add IDs as EAS environment variables; rebuild.
  5. EAS: `eas build --profile production --platform android` → AAB (EAS holds the upload key).
  6. Play Console: create app; store listing; data safety; content rating; target audience; ads = yes; privacy policy URL; account-deletion URL; first AAB upload is manual in the console (after that `eas submit` with a Play service-account key works).
  7. Closed testing: web-search the current tester-count/duration rule for new personal developer accounts; run it; then apply for production.
  8. Search Console + Bing Webmaster verification and sitemap submission; host app-ads.txt on developerWebsite.
