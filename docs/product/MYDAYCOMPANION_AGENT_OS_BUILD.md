# MyDayCompanion — Personal Agent OS: Revised Build Document (v2)

| | |
|---|---|
| Status | Draft for founder approval, 2026-10-09 |
| Replaces | Scope sections of `docs/product/MYDAYCOMPANION_MASTER_BUILD.md` §10–§11 and `MYDAYCOMPANION_BUILD_SPEC.md` §8 (Phase 0/1 split) |
| Keeps | Every security, privacy and AI-trust rule of the master document (§12–§23) unless this document says otherwise |
| Repo copy | `docs/product/MYDAYCOMPANION_AGENT_OS_BUILD.md` (top of the precedence list in `CLAUDE.md`) |
| Owner | Founder (Equisteg) |

---

## 0. Why this revision exists

The founder installed the Phase 0 build on a phone and reported:

1. **It does not understand normal text.** Messy input, times written in odd places or odd formats ("gym 6am", "call mom around 4ish", "tmrw 3-4pm dentist", "half past 7 dinner") was dropped or mis-read.
2. **Many features do not work.** Reminders, voice, calendar, routines, memory, subtasks, editing, settings and onboarding were interfaces behind switched-off flags (see `MyDayCompanion-feature-inventory.md`). "Delete everything" left the encryption key behind.
3. **The product must become a personal agent OS**: it takes care of the owner's activities by itself, but only after the owner approves, using device security or preset challenge questions for personal tasks.
4. **Two risks of a local-only agent must be solved:** moving to a new phone, and a stolen phone.
5. **Business model:** a customised agent that runs locally on each customer's device, charged monthly to the customer, built and run at zero cost to Equisteg.
6. **All features ship now**, not in a later phase. Anything that costs money is listed with its price and is not built until the founder approves it.

### Root causes found in the code

| Symptom | Cause | Fix in this document |
|---|---|---|
| Times not understood | `RuleBasedTaskExtractor` only accepts a time after the word "at" (`at 5`, `@5`). "gym 6am", "5pm call", "3-4pm", "half past 7", "noon", "tonight at 9", "in 2 hours" were ignored or became part of the title | §5: new time and date grammar (`TimeParser`, `DateParser`), schema `task-extraction/v2` |
| Wrong splitting | Every "and" splits a task: "meeting with Tom and Jerry at 3" became two items | §5.4: split only where a new action starts |
| One day per message | The schema carries one `targetDate`; "today X, tomorrow Y" put both on one day | §5.6: per-item `date` |
| No soft times | "go for a run in the morning" had nowhere to go | §5.6: `preferredStart` on tasks; planner honours it |
| Features "not working" | Flags `reminders`, `voice`, `calendar_read`, `routines`, `local_llm` off; only `No*` implementations wired | §4: every feature built and turned on |
| Key survives delete | `AppContainer` passes a no-op `platformWipe` | §8.4: wipe Keystore alias, wrapped key file and DB files |

---

## 1. Product definition

**MyDayCompanion is a personal agent OS for Android.** The owner talks or types the way they normally would. The agent turns that into tasks, events, reminders and routines, plans the day around fixed commitments, keeps the plan current as the day changes, and carries out device actions (reminders, alarms, calls, messages drafts, navigation, calendar entries) **only after the owner approves**. Everything runs on the phone. Nothing about the owner leaves the phone unless the owner exports it.

### 1.1 What "agent OS" means here, honestly

Android does not let any Play Store app control the whole phone. The agent can do everything the Android sandbox allows a normal app to do, and nothing more:

| The agent can | How | The agent cannot (by Android design) |
|---|---|---|
| Plan, track and re-plan the day | Its own database and planner | Read other apps' data (WhatsApp, Gmail, bank apps) |
| Remind the owner at the right time, even after reboot | `AlarmManager` + notifications + boot receiver | Tap buttons inside other apps (needs an Accessibility service, which Play restricts to accessibility use) |
| Set phone alarms and timers | `AlarmClock.ACTION_SET_ALARM` intent | Send an SMS or place a call silently (Play restricts SMS/Call-log permissions) |
| Start a call, an SMS or e-mail draft, or a WhatsApp chat | `ACTION_DIAL`, `ACTION_SENDTO`, share intents; the owner presses Send | Pay, transfer money or log in anywhere for the owner |
| Start navigation | `geo:` / maps intent | Run forever in the background (Android battery limits) |
| Read the owner's calendar | `READ_CALENDAR` (read-only) | Change other apps' settings |
| Take voice commands and speak back | On-device `SpeechRecognizer` and `TextToSpeech` | Listen all the time for a wake word (battery, privacy, Play policy) |

This is the boundary that keeps the product approvable on Google Play and safe if the phone is lost. Each intent-based action is an approved, audited tool (§6, §7).

### 1.2 Customer and business model

| Item | Decision |
|---|---|
| Customer | One owner per phone. Personal use. |
| Price model | Monthly subscription through Google Play Billing; a free tier with the core planner. Price is a founder decision (§13, D-01). |
| Equisteg running cost per customer | ₹0. No servers, no cloud AI, no per-user infrastructure (§10). |
| Unavoidable cost per sale | Google Play service fee on subscriptions (§10.2). This is a per-transaction fee, already accepted by company rules. |
| "Customised agent" | Each install learns the owner's preferences locally (day window, buffer, routines, approved memories, challenge questions). There is no shared model or account. |

---

## 2. Non-negotiables (kept from the master document, plus new ones)

Kept:

1. Local-first. No backend, cloud database, cloud AI, analytics, crash SDK, ads or account system in the default build.
2. Model output is untrusted data. It can only produce schema-valid `task-extraction` JSON, validated before use.
3. Every effect goes through `AgentOrchestrator.execute` → `ToolRegistry` → argument validation → `ToolAuthorizationPolicy` → Android permission → tool → verification → audit.
4. Only a user gesture creates a `UserConfirmation`. Model output never can.
5. Audit entries never contain task titles, memory content or raw user text.
6. Database changes bump `MdcDatabase.VERSION` with a `Migration`; no destructive fallback.
7. Never weaken a test, validator, policy or manifest check to make CI pass.

New:

8. **Owner verification is part of approval.** High-risk and personal actions need the owner's device credential (fingerprint, face, PIN, pattern or password) or a preset challenge answer, not only a tap (§6).
9. **Zero cost by default.** Anything with a price is listed in §10 with options and stays unbuilt until the founder approves it in writing.
10. **The phone can be lost.** Data at rest must stay unreadable to a thief, and the owner must have a way to recover onto a new phone (§7, §8).
11. **No silent autonomy.** Standing approvals ("always do this") exist only for low-risk actions, expire, and can be revoked in one tap (§6.4).

---

## 3. Users' core loop

```
Owner speaks or types (any order, any format)
  → Understand: tasks, events, times, dates, durations, priorities, repeats, reminders
  → Show a proposal: the plan for each day affected, reminders, actions
  → Owner approves (tap; plus device credential or challenge answer when the risk needs it)
  → Agent acts: saves, plans, schedules reminders, fires intents
  → Agent keeps watch: reminders fire, plan re-flows when the owner is behind or a task is done
  → History shows every action, without personal content
```

---

## 4. Feature scope — everything ships now

Legend: **Build** = implemented and switched on in this release. **Decision** = designed here, built only after the founder approves the cost or policy question in §13.

| # | Feature | What the owner gets | Status in this release | Android permission | Risk level |
|---|---|---|---|---|---|
| F1 | Natural-language understanding v2 | Messy text, jumbled times, many days in one message, soft times, repeats, reminders | Build (PR 1) | none | — |
| F2 | Tasks: create, edit, reschedule, delete, subtasks, notes | Full task management | Build | none | L1–L2 |
| F3 | Planner v2 | Multi-day, preferred times, deadlines, buffers, automatic re-plan | Build | none | L1 |
| F4 | Reminders and notifications | Reminders at a time or before an event; survive reboot; snooze; done from the notification | Build | `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` | L1 |
| F5 | Routines | "Gym every weekday at 6am", "water plants every Sunday"; generated into each day | Build | none | L2 |
| F6 | Voice in and out | Press-to-talk with the on-device recogniser; spoken summary of the plan | Build | `RECORD_AUDIO` | L1 |
| F7 | Calendar read | Calendar events appear as fixed events in the plan | Build | `READ_CALENDAR` | L1 |
| F8 | Device actions | Set phone alarm, start a call, SMS/e-mail draft, navigation, add to calendar via the calendar app | Build | none (intents) | L3 |
| F9 | Memory | The agent proposes things to remember ("you prefer gym before 8"); the owner accepts or rejects; review and delete screen | Build | none | L2 |
| F10 | Settings and onboarding | Day window, buffer, date format, voice, reminders lead time, approvals, security; 4-step first-run setup | Build | none | L1 |
| F11 | Owner verification and approvals | Biometric/device-credential approval and challenge questions; standing approvals | Build | `USE_BIOMETRIC` (normal permission) | — |
| F12 | App lock | Lock on open and after N minutes in background | Build | `USE_BIOMETRIC` | — |
| F13 | Encrypted backup and phone transfer | Passphrase-encrypted backup file the owner keeps; restore on the new phone | Build | none (system file picker) | L3/L4 |
| F14 | Theft protection | Keys locked by device security, wrong-answer lockout and wipe, panic wipe, guidance for Find My Device erase | Build (local parts); remote wipe = Decision | none | L4 |
| F15 | Export and delete everything | Fix: delete also destroys the key, wrapped key file and database files | Build (PR 2) | none | L3/L4 |
| F16 | History | Every action with risk, decision, result; filter; retention | Build | none | L0 |
| F17 | On-device language model | Better understanding for long or unusual sentences, still validated | Decision D-03 (₹0 options exist) | none | — |
| F18 | Subscription | Monthly plan via Play Billing; free tier stays offline | Decision D-01/D-02 | see §10.3 | — |
| F19 | Home-screen widget and quick-add tile | Today's next items; quick add | Build | none | L1 |

Out of scope, even now, because Android or Play policy forbids them or they need money the founder has not approved: silent SMS/calls, controlling other apps, always-on wake word, cloud sync, account system, payments on the owner's behalf, banking.

---

## 5. Text and time understanding (F1) — the first fix

### 5.1 Goal

The owner can type the way they speak, in any order, with typos and shorthand, and the agent gets it right or says exactly what it could not understand. Target on the golden set: **≥ 95% exact extraction**, 100% schema validity, 100% unsafe-action rejection, 100% plan validity.

### 5.2 Pipeline

```
raw text
 → InputNormalizer (control chars, ≤2,000 chars)
 → TextCleaner: lower-case copy for matching, typo/shorthand map, unify dashes and "a.m."
 → DateParser: finds every date phrase and its span
 → TimeParser: finds every time, range, day-part, relative time and deadline with its span
 → Clause splitter (§5.4)
 → Per clause: attach the nearest date and time, read duration, priority, repeat, reminder
 → Classify: fixed event (has a clock time or range) / task with soft time / task with deadline / plain task
 → Emit task-extraction/v2 JSON
 → TaskExtractionValidator (schema + domain) → planner
```

Everything above runs on the phone in pure Kotlin (`domain/ai/text/*`). An on-device LLM (F17), when approved, is one more provider in the same router and its output passes the same validator.

### 5.3 What must be understood

**Dates**

| Written | Meaning (today = Tue 6 Oct 2026) |
|---|---|
| today, tdy, tonight, this evening | 6 Oct |
| tomorrow, tmrw, tmr, tmro, 2moro, tomorow, tommorow | 7 Oct |
| day after tomorrow, overmorrow | 8 Oct |
| mon / monday / this monday / on monday | next Monday after today (12 Oct) |
| next friday | 9 Oct (the coming Friday; matches how most users speak) |
| this weekend / weekend | Saturday 10 Oct |
| next week | Monday 12 Oct |
| in 3 days / 3 days later | 9 Oct |
| 12th, the 12th, on 12th | 12 Oct (next month if already past) |
| 12 Oct, Oct 12, 12 October, October 12th | 12 Oct |
| 12/10 | 12 Oct in day-month order (setting: day-month / month-day; default day-month for India) |
| 2026-10-12 | 12 Oct |

**Times**

| Written | Meaning |
|---|---|
| 5pm, 5 pm, 5p, 5PM, 5 p.m. | 17:00 |
| 5:30, 5.30, at 0530, at 1730 (4 digits need "at"/"by" or am/pm) | bare-hour rule for 5:30 / 5.30; 05:30; 17:30 |
| 17:30, 17.30 | 17:30 |
| at 5 / @5 / around 5 / ~5 / 5ish / by about 5 | bare-hour rule |
| 5 o'clock | bare-hour rule |
| noon, midday, lunch time | 12:00 |
| midnight | read as a 23:59 deadline (events cannot cross midnight) |
| half past 7, quarter past 7, quarter to 8, 10 to 8, 20 past 6 | 19:30 / 19:15 / 19:45 / 19:50 / 18:20 (bare-hour rule) |
| 7 in the morning / 7 morning / 7am | 07:00 |
| 7 in the evening / 7 tonight / 7 at night | 19:00 |
| 3-4pm, 3 to 4 pm, from 3 till 4, between 3 and 4, 3pm–4:30pm | start 15:00, duration 60 / 90 minutes |
| in 2 hours, in 30 min, after 1 hour | now + offset (today only) |
| morning / afternoon / evening / night (no clock time) | soft time: 09:00 / 14:00 / 18:00 / 20:00 as `preferredStart` |
| before 10, by 5pm, by noon, by eod, end of day, before lunch | deadline 10:00 / 17:00 / 12:00 / 18:00 / 18:00 / 12:00 |
| after lunch, after 6 | soft time 13:30 / 18:00 |

**Bare-hour rule** (no am/pm given): 1–6 → pm; 7–11 → am, unless the clause has an evening word (dinner, supper, party, movie, drinks, tonight, night, evening, date) → pm; 12 → noon. A meal word sets the half of the day (breakfast → am; lunch → around noon; dinner → pm).

**Position does not matter.** All of these mean the same: "call mom at 5pm", "5pm call mom", "call mom 5pm", "at 5 pm, call mom", "call 5pm mom" (time is cut from wherever it is and the rest is the title).

**Durations**: for 2 hours, 2h, 2hrs, 90 min, 1.5h, 1h30, an hour, half an hour, an hour and a half, 45-min, (30m), 2-3 hours. Ranges give the duration directly. No duration → the keyword table (calls 15, writing 60, report 120, gym 60, groceries 45 …) or 30 minutes.

**Numbers that are not times stay in the title**: "read 3 chapters", "read pages 3-4", "pay rs 12.50", "task number 5". A bare number only becomes a time with "at/@/by/around", am/pm, a colon, "o'clock/ish", a part of day, or straight after an event word ("dentist 4", "lunch 1 to 2").

**Priority**: urgent, asap, important, critical, must, high priority, top priority, !!, ! → HIGH; maybe, if time, optional, someday, low priority, if possible → LOW. A deadline also raises priority to HIGH.

**Repeats**: every day, daily, each morning, every weekday, weekdays, every monday, mondays, every week, weekly → routine (F5).

**Reminders**: "remind me at 5 to stretch", "remind me 10 min before the meeting", "don't let me forget X at 6" → a task plus a reminder at that time.

**Typos and shorthand map** (applied to a matching copy, never to the stored title except obvious fixes): tmrw→tomorrow, tdy→today, mins→min, hrs→hours, appt→appointment, mtg→meeting, b4→before, w/→with, pls/plz→please, n→and (between items), &→and, +→and.

### 5.4 Splitting one message into items

1. Always split on new lines, `;`, bullet characters and sentence ends.
2. Split on `,` unless the right side is a plain object continuing a list started by a verb ("buy eggs, milk and bread" stays one task: "Buy eggs, milk and bread").
3. Split on `and`, `then`, `also`, `plus`, `after that` **only when** the right side starts a new action: a known verb (call, buy, email, finish, go, pick up, meet, pay, book, send, write …), a pronoun lead-in (I, I'll, I have, we need), a date or time phrase, or a known event noun with a time. Otherwise keep it: "meeting with Tom and Jerry at 3" is one event.
4. A date phrase that starts a clause ("tomorrow: …", "on friday …") applies to every following item until the next date phrase. A date inside an item applies only to that item.

### 5.5 Classification

| The item has | It becomes |
|---|---|
| A clock time or a range ("dentist 4pm", "pick up the kids at 5:30", "lunch 1 to 2") | Fixed event at that time (duration from the range, an explicit duration, or 60 minutes; 15 for calls and stand-ups) |
| A clock time on a "remind me …" or a repeating item | Task placed at that time (`preferredStart`, and `remindAt` for reminders) |
| A day-part or "after X" | Task with `preferredStart` |
| "by / before X" | Task with deadline |
| "every …" | Routine proposal |
| "remind me …" | Task + reminder |
| An event word but no time ("I have a dentist appointment") | Warning: "When is the dentist appointment?" — shown in the proposal with a time picker, never silently dropped |
| Time in the past for today ("at 8am" when it is 10am) | Kept, with a warning "8am has passed — move to tomorrow?" offering one-tap change |

### 5.6 Schema `task-extraction/v2`

Unknown fields still invalidate the whole output.

```json
{
  "version": 2,
  "targetDate": "YYYY-MM-DD",
  "tasks": [{
    "title": "string ≤120",
    "priority": "LOW|MEDIUM|HIGH",
    "durationMinutes": 5..480,
    "date": "YYYY-MM-DD|null",
    "deadline": "HH:mm|null",
    "preferredStart": "HH:mm|null",
    "repeat": "DAILY|WEEKDAYS|WEEKLY|null",
    "remindAt": "HH:mm|null"
  }],
  "fixedEvents": [{
    "title": "string ≤120",
    "date": "YYYY-MM-DD|null",
    "start": "HH:mm",
    "durationMinutes": 5..480
  }],
  "warnings": ["string ≤200"]
}
```

`date: null` means `targetDate`. Dates must be between today and today + 366 days. A `fixedEvent` may not cross midnight. The prompt version is bumped to 2 and the rule extractor to `rule-extractor/2`.

### 5.7 Golden evaluation

`tests/testdata/golden/task-extraction.v2.json` contains every v1 case unchanged plus 50 messy cases (75 in total) (jumbled time position, typos, ranges, day-parts, multi-day, lists, names with "and", deadlines, repeats, reminders, past times). The v1 file stays for history. Thresholds: structured validity 1.0, extraction ≥ 0.95, plan validity 1.0, unsafe rejection 1.0. CI fails below threshold.

### 5.8 When the agent is unsure

It never guesses silently. The proposal card shows each item with what was understood, and highlights anything uncertain ("I read '7' as 7:00 am") with a one-tap fix. Nothing is saved until the owner approves.

---

## 6. Approvals and owner verification (F11)

### 6.1 Approval levels

| Risk | Examples | Default approval |
|---|---|---|
| L0 | Read the plan, show history | None |
| L1 | Re-plan, complete a task, schedule a reminder, voice summary | None when the owner started it; one tap when the agent proposed it |
| L2 | Save tasks, events, routines; edit or delete one task; accept a memory | One tap on the proposal |
| L3 | Device actions (alarm, call, SMS/e-mail draft, navigation, calendar insert), export, restore from backup | Tap **plus** device credential (fingerprint/face/PIN/pattern/password) |
| L4 | Delete everything, change security settings, turn off app lock, remove challenge questions, disable wipe-on-failure | Device credential **plus** one challenge answer |
| Personal | Any item the owner marks "personal", any memory marked sensitive, any action on a task in the "personal" list | Challenge answer **or** device credential (owner chooses in settings) |

The owner can raise any level (for example, require the device credential for every save) but not lower L3/L4 below the defaults.

### 6.2 Device security (BiometricPrompt)

- `androidx.biometric` (Apache-2.0) `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`, so a phone without a fingerprint still works with its PIN.
- The prompt text names the action: "Approve: start a call to Raj". The `UserConfirmation` is created only in the success callback, and it is bound to the action fingerprint (SHA-256 of tool id + canonical arguments), as today.
- For L4 and for unlocking backups, the approval is tied to a Keystore key created with `setUserAuthenticationRequired(true)` and `setInvalidatedByBiometricEnrollment(true)`: a new fingerprint added by a thief invalidates the key.
- If the phone has no screen lock, the app asks the owner to set one (L3/L4 actions are unavailable until then) or to use challenge questions only.

### 6.3 Challenge questions

- The owner picks 3 questions in onboarding from a list or writes their own, and types answers.
- Answers are normalised (trim, lower-case, collapse spaces, remove punctuation) and stored as **PBKDF2-HMAC-SHA256, 310,000 iterations, 16-byte random salt per answer** (built into the JDK; no new dependency). Only the hash is stored, in the encrypted database. Answers never reach a model, the audit log or an export in clear text.
- At approval time one question is chosen at random.
- 5 wrong answers → 15-minute lockout; 10 wrong answers in 24 h → the owner's chosen response: lock the app until the device credential is given (default) or wipe local data (opt-in, §8.3).
- Changing or removing questions is L4.

### 6.4 Standing approvals (the "takes care of things by itself" part)

- The owner can say "always do this" on an L1 or L2 proposal kind: for example "always schedule reminders for my routines", "always re-plan when I fall behind", "always add my calendar events to the plan".
- A standing approval is stored as a rule: tool id, allowed argument shape, scope (which routine or list), expiry (default 30 days, max 90), created time. It is shown in Settings → Approvals, revocable in one tap, and every use is audited as `decision=ALLOWED reason=STANDING_APPROVAL`.
- Never for L3, L4 or personal items. Never created by model output.

### 6.5 Background behaviour

What the agent does without the owner opening the app, all inside Android's battery rules:

| Trigger | Agent action | Approval |
|---|---|---|
| Reminder time | Notification with Done / Snooze 10 min / Open | None (owner already approved the reminder) |
| Phone restarted | Re-register all future reminders | None |
| 06:00 daily (inexact, WorkManager) | Generate routine items for today, re-plan, post "Your day" notification | Standing approval, or a notification asking "Plan today?" |
| Owner is behind (a task's slot ended, not done) | Notification "Move the rest of today?" with one-tap re-plan | Tap, or standing approval |
| Calendar changed | Re-plan affected day on next open | None (L1 on owner's data) |

---

## 7. Changing phones: backup and transfer (F13)

### 7.1 Principles

- The backup belongs to the owner. Equisteg never sees it. No cloud upload by the app.
- Android's own cloud backup stays **off** (`allowBackup=false`), because it would copy the database without the owner's passphrase.

### 7.2 Backup file

- Content: every store (tasks, events, plans, routines, memories, preferences, standing approvals, challenge-question hashes, audit), the same JSON as export plus a manifest.
- Encryption: owner passphrase (minimum 10 characters; strength meter) → **PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte salt** → 256-bit key → **AES-256-GCM** with a 12-byte random nonce. File format `MDCB` magic, version 1, KDF parameters, salt, nonce, ciphertext, tag. Everything from the JDK; no new library.
- File name `mydaycompanion-YYYYMMDD.mdcbackup`, saved through the system file picker wherever the owner chooses (phone storage, USB, their own Google Drive or OneDrive app, Quick Share to the new phone).
- Optional automatic weekly backup to a folder the owner picked once (Storage Access Framework persistable permission), still encrypted with the same passphrase.
- Approval: L3 (device credential).

### 7.3 Restore on the new phone

1. Install the app, choose "Restore from backup" in onboarding.
2. Pick the file, type the passphrase.
3. The file is decrypted and validated (schema, sizes, dates) as untrusted input before anything is written.
4. A new database key is generated on the new phone; the old phone's key is never in the backup.
5. The owner sets the device-credential approval again (biometrics are per device). Challenge questions come across as hashes.
6. Reminders and routines are re-registered.

Wrong passphrase 10 times → the restore screen locks for 1 hour. There is no recovery without the passphrase; the app says so plainly before the first backup.

### 7.4 Direct phone-to-phone transfer

Phase "now": through the backup file and Quick Share/USB (₹0, no network permission for the app). A direct Wi-Fi/Bluetooth transfer inside the app would need network permissions; it is listed as Decision D-05.

---

## 8. Stolen or lost phone (F14)

### 8.1 Layers

| Layer | What protects the owner | Cost |
|---|---|---|
| Android screen lock + full-disk encryption | Thief cannot open the phone or read storage | ₹0 (OS) |
| Android Theft Detection Lock, Offline Device Lock, Remote Lock (Android 10+ with Play services, rolled out 2024–25) | Phone locks itself if snatched; owner can lock it from android.com/lock | ₹0 (OS) |
| SQLCipher database + Keystore-wrapped key (today) | Copying the app's files gives ciphertext only | ₹0 |
| App lock (F12) | Even an unlocked phone does not open the agent without the device credential | ₹0 |
| Approval key invalidated by new biometrics | A thief who adds a fingerprint cannot approve L4 actions | ₹0 |
| Challenge-question lockout and optional wipe | Guessing answers locks or wipes the agent | ₹0 |
| Panic wipe | "Wipe now" from the lock screen with the challenge answer (for a phone about to be handed over) | ₹0 |
| Google Find My Device "Factory reset device" (find.google.com) | Owner erases the whole phone remotely, including this app | ₹0 (OS) — onboarding shows the owner how to turn it on |

### 8.2 What a thief gets

With the phone locked: nothing readable. With the phone unlocked but the app locked: nothing (app lock). With both unlocked (owner was using it): the thief can read the plan, but cannot run L3/L4 actions, export, change security or restore without the device credential and a challenge answer; the agent locks again after the configured idle time (default 5 minutes).

### 8.3 Wipe on repeated failure (opt-in)

When switched on: 10 failed challenge answers in 24 hours, or 20 failed app-lock attempts, run the same wipe as "Delete everything" (§8.4). Default off so a child playing with the phone cannot destroy data; onboarding recommends turning it on with a recent backup.

### 8.4 What "wipe" means (fixes the Phase 0 bug)

1. Cancel all reminders and work.
2. Empty every store and verify zero rows (today).
3. Close the database; delete the database file, `-wal`, `-shm` and `-journal`.
4. Delete the Keystore aliases (DB key-wrapping key, approval key) and the wrapped key file `db.key.v1.enc`.
5. Clear preferences files.
6. Audit is gone with the data; the app returns to onboarding.

### 8.5 Remote wipe of only this app

A wipe triggered from another device needs the app to receive a message from the internet, so it needs `INTERNET`, a push service and a small server. Options are in §10.3 (Decision D-04). Until approved, the remote answer is Find My Device erase.

---

## 9. Architecture changes

### 9.1 Modules (unchanged structure)

```
domain/  (pure Kotlin, no Android)
  ai/text/        TextCleaner, DateParser, TimeParser, ClauseSplitter, ItemClassifier   (new)
  ai/             RuleBasedTaskExtractor v2, TaskExtractionValidator v2, PromptRegistry v2
  planner/        DeterministicPlanner v2: preferredStart, multi-day, routines expansion
  approvals/      ApprovalLevel, OwnerVerifier interface, ChallengeQuestionService, StandingApprovalStore   (new)
  routines/       RoutineEngine (expand routines into a day)   (new)
  reminders/      ReminderPlanner (which reminders exist for a day)   (new)
  backup/         BackupCodec (PBKDF2 + AES-GCM), BackupService   (new)
  security/       WipeService, LockPolicy   (new)
  tools/          new tools listed in §9.2
data/
  db/             MdcDatabase v2: new columns and tables (§9.3), Migration 1→2
  encryption/     KeystoreDatabaseKeyProvider.destroy(), ApprovalKeyStore
  reminders/      AlarmManagerNotificationScheduler, BootReceiver, ReminderReceiver
  calendar/       ContentResolverCalendarProvider (read-only)
  voice/          AndroidSpeechProvider (on-device), AndroidTtsSpeaker
app/
  ui/             Today, Inbox/Tasks, Routines, Memory, History, Settings, Onboarding, Lock screen
  security/       BiometricOwnerVerifier, AppLockController
  widget/         Glance widget (if dependency approved as Apache-2.0) or RemoteViews
```

### 9.2 Tools (all through `AgentOrchestrator.execute`)

| Tool | Risk | Agent may propose | Confirmation |
|---|---|---|---|
| `tasks.create` (v2: per-item date, preferredStart, remindAt) | L2 | yes | tap when agent-proposed |
| `tasks.update` (title, date, time, duration, priority, notes, parent) | L2 | yes | tap when agent-proposed |
| `tasks.delete` (one task) | L2 | yes | tap always |
| `tasks.complete` / `tasks.reopen` | L1 | yes | tap when agent-proposed |
| `tasks.rollOver` (open, non-routine tasks from earlier days, with their steps, to today or up to 14 days ahead; max 50) | L2 | yes | tap when agent-proposed; screen lock if any is personal |
| `settings.privacy` (lock-screen text, widget names, History period; recorded in History without content; `userOnly`, so no background job can run it; a shorter period needs the confirmed prune first) | L1 | no | never (owner only) |
| `plan.generate` | L1 | yes | never |
| `routines.save` / `routines.delete` | L2 | yes | tap |
| `reminders.schedule` / `reminders.cancel` | L1 | yes | never (owner approved the item) |
| `memory.propose` (creates PROPOSED only) | L1 | yes | never |
| `memory.activate` / `memory.delete` | L2 | **no** (owner only) | tap |
| `device.setAlarm`, `device.dial`, `device.composeMessage`, `device.navigate`, `device.calendarInsert` | L3 | yes | tap + device credential |
| `data.export`, `backup.create` | L3 | no | tap + device credential |
| `backup.restore` | L4 | no | device credential + passphrase |
| `security.update` (lock, questions, wipe rules) | L4 | no | device credential + challenge |
| `data.deleteAll` | L4 | no | device credential + challenge |

### 9.3 Database v2

New columns: `tasks.notes`, `tasks.preferred_start`, `tasks.remind_at`, `tasks.personal`, `tasks.routine_id`, `fixed_events.calendar_event_id`. New tables: `reminders`, `challenge_questions` (question text, salt, hash, iterations), `standing_approvals`, `security_state` (failed attempts, lockout until). Migration 1→2 with an exported schema and a migration test.

### 9.4 Dependencies to add (all free, licences pre-approved)

| Library | Licence | Why |
|---|---|---|
| `androidx.biometric:biometric` | Apache-2.0 | Device-credential approval and app lock |
| `androidx.work:work-runtime-ktx` | Apache-2.0 | Daily planning job, routine generation |
| `androidx.navigation:navigation-compose` | Apache-2.0 | More screens |
| `androidx.glance:glance-appwidget` (optional) | Apache-2.0 | Widget |

No network, analytics or paid SDK.

---

## 10. Infrastructure and cost

### 10.1 What the product runs on — default build

| Piece | Choice | Where it runs | Cost to Equisteg | Cost to customer |
|---|---|---|---|---|
| Database | Room on SQLCipher (AES-256), key wrapped by Android Keystore | Customer's phone | ₹0 | ₹0 |
| Server / backend | **None** | — | ₹0 | — |
| API gateway | **None** (no network calls) | — | ₹0 | — |
| AI for understanding | Deterministic on-device parser (§5) | Phone | ₹0 | ₹0 |
| Planner | Deterministic on-device planner | Phone | ₹0 | ₹0 |
| Reminders | `AlarmManager` (inexact) + notifications + WorkManager | Phone | ₹0 | ₹0 |
| Voice | Android on-device `SpeechRecognizer` + `TextToSpeech` | Phone | ₹0 | ₹0 (may need the offline language pack from phone settings) |
| Calendar | Android Calendar Provider, read-only | Phone | ₹0 | ₹0 |
| Backup and transfer | Encrypted file the owner stores where they like | Owner's storage | ₹0 | ₹0 |
| Theft protection | Keystore, BiometricPrompt, Android Find My Device | Phone / Google OS service | ₹0 | ₹0 |
| CI | GitHub Actions (private repo: 2,000 free minutes/month on GitHub Free) | GitHub | ₹0 within the free minutes | — |
| Crash reports | Google Play Console Android vitals | Google | ₹0 | — |
| Privacy policy page | Existing equisteg.com site | Existing hosting | ₹0 extra | — |

### 10.2 Costs that exist no matter what (already accepted by company rules)

| Cost | Amount | When |
|---|---|---|
| Google Play developer registration | US$25 one time | Before the first upload (OQ-05) |
| Google Play service fee on subscriptions | 15% of each subscription payment in most markets including India; in the US, UK, EEA, Australia and Japan 10% + 5% billing fee for installs after the 2026 rollout dates (Google Play Console help, checked 2026-10-09) | Per payment, deducted by Google |
| Product domain (optional for this app) | At-cost renewal | Only if the founder buys one (OQ-03) |

### 10.3 Optional paid or network items — not built until the founder approves

| ID | Item | Why someone might want it | Options and price | Recommendation |
|---|---|---|---|---|
| D-02 | Subscription purchase check | Play Billing needs the app to talk to the Play Store app; Google recommends a server to verify purchases and catch refunds | (a) On-device check through Play Billing Library only: ₹0, no server, weaker against tampering. (b) Cloudflare Worker + Google Play Developer API on the free tier (100,000 requests/day): ₹0 while under the limit, but the app then needs `INTERNET` and one network call per check. | **Decided 2026-10-09: (b).** The founder chose the free server check. The free tier stays offline; only the purchase check uses the network. Note: adding the Play Billing Library may add a network-related permission to the manifest; CI's manifest check will show it, and it needs the founder's yes because the current promise is "no internet permission". |
| D-03 | Better language understanding with an LLM | Long, rambling or unusual sentences | (a) **Gemini Nano via ML Kit GenAI Prompt API**: ₹0, on-device, only on supported phones (Pixel 10 series and some flagships; API was in alpha in Oct 2025, verify status at build), needs Google Play services/AICore. (b) **Open model (e.g. Gemma 3 1B / 270M) via MediaPipe LLM Inference / LiteRT**: ₹0, works on more phones, adds a 300 MB–1 GB download, model licence (Gemma Terms of Use) needs founder review, delivery via Play Asset Delivery (free). (c) **Cloud LLM** (e.g. Claude Haiku 5.5 at US$0.10 per million input tokens and US$0.50 per million output tokens): about US$0.27 per active user per month at 30 requests/day × 1,500 input + 300 output tokens; needs `INTERNET`, a server-side key proxy and a privacy-policy change. | (a) where available, falling back to the rule parser; no cloud. The rule parser in §5 is the baseline that must already meet the golden thresholds. |
| D-04 | Remote wipe of only this app | Wipe the agent without erasing the whole phone | Firebase Cloud Messaging (free) + Cloudflare Worker (free tier) + a pairing code: ₹0 on free tiers, needs `INTERNET` and a tiny server holding device tokens (personal data under DPDP/GDPR). | Not now; Find My Device erase covers the risk at ₹0. |
| D-05 | Direct Wi-Fi/Bluetooth transfer between phones | Faster than a file | Nearby Connections (Play services, free) needs Bluetooth/Wi-Fi/location permissions | Not now; file + Quick Share works today. |
| D-06 | End-to-end encrypted cloud sync between several devices | Same agent on phone and tablet | Cloudflare D1/R2 free tier (5 GB D1, 10 GB R2), ₹0 under limits; needs accounts, `INTERNET`, server code and DPDP/GDPR obligations | Not now. |
| D-07 | Instrumented emulator test job | Proves SQLCipher/Keystore on real Android | GitHub Actions free minutes (emulator jobs use ~10–15 min each) | Yes, ₹0 within free minutes. |

**Rule:** none of D-02…D-06 is coded until the founder says yes in the thread or in `docs/prebuild/12-open-questions.md`.

---

## 11. Security and privacy additions

- Threat model additions: stolen unlocked phone, shoulder-surfed PIN, coerced owner, malicious backup file, intent hijacking (explicit package or chooser for device actions), notification content on the lock screen (reminder text hidden on the secure lock screen by default; setting to show).
- Backup files are validated as untrusted input; size cap 20 MB; unknown fields rejected.
- Device-action intents never include memory content; the owner sees the exact text before approval.
- `FLAG_SECURE` on screens that show challenge questions, backup passphrase and memory.
- Audit adds `verification=BIOMETRIC|DEVICE_CREDENTIAL|CHALLENGE|TAP|STANDING` without content.
- Permissions are requested in context (first reminder → notifications; first voice use → microphone; calendar toggle → calendar), each justified in `docs/prebuild/06-permission-model.md` and allowlisted in `ManifestSecurityTest`.
- `INTERNET` stays absent. CI keeps failing if it appears, until a founder-approved decision (D-02/D-03c/D-04/D-06) changes the rule.

---

## 12. Testing

| Area | Tests |
|---|---|
| Understanding | `TimeParserTest`, `DateParserTest`, `ClauseSplitterTest`, extractor tests, golden v2 (≥ 95%) |
| Planner | preferred start, multi-day, routines expansion, deadlines, no overlaps (property test over random inputs) |
| Approvals | level mapping, challenge hashing and lockout, standing approval expiry and scope, model output cannot create a confirmation |
| Backup | round-trip, wrong passphrase, tampered file (GCM tag), oversized file, unknown fields |
| Wipe | every store empty, key destroyed, key file and DB files gone |
| Reminders | schedule, cancel, reboot re-registration (Robolectric), notification actions |
| UI | magic moment, edit task, approval prompt shown for L3, onboarding, lock screen |
| Manifest | only allowlisted permissions; no `INTERNET` |

Local gate before every PR (unchanged): `./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest lintDebug :app:assembleDebug`.

---

## 13. Founder decisions needed

| ID | Question | Default used until answered |
|---|---|---|
| D-01 | Monthly price and what is in the free tier vs paid | Build the full app; billing not wired; suggest free = planner, paid = reminders/voice/routines/device actions/backup |
| D-02 | Allow Play Billing (and the permission it may add) for subscriptions; on-device or server verification | **Decided: free Cloudflare Worker check (b).** Built in a billing PR after PR 6; price waits on D-01 |
| D-03 | On-device LLM: Gemini Nano (where available) and/or Gemma via MediaPipe; cloud LLM never unless approved | Rule parser v2 only |
| D-04 | App-only remote wipe through a free server | Not built; Find My Device guidance |
| D-05 | Direct phone-to-phone transfer | Not built; backup file |
| D-06 | Multi-device sync | Not built |
| D-07 | Emulator test job in CI | Will add (₹0) |
| D-08 | Date order for "12/10": day-month (India) by default? | Day-month, changeable in settings |
| D-09 | Wipe-on-failure default | Off, recommended in onboarding |

Earlier open questions OQ-01…OQ-14 in `docs/prebuild/12-open-questions.md` still apply.

---

## 14. Delivery plan (feature branches, one PR each, CI green before the next)

| PR | Branch | Contents |
|---|---|---|
| 1 | `claude/project-thread-d6ydk7` | This document in the repo; F1 understanding v2 (schema v2, parsers, splitter, multi-day, preferred start, golden v2); planner preferredStart and multi-day proposal UI |
| 2 | follow-up | F15 wipe fix (key + files), F11 approvals (BiometricPrompt, challenge questions), F12 app lock, DB v2 migration |
| 3 | follow-up | F2 task edit/delete/subtasks/notes, F10 settings + onboarding |
| 4 | follow-up | F4 reminders (AlarmManager, boot, notification actions), F5 routines + daily WorkManager job, standing approvals |
| 5 | follow-up | F6 voice, F7 calendar read, F8 device actions |
| 6 | follow-up | F9 memory review, F13 backup/restore, F14 theft-protection settings, F16 history filters, F19 widget |
| 7a | follow-up | New look (palette, light/dark, My day redesign, last-7-days insights) — founder request "best advanced aesthetic UI" (2026-10-28) |
| 7b | follow-up | Smarter agent on My day: chat answers (what's next / left), move by chat (F2), roll-over of unfinished tasks (§6.5, `tasks.rollOver`), focus timer — founder request "more to make a powerful agentic AI product" (2026-10-28); focus timer limits in OQ-27 |
| 8 | follow-up | Hardening from the 6a, 6b and 7b reviews: private memories hidden until verified, privacy switches recorded in History, History filters in SQL; Restore guess count tied to the file and the lock on the uptime clock, imported History marked, restored days re-planned on view; focus end notification; a move re-checked right before it runs (OQ-22, OQ-24, OQ-27) |

Each PR updates `PROGRESS.md`, `CHANGELOG.md`, the test-case workbook, `docs/prebuild/06` (permissions) and `11` (dependencies) where relevant.

---

## 15. Acceptance for "done"

- The founder's own sentences from phone testing are added to the golden set and pass (send them in the thread; `founder-01` is a stand-in until then).
- Every feature row F1–F16 and F19 works on a physical phone, checked with the manual test cases.
- No `INTERNET` permission; CI green; golden thresholds met.
- Delete everything leaves no database file, key file or Keystore alias.
- A backup made on one phone restores on another with the passphrase, and fails safely without it.
