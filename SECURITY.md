# Security

## Reporting a problem
Email **support@equisteg.com** with the subject `SECURITY: <product name>`. Please include what you found, how to reproduce it and the URL or app version. Do not open a public issue, and do not access other people's data while testing.

We reply within 3 working days, fix confirmed issues in priority order (payments, personal data and account takeover first) and tell you when the fix is live.

## How this repository protects itself
Every product built from this template follows [docs/standards/repo-security.md](./docs/standards/repo-security.md): private repository, no secrets in git (pre-commit check, Gitleaks in CI, a guard that stops Claude reading or sending secrets), pinned CI actions, dependency scanning, server-side checks for every payment and premium request, and automatic rollback in production.
