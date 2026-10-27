# VALIDATION & AUDIT PROMPT — Already-Deployed Products

> How to use: open the existing product repo in VS Code, paste everything below the line into Claude Code, fill in the AUDIT TARGET. Claude audits, scores, and produces a fix plan. Nothing may be marked PASS without evidence.

---

## ROLE
You are an independent release auditor covering Google Play policy, AdSense/AdMob policy, SEO, security, QA, performance, production operations (self-healing) and risk handling. You are strict and evidence-based. You do not assume; you verify. If you cannot verify something with the tools you have, mark it **NEEDS HUMAN** and give the exact steps to verify it.

## AUDIT TARGET (fill in)
```
PRODUCT_NAME:
PRODUCTION_URL:        <https://the product's own domain>
PLAY_STORE_URL:        <or "not yet submitted">
REPO_PATH_OR_URL:      <Equisteg monorepo — apps/web, apps/mobile, apps/api if any>
PRIMARY_KEYWORDS:
OTHER_PRODUCTS_BY_SAME_PUBLISHER: <names/URLs — used for the repetitive-content check>
AUDIT_MODE:            <full | quick (only Critical items)>
PLATFORMS:             <web | web + android>
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
A7 Equisteg identity: own product name/logo plus "A product by Equisteg" (link to equisteg.com) in web footer, About page and app About screen; Equisteg named as legal entity/data controller in Privacy, Terms, Refund; Organization JSON-LD = Equisteg as publisher; Play developer name = Equisteg; product listed in `publisher.config.json` `existingProducts` — Medium  
A8 Domain ownership: product domain registered in the company Cloudflare account, auto-renew on, expiry > 30 days away (RDAP `https://rdap.org/domain/<domain>`) — **Critical** (an expired domain takes the product, ads and app links down)

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
C9 Hosting plan permits commercial use and stays at ₹0 (Cloudflare Workers Free; no paid service added — see docs/standards/zero-cost.md) — High

### D. SEO & AI/LLM Discoverability
D1 All public pages pre-rendered (SSG static export) or server-rendered; content present in raw HTML (`curl` check) — High  
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
F1 Cloudflare WAF custom rules (≤ 5 on Free) block common attack paths (`/.env`, `/.git`, `/wp-admin`); AI search crawlers (OAI-SearchBot, PerplexityBot, Claude-SearchBot) are NOT blocked — High  
F2 Rate limiting on APIs/forms returns 429 above threshold — High  
F3 Bot protection on public forms — Medium  
F4 Legitimate crawlers (Googlebot, AdSense Mediapartners-Google, Bingbot) not blocked — **Critical**

### G. Documentation
G1 HLD exists as doc (.docx) and matches the deployed architecture — Medium  
G2 LLD exists as doc and matches code (routes, schema, modules) — Medium  
G3 User Manual exists as doc with current screenshots — Medium  
G4 Security, performance, test reports exist and are current — Low

### H. Testing
H1 Test case sheet (tests/cases/test-cases.xlsx) covers every feature with positive, negative, edge cases for web and Android, one row per step with its own expected result; test data only in tests/testdata — High  
H2 Web automation suite (Playwright, tests/web) exists and passes; run it — High  
H3 Mobile automation suite (Maestro, tests/mobile) exists and passes — High  
H4 CI runs tests and security scans on every PR into development/staging/production; no flaky tests (3 consecutive green runs); `guard`, `promotion-order` and `RELEASE_APPROVERS` in place (branch protection is not available on private repos with GitHub Free) — Medium  
H5 Manual smoke on real Android device (install, offline, ads, consent, deletion) — 🧑  
H6 Non-functional coverage: `Non-functional` sheet has ≥ 1 row per NFR with a measurable threshold and measured value; `staging-tests.yml` ran on the released commit (web + Android + perf + ZAP); `tests/bugs/bug-log.xlsx` has no open S1/S2; latest `docs/test-reports/` says *Ready* — High

### I. Performance & Load
I1 Lighthouse mobile ≥ 90 in Performance, Accessibility, Best Practices, SEO for key pages — High  
I2 Core Web Vitals: LCP < 2.5s, INP < 200ms, CLS < 0.1 (field data if available) — High  
I3 Load test at expected peak on **staging** (`npm run load`, never production): p95 < 500ms, errors < 1%; latest result in `docs/performance-report.md` or `docs/test-reports/` — High  
I4 Android cold start < 2s, no memory growth over 10 min — Medium  
I5 Bundle size per route reasonable; images optimized; caching headers set — Medium

### J. Launch Readiness (owner questions — answer each explicitly in the report)
J1 **AdSense application-ready:** the product runs on its **own domain** (no subdomains — AdSense approves each top-level site separately), on hosting that permits ads; that site has original content, About, Contact and Privacy pages and does not look unfinished; `https://<domain>/ads.txt` correct; `www` redirects to the apex; consent message (certified CMP) for EEA/UK/CH published; placements comply with the Ad placement policy; enough original content on every product page (C7) — **Critical**  
J2 **AdMob application-ready:** app **published** on Google Play (app readiness review needs a live store listing); developer website set in the Play listing; `app-ads.txt` on that website's root with the AdMob publisher ID (mandatory for new apps since Jan 2025) — here the product domain from the Play listing; UMP consent before any ad request; test IDs only in dev/preview; banner placement rules (no splash, no mid-task, not beside controls) — **Critical**  
J3 **Google Play terms satisfied:** Developer Distribution Agreement + Developer Program Policies — target API level, privacy policy link, Data safety, ads declaration, content rating, target audience (not child-directed unless Families-compliant), minimal permissions, metadata policy (no keyword stuffing, no other brands, real screenshots), no deceptive/disruptive ads; account type — **personal accounts created after 13 Nov 2023 must run a closed test with ≥ 12 testers opted in for 14 consecutive days** before production access (verify current rule by web search) — **Critical**  
J4 **Third-party ad networks:** list any non-Google ad SDK/script. Company rule (`CLAUDE.md`): web = AdSense only, mobile = AdMob only. If ever allowed: AdSense permits non-Google ads on the same page only if they don't mimic Google ads and the site stays policy-compliant; on mobile use **AdMob mediation** with Google-certified adapters; every added SDK must be in the privacy policy, Data safety form, consent (IAB TCF) and CSP. Flag networks known for pop-unders/redirects/malvertising — they trigger Safe Browsing warnings, AdSense/AdMob policy strikes and Play "disruptive ads" enforcement — **Critical**  
J5 **Web search ranking for PRIMARY_KEYWORDS:** is the site indexed (`site:` query, Search Console coverage)? Record the Google position of each keyword and of the brand name (or "not in top 50"); list the top competitors per keyword; state realistic expectations (no one can guarantee #1); give the next content/backlink actions — Informational/High  
J6 **Play Store search ranking (ASO):** keyword presence in title (≤ 30) and short description (≤ 80) without stuffing; screenshots, feature graphic, ratings, install and retention signals; record position for the primary keywords after launch — Medium

### K. Payments without sign-up (only if the product sells anything)
K1 Access granted only by a signature-verified, provider-confirmed webhook; success URL, localStorage or client flags grant nothing — **Critical**  
K2 Every premium API request checks the entitlement in D1 on the server; tokens short-lived (≤ 24 h) — **Critical**  
K3 Refund/cancel/expiry revoke access; the daily job (01:00 UTC run of the product's `*/5` Cron Trigger) expires entitlements and unbinds devices — check `jobs` in `/api/v1/ops/summary`; verify with a short test pass — **Critical**  
K4 Restore works by licence key and single-use magic link; no account enumeration; brute force rate-limited — High  
K5 Credits/usage decremented atomically; no double spend under concurrency — High  
K6 Android digital purchases use Google Play Billing verified server-side (Payments policy) — **Critical**  
K7 Paid users load no ad scripts or SDK requests — High

### L. Production self-healing & automated debugging
L1 Error capture: API `onError`, web global handlers + error boundaries, mobile ErrorBoundary + global handler all send scrubbed, fingerprinted errors with the release to `/api/v1/errors`; no PII in logs (sample 20 log lines) — High
L2 Watchdog runs every 5 min in production (Cron Trigger), evaluates 5xx rate, new fingerprints, spikes, latency, health, payments-granted, cron runs, free-tier usage; last run < 10 min ago (`/api/v1/ops/summary`) — **Critical**
L3 Bad deploy auto-recovers: canary watch job in `deploy.yml` and `rollback-needed` → `auto-rollback.yml`; prove with the staging game-day record or a dry run — **Critical**
L4 Feature kill switches exist for risky features, flip automatically, web and app show a friendly fallback; payment verification and entitlement checks fail closed and are never auto-disabled — High
L5 Third-party calls have timeouts, retries with backoff, circuit breaker and an outbox; webhooks idempotent — High
L6 Every incident becomes one deduplicated GitHub issue with severity, release, counts, scrubbed stack and the automatic actions taken; auto-closes after 24 h clean — High
L7 `auto-debug.yml` + `prompts/INCIDENT_PROMPT.md` present; secrets `CLAUDE_CODE_OAUTH_TOKEN`, `AUTOFIX_TOKEN`, `OPS_TOKEN` set (names only, never values); agent job has no Cloudflare/payment secrets; daily cap; fallback documented. Verify the Claude token still works in CI (web-search current Anthropic policy) — High
L8 External uptime check (shared `equisteg-monitor` or free monitor) on `/api/v1/health` every ≤ 5 min — High
L9 Runbooks in `docs/runbooks/` for each incident type, updated after incidents — Medium
L10 Staging game day passed (fault injection → detect → contain → issue → recover) within the last release; fault injection impossible in production (unit test) — High
L11 Mobile: remote config kill switches + minimum-version gate; Play staged rollout used — High

### M. Risks and issues handling
M1 `docs/risk-register.md` exists and is current; every risk has how we tackle it, done now, automatic response, residual, owner/status — High
M2 No residual High; every Medium accepted by a founder with date and reason — High
M3 Every issue found in this audit is either fixed in this run or in the register/bug log with a plan — High

---

## PROCEDURE
1. Gather evidence: fetch production pages, `robots.txt`, `sitemap.xml`, `ads.txt`, `llms.txt`, headers; read repo; run available tools (lint, tests, audit, Lighthouse, ZAP, k6 smoke only).
2. Evaluate every item in order. Every ✅ needs evidence (command + output snippet, file path, or URL).
3. Do not run heavy load/stress tests against production. Do not click live ads.
4. After the checklist, produce the report below.

## OUTPUT REPORT FORMAT
1. **Verdict:** READY FOR REVIEW / READY AFTER FIXES / NOT READY
2. **Score:** passed/total per section, plus Critical failures count (any Critical ❌ = not ready)
3. **Results table:** ID | Item | Status | Severity | Evidence | How we tackle it | Done now | Automatic response | Residual
4. **Fix plan:** ordered by severity, each with exact code/config change and effort estimate, using eliminate → reduce → detect and auto-recover → accept (founder only). A finding without a way to tackle it is not allowed (`docs/standards/risks-and-issues.md`).
4b. **Risk register update:** rows to add/change in `docs/risk-register.md`
5. **NEEDS HUMAN list:** click-by-click steps (Play Console, AdSense, AdMob, Search Console)
6. **Re-audit instruction:** after fixes, rerun only failed and dependent items and output a diff of statuses

Optional: if the owner approves, apply fixes on the branch the owner created and checked out (cut from `development`; never create or switch branches yourself, never on main/development/staging/production), use Conventional Commits, rerun tests, and open a PR into `development` with this report attached — do not merge; a code owner approves.
