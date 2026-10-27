# Phase 6 — Security review & firewall

## Security — `docs/security-report.md` (finding · severity · fix · status · evidence)
- Walk OWASP Top 10, OWASP ASVS Level 1, and OWASP MASVS (mobile) item by item.
- **Web:** headers in the Worker's secure-headers middleware and `apps/web/public/_headers` — strict CSP (only required AdSense/consent/analytics domains, nonces for inline scripts), HSTS preload, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, frame-ancestors. zod validation client + server, authz on every route handler, secure cookies (HttpOnly/Secure/SameSite), CSRF on mutations, generic errors.
- **Mobile:** tokens only in `expo-secure-store`; no secrets in `EXPO_PUBLIC_*` or app.config; HTTPS only (no cleartext); deep links validated; `allowBackup` reviewed; no debug flags in production profile; logs stripped in production.
- **API (if present):** helmet, CORS allow-list, rate limits, input validation, authz, no stack traces in responses, dependency pinning.
- Run and fix until clean:
  - `npm audit --omit=dev` (all workspaces) → 0 high/critical
  - Semgrep SAST
  - gitleaks on repo and built web bundles
  - OWASP ZAP baseline on the staging URL (Docker or GitHub Action)
  - MobSF static scan on the preview APK (Docker). If Docker is unavailable, add a CI job and mark the result NEEDS HUMAN.

## Firewall / edge — `docs/firewall-report.md`
- Cloudflare WAF custom rules (Free plan allows 5 — combine paths into one expression): block `/.env*`, `/.git*`, `/wp-admin*`, `/wp-login*`, `/phpmyadmin*`, `/xmlrpc.php`; document Under Attack Mode for incidents.
- Rate limiting on API routes/forms (Cloudflare rate-limiting rule where the plan allows it, plus a D1/KV counter in the Worker) → 429.
- Bot protection on abusable forms (Cloudflare Turnstile).
- Verify with curl: blocked paths → 403/404; rate limit → 429 after threshold; Googlebot, Mediapartners-Google and Bingbot user agents → 200.
- Rules that must be created in the Cloudflare dashboard → NEEDS HUMAN with exact rule expressions.
- Payments: verify every Phase 4b security test passes; webhook secrets only in `wrangler secret`.

## Security test cases
Add `Security` rows to the `Non-functional` sheet of `tests/cases/test-cases.xlsx` for: ZAP baseline (0 High/Medium, Automation ref `zap-baseline`), security headers present, blocked paths → 403/404, rate limit → 429, CORS rejects unknown origins, webhook with bad signature → 401, premium API without entitlement → 403, no secrets in built bundles. Automate them as Playwright `@nfr` request specs or unit tests; ZAP runs after every staging deploy in `staging-tests.yml`. Findings that can't be fixed in this phase are logged in `tests/bugs/bug-log.xlsx` (S1 for High).
