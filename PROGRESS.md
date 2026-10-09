# Progress

## 2026-10-09 — Agent OS PR 2: approvals, app lock, theft protection

- Founder decision D-02 recorded: purchases will be checked by a free Cloudflare Worker (billing PR after PR 6).
- `domain/security`: challenge answers hashed with PBKDF2-HMAC-SHA256 (310,000 iterations, 16-byte salt),
  `LockoutPolicy` (5 wrong → 15 min; 10 in 24 h → device credential, or wipe when opted in), `ApprovalRules`
  (tap; + device credential for L3/L4; + challenge for L4 when set up; personal items by the owner's choice).
- New L4, user-only tools `security.updateSettings` and `security.setChallenges`; answers are hashed in the UI so clear
  answers never reach the orchestrator or the audit log.
- `data`: Room schema v3 (`security_settings`, `challenge_answers`, `auth_failures`) with `MIGRATION_2_3` and a
  `MigrationTestHelper` test from v1; `LocalStorageWiper` deletes the database files and the Keystore key after a
  verified delete (fixes the Phase 0 gap where only rows were deleted).
- `app`: `MainActivity` is a `FragmentActivity` hosting `BiometricPrompt` (`androidx.biometric` 1.1.0, Apache-2.0);
  lock screen with panic wipe; Settings → Security (app lock, lock delay, wipe-on-failure, personal approvals,
  challenge questions, Find My Device guidance). Permissions USE_BIOMETRIC / USE_FINGERPRINT (normal) declared and
  allowlisted.

## 2026-10-09 — Agent OS build document and text/time understanding v2

- Founder phone test: the app did not understand normal or jumbled text and many features were switched off.
- Wrote `docs/product/MYDAYCOMPANION_AGENT_OS_BUILD.md` (top of the precedence list): personal agent OS scope with every
  feature shipping now, approvals with device security and challenge questions, encrypted backup for new phones,
  theft protection, and an infrastructure table with a cost for each item (default ₹0). Founder decisions D-01..D-09
  added to `docs/prebuild/12-open-questions.md`.
- PR 1 (this change): new on-device readers for dates, times and durations (`domain/ai/text/*`), smarter splitting,
  schema `task-extraction/v2`, extractor `rule-extractor/2`, prompt v2, planner v2 (preferred start), multi-day
  proposals in the UI, Room schema v2 (`tasks.preferredStart`). Golden set v2: 75 cases, 100% on all four metrics.
- Next PRs, in order: delete-key fix + approvals + app lock; task edit/settings/onboarding; reminders + routines;
  voice + calendar + device actions; memory review + backup/restore + theft-protection settings.

## 2026-10-06 — Precheck and Phase 0 foundation

- Renamed product from MyDayGuide to MyDayCompanion across all documents.
- Ran `/precheck`; evidence in `docs/prebuild/00`–`12`. Founder approved Gate P0 with documented defaults.
- Built Phase 0 foundation on `feature/phase0-foundation`:
  - Gradle multi-module project: `domain` (pure Kotlin), `data` (Android library), `app` (Compose).
  - Deterministic planner + plan validator; rule-based on-device task extractor behind the `AiModelProvider` abstraction;
    inference router with energy policy; versioned prompts; strict structured-output validation.
  - Agent orchestrator with tool allowlist, deterministic authorization policy, confirmations bound to exact arguments,
    verification and privacy-minimised audit log.
  - Memory model with poisoning defences and retention.
  - Room schema v1 on SQLCipher with a Keystore-wrapped key; backups disabled; no INTERNET permission.
  - Full export (JSON via system file picker) and verified full deletion.
  - Tests: unit, Room integration (Robolectric), Compose UI, security, privacy, golden AI evaluation with thresholds.
  - CI: secret scan, wrapper validation, tests, lint, debug build, APK permission check, dependency scan (report-only).

- Integrated with the EQUISTeG product template already in the repo (kept its rules, CI and workbooks; Android
  test cases TC-M-101..107 and TC-N-101..104 added to `tests/cases/test-cases.xlsx`).

## Next (Phase 0 exit → Phase 1)

- Room schema v1 committed (`data/schemas/`); CI fails on schema drift.
- On-device tests for SQLCipher + Keystore (instrumented, emulator in CI).
- Founder decisions in `docs/prebuild/12-open-questions.md` (brand/domain/trademark, Play account, branch protection).
- Phase 1 capabilities, one at a time behind flags: reminders, voice, calendar read, routines, memory review UI.
