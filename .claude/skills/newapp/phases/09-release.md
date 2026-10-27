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

## Proven practice (from the first product)
- **Screenshots:** web — a small Playwright script saves the manual's screenshots from the deployed dev URL; Android — a `store-screenshots.yaml` Maestro flow (`takeScreenshot` per screen) run on the CI emulator by `mobile.yml`; make sure the screenshot files are inside the uploaded results folder. Until the first CI run exists, the listing folder says which files are pending (NEEDS HUMAN: download the artifact into `release/listing/`).
- **`NEEDS-HUMAN.md` is the production checklist.** Group it by console (GitHub → Cloudflare → payment provider → AdSense → AdMob → EAS → Play → Search Console/Bing), each step click by click with the exact value to paste and how to confirm it worked. Add a "before promoting to production" block: staging tests green, `/test` recommendation *Ready*, no S1/S2 open, founder decisions from the risk register taken.
- **Phase-wise launch:** production starts with the smallest safe feature set (ads and anything awaiting an approval off); each later phase is switched on by changing release flags in KV (give the product one small script for it, e.g. `npm run release -- <env> enable|disable <feature>`, that only writes the flag), not by a deploy. List the phases and the trigger for each (e.g. "AdSense approved → enable ads") in `NEEDS-HUMAN.md`.
- **Ads placement note for reviewers** (`ads-declaration.md`): state where ads appear *and where they never do* (never on the task flow, download/print/payment steps, beside buttons or navigation).
