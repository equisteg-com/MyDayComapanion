# Progress

## 2026-10-27 — Consolidation into `equisteg-com/MyDayComapanion`

Every earlier version of the app is combined here, with its commit history:
- `equisteg-com/MyDayCompanion` `development` (cf01b3d): Phase 0 foundation on template v4, product override,
  Android CI. Brought in as one commit, because that repository's history had been reset.
- `equisteg-com/MyDayCompanion-archive` branch `claude/project-thread-d6ydk7` (PR #15, head 6357743): agent OS
  parts 1–3. Its 10 commits were cherry-picked unchanged and applied without conflicts.
- Checked and already included: the project-files bundle (`feature/phase0-foundation` a61b830 is an ancestor of
  PR #15), the part 2 patch (same as commit 4be93fb), the prebuild documents (the repository copies are newer), the
  agent OS build document (identical) and the MyDayGuide spec (renamed to `MYDAYCOMPANION_BUILD_SPEC.md`).
- Not brought in: Dependabot version-bump PRs from both old repositories (Dependabot reopens them here) and the
  `chore/private-kit` change, which `MyDayCompanion` had already reverted.

Changes for this repository: `ci.yml` and `android.yml` also run on `main`, because this repository has no
`development` branch yet (OQ-15). New `docs/architecture/cloud-infrastructure.md` answers which features need cloud
infrastructure and why.

Checks run here: `:domain:test` 83 tests pass; golden evaluation `task-extraction` v2 at 100% on all four metrics.
The Android modules (`:data`, `:app`, lint, APK) need the Android SDK, which this environment cannot download; CI
runs them on the PR.

Next: agent OS part 4 (reminders, routines, standing approvals), part 5 (voice, calendar, device actions), part 6
(memory review, backup and restore, widget), then billing once the price (D-01) is set.

## 2026-10-09 — Agent OS PR 3: task editing, settings, first-run setup

- New tools: `tasks.update` (every editable field, subtasks follow the task to a new day), `tasks.delete` (with its
  subtasks, always a tap), `tasks.addSubtask` (one level, inherits day/priority/personal), `tasks.reopen`, and the
  owner-only `settings.update` (L1).
- Personal tasks: `AgentTool.touchesPersonal` makes the policy require a confirmation for any action on a task marked
  personal; the UI adds the owner's chosen check (screen lock by default, or a challenge answer).
- Planner `deterministic-planner/3`: a task broken into steps is planned through its steps. Extractor
  `rule-extractor/3`: numeric dates ("3/4") follow the owner's day-first or month-first setting.
- `data`: Room schema v4 (`tasks.notes`, `tasks.personal`, `preferences.dateOrder`, `reminderLeadMinutes`,
  `onboardingDone`) with `MIGRATION_3_4`; installs that already hold tasks skip first-run setup.
- `app`: the day's tasks listed under the plan (tick to complete or reopen, tap to edit); edit dialog with notes,
  steps and the personal switch; Settings → Your day; 4-step first-run setup (welcome, your day, protection, try it).

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
  schema `task-extraction/v2`, extractor `rule-extractor/2` (now /3), prompt v2, planner v2 (preferred start), multi-day
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
