# Security

## Reporting a problem
Email **support@equisteg.com** with the subject `SECURITY: <product name>`. Please include what you found, how to reproduce it and the URL or app version. Do not open a public issue, and do not access other people's data while testing.

We reply within 3 working days, fix confirmed issues in priority order (payments, personal data and account takeover first) and tell you when the fix is live.

## How this repository protects itself
Private repository; no secrets in git (pre-commit check, Gitleaks in CI); pinned CI actions; dependency scanning; least-privilege access (build-team members read and contribute through forks, which get no secrets); server-side checks for every payment and premium request; automatic rollback in production.
