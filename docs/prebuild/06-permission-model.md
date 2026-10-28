# 06 — Permission model

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| PM-01 | Permission matrix complete | PASS | Android permissions table below. |
| PM-02 | Every permission justified | PASS | Phase 0 requested none; USE_BIOMETRIC / USE_FINGERPRINT added for owner verification (build doc §6.2), justified below. |
| PM-03 | Tool allowlist complete | PASS | Tool table below = `ToolRegistry` contents in `AppContainer`. |
| PM-04 | Agent authorization flow complete | PASS | Policy rules below; `ToolAuthorizationPolicyTest`. |
| PM-05 | Revoked/denied permission handled | PASS | Checked live on every call (`AndroidPermissionChecker`); denial → `PERMISSION_MISSING`, audited. |
| PM-06 | Risk level definitions | PASS | Master §17 wins over spec §16 (03 X1). |

## Android permissions

| Permission | Phase | Purpose | Risk | Status |
|---|---|---|---|---|
| INTERNET, ACCESS_NETWORK_STATE | never in V1 | — | — | Explicitly removed (`tools:node="remove"`) |
| USE_BIOMETRIC, USE_FINGERPRINT (API < 28) | 1 (agent OS PR 2) | Show the system fingerprint/face/screen-lock prompt for app lock and L3/L4/personal approvals. Install-time (normal) permissions, no runtime dialog; declared by `androidx.biometric` and declared explicitly in our manifest so the allowlist test sees them. The app never receives biometric data, only success or failure. | L0 | Declared |
| POST_NOTIFICATIONS (API 33+) | 1 (agent OS part 4) | Local reminders, the Done button and the morning brief. Asked once after first-run setup; if refused, reminders are skipped (no tool call is made) and everything else works. Lock-screen notifications show only "Reminder" (`VISIBILITY_PRIVATE` with a public version); personal tasks never show their title anywhere. | L1 | Declared |
| RECEIVE_BOOT_COMPLETED | 1 (agent OS part 4) | Android drops alarms on restart. `ReminderReceiver` (not exported) re-runs daily maintenance after boot, an app update or a clock/time-zone change. Install-time permission, no dialog. | L0 | Declared |
| RECORD_AUDIO | 1 (agent OS part 5) | Press-to-talk (F6). Asked on the first tap of 🎤; the recogniser runs only from the tap to the end of the sentence; no wake word, no background listening. On Android 12+ phones with on-device recognition the audio never leaves the phone; elsewhere the phone's default recogniser is used with "prefer offline" and Settings says it may use its provider's service. The words only fill the input box. | L1 | Declared |
| READ_CALENDAR | 1 (agent OS part 5) | Calendar events planned around (F7), read-only, only while the owner's "Show my calendar in plans" switch is on. All-day, declined and hidden events are skipped; titles that look like instructions are shown as "Calendar event". Nothing is written back or copied out except into the owner's own plans. | L1 | Declared |
| SET_ALARM | 1 (agent OS part 5) | Hand an alarm to the clock app (F8, `device.setAlarm`). Install-time (normal) permission. | L3 action | Declared |
| CALL_PHONE, SEND_SMS, WRITE_CALENDAR | never | Device actions open the dialler, a message draft or the calendar's new-event screen; the owner presses Call, Send or Save there | — | Not requested (`ManifestSecurityTest` fails if added) |
| SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM | not planned | `setAndAllowWhileIdle` alarms need no permission; in deep idle they can arrive a few minutes late (OQ-17) | — | Not requested |
| WRITE_CALENDAR, contacts, SMS, location, storage | not planned | — | — | Not requested |

Components: one exported activity (the launcher) and one receiver, `.reminders.ReminderReceiver`, with
`exported="false"`; `ManifestSecurityTest` fails on any other receiver, any exported receiver, or any service or provider.

Adding one requires: a row here, the allowlist in `ManifestSecurityTest`, a just-in-time rationale screen, and
graceful behaviour when denied.

## Tools (allowlist)

| Tool | Risk | Agent may propose | Confirmation | Android permission | Flag | Verification |
|---|---|---|---|---|---|---|
| `tasks.create` | L2 | yes | when proposed by agent | — | — | re-read created ids |
| `plan.generate` | L1 | yes | never | — | — | re-read + `PlanValidator` |
| `tasks.complete` / `tasks.reopen` | L1 | yes | when proposed by agent; always for a personal task | — | — | status is DONE / OPEN |
| `tasks.update` | L2 | yes | when proposed by agent; always for a personal task | — | — | every field re-read and equal |
| `tasks.delete` | L2 | yes | always | — | — | task and its subtasks can no longer be read |
| `tasks.addSubtask` | L2 | yes | when proposed by agent; always under a personal task | — | — | new subtask re-read with its parent |
| `settings.update` | L1 | **no** | never | — | — | stored preferences equal requested |
| `reminders.sync` | L1 | **no** | never | POST_NOTIFICATIONS | reminders (on) | scheduler registered exactly the planned reminders |
| `routines.create` / `routines.update` | L2 | yes | when proposed by agent | — | routines (on) | routine re-read and equal |
| `routines.delete` | L2 | yes | always | — | routines (on) | routine can no longer be read; its tasks stay |
| `routines.materialize` | L1 | **no** | never | — | routines (on) | each due routine has exactly one task that day |
| `device.setAlarm` / `device.dial` / `device.composeMessage` / `device.navigate` / `device.calendarInsert` | L3 | yes | always (tap + device credential) | SET_ALARM for alarms | — | another app accepted the intent; arguments (numbers, addresses, text) never audited |
| `voice.speak` | L1 | **no** | never | — | voice (on) | engine accepted the text; the text is never audited |
| `data.export` | L3 | **no** | always | — (SAF) | — | bytes written = export size |
| `data.deleteAll` | L4 | **no** | always | — | — | every store counts 0; then the app deletes the database files and destroys the key |
| `security.updateSettings` | L4 | **no** | always | — | — | stored settings equal requested |
| `security.setChallenges` | L4 | **no** | always | — | — | stored hashes equal requested |

## Owner verification (build doc §6)

After the policy returns `RequireConfirmation`, the UI collects the approval steps from `ApprovalRules`
before it creates the `UserConfirmation`: a tap for every level; the device credential
(`BiometricPrompt`, `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on Android 11+, `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` before)
for L3 and L4; one challenge answer for L4 when questions are set up. A phone without a screen lock falls back to a
challenge answer; with neither, L3/L4 actions are unavailable. Challenge answers are PBKDF2-HMAC-SHA256 hashes
(310,000 iterations, 16-byte salt). 5 wrong answers lock for 15 minutes; 10 in 24 hours require the device credential,
or wipe local data when the owner opted in.

Personal items: a tool reports through `AgentTool.touchesPersonal` when its arguments act on a task the owner marked
personal (`tasks.update`, `tasks.delete`, `tasks.complete`, `tasks.reopen`, `tasks.addSubtask`). The policy then
always requires a confirmation, and `ApprovalRules` adds the owner's chosen check (device credential by default, or a
challenge answer) after the tap. Taking the personal mark off a task is itself a change to a personal item.

## Policy (deterministic, first failing rule wins)

1. Unknown tool → deny `UNKNOWN_TOOL`.
2. Feature flag off → deny `FEATURE_DISABLED`.
3. Agent requests a non-proposable tool → deny `NOT_AGENT_PROPOSABLE` (even with a confirmation).
3a. The app's own scheduled work (`Requester.SYSTEM`: nightly run, after a restart) requests anything above L1 or
    anything that can need a confirmation → deny `NOT_SYSTEM_RUNNABLE`. It is audited as actor SYSTEM.
4. Arguments fail the tool's own validation → deny `INVALID_ARGUMENTS`.
5. Required Android permission not granted now → deny `PERMISSION_MISSING`.
6. L3/L4, or confirmation ALWAYS, or a personal item, or WHEN_PROPOSED_BY_AGENT with an agent requester → require a
   `UserConfirmation` whose fingerprint matches SHA-256(tool id + canonical args); mismatch → deny.
   A SYSTEM requester can never satisfy this step → deny `NOT_SYSTEM_RUNNABLE`.
7. Otherwise allow. Every outcome is audited.
