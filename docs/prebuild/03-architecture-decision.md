# 03 — Architecture decision

Sources: master §6, §12–§20, §36–§37; spec §4, §9–§16.

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| AR-01 | Android-first confirmed | PASS | `product.config.json` platforms=[android]. |
| AR-02 | Kotlin + Jetpack Compose | PASS | `app/build.gradle.kts` (Compose), Kotlin 2.2.21. |
| AR-03 | Room/SQLite | PASS | `data/.../db/MdcDatabase.kt`, Room 2.7.2. |
| AR-04 | Encryption design (Keystore-backed) | PASS | SQLCipher DB; 32-byte random passphrase wrapped with AES-256-GCM key in AndroidKeyStore, stored in `noBackupFilesDir` (`KeystoreDatabaseKeyProvider`). |
| AR-05 | AI provider abstraction | PASS | `AiModelProvider`, `AiInferenceRouter`, `ContextBuilder`, `PromptRegistry`, `TaskExtractionValidator`. |
| AR-06 | Agent/tool boundary | PASS | `AgentOrchestrator`, `AgentTool`, `ToolRegistry`; model output can only be `task-extraction/v1` data. |
| AR-07 | Permission policy | PASS | `ToolAuthorizationPolicy` + `AndroidPermissionChecker`. See 06. |
| AR-08 | Planner architecture | PASS | `DeterministicPlanner` + independent `PlanValidator`. AI may only suggest durations/priorities. |
| AR-09 | Scheduler | PASS (Phase 1 impl) | `NotificationScheduler` interface; Phase 1: AlarmManager inexact window alarms + boot re-registration; no exact-alarm permission. |
| AR-10 | Memory architecture | PASS | `MemoryItem` (source, confidence, status, sensitivity, retention, visibility, timestamps, deletion) + `MemoryPolicy`. |
| AR-11 | Audit architecture | PASS | `AuditLog`, privacy-minimised `AuditEntry`; every tool call (allowed, denied, needs-confirmation) is recorded. |
| AR-12 | Offline architecture | PASS | No network code and INTERNET permission removed; everything runs on device. |
| AR-13 | Network policy | PASS | V1 network path: none. See 04 §Network. |
| AR-14 | Export/delete architecture | PASS | `DataExportService`, `DataDeletionService` over `ErasableStore`; L3/L4 tools. |
| AR-15 | Migration strategy | PASS | Schema export on, explicit `Migrations.ALL`, no destructive fallback, continuity test. Schema JSON committed after first CI build (PROGRESS next steps). |
| AR-16 | Feature flag strategy | PASS | `FeatureFlags`/`Flag`; cloud/sync/accounts/analytics/integrations are locked off in V1. |
| AR-17 | Future cloud boundary documented | PASS | `future/FutureInterfaces.kt`: `SyncProvider`, `CloudBackend`, `ExternalIntegration` with disabled implementations only. |
| AR-18 | Local-first not contradicted | PASS | Hard-stop check below. |
| AR-19 | EQUISTeG template inspected | PASS | `equisteg-com/MyDayCompanion` was created from the EQUISTeG product template. Its rules (CLAUDE.md, CONTRIBUTING.md, PR template, branch flow, test-case and bug-log workbooks, CHANGELOG, `ci.yml`) are kept. The product override (native Kotlin/Compose, local-first, no Cloudflare/API) is recorded at the top of CLAUDE.md; `apps/*` stay as template placeholders and the Android code lives in `app/`, `data/`, `domain/`. |

## Module structure

The documents' `app/ domain/ data/` layout is kept as three Gradle modules:

- **domain** (pure Kotlin/JVM, no Android): models, planner, AI abstraction + validation, safety, policy, tools,
  agent, memory, audit, export/delete, future interfaces. Testable on any JVM; cannot call Android APIs, so
  business rules cannot bypass the policy layer.
- **data** (Android library): Room + SQLCipher, Keystore, device-state, permission checker, SAF export targets.
- **app** (Android application): Compose UI, navigation, wiring (`AppContainer`).

Tests live in each module's `src/test` (Gradle convention); `tests/` holds shared data, golden sets, cases and bugs
(spec §5 layout). The master's `tests/unit|integration|ui|ai|security` split maps to test packages.

## Request flow (implemented)

```
UI text → InputNormalizer → ContextBuilder (minimised, versioned prompt) → AiInferenceRouter
  (energy policy, on-device only) → provider.generate (timeout) → TaskExtractionValidator (schema + domain)
  → fallback to RuleBasedTaskExtractor on any failure → DeterministicPlanner + PlanValidator (preview only)
  → AgentProposal(actions) → user confirms → AgentOrchestrator.execute → ToolRegistry → arg validation
  → ToolAuthorizationPolicy → Android permission → tool → verify → AuditLog
```

## Contradictions found between the two documents

| # | Topic | Master | Spec | Resolution |
|---|---|---|---|---|
| X1 | Risk levels L2/L3 | L2 = user data/meaningful state; L3 = external or sensitive data ops | L2 = external/system; L3 = sensitive external | Master wins (its §1). Encoded in `RiskLevel`. |
| X2 | Repo layout | `tests/unit…`, docs set A | `tests/cases…`, docs set B | Union of docs folders; module-local tests (above). |
| X3 | Analytics | profile says `"minimal"`; stack table says no external analytics | `privacy_minimal` | No analytics SDK at all; local counters only. |
| X4 | Entitlement | future payment provider + verified event | "server-authoritative entitlement" | Payments are future; needs a backend decision then (OQ-09). |
| X5 | Calendar | "calendar integration" | "calendar read integration if approved" | Read-only, Phase 1, behind flag + READ_CALENDAR. |
| X6 | Crash monitoring | required | no SDKs | Play Console Android vitals (no SDK); optional local error log in export planned for Phase 1. |
| X7 | Branch names | production | production | `production` is the production branch (template flow). |
| X8 | Website | none in V1 | none | Play requires a public privacy-policy URL → host a page on the existing company site (OQ-06). |

## Hard-stop conditions (master §8)

| Condition | Result |
|---|---|
| Critical security issue unresolved | Not present |
| Data-flow/privacy model unclear | Clear (04) |
| Required permission unjustified | None requested in Phase 0 (06) |
| AI model/data path unclear | Clear: on-device only, deterministic default (07) |
| Dependency licensing unacceptable | None (11) |
| Architecture contradicts local-first | No |
| Mandatory V1 scope undefined | Defined (01) |
| Cost exposure uncontrolled | Capped (09) |
| Release path undefined | Defined (10) |
| Scope silently expanded | No |
| Future integration became a V1 dependency | No; disabled interfaces only |
