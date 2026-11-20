# MyDayCompanion 0.3.0 premium — release acceptance evidence

**Status: development preview; not a certified worldwide production release.**

## Shipped in the 0.3 development source

| Area | Implemented behavior | Automated evidence |
|---|---|---|
| Local AI | Text and device speech commands, deadline and seven-day summaries, explain-plan and conflicts, contextual parse recovery | Domain agent command/parser tests, app agent/voice UI tests |
| Planner | Day timeline, fixed events, deadlines, buffers, preference-based drag and 15-minute accessibility controls; guarded undo | Planner validator tests, TimeShiftTest, VisualRescheduler tests |
| Calendar | Week and locale-sensitive month navigation, future routine forecasts without duplicate materialization | PremiumOperationsTest, PremiumWorkflowUiTest, international week tests |
| Tasks | Subtasks, routines, reminders, status/priority and notes search, templates, guarded bulk updates | Tool integration tests, PremiumBatchIntegrationTest, bounded search |
| Productivity | Focus, insights and local progress coaching with direct action links | Agent, focus, coach and UI test coverage |
| UX | Material 3, theme modes, five-page premium system, small/large screen navigation adaptation | UI regression and responsive tests |
| Security/privacy | SQLCipher/Keystore local data, encrypted file backup, approvals, live permission summary, export/erase controls | Security and backup suites, APK merged-manifest network-permission check |

**Important distinctions:** Time drag adjusts a task's *preferred* time, not a guarantee that fixed meetings can be overlapped. Bulk edits preflight all tasks and are audited individually; if a write fails mid-batch the UI must disclose partial completion. Personal tasks and protected subtasks require individual approval. Built-in templates populate editable text; they do not save tasks without confirmation. Offline ML Kit Nano availability varies by device.

## Real-device acceptance matrix — human evidence REQUIRED

For each of Android 8.0 (minimum), Android 10/12, Android 13/14, and Android 16 (target), exercise:

1. Install/upgrade: package opens in Android Package Installer; version code **3**, version name **0.3.0-premium**; older debug key compatibility; storage survives a verified *same-signature* update. If debug signatures differ, back up user data before uninstalling.
2. First launch: four onboarding steps, revoke each optional permission and prove manual text planning still works without mic, calendar, or notifications.
3. Assistant: enter plain language, ambiguous and malformed dates, no-task command, multiple name matches, deadline summary, explain schedule; ask a question using voice where a supported engine is available; test opt-out of phone speech.
4. Day planner: fixed meeting, deadline clash, overfull day, rollover near midnight/DST, prior date, no calendar permission, replan; ensure unscheduled tasks are not silently lost.
5. Schedule control: long-press/drag one flexible block, alternate ±15-minute buttons, observe conflicts and updated plan, then undo. Fixed event, personal task, and routine must not be freely draggable.
6. Task editor: create and complete parent+steps; move parent and verify each subtask's date/deadline; test private child requiring device credential for move/delete/rollover.
7. Bulk edit: select ordinary tasks, preview and confirm, move/reopen/prioritize; select a personal task or parent with a private step and verify the entire preflight rejects it.
8. Calendar and routines: US Sunday-first versus UK/India Monday-first, 30/31-day and leap-February months, future recurring previews, no duplicate routine materialization; notifications across reboot/timezone changes.
9. Focus/report: focus timer during background/lock, progress counts, completion accuracy, missed deadline and backlog guidance; check day switching.
10. Data rights: export all data (warn that JSON is plaintext), encrypted backup with wrong passphrase, restore after reinstall, erase including encryption key, auditable approval prompts, history retention.
11. Accessibility: full TalkBack traversal and actionable label checks, switch access, hardware keyboard and D-pad focus, font scale **200%**, display zoom, landscape, small 320dp screen and 840dp tablet/foldable, light/dark contrast, RTL layouts. Automated Compose tests do **not** replace these real-device checks.
12. Regional acceptance: localize copy/time formats; obtain publisher legal notices and youth-audience decisions; verify India DPDP, US privacy/COPPA and state laws, Canada PIPEDA/provincial, UK GDPR/Children's Code, Australia Privacy Act/APP rules. See `docs/release/global-compliance-readiness.md`; legal counsel sign-off remains mandatory.

Record for each device: OS/API, model, locale, permissions, APK SHA-256, scenario result, screenshots/video where safe, and defect issue links. Redact personal task content from support tickets.

## Load and distribution — what is actually tested

- `OfflineWorkloadSimulationTest`: **100,000 sequential** deterministic planner operations with validator checks on a single runner.
- `TaskSearchTest`: bounded output and correct 100,000-record local search result counts.
- These do **not** establish 100,000 simultaneously active Android devices or 10–100 million successful downloads. Without a backend, scheduling and stored data scale independently on users' own devices; each device still needs memory, CPU, disk, battery, and privacy testing.
- A 25 MB payload across **10 million** downloads is about **250 TB** and **100 million** downloads is about **2.5 PB** transferred. Actual APK size, app bundles, regional delivery and CDN charges differ. GitHub Actions artifacts are transient preview downloads, **not** a free global CDN, production store or download stress-test endpoint.
- Never run large-scale download scripts against Google Play, GitHub or a provider without authorization, quotas, and an explicit budget.
- Verify Android vitals (ANR, startup, battery, storage), SQLCipher restore on low-storage phones, and large task databases on physical devices before rollout.

## Release blocks (do not promote this debug artifact as production)

- Android test+lint+schema+dependency+APK-manifest security checks all green on **one final commit**.
- Release keystore stored only in secrets, signed Play App Bundle verified independently; maintain versioned upgrade test. No signing material in repository.
- Required regional user-facing terms, store disclosures, customer-support/deletion handling and user-rights procedures written and reviewed.
- TalkBack/font scaling/manual device matrix and battery/performance baselines completed with evidence.
- Small opt-in tester release, crash/ANR collection with consent and privacy-respecting diagnostic process; production rollback path and owner on call.

**Cost policy:** Changes here use existing open-source/Android tooling. Production store accounts, distribution bandwidth, physical devices, legal review and some third-party speech engines cannot be guaranteed zero-cost or no-card.
