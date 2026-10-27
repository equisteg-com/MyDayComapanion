# Zero-cost standard (checked 30 Sep 2026 — re-check before each launch)

Rule: no card on file anywhere except the payment provider's payout account. Without a card, every free tier below stops at its limit instead of billing.

| Need | Tool | Free limit | At the limit |
|---|---|---|---|
| Code, CI | GitHub Free, **private** repo | 2,000 Actions minutes and 500 MB artifacts per month | Jobs blocked until next month |
| Staging tests (`staging-tests.yml`) | GitHub Actions + open-source Playwright, Lighthouse CI, k6, OWASP ZAP | ~25 runner minutes per staging deploy; Playwright traces/reports kept 30 days, summary 90 days | Run `/test` locally against staging (same tools, Docker), or lower `retention-days` |
| Branch protection, required reviewers, CodeQL, secret scanning | GitHub Free | Public repos only | Replaced by `guard`, `promotion-order`, `RELEASE_APPROVERS`, Gitleaks, Semgrep CE, OSV-Scanner |
| Hosting web + API | Cloudflare Workers Free + Static Assets | 100,000 requests/day, 10 ms CPU/request, 100 Workers, 20,000 files/version | Error 1027 until next day |
| Optional Git-connected builds | Cloudflare Workers Builds | 3,000 build minutes/month, 1 concurrent build | Builds wait |
| Database | D1 | 5 GB, 5M rows read and 100k rows written per day | Queries fail until reset |
| Key-value | KV | 1 GB, 100k reads and 1k writes per day | Writes fail until reset |
| Files | R2 | 10 GB-month, 1M Class A and 10M Class B ops/month, free egress | Enable only at maturity Level 3 |
| Background jobs | Queues + Cron Triggers | 10k queue ops/day; 5 cron triggers per account | Jobs wait |
| LLM | Workers AI | 10,000 Neurons/day (hard cap on Free) | Gateway falls back to deterministic output |
| WAF | Cloudflare custom rules | 5 rules | Combine expressions |
| Bot protection | Turnstile | Free | — |
| Errors | Sentry Developer | 5,000 errors/month, 1 user | Events dropped |
| Email | Resend Free | 3,000/month, 100/day | Sending stops for the day |
| Mobile builds | EAS Free | 15 Android + 15 iOS builds/month, low priority; 60 workflow minutes | Build locally with Gradle (free) |
| Analytics | Cloudflare Web Analytics | Free | — |
| Search | Google Search Console, Bing Webmaster Tools, IndexNow | Free | — |
| Tests and scans | Playwright, Maestro CLI, k6, Vitest, Jest, Gitleaks, Semgrep CE, OSV-Scanner, OWASP ZAP | Open source | — |

Accepted unavoidable costs: `equisteg.com` domain renewal, Google Play US$25 one-time, payment fees per successful transaction (Razorpay/Stripe/Google Play Billing), Apple developer fee only if iOS is ever published.

Cron budget (5 per Cloudflare account): reserve 1 per live product in production plus 1 for the shared monitor Worker. Put several jobs inside one `scheduled()` handler instead of adding triggers.
