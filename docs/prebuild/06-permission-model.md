# 06 — Permission model

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| PM-01 | Permission matrix complete | PASS | Android permissions table below. |
| PM-02 | Every permission justified | PASS | Phase 0 requests none. |
| PM-03 | Tool allowlist complete | PASS | Tool table below = `ToolRegistry` contents in `AppContainer`. |
| PM-04 | Agent authorization flow complete | PASS | Policy rules below; `ToolAuthorizationPolicyTest`. |
| PM-05 | Revoked/denied permission handled | PASS | Checked live on every call (`AndroidPermissionChecker`); denial → `PERMISSION_MISSING`, audited. |
| PM-06 | Risk level definitions | PASS | Master §17 wins over spec §16 (03 X1). |

## Android permissions

| Permission | Phase | Purpose | Risk | Status |
|---|---|---|---|---|
| INTERNET, ACCESS_NETWORK_STATE | never in V1 | — | — | Explicitly removed (`tools:node="remove"`) |
| POST_NOTIFICATIONS (API 33+) | 1 | Local reminders | L1 | Not declared yet |
| RECEIVE_BOOT_COMPLETED | 1 | Re-register reminders after reboot | L0 | Not declared yet |
| RECORD_AUDIO | 1 | On-device speech input, only while the mic button is held | L2 | Not declared yet |
| READ_CALENDAR | 1 | Show fixed events when planning (read-only) | L2 | Not declared yet |
| SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM | not planned | Inexact window alarms are enough | — | Not requested |
| WRITE_CALENDAR, contacts, SMS, location, storage | not planned | — | — | Not requested |

Adding one requires: a row here, the allowlist in `ManifestSecurityTest`, a just-in-time rationale screen, and
graceful behaviour when denied.

## Tools (allowlist)

| Tool | Risk | Agent may propose | Confirmation | Android permission | Flag | Verification |
|---|---|---|---|---|---|---|
| `tasks.create` | L2 | yes | when proposed by agent | — | — | re-read created ids |
| `plan.generate` | L1 | yes | never | — | — | re-read + `PlanValidator` |
| `tasks.complete` | L1 | yes | when proposed by agent | — | — | status is DONE |
| `reminders.schedule` | L1 | yes | never | POST_NOTIFICATIONS | reminders (off) | scheduler accepted |
| `data.export` | L3 | **no** | always | — (SAF) | — | bytes written = export size |
| `data.deleteAll` | L4 | **no** | always | — | — | every store counts 0 |

## Policy (deterministic, first failing rule wins)

1. Unknown tool → deny `UNKNOWN_TOOL`.
2. Feature flag off → deny `FEATURE_DISABLED`.
3. Agent requests a non-proposable tool → deny `NOT_AGENT_PROPOSABLE` (even with a confirmation).
4. Arguments fail the tool's own validation → deny `INVALID_ARGUMENTS`.
5. Required Android permission not granted now → deny `PERMISSION_MISSING`.
6. L3/L4, or confirmation ALWAYS, or WHEN_PROPOSED_BY_AGENT with an agent requester → require a
   `UserConfirmation` whose fingerprint matches SHA-256(tool id + canonical args); mismatch → deny.
7. Otherwise allow. Every outcome is audited.
