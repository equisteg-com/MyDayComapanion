# Phase 6 — Security review & firewall

## Security — `docs/security-report.md` (finding · severity · fix · status · evidence)
- Walk OWASP Top 10, OWASP ASVS Level 1, and OWASP MASVS (mobile) item by item.
- **Web:** headers in `next.config` / `vercel.json` — strict CSP (only required AdSense/consent/analytics domains, nonces for inline scripts), HSTS preload, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, frame-ancestors. zod validation client + server, authz on every route handler, secure cookies (HttpOnly/Secure/SameSite), CSRF on mutations, generic errors.
- **Mobile:** tokens only in `expo-secure-store`; no secrets in `EXPO_PUBLIC_*` or app.config; HTTPS only (no cleartext); deep links validated; `allowBackup` reviewed; no debug flags in production profile; logs stripped in production.
- **API (if present):** helmet, CORS allow-list, rate limits, input validation, authz, no stack traces in responses, dependency pinning.
- Run and fix until clean:
  - `npm audit --omit=dev` (all workspaces) → 0 high/critical
  - Semgrep SAST
  - gitleaks on repo and built web bundles
  - OWASP ZAP baseline on the Vercel preview (Docker or GitHub Action)
  - MobSF static scan on the preview APK (Docker). If Docker is unavailable, add a CI job and mark the result NEEDS HUMAN.

## Firewall / edge — `docs/firewall-report.md`
- Vercel Firewall custom rules: block `/.env*`, `/.git*`, `/wp-admin*`, `/wp-login*`, `/phpmyadmin*`, `/xmlrpc.php`; document Attack Challenge Mode for incidents.
- Rate limiting on route handlers/forms (Vercel WAF rate-limit rule or middleware + Upstash) → 429.
- Bot protection on abusable forms (Cloudflare Turnstile).
- Verify with curl: blocked paths → 403/404; rate limit → 429 after threshold; Googlebot, Mediapartners-Google and Bingbot user agents → 200.
- Rules that must be created in the Vercel dashboard → NEEDS HUMAN with exact rule definitions.
