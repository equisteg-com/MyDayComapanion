# 12 — Open questions and founder decisions

"Blocks" says what each item holds up. Nothing here blocks Phase 0 code.

| ID | Question | Status | Default taken | Blocks |
|---|---|---|---|---|
| OQ-01 | Product name: is "MyDayCompanion" final? (Founder typed "MyDayComapanion"; treated as a typo.) | NEEDS DECISION | MyDayCompanion, provisional | Play listing |
| OQ-02 | Brand screen: exact/variant web search, Google Play and App Store conflict search, social handles | NEEDS DECISION (founder action) | Not done; cannot be done reliably from the build environment | Alpha upload |
| OQ-03 | Domain: check `mydaycompanion.com` at a live registrar (search-engine absence ≠ availability); buy only at normal price within budget; no premium purchase without approval | NEEDS DECISION (founder action) | Not bought; not required for V1 | Nothing (optional) |
| OQ-04 | On-device LLM: which runtime/model (Gemini Nano via AICore vs open-weight model via MediaPipe/LiteRT), weights licence, and how weights reach the device without a network path | NEEDS DECISION | Deterministic extractor only | Phase 1 "local model" item only |
| OQ-05 | Register Google Play Console (US$25) under which legal entity? | NEEDS DECISION (founder action) | Not registered | Alpha upload |
| OQ-06 | Where to host the public privacy policy and terms (Play requires a URL) | NEEDS DECISION | Propose a page on the existing EQUISTeG website | Alpha upload |
| OQ-07 | Confirm the Phase 0–1 cash cap | NEEDS DECISION | ₹10,000 total, ₹0/month | Any spend |
| OQ-08 | Branch protection on a private repo needs GitHub Pro/Team | NEEDS DECISION | PR-only convention; no paid plan | Nothing |
| OQ-09 | Future payments: Play Billing needs server-side purchase verification for "server-authoritative entitlement" (spec §23), which means a backend | NEEDS DECISION (future) | No payments in V1 | Monetization phase |
| OQ-10 | Trademark screening/filing jurisdictions (India classes 9/42? others?) | NEEDS DECISION (founder action) | Not done | Public V1 |
| OQ-11 | Application ID `com.equisteg.mydaycompanion` becomes permanent after the first Play upload | NEEDS DECISION | Use it | Alpha upload |
| OQ-12 | Kill-criteria thresholds in 02 | NEEDS DECISION | Values in 02 | Alpha exit |
| OQ-13 | EQUISTeG product-factory template location | RESOLVED | The target repo already carries the template; work was integrated with it | Nothing |
| OQ-14 | Instrumented emulator test job for SQLCipher/Keystore in CI | NEEDS DECISION | Add at Phase 0 exit (free minutes) | Phase 0 exit |
| OQ-15 | `equisteg-com/MyDayComapanion` was created with only `main`; the template flow needs `development` (default), `staging` and `production` | RESOLVED 2026-10-28: `development` created and made the default branch by the founder | PRs go into `development` | Nothing |
| OQ-16 | `equisteg-com/MyDayComapanion` is public; the company rule is private product repos | DECIDED 2026-10-28 by the founder: stays public, pushed as is | Nothing; to change later: Settings → General → Danger Zone → Change visibility → Private. While public, CI minutes are unlimited | Nothing |
| OQ-17 | Reminders use inexact alarms (`setAndAllowWhileIdle`), so in deep idle a reminder can be a few minutes late. Exact alarms need `SCHEDULE_EXACT_ALARM` (the owner grants it in Settings on Android 14+) or `USE_EXACT_ALARM` (Play allows it only for alarm/calendar apps) | NEEDS DECISION | Inexact; no extra permission | Only if testers report late reminders |
| OQ-18 | "Standing approvals" (agent OS build doc, part 4): let the owner pre-approve a kind of action (for example "the assistant may always add tasks to tomorrow") so it doesn't ask each time | NEEDS DECISION | Not built in part 4; every agent-proposed L2 action still needs a tap. Proposed: only L1/L2 agent-proposable tools (for example `tasks.create` or `routines.create`), revocable in Settings, audited | Owner finds the taps tiresome |
| OQ-19 | Device actions (`device.*`) are agent-proposable, but the rule "a personal task's title never goes to another app" is enforced only on the task screen (part 5) | NEEDS DECISION | Nothing proposes device actions today, and every one needs Confirm plus the screen lock. Proposed before any agent proposes them: the tool takes a `taskId` and resolves a neutral label itself for personal tasks, or the tools stay user-only | Before the agent may propose device actions |
| OQ-20 | Gemini Nano on a real phone: (a) run the golden phrases with Gemini Nano and compare with the rules (CI cannot run the model); (b) confirm ML Kit's bundled usage-logging library doesn't crash or misbehave without ACCESS_NETWORK_STATE on Android 14+; (c) confirm the Play Data safety answers against Google's ML Kit and AICore data disclosures | NEEDS HUMAN | Steps: install the CI debug APK on a phone Google lists for Gemini Nano → Settings → Smarter understanding → Download → plan the golden phrases → check for crashes in Play vitals or `adb logcat`. If (b) fails, an update with the `local_llm` flag off stops every ML Kit call (flags are fixed at build time, so this needs a new release; owners can also switch it off in Settings at once) | Before the closed test |
| OQ-21 | CI's APK check blocks only INTERNET and ACCESS_NETWORK_STATE; a new library could add another permission without CI noticing (`ManifestSecurityTest` reads the source manifest) | NEEDS HUMAN (workflow files are protected) | Proposed change to `.github/workflows/android.yml`: compare `aapt2 dump permissions` of the debug APK with the `ManifestSecurityTest` allow-list and fail on anything else. Until then the dump is read by hand on each dependency change (done for ML Kit on 2026-10-28) | Next dependency change |
| D-01 | Monthly subscription price, and what is free vs paid (agent OS build doc §1.2, §13) | NEEDS DECISION | Full app built; billing not wired | Monetization |
| D-02 | Allow Play Billing (it may add a network-related permission) and choose on-device or free-tier server purchase checks (§10.3) | DECIDED 2026-10-09 by the founder: (b) free server check — Play Billing plus a Cloudflare Worker on the free tier that verifies purchases and refunds through the Google Play Developer API. The paid tier may use `INTERNET` only for this check; the free tier stays offline. | Billing PR after PR 6; price waits on D-01 | Monetization |
| D-03 | On-device LLM: Gemini Nano (ML Kit Prompt API, supported phones only) and/or Gemma via MediaPipe; cloud LLM (about US$0.27 per active user per month on Claude Haiku 5.5) never unless approved | DECIDED 2026-10-28 by the founder: Gemini Nano where available; the rule extractor stays the fallback on every other phone. ML Kit Terms accepted by the founder on 2026-10-28; built with `com.google.mlkit:genai-prompt` 1.0.0-beta4, owner switch in Settings (on by default), rule extractor as fallback | Rule extractor on unsupported phones | Nothing |
| D-04 | App-only remote wipe through FCM + a free-tier Worker (needs INTERNET) | NEEDS DECISION | Not built; Find My Device erase guidance | Nothing |
| D-05 | Direct phone-to-phone transfer (Nearby Connections) | NEEDS DECISION | Encrypted backup file + Quick Share/USB | Nothing |
| D-06 | Multi-device end-to-end encrypted sync | NEEDS DECISION | Not built | Nothing |
| D-07 | Emulator test job in CI (₹0 within free minutes) | NEEDS DECISION | Will add | Phase exit |
| D-08 | Numeric dates like 12/10: day-month by default (India) | NEEDS DECISION | Day-month; setting to switch | Nothing |
| D-09 | Wipe-on-repeated-failure default | NEEDS DECISION | Off; recommended in onboarding | Nothing |

## Resolved during precheck (defaults applied under the founder's Gate P0 approval)

| Topic | Decision |
|---|---|
| Document conflicts | Master build document wins (03 §Contradictions) |
| Risk levels | Master §17 definitions |
| Min SDK | 26 (Android 8.0) |
| DB encryption | SQLCipher with Keystore-wrapped random key |
| Network | None; INTERNET removed and checked in CI |
| Analytics / crash | None / Play vitals |
| Voice | On-device recognizer only; no cloud fallback |
| Reminders | Inexact alarms; no exact-alarm permission |
| Calendar | Read-only |
| Production branch | `production` |
