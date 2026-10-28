# 01 — Product scope

Sources: master §3, §10, §11, §31; spec §2, §8, §22.

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| PS-01 | Target user defined | PASS (provisional) | See "Target user" below. Default chosen; founder may refine after alpha interviews. |
| PS-02 | Primary job-to-be-done defined | PASS | "Turn everything I have to do today/tomorrow into a realistic plan around my fixed commitments, and keep it realistic when the day changes." |
| PS-03 | V1 problem defined | PASS | Below. |
| PS-04 | V1 outcome defined | PASS | A confirmed, conflict-free day plan with reminders, created in under a minute, fully offline. |
| PS-05 | Magic moment defined | PASS | Master §10; automated in `AgentOrchestratorTest` and `MagicMomentUiTest`. |
| PS-06 | V1 non-goals defined | PASS | Master §11 / spec §8, copied below; locked flags enforce the cloud ones in code (`FeatureFlags`). |
| PS-07 | V1 scope frozen | PASS | Phase table below. Additions need a founder decision (`/change`). |
| PS-08 | Acceptance criteria defined | PASS | Below, per capability. |
| PS-09 | Success metrics defined | PASS | See 02-demand-assumptions §Metrics. |
| PS-10 | Kill criteria defined | PASS (provisional) | See 02-demand-assumptions §Kill criteria. Founder to confirm thresholds. |
| PS-11 | No silent scope expansion | PASS | Phase 0 implements only foundation items (master §31). Reminders, voice, calendar, routines are interfaces + flags only. |

## Target user (default)

An Android user who juggles work and personal tasks, plans in their head or in scattered notes, and does not
want a cloud account or their personal schedule on someone else's server. First alpha cohort: 10–20 people from
the founder's network (India), mixed professionals and students, Android 8+.

## V1 problem

People know what they need to do but not *when*. Lists ignore fixed meetings and realistic durations, so days
overrun and tasks silently slip. Existing assistants need an account and cloud processing.

## Phase plan (frozen)

| Capability | Phase 0 (now) | Phase 1 alpha | Notes |
|---|---|---|---|
| Text input, task capture/extraction | Built (rule-based) | Local model optional | Same validated schema either way |
| Deadlines, priority, duration estimate | Built | Tune from alpha data | |
| Subtasks | Data model (`parentId`) | UI | |
| Daily planning with fixed events | Built (deterministic) | | |
| Proposal + confirmation | Built | | Nothing saved before confirm |
| Replanning | Built (complete task / Replan) | Auto prompts when behind | |
| Reminders / notifications | Interface + tool + flag (off) | Built | POST_NOTIFICATIONS, inexact alarms |
| Routines | Data model + flag (off) | Built | |
| Local memory | Model + policy + store | Review/approve UI | |
| Calendar | Interface + flag (off) | Read-only | READ_CALENDAR |
| Voice in/out | Interface + flag (off) | On-device only | See 07-ai-risk-model AI-12 |
| Encrypted local storage | Built | | SQLCipher + Keystore |
| Export, full deletion | Built | | |
| Audit history | Built | Retention pruning | |
| Settings, permission management | Privacy screen | Per-permission controls | |
| Battery/resource-aware AI | Built (router + energy policy) | | |
| Device capability detection | Built (`AndroidDeviceStateProvider`) | Model capability probe | |
| Onboarding, local profile/preferences | Preferences model | Onboarding UI | |

## Non-goals (V1 must not require)

iOS, web app, website, cloud sync, mandatory login, cloud database/storage, cloud AI, email/WhatsApp/messaging
automation, browser automation, banking, smart-home, plugin marketplace, unrestricted device control, autonomous
messaging/purchases, team/multi-user, always-on AI.

## Acceptance criteria (Phase 0)

1. Magic-moment sentence yields 4 tasks + 1 fixed event, a plan with the meeting at 11:00 unmoved, no overlaps,
   and nothing stored until the user confirms. (`AgentOrchestratorTest`, `MagicMomentUiTest`)
2. Overloaded/impossible days report unscheduled tasks or deadline risk instead of overlapping. (`DeterministicPlannerTest`)
3. Malformed or hostile model output never reaches the planner or a tool. (`TaskExtractionValidatorTest`, `AgentSecurityTest`)
4. Export contains every store; delete-everything leaves every store at zero rows and is verified. (`PrivacyTest`, `RoomStorageTest`)
5. The APK has no network permission and backups are disabled. (`ManifestSecurityTest`, CI APK check)
6. Golden evaluation meets thresholds (see 07). (`GoldenEvaluationTest`)
