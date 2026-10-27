# Phase 9 — User manual & submission pack

## User manual
`docs/User-Manual.md` + `.docx` (pandoc): introduction · access (web, PWA, Android install) · getting started · every feature with real screenshots · settings · privacy & data deletion · troubleshooting · FAQ · contact.

## release/ folder
- `listing/`: title, short & full description, screenshots (phone, 7", 10"), feature graphic, icon 512.
- `data-safety.md`: for every SDK (AdMob, UMP, analytics, Sentry if used, auth, DB): data type, collected/shared, purpose, optional/required, encrypted in transit, deletion available.
- `content-rating.md`, `target-audience.md` (18+ or 13+; if under-13s are included, apply Families policy and child-directed ad settings), `ads-declaration.md` (= contains ads), `app-access.md` (reviewer test login if needed).
- `apps/web/public/app-ads.txt` (AdMob) and `apps/web/public/ads.txt` (AdSense) at the root of the product's own domain; the Play listing's website is `https://<domain>`.
- `NEEDS-HUMAN.md`, click-by-click, in this order:
  1. GitHub: review the PR into `development`, squash-merge; then open and merge `development → staging`, wait for the staging tests, type `/test` and fix what it reports until the release recommendation is *Ready*, then `staging → production`.
  2. Cloudflare: production secrets set (`wrangler secret put … --env production`); product domain bought (phase 2) and its zone active; custom domains `<domain>` and `www.<domain>` attached under Workers → Settings → Domains & Routes; Email Routing `support@<domain>` → founders' inbox (free); `PRODUCTION_URL` repo variable = `https://<domain>`; payment provider: add `https://<domain>` as an approved website and set the production webhook URL (check the provider's current website-approval rules); AI crawler settings per Phase 5; WAF rules per Phase 6; payment provider switched from test to live keys.
  3. AdSense: each product domain is a separate site and is reviewed on its own. AdSense → Sites → Add site → `<domain>` → confirm `https://<domain>/ads.txt` → request review only after the content pages, About, Contact and Privacy are live.
  4. AdMob: create app + ad units; add IDs as EAS environment variables; rebuild.
  4b. Self-healing setup (phase 9b §4): labels, `OPS_TOKEN`, `AUTOFIX_TOKEN`, `CLAUDE_CODE_OAUTH_TOKEN`, `OPS_BOT_LOGIN`, Worker `GITHUB_ISSUES_TOKEN`, uptime monitor entry.
  5. EAS: `eas build --profile production --platform android` → AAB (EAS holds the upload key).
  6. Play Console: create app; store listing; data safety; content rating; target audience; ads = yes; privacy policy URL; account-deletion URL; first AAB upload is manual in the console (after that `eas submit` with a Play service-account key works).
  6b. Real-device smoke (audit H5): install the production build on a real Android phone — open offline, check consent, ads (test device), purchase with a licence tester, restore, Delete my data, back button; record Pass/Fail in the test-case sheet.
  7. Closed testing: web-search the current tester-count/duration rule for new personal developer accounts; run it; then apply for production.
  7b. Company records: confirm the domain sits in the Equisteg Cloudflare account with auto-renew on; add the product (name, domain, Play URL, renewal date, category) to `publisher.config.json` `existingProducts` and to the products page on `equisteg.com`; Play listing developer name = Equisteg.
  8. Search Console (Domain property via DNS TXT) + Bing Webmaster verification and sitemap submission; confirm `https://<domain>/app-ads.txt` loads (AdMob checks it via the Play listing website).
