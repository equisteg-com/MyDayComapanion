# Where MyDayCompanion needs cloud infrastructure

Short answer: **only one decided feature needs a server, the monthly subscription check.** Everything else the
agent does runs on the phone. A few optional ideas would also need cloud pieces, and none of them is built until a
founder says yes (`docs/prebuild/12-open-questions.md`, D-01 to D-07).

Sources: `docs/product/MYDAYCOMPANION_AGENT_OS_BUILD.md` §4 and §10, founder decision D-02 (2026-10-09), and the code on
this branch (checked 2026-10-27).

## 1. Features that need cloud infrastructure

| Feature | Status | What cloud piece | Why it can't be done on the phone alone | Cost to Equisteg |
|---|---|---|---|---|
| **F18 Monthly subscription: purchase check** | Decided (D-02 = free server check). Not built; waits on the price (D-01) | One Cloudflare Worker route, through the company platform Worker `equisteg-platform` (company rule: entitlement logic lives there, not per product). Google Play Developer API with a service account. KV or D1 for the entitlement record. A daily Cron Trigger that reads Play's voided-purchases list to catch refunds | A purchase checked only on the phone can be faked on a rooted or modified phone. Google recommends checking the purchase token on a server and acknowledging it there. Refunds and chargebacks are only visible through the Play Developer API, which needs a secret service-account key, and a secret can't be shipped inside the app | ₹0 on the Workers free tier (100,000 requests a day). Google's subscription fee (15% in India) is per sale, already accepted |
| **Privacy policy page** | Needed before the Play listing | A static page: the existing equisteg.com site, or `apps/web` on Cloudflare Workers Static Assets | Play requires a public privacy-policy URL for every app (verify against the current Play User Data policy at listing time) | ₹0 |

What the subscription check changes in the app:
- The APK gains `INTERNET` (and Play Billing adds `com.android.vending.BILLING`). Android permissions apply to the
  whole app, so free users' APK has `INTERNET` too. The free tier still makes **no** network calls. Only the
  purchase check calls the Worker, and it sends just the purchase token and product ID, never tasks or memory.
- CI's manifest check (`ManifestSecurityTest`, the APK permission step in `android.yml`) must be changed in the same
  PR, with the reason recorded in `docs/prebuild/06-permission-model.md`.
- Real-time notifications from Play (Google Cloud Pub/Sub) are not needed. The daily voided-purchases check and a
  check when the app opens cover refunds and cancellations without Pub/Sub. If a later need for Pub/Sub turns out to
  need a card on a Google Cloud billing account, that is **NEEDS HUMAN** under the zero-cost rule.

## 2. Optional ideas that would need cloud (not built, need a founder's yes)

| Idea | Cloud piece | Why | Cost | Recommendation |
|---|---|---|---|---|
| D-04 Remote wipe of only this app | Firebase Cloud Messaging + a Worker + a small store (KV or D1) holding device tokens | A wipe from another device needs a message to reach the phone over the internet | ₹0 on free tiers, but it stores device tokens (personal data under DPDP/GDPR) | Not now. Android's Find My Device already erases the whole phone at ₹0 |
| D-06 Sync between several devices | Accounts, a Worker, D1/R2 for end-to-end encrypted data | Two devices can only share data through something both can reach | ₹0 under free limits; adds accounts and data-protection duties | Not now |
| D-03c Cloud AI for long sentences | A Worker holding the API key, plus a paid model API | The model runs in the cloud and the key must stay on a server | **Costs money**: about US$0.27 per active user per month (agent OS doc §10.3) | No. Use the on-device options below |

## 3. Features that need no cloud infrastructure

| Feature | Runs on | Built on this branch? |
|---|---|---|
| F1 Understanding messy text and times | Phone (rule extractor v3, 75-case golden set at 100%) | Yes |
| F2 Tasks: create, edit, delete, steps, notes, personal | Phone (encrypted Room database) | Yes |
| F3 Planner (preferred times, several days, steps) | Phone | Yes |
| F10 Settings and first-run setup | Phone | Yes |
| F11 Approvals (fingerprint, face, PIN, challenge questions) | Phone (BiometricPrompt, Keystore) | Yes |
| F12 App lock | Phone | Yes |
| F15 Export and delete everything (files and key destroyed) | Phone | Yes |
| F16 History of actions | Phone | Yes (filters still to do) |
| F4 Reminders that survive a restart | Phone (AlarmManager, notifications) | Yes (part 4) |
| F5 Routines | Phone (AlarmManager nightly run) | Yes (part 4) |
| F6 Voice in and out | Phone (on-device speech recogniser where the phone has one; text-to-speech) | Yes (part 5) |
| F7 Calendar read | Phone (Calendar Provider, read-only) | Yes (part 5) |
| F8 Device actions: alarm, call, SMS or e-mail draft, maps, calendar entry | Phone (Android intents; the owner presses Call, Send or Save) | Yes (part 5) |
| F9 Memory review | Phone | Model built; review screen not yet (part 6) |
| F13 Backup and moving to a new phone | Phone; the owner keeps a passphrase-encrypted file wherever they like | Not yet (part 6) |
| F14 Theft protection | Phone (Keystore, lockout, wipe) + Google's Find My Device | Local parts yes; settings polish in part 6 |
| F19 Home-screen widget | Phone | Not yet (part 6) |
| D-03a/b On-device AI model (Gemini Nano or Gemma) | Phone; a Gemma model would download through Play Asset Delivery, which needs no server of ours | Not built (founder decision D-03) |
| D-05 Direct phone-to-phone transfer | Phone (Nearby Connections) | Not built |

## 4. Infrastructure that is not part of the app

| Piece | Service | Cost |
|---|---|---|
| CI (tests, lint, APK build, permission check) | GitHub Actions. Unlimited minutes while the repo is public; 2,000 minutes a month on GitHub Free once it is private | ₹0 |
| Crash reports | Google Play Console Android vitals | ₹0 |
| Database, AI, planner, backups | None on our side; all on the owner's phone | ₹0 |
| Google Play developer account | One-time US$25, already accepted | US$25 once |
