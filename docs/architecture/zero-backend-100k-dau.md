# Zero-card infrastructure and 10k–100k DAU: feasibility and release gates
**Date:** 2026-11-20 | **Status:** design and validation plan — not a performance benchmark.

## What can genuinely scale at zero backend cost
The current Android product is local-first and intentionally has no app backend or INTERNET permission. Each user's device independently stores tasks, generates plans, displays UI and schedules reminders. There is **no shared API request path** to saturate as daily active users increase. Therefore 10,000–100,000 daily active devices need no central compute, database, load balancer or cloud AI for the existing offline feature set. This is an architectural property, **not proof** that all target phones run smoothly.

| Metric | 10k DAU | 100k DAU | Notes |
|---|---|---|---|
| App server requests for native offline functions | 0 | 0 | Only if shipped build remains genuinely local-first |
| Central database write throughput | 0 | 0 | Each device owns its own Room database |
| Central inference spend | $0 | $0 | Models are on-device; capability varies by device |
| Initial install traffic at a hypothetical 25 MB APK | 250 GB | 2.5 TB | Illustrative download traffic, **not free hosting** |
| Store fees, legal review, support and development time | Not zero by default | Not zero by default | Must budget separately |

Do **not** equate 100k DAU with 100k requests/second: DAU is a population metric, not throughput. If synchronization, login, web backend, telemetry or cloud inference is introduced, this zero-backend scaling claim no longer applies.

## No card / zero ongoing fee configuration
1. Develop with JDK, Android SDK command-line tools, Gradle, Kotlin/JUnit/Robolectric, Compose UI tests and locally run scanners (free/open tooling under their licenses).
2. Keep all user data and inference on device; never silently introduce hosted APIs, Firebase dependencies, paid crash collectors, analytics or a speech proxy.
3. Run pre-commit guards and local tests. Public GitHub-hosted CI may offer free allowances subject to GitHub's current quotas/terms; avoid promising unlimited CI.
4. Keep documentation and source in the existing repository. A basic static privacy page can use an eligible free static-hosting plan, subject to its usage, acceptable-use and bandwidth restrictions.
5. Distribute internal test APKs using controlled tester channels. **Do not promise unlimited zero-card public APK distribution**. Official Play Console registration is US$25 once, generally with a credit/debit payment method. Android's ongoing developer-verification and distribution rules must be revisited for the target geographies.
6. Never use third-party free tiers as if they guarantee 100k-user bandwidth or SLA. Design signed release and update distribution before public scaling.

## Device-level test gates (actual evidence required)
- Baseline devices: Android API 26, 30, 34, 36; low-memory and common midrange physical devices.
- Startup: measure cold and warm start (p50/p95); target <2.5 s cold on midrange, investigate slow-tier outliers.
- Input: load 100, 1,000, and 20,000 tasks on device, compare 50th/95th percentile list scroll, search, plan and encryption round trips.
- Stress: 24h soak, repeated lock/unlock, timezone changes, midnight rollover, notification reboot recovery, storage pressure, process death, permissions denied/revoked, background restrictions and clock drift.
- Security/reliability: full wipe and backup/restore on real devices, schema upgrades from all supported versions, corrupt backup files and invalid model output.
- Accessibility: 320dp widths, 200% font scaling, dark/light/high-contrast, TalkBack, switch access, touch target sizing, keyboard and RTL.
- CI: verify unit, integration, Robolectric UI, lint, dependency/secret scans, APK manifest; separate emulator instrumentation and manual physical tests. No test should be marked PASS without a recorded report.
- Privacy: packet capture showing the app itself does not transmit; separately evaluate Android system speech providers if the user enables them.

## Candidate feature roadmap (gated)
**Phase 1 — offline premium experience:** quick capture, accessible natural-language planning, timeline drag/reorder with confirm/undo, weekly insights, recurring tasks, rich search/filter, reminder recovery, offline import/export, guided onboarding, contextual help.
**Phase 2 — reliability and polish:** conflict explanations, flexible timezone/locale-aware scheduling, better TalkBack, large-text layouts, performance budgets, cross-device transfer using owner-encrypted files.
**Phase 3 — opt-in connected features:** sync, shared lists, calendar cloud integration, cross-device notifications and support diagnostics. These require privacy, legal, backend, pricing, security and 100k DAU capacity plans. Do not implement until costs/consents are approved.

## Release gates and ownership
- **Mobile:** screenshots/videos of UX matrices, startup/scroll benchmarks, ANR/crash-free target measured with a privacy-preserving process.
- **Domain:** planner invariants, data integrity, accessibility, migration and property tests.
- **Security/privacy:** threat model, independent audit, release signing, permissions, data map and market checklist.
- **Founder/release owner:** store identity and fees, support contacts, distribution capacity, legal approval, rollback plan.
A change may only ship when all applicable gates have evidence. Branch protection/rulesets should require Android + security checks; action workflow checks alone do not prevent administrators from merging failing PRs.
