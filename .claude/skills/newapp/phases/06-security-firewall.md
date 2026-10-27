# Phase 6 — Security review & firewall

## Security — `docs/security-report.md` (finding · severity · fix · status · evidence)
- Walk OWASP Top 10, OWASP ASVS Level 1, and OWASP MASVS (mobile) item by item.
- **Web:** headers in the Worker's secure-headers middleware and `apps/web/public/_headers` — strict CSP (only required AdSense/consent/analytics domains; inline scripts allowed by per-page SHA-256 hashes — a static export cannot use nonces, see Proven practice below), HSTS preload, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, frame-ancestors. zod validation client + server, authz on every route handler, secure cookies (HttpOnly/Secure/SameSite), CSRF on mutations, generic errors.
- **Mobile:** tokens only in `expo-secure-store`; no secrets in `EXPO_PUBLIC_*` or app.config; HTTPS only (no cleartext); deep links validated; `allowBackup` reviewed; no debug flags in production profile; logs stripped in production.
- **API (if present):** helmet, CORS allow-list, rate limits, input validation, authz, no stack traces in responses, dependency pinning.
- Run and fix until clean:
  - `npm audit --omit=dev` (all workspaces) → 0 high/critical
  - Semgrep SAST
  - gitleaks on repo and built web bundles
  - OWASP ZAP baseline on the staging URL (Docker or GitHub Action)
  - MobSF static scan on the preview APK (Docker). If Docker is unavailable, add a CI job and mark the result NEEDS HUMAN.

## Firewall / edge — `docs/firewall-report.md`
- Cloudflare WAF custom rules on the product's own zone (Free plan allows 5 per zone — combine paths into one expression): block `/.env*`, `/.git*`, `/wp-admin*`, `/wp-login*`, `/phpmyadmin*`, `/xmlrpc.php`; document Under Attack Mode for incidents.
- Rate limiting on API routes/forms (Cloudflare rate-limiting rule where the plan allows it, plus a D1/KV counter in the Worker) → 429.
- Bot protection on abusable forms (Cloudflare Turnstile).
- Verify with curl: blocked paths → 403/404; rate limit → 429 after threshold; Googlebot, Mediapartners-Google and Bingbot user agents → 200.
- Rules that must be created in the Cloudflare dashboard → NEEDS HUMAN with exact rule expressions.
- Payments: verify every Phase 4b security test passes; webhook secrets only in `wrangler secret`.

## Security test cases
Add `Security` rows to the `Non-functional` sheet of `tests/cases/test-cases.xlsx` for: ZAP baseline (0 High/Medium, Automation ref `zap-baseline`), security headers present, blocked paths → 403/404, rate limit → 429, CORS rejects unknown origins, webhook with bad signature → 401, premium API without entitlement → 403, no secrets in built bundles. Automate them as Playwright `@nfr` request specs or unit tests; ZAP runs after every staging deploy in `staging-tests.yml`. Findings that can't be fixed in this phase are logged in `tests/bugs/bug-log.xlsx` (S1 for High).

## Proven practice (from the first product)
- **CSP on a static export uses hashes, not nonces** (there is no server render to mint a nonce). Site-wide header in `apps/web/public/_headers`; then `apps/web/scripts/csp-hashes.mjs` (reference folder) runs after `next build` and writes a per-page `<meta http-equiv="Content-Security-Policy">` with the SHA-256 of every inline script on that page. Build script order: `next build && node scripts/csp-hashes.mjs && <service-worker build>`. Browsers apply both policies, so the page is as strict as the hashed one.
- **zod:** `z.config({ jitless: true })` (reference `zod-config.ts`, imported first) — otherwise zod probes `new Function` and every page logs a CSP violation.
- **CSRF without cookies or tokens:** the reference `security.ts` `mutationGuard` rejects any state-changing request with a foreign `Origin` (403) or a non-JSON content type (415); webhooks are exempt and verified by signature. Add `bodyLimit` 64 KB (413).
- **Copy `tests/web/specs/security.nfr.spec.ts`** from the reference folder and adapt routes/bodies: headers + hashed CSP, probe paths, rate limit → 429, CORS, webhook signature, entitlement, secrets in built bundles, CSRF, crawler user agents → 200, body limit, zero CSP violations in the console.
- **Rate limits:** Workers Rate Limiting bindings (`RL_STRICT`, `RL_API` in `wrangler.jsonc`, free) with an in-memory fallback for local runs; a WAF rate-limiting rule is the outer layer once the zone exists.
- **CI scans** already run in `ci.yml` / `staging-tests.yml` / `mobile.yml` (Gitleaks on repo and built bundle, Semgrep CE, OSV-Scanner, ZAP baseline, MobSF). Docker is not needed locally: record the local result as "runs in CI" and read the first CI/staging run before marking the phase ✅.
- **Mobile:** `allowBackup: false`, `usesCleartextTraffic: false`, `blockedPermissions` for everything a library adds that the app does not use; verify in the merged manifest that MobSF reports.
