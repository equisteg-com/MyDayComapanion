# 08 — Test strategy

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| TS-01 | Unit | PASS | `domain/src/test`: planner, extractor, validator, router/energy, policy, memory, privacy, security (JUnit 4, JVM). |
| TS-02 | Integration | PASS | `data/src/test/RoomStorageTest` (Robolectric, Room round-trip, export, deletion, migration continuity). |
| TS-03 | UI | PASS | `app/src/test/MagicMomentUiTest` (Compose on Robolectric: capture → proposal → confirm → plan; delete with confirmation). |
| TS-04 | AI evaluation | PASS | `GoldenEvaluationTest` with thresholds; report at `domain/build/reports/ai-eval/`. |
| TS-05 | Security | PASS | `AgentSecurityTest`, `ToolAuthorizationPolicyTest`, `MemoryPolicyTest`, `ManifestSecurityTest`, CI secret scan + APK permission check. |
| TS-06 | Offline | PASS | The app cannot use the network (no permission), so every test is an offline test; manual TC-001 in airplane mode. |
| TS-07 | Permission tests | PASS | Missing/revoked permission and disabled-flag denials (`AgentSecurityTest`). |
| TS-08 | Data deletion / export tests | PASS | `PrivacyTest`, `RoomStorageTest`, UI delete test. |
| TS-09 | Battery/resource | PASS (plan) | Router behaviour unit-tested; Battery Historian runs on 3 devices before alpha (02 H6). |
| TS-10 | Device matrix | PASS (plan) | Before alpha: API 26 (low-RAM emulator), API 30, API 34, API 36; one low-end and one mid-range physical device. |
| TS-11 | Regression suite | PASS | Whole suite runs on every PR (CI). |
| TS-12 | Release smoke test | PASS (plan) | TC-001 on the release build from each track before promotion. |
| TS-13 | Reliability: restart, reboot, scheduler recovery, migration | PASS (plan) | Restart: state is in Room. Reboot/scheduler recovery: with Phase 1 reminders. Migration: MigrationTestHelper per version from v2. |
| TS-14 | Encrypted DB on device | NEEDS DECISION (non-blocking) | See 05 TM-13. |

## Mandatory real scenarios (spec §18) → where covered

| Scenario | Covered by |
|---|---|
| Fixed appointment + flexible tasks | Planner magic-moment test; golden normal-01 |
| Conflicting deadlines | Planner deadline test; golden deadline-01 |
| Overloaded day | Planner overload test; golden impossible-01 |
| High-priority task | Planner ordering; extractor priority test |
| Changed task / missed task / meeting overrun | Replan with `notBefore` (planner + orchestrator tests); UI auto-prompt in Phase 1 |
| Recurring routine | Phase 1 (model exists, flag off) |
| Notification permission denied / revoked permission | `AgentSecurityTest` permission test |
| Device restart | Phase 1 with reminders |
| Airplane mode | No network path at all; TC-001 |
| Unavailable model | Router unavailable → fallback test |
| Low-resource device | Energy policy tests (RAM/battery/thermal) |
| Malicious calendar/task text | Golden adversarial cases; extractor injection test |
| Unauthorized tool call | `AgentSecurityTest` |
| Invalid tool arguments | `AgentSecurityTest` |
| Destructive-action confirmation | `PrivacyTest`, UI delete test |

## Commands

```bash
./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest lintDebug :app:assembleDebug
```
