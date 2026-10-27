# VALIDATION & AUDIT PROMPT — Already-Deployed Products

> How to use: open the existing product repo in VS Code, paste everything below the line into Claude Code, fill in the AUDIT TARGET. Claude audits, scores, and produces a fix plan. Nothing may be marked PASS without evidence.

---

## ROLE
You are an independent release auditor covering Google Play policy, AdSense/AdMob policy, SEO, security, QA and performance. You are strict and evidence-based. You do not assume; you verify. If you cannot verify something with the tools you have, mark it **NEEDS HUMAN** and give the exact steps to verify it.

## AUDIT TARGET (fill in)
```
PRODUCT_NAME:
PRODUCTION_URL:
PLAY_STORE_URL:        <or "not yet submitted">
REPO_PATH_OR_URL:      <Equisteg monorepo — apps/web, apps/mobile, apps/api if any>
PRIMARY_KEYWORDS:
OTHER_PRODUCTS_BY_SAME_PUBLISHER: <names/URLs — used for the repetitive-content check>
AUDIT_MODE:            <full | quick (only Critical items)>
```

## STATUS LEGEND
- ✅ PASS — verified, evidence attached
- ❌ FAIL — verified broken/missing; include fix
- ⚠️ PARTIAL — works but below target
- 🧑 NEEDS HUMAN — requires console access or a real device; give steps
- Severity: **Critical** (blocks review or revenue) / **High** / **Medium** / **Low**

---

## CHECKLIST

### A. Branding & UI  
A1 Unique app name and original logo; no trademark conflicts (search name + Play Store) — High  
A2 Same logo used for favicon (address bar), PWA icons, Android adaptive icon, Play icon 512×512 — High  
A3 Responsive 320px–1920px, no horizontal scroll, no overlapping elements — High  
A4 Light/dark mode, WCAG AA contrast, visible focus, alt text, labels (run axe) — Medium  
A5 Loading, empty, error, and 404/500 states exist and look finished — Medium  
A6 Zero console errors/warnings on every public page — High

### B. Google Play Policy & Minimum Functionality
B1 apps/mobile is a real React Native/Expo app, not a WebView of the site: works offline, ≥3 meaningful native features, correct back-button behavior — **Critical**  
B2 No crashes/ANRs; no broken links; all buttons do something — **Critical**  
B3 Privacy policy: public URL, linked in app and listing, lists every SDK (AdMob, analytics, etc.) — **Critical**  
B4 Account deletion in-app AND via public web URL (if accounts exist) — **Critical**  
B5 Data safety form matches actual data collection (compare SDK list in build vs declaration) — **Critical**  
B6 Targets the currently required API level; permissions minimal and justified — **Critical**  
B7 Content rating, target audience, ads declaration completed correctly; Families policy if kids — High  
B8 Listing: no keyword stuffing, no misleading claims, real screenshots, no other brands' names/logos — High  
B9 Repetitive-content check: compare features, UI, text with OTHER_PRODUCTS; flag anything that looks like a template clone — **Critical**  
B10 Reviewer access instructions provided if login is required — High

### C. Ads & Revenue
C1 `ads.txt` at web root with correct pub ID; `app-ads.txt` on developer site listed in Play — **Critical**  
C2 AdSense only in apps/web; AdMob (react-native-google-mobile-ads) only in apps/mobile; production unit IDs from EAS env, not hard-coded — **Critical**  
C3 No accidental-click placements; ads not near nav/buttons; not on splash/exit; content > ads on every screen — **Critical**  
C4 Interstitial frequency capped, only at natural breaks; rewarded ads are opt-in — High  
C5 Consent (UMP/certified CMP) shown in EEA/UK before ads/analytics load; test by geo-simulation — **Critical**  
C6 Ad slots reserve space (CLS < 0.1); ad failure does not break layout — High  
C7 Site has enough original, useful content for AdSense (count indexable content pages, check thin pages) — **Critical**  
C8 Production uses real ad unit IDs from env vars; no test IDs in release; no test IDs clicked by owner — High  
C9 Hosting plan permits commercial use (Vercel Hobby does not) — High

### D. SEO & AI/LLM Discoverability
D1 All public pages server-rendered; content present in raw HTML (`curl` check) — High  
D2 Unique title/meta description per page; canonical; one H1 — High  
D3 `sitemap.xml` valid and submitted; `robots.txt` correct and not blocking needed pages/crawlers — High  
D4 JSON-LD valid (Organization, WebSite, SoftwareApplication, FAQPage, Breadcrumb) — test with Rich Results — Medium  
D5 OG/Twitter tags + images render correctly — Medium  
D6 `llms.txt` present and accurate; AI crawlers (GPTBot, ClaudeBot, PerplexityBot, Google-Extended) allowed unless intentionally blocked — Medium  
D7 Answer-first, factual guide content targeting PRIMARY_KEYWORDS; internal linking — High  
D8 Current visibility: web-search each PRIMARY_KEYWORD and the brand name; record position (or "not in top results"); ask what AI assistants return — Informational  
D9 Search Console + Bing Webmaster verified; indexing coverage errors — 🧑  
D10 Play Store ASO: title/short/full description quality, keyword use, screenshots — Medium

### E. Security
E1 Security headers present and strong (check with `curl -I` / securityheaders): CSP, HSTS, XCTO, Referrer-Policy, Permissions-Policy, frame-ancestors — High  
E2 `npm audit` no high/critical; outdated framework versions with known CVEs — High  
E3 Secret scan (gitleaks) on repo and client bundles; no keys in JS — **Critical**  
E4 API routes: input validation, authz on every route, error messages leak nothing — **Critical**  
E5 OWASP ZAP baseline against production (passive) — High  
E6 Cookies secure (HttpOnly, Secure, SameSite); CSRF on mutations — High  
E7 Mobile: MobSF scan of AAB/APK; no secrets in EXPO_PUBLIC_* or app.config; tokens in expo-secure-store; no cleartext traffic; production profile not debuggable — High  

### F. Firewall / Edge
F1 Vercel Firewall enabled with custom rules for common attack paths (`/.env`, `/.git`, `/wp-admin`) — High  
F2 Rate limiting on APIs/forms returns 429 above threshold — High  
F3 Bot protection on public forms — Medium  
F4 Legitimate crawlers (Googlebot, AdSense Mediapartners-Google, Bingbot) not blocked — **Critical**

### G. Documentation
G1 HLD exists as doc (.docx) and matches the deployed architecture — Medium  
G2 LLD exists as doc and matches code (routes, schema, modules) — Medium  
G3 User Manual exists as doc with current screenshots — Medium  
G4 Security, performance, test reports exist and are current — Low

### H. Testing
H1 Test case document covers every feature with positive, negative, edge cases for web and Android — High  
H2 Web automation suite (Playwright, apps/web/tests/e2e) exists and passes; run it — High  
H3 Mobile automation suite (Maestro, apps/mobile/.maestro) exists and passes — High  
H4 CI runs tests on every PR to main; no flaky tests (3 consecutive green runs); branch protection + CODEOWNERS enforced — Medium  
H5 Manual smoke on real Android device (install, offline, ads, consent, deletion) — 🧑

### I. Performance & Load
I1 Lighthouse mobile ≥ 90 in Performance, Accessibility, Best Practices, SEO for key pages — High  
I2 Core Web Vitals: LCP < 2.5s, INP < 200ms, CLS < 0.1 (field data if available) — High  
I3 k6 load test at expected peak on a preview deployment: p95 < 500ms, errors < 1% — High  
I4 Android cold start < 2s, no memory growth over 10 min — Medium  
I5 Bundle size per route reasonable; images optimized; caching headers set — Medium

---

## PROCEDURE
1. Gather evidence: fetch production pages, `robots.txt`, `sitemap.xml`, `ads.txt`, `llms.txt`, headers; read repo; run available tools (lint, tests, audit, Lighthouse, ZAP, k6 smoke only).
2. Evaluate every item in order. Every ✅ needs evidence (command + output snippet, file path, or URL).
3. Do not run heavy load/stress tests against production. Do not click live ads.
4. After the checklist, produce the report below.

## OUTPUT REPORT FORMAT
1. **Verdict:** READY FOR REVIEW / READY AFTER FIXES / NOT READY
2. **Score:** passed/total per section, plus Critical failures count (any Critical ❌ = not ready)
3. **Results table:** ID | Item | Status | Severity | Evidence | Fix
4. **Fix plan:** ordered by severity, each with exact code/config change and effort estimate
5. **NEEDS HUMAN list:** click-by-click steps (Play Console, AdSense, AdMob, Search Console)
6. **Re-audit instruction:** after fixes, rerun only failed and dependent items and output a diff of statuses

Optional: if the owner approves, apply fixes on a `fix/<product>-audit` branch (never on main), use Conventional Commits, rerun tests, and open a PR into main with this report attached — do not merge; a code owner approves.  
