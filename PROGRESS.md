# Progress

## 2026-10-28 — Agent OS part 8: hardening

Closes the follow-ups the reviews of parts 6a, 6b and 7b left open.
- Memory (OQ-22 a): private memories show as "Private memory" until the owner taps Show private and passes the
  screen lock; they hide again when the app leaves the screen or locks.
- History (OQ-22 b, c): turning lock-screen text, widget names or the History period on or off now goes through the
  new `settings.privacy` tool (L1, owner only) and is recorded with only the switch and its value. Filters run in
  the database, so "Blocked or failed" finds old entries beyond the newest 300.
- Backup (OQ-24 a, c, d): wrong passphrases count against the file being guessed (its SHA-256); only the right
  passphrase on that file clears them, so another backup can't reset the count. The 1-hour Restore lock also runs on
  the uptime clock in the same boot, so setting the phone's clock forward doesn't reopen it. Entries older than the
  last restore show "from backup". Restored days from today on are planned again the first time they are opened.
- Focus (OQ-27): the end of a focus session is also a notification ("Focus time is up", no task name) when the app
  is closed; stopping, finishing, the app lock and "Delete everything" cancel it. Inexact like reminders.
- Moves: a move of a personal task is checked again after the screen lock, right before it runs.
- Still accepted: the backup passphrase exists briefly as a text-field string (OQ-24 b).
- No new permission, dependency or database change.

## 2026-10-28 — Agent OS part 7b: smarter agent

- My day is a short chat. Typed (or spoken) lines are first checked against fixed command rules on the phone
  (`AgentCommandParser`), never the model; anything that isn't a command goes to the planner as before.
- "What's next?" / "What now?" answers Now / Next from today's plan; "What's left?" says how many are done and
  names up to three open tasks. Quick chips under the input ask the same.
- "Move gym to Friday", "push call Raj to 4pm", "reschedule dentist to tomorrow 3pm", "postpone report" (=
  tomorrow): `TaskMatcher` finds the open task (exact title, then contains, then all words); when several match
  the agent asks which one. The move is offered as a card; "Move" runs `tasks.update` through the orchestrator as
  the agent, so the owner's tap is the approval and a personal task still needs the screen lock.
- Roll-over: new tool `tasks.rollOver` (L2, agent-proposable, at most 50 tasks) moves open, non-routine tasks from
  earlier days (and their steps) to a new day; a banner on today offers it for the last 14 days.
- Focus timer: "focus 25 min on report", "focus" (25 min) or the chip under Now. In-app only (OQ-27). When it ends
  the agent offers to mark the task done.
- Privacy: the chat is kept in memory only (last 20 lines), is never written to History or the database, and is
  cleared when the app locks. Audit entries for moves and roll-overs carry only counts and ids.
- No new permission, dependency or database change.

## 2026-10-28 — Agent OS part 7a: new look

- A new palette ("calm dawn": deep teal for tasks, apricot for fixed events, violet for the agent), rounder shapes
  and stronger headings. Settings → Appearance: Match phone / Light / Dark, and "Use wallpaper colours" on
  Android 12+. The look is read before the first frame, the window behind it follows the phone's light or
  dark mode, and the status and navigation bar icons follow the app's theme.
- My day: a greeting and date with ‹ › and a Today chip; the input in its own card with the agent's reply as a
  bubble; a top card with a progress ring ("2 of 5 done") and, for today, Now / Next; the plan as a colour-coded
  timeline where the block running now is marked and done tasks fade and strike through; "Your last 7 days" with seven
  bars, the streak and minutes finished today. Insights are worked out on the phone from the task list; nothing
  new is stored.
- Motion: the proposal card slides in and out, the ring and bars animate, tabs cross-fade.
- No new dependency or permission; no database change (two new device settings: theme and wallpaper colours).
- Agent chat moved to part 7b (Smarter agent), where the new commands give it something to say. The review
  follow-ups OQ-22 and OQ-24 and the automatic-backup question OQ-23 are retargeted to after part 7b.

## 2026-10-28 — Agent OS part 6c: home-screen widget

- A MyDayCompanion widget for the home screen (F19): a headline ("3 left today · next at 14:00") and up to three
  items with their times. Tapping it, or "+ Add to my day", opens the app (behind the app lock as usual).
- The widget sits outside the app lock, so by default it shows only times, "Task" and "Event". Settings → Home-screen
  widget → "Show names on the widget" adds names; a personal task never shows its name. "Delete everything" clears it.
- It only reads today's plan; nothing changes data from the widget. Redrawn whenever the plan changes and every
  30 minutes. Built with RemoteViews: no new dependency, no permission, and the receiver is not exported.

## 2026-10-28 — Agent OS part 6b: encrypted backup and restore

- Settings → Your data → Back up: the owner picks where to save `mydaycompanion-YYYYMMDD.mdcbackup`, chooses a
  passphrase (at least 10 characters, typed twice, with a strength label and a "no recovery" warning), then confirms
  with the screen lock. The file is encrypted with AES-256-GCM using a key from PBKDF2-HMAC-SHA256 (600,000
  iterations, random salt); the header is authenticated, so any changed byte makes it fail to open. The app opens
  the file once before saving it, so a backup that couldn't be restored is never written.
- Restore (Settings, or "Restore from backup" on the first setup screen of a new phone): the file is read (20 MB
  cap), decrypted, and every item checked like any untrusted input (unknown fields, rule-like titles or memories,
  bad settings and broken references refuse the whole backup). The owner then sees what the backup holds and
  approves replacing everything (L4: Confirm, screen lock, and a challenge answer when set up). Reminders and
  routines are registered again afterwards. Ten wrong passphrases close Restore for an hour.
- The passphrase and the decrypted backup never go into tool arguments, the approval fingerprint or History: they
  wait in memory for at most 10 minutes under a random one-time token, and are cleared when the owner cancels.
- No new permission or dependency (JDK crypto, Storage Access Framework), and no database change.

## 2026-10-28 — Agent OS part 6a: memory, history, lost phone

- Memory (F9): a new Memory tab. Each night the agent looks at finished tasks; a task finished on 3 different days
  in 3 weeks at about the same time becomes a suggestion ("You usually do "Gym" around 07:30"). Personal tasks are
  never used. Suggestions are not used until the owner taps Keep; "No thanks" means it is never suggested again.
  The owner can add their own memories (active at once) and mark one private: it is kept encrypted but never given
  to the model, and forgetting it needs the owner's verification. Kept memories go to Gemini Nano as preferences,
  fenced as data. `MemoryPolicy` refuses rule-like, credential-like and link-like text from anyone.
- Tools: `memory.propose` (L1, agent may propose), `memory.add` / `memory.activate` / `memory.delete` (L2, owner
  only), `memory.reject`, `memory.purgeExpired`, `history.prune` (L1, owner or nightly run). History never holds
  what a memory says.
- History (F16): filters (blocked or failed, high risk, by me, by the assistant, automatic), plain action names,
  and "Keep history for" 30 / 90 / 180 / 365 days (180 by default); older entries are removed nightly. The newest
  30 days can never be removed this way.
- Lost phone (F14): Settings explains what protects the owner, how to turn on Find My Device and Theft protection,
  opens Android's security settings, and adds "Show reminder text on the lock screen" (off by default; personal
  tasks never show their title). Screenshots and the recent-apps preview are blocked on Memory and the
  challenge-question dialogs.
- No new permission, dependency or database change (History retention uses two new queries on the existing table).

## 2026-10-28 — Gemini Nano (founder decision D-03)

- On phones with Gemini Nano (Android AICore), typed and spoken text is understood by the model through ML Kit's
  Prompt API (`com.google.mlkit:genai-prompt` 1.0.0-beta4, ML Kit Terms accepted by the founder). Elsewhere, and
  whenever the model's answer is not valid task-extraction/v2 JSON or takes longer than 12 s, the rule extractor
  answers as before. The owner still approves every plan.
- The owner's text is fenced as data in the prompt (prompt v3) and cannot close the fence. Model output goes
  through the same schema, domain and content-safety checks as before; no tool or permission changed.
- Settings → Smarter understanding: on/off switch (on by default), the phone's status, and a Download button that
  asks AICore to fetch the model. The app still has no INTERNET permission. "Delete everything" resets the switch.
- The model is used down to 15% battery (it is classed as a small local model) and never in severe heat.
- NEEDS HUMAN (OQ-20): run the golden phrases on a phone that Google lists as supporting Gemini Nano, check that
  ML Kit's bundled usage-logging library stays quiet without network permissions, and confirm the Data safety answers.

## 2026-10-28 — Agent OS part 5: voice, calendar, device actions

- Voice (F6): 🎤 on My day fills the input box from speech; nothing runs until "Plan it". On-device recognition
  only (Android 12+ phones that have it); no cloud fallback, so on other phones the mic is shown disabled and
  Settings says why. The VOICE switch hides the mic too. "Read" speaks the day's summary; "Speak replies" says what was added. `voice.speak` is L1, user-only, and the
  text is never audited.
- Calendar (F7): "Show my calendar in plans" (off by default) asks for READ_CALENDAR and adds the day's timed,
  accepted, visible events as fixed events in `plan.generate`. Read-only. Events already typed in are not doubled.
- Device actions (F8): from a task: Alarm, Calendar, Directions, Call, Text/E-mail. Five `device.*` tools, all L3:
  Confirm plus fingerprint, face or screen lock, every time. Messages go through the system chooser; the owner
  presses Call/Send/Save in the other app. Numbers, addresses and message text are never audited.
- Permissions: RECORD_AUDIO, READ_CALENDAR (both asked in context), SET_ALARM (install-time). CALL_PHONE, SEND_SMS,
  WRITE_CALENDAR, contacts and location stay forbidden by `ManifestSecurityTest`.

## 2026-10-28 — Agent OS part 4: reminders, routines, morning brief

- Reminders (F4): `ReminderPlanner` turns the saved plans for today and tomorrow into notifications: each task
  `reminderLeadMinutes` before its planned start (or at its own "remind me at" time), each fixed event the same lead
  before it, and a morning brief at the start of the day. Personal tasks show "Personal task", never the title; the
  lock screen shows only "Reminder". `reminders.sync` (L1, user-only) registers them with AlarmManager and is run
  after every plan change, on open, each night after midnight and after a restart, a clock change or an app update.
- Notification buttons: Done marks the task done through the orchestrator; a personal task asks the owner to open the
  app, because it needs the fingerprint, face or PIN. "In 10 min" snoozes a task or event reminder.
- Deferred from the part 4 list: standing approvals (OQ-18). The build doc's "daily WorkManager job" is an
  AlarmManager alarm shortly after midnight instead, so no new dependency is needed.
- Routines (F5): `routines.create/update/delete/materialize`. "Gym every weekday at 7am" now proposes the first task
  and a routine; the routine adds one task on each due day from then on. Deleting a routine task skips it for that
  day (kept as CANCELLED, hidden) so it isn't added back. New Routines tab.
- `DailyMaintenance` runs the routine and reminder tools through the orchestrator as the new `Requester.SYSTEM`
  (policy: L0/L1 tools that never need a confirmation only; audited as SYSTEM), one run at a time, skipping calls
  that would do nothing. Routine tasks have a fixed id per routine and day, so overlapping runs can't duplicate them.
  The nightly alarm is set after every run, so routines keep working when notifications are refused.
- Room schema v5 (`MIGRATION_4_5`): `tasks.routineId`, `tasks.remindAt`, `routines.dayOfWeek`, `routines.remind`.
- Permissions: POST_NOTIFICATIONS (asked once, after setup) and RECEIVE_BOOT_COMPLETED; one non-exported receiver.
  Still no INTERNET.

Checks run here: `:domain:test` passes (new `RoutinesAndRemindersTest`, golden evaluation unchanged). `:data`, `:app`,
lint and the APK run in CI.

## 2026-10-27 — Consolidation into `equisteg-com/MyDayComapanion`

Every earlier version of the app is combined here, with its commit history:
- `equisteg-com/MyDayCompanion` `development` (cf01b3d): Phase 0 foundation on template v4, product override,
  Android CI. Brought in as one commit, because that repository's history had been reset.
- `equisteg-com/MyDayCompanion-archive` branch `claude/project-thread-d6ydk7` (PR #15, head 6357743): agent OS
  parts 1–3. Its 10 commits were cherry-picked unchanged and applied without conflicts.
- Checked and already included: the project-files bundle (`feature/phase0-foundation` a61b830 is an ancestor of
  PR #15), the part 2 patch (same as commit 4be93fb), the prebuild documents (the repository copies are newer), the
  agent OS build document (identical) the MyDayGuide spec (renamed to `MYDAYCOMPANION_BUILD_SPEC.md`), and the Phase 0 feature inventory
  (superseded by `docs/architecture/cloud-infrastructure.md` §3).
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
