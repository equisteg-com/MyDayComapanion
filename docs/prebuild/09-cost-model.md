# 09 — Cost model

Figures are estimates to verify at purchase time. Nothing has been bought.

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| CM-01 | Initial cash exposure defined | PASS | ₹0 for Phase 0. Cap proposed for Phase 0–1: **₹10,000** total (founder to confirm, OQ-07). |
| CM-02 | Recurring cost target = ₹0 | PASS | No backend, cloud, AI API, analytics or paid tool. |
| CM-03 | Paid dependencies identified | PASS | None. All libraries are free/open source (11). |
| CM-04 | Free alternatives evaluated | PASS | Table below. |
| CM-05 | AI compute cost model | PASS | On-device only → ₹0 cash; cost is battery, governed by `EnergyPolicy`. |
| CM-06 | Play Store/publisher cost | PASS | One-time US$25 (~₹2,100) Play Console registration, when alpha starts. |
| CM-07 | Domain cost | PASS | ~₹800–1,500/year for a normal-priced .com if bought; not required for V1 (privacy page can live on the company site). |
| CM-08 | Maximum monthly spend defined | PASS | ₹0/month until a cost trigger fires. |
| CM-09 | Spending approval rule | PASS | Every spend answers master §4.1's 7 questions in an issue and needs founder approval. |
| CM-10 | Cost-trigger thresholds | PASS | Below. |

## One-time / conditional

| Item | Estimate | When | Required? |
|---|---|---|---|
| Google Play Console | US$25 one-time | Before alpha upload | Yes for Play distribution |
| Domain (normal price) | ₹800–1,500/yr | Optional | No |
| Trademark filing (India, per class; classes 9 and 42 likely) | ~₹4,500–9,000 per class government fee | Before public V1 | Founder decision |
| Test devices | ₹0 (existing devices + emulators) | Before beta | No |
| GitHub | ₹0 (private repo, Actions free minutes) | Now | No |

## Free alternatives used

| Need | Paid option avoided | Used instead |
|---|---|---|
| Crash reporting | Crashlytics/Sentry | Play Console Android vitals |
| Analytics | Firebase/Mixpanel | Local counters + interviews |
| Backend/sync | Firebase/Supabase | None (local only) |
| AI | Cloud LLM APIs | Deterministic extractor; on-device model later |
| Secret scan | Paid scanners | Gitleaks (MIT) |
| Vulnerability scan | Snyk/GHAS | OSV-Scanner + Dependabot |

## Cost triggers

Re-open the cost model only when: GitHub Actions free minutes are exhausted two months running; a security
requirement cannot be met for free; measured demand needs a capability that cannot run on device; or revenue
covers the expense with positive contribution.
