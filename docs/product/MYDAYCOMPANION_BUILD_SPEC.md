# EQUISTeG — MyDayCompanion Product Build Specification

> **Rename note (2026-10-06):** This product was renamed from *MyDayGuide* to *MyDayCompanion* at the founder's request. All references were updated; the content is otherwise unchanged from the founder-supplied source.

**Status:** PRE-BUILD — implementation must not begin until the mandatory pre-build gate passes.

## 1. Purpose

This is the authoritative product-specific build instruction for MyDayCompanion. It adapts the reusable EQUISTeG product-factory template to an Android-first, privacy-first personal AI assistant.

The reusable template remains generic. Product-specific decisions belong here or in product configuration.

**Build principle:** build the reusable technical foundation once, but release capabilities phase by phase based on evidence. Do not fully implement every future feature before launch.

## 2. Product vision

MyDayCompanion is a private personal AI assistant that:
- accepts text and voice;
- understands tasks, priorities, schedules and routines;
- creates realistic daily plans;
- reminds users at useful times;
- replans when circumstances change;
- stores approved personal context locally;
- performs safe device actions only when explicitly permitted;
- adapts to device capability, battery and resources;
- works without a mandatory account;
- works offline for core functionality;
- keeps user data on-device by default;
- does not require a backend or cloud AI for the initial product;
- can later expand to other platforms without rewriting core product logic.

Long-term direction: **Personal Agent → Personal Agent OS.**

## 3. Non-negotiables

1. Privacy is a product feature.
2. Local-first is the default.
3. No cloud AI in V1 unless explicitly approved as an optional disclosed path.
4. No mandatory account in V1.
5. No backend in V1 unless an infrastructure gate proves it is required.
6. No paid infrastructure without evidence.
7. No always-running AI.
8. AI never receives unrestricted Android/device access.
9. Deterministic code controls permissions, actions, schedules, deletion, security and release.
10. Every privileged action passes policy and permission checks.
11. AI-generated code never goes directly to production.
12. Every material feature requires tests.
13. Data must be exportable and deletable.
14. Future capabilities use stable interfaces rather than premature implementations.
15. No feature is built merely because it is technically possible.
16. Revenue and retention evidence determine expansion.
17. Never claim 100% security or absolute data isolation unless technically verified.
18. Preserve reusable assets if the product fails.

## 4. Template override for MyDayCompanion

### Generic template

The EQUISTeG template supports Web (Next.js), Mobile (React Native + Expo), API (Cloudflare Worker), shared packages, Playwright, Maestro, k6, GitHub Actions, Cloudflare and Claude Code commands.

### MyDayCompanion initial implementation

- Android: Kotlin + Jetpack Compose
- Local database: Room/SQLite
- Secure storage: Android Keystore-backed encryption
- Local AI abstraction: model/provider interface
- Agent core: deterministic orchestration around AI
- Planner: deterministic scheduling + AI assistance
- Memory: local structured memory
- Scheduler: Android scheduling APIs
- Notifications: Android notification system
- Voice: Android speech/input/output abstractions
- Tools: explicit allowlisted device capabilities
- Permission policy: deterministic policy engine
- Audit log: local and privacy-minimized
- CI: GitHub Actions
- Distribution: Google Play initially
- No production website
- No production API
- No cloud database
- No cloud storage
- No required authentication
- No mandatory cloud analytics containing personal content
- No mandatory recurring backend cost

### Future interfaces

Define stable interfaces for:
`AiModelProvider`, `AiInferenceRouter`, `AgentTool`, `ToolAuthorizationPolicy`, `MemoryStore`, `PlannerEngine`, `CalendarProvider`, `VoiceProvider`, `NotificationScheduler`, `SyncProvider`, `CloudBackend`, `ExternalIntegration`.

Future interfaces must not create V1 runtime dependencies.

## 5. Repository structure

```text
mydaycompanion/
├── app/
├── domain/
├── data/
├── tests/
│   ├── cases/
│   ├── bugs/
│   ├── tools/
│   ├── testdata/
│   ├── security/
│   ├── ai-evals/
│   └── device/
├── docs/
│   ├── product/
│   ├── requirements/
│   ├── architecture/
│   ├── security/
│   ├── privacy/
│   ├── ai/
│   ├── testing/
│   ├── release/
│   ├── operations/
│   ├── prebuild/
│   └── test-reports/
├── inputs/
├── prompts/
├── .claude/
├── .github/
├── CLAUDE.md
├── CONTRIBUTING.md
├── PROGRESS.md
├── publisher.config.json
├── product.config.json
└── README.md
```

## 6. Mandatory pre-build gate

**ABSOLUTE RULE: no production implementation starts before all mandatory pre-checks pass.**

`/precheck` must inspect the repository, template, inputs and this specification, then generate:

- `docs/prebuild/00-prebuild-summary.md`
- `01-product-scope.md`
- `02-demand-assumptions.md`
- `03-architecture-decision.md`
- `04-data-map.md`
- `05-threat-model.md`
- `06-permission-model.md`
- `07-ai-risk-model.md`
- `08-test-strategy.md`
- `09-cost-model.md`
- `10-release-plan.md`
- `11-dependency-license-inventory.md`
- `12-open-questions.md`

`/precheck` must mark every item **PASS / FAIL / NEEDS DECISION** and stop on blockers. It must not implement product features.

## 7. Mandatory pre-checks

### Product
- [ ] Problem defined
- [ ] Target user defined
- [ ] Magic moment defined
- [ ] V1 scope frozen
- [ ] Explicit non-goals documented
- [ ] Success metrics defined
- [ ] Kill criteria defined

### Brand
- [ ] Product name confirmed or provisional
- [ ] Domain checked
- [ ] Trademark screening performed
- [ ] Google Play conflict screening performed
- [ ] App Store conflict screening performed
- [ ] Package/application ID strategy defined

### Architecture
- [ ] Android stack approved
- [ ] Local-first architecture approved
- [ ] AI abstraction approved
- [ ] Agent/tool interfaces approved
- [ ] Permission architecture approved
- [ ] Memory architecture approved
- [ ] Planner architecture approved
- [ ] Migration strategy approved
- [ ] Offline strategy approved
- [ ] Future cloud boundary documented

### Privacy
- [ ] Data map complete
- [ ] Data classification complete
- [ ] Local storage policy complete
- [ ] Encryption design complete
- [ ] Export design complete
- [ ] Deletion design complete
- [ ] Network policy complete
- [ ] Backup/privacy behavior reviewed

### Security
- [ ] Threat model complete
- [ ] Prompt-injection model complete
- [ ] Memory-poisoning model complete
- [ ] Tool-abuse model complete
- [ ] Permission model complete
- [ ] Dependency/license inventory complete
- [ ] Secret handling complete
- [ ] Audit model complete

### AI
- [ ] Model abstraction complete
- [ ] Supported model classes documented
- [ ] Battery/resource policy documented
- [ ] Prompt versioning documented
- [ ] Structured output strategy documented
- [ ] Golden dataset planned
- [ ] Evaluation thresholds defined
- [ ] Fallback behavior defined
- [ ] Token/resource budgets defined

### Testing
- [ ] Unit strategy
- [ ] Integration strategy
- [ ] UI strategy
- [ ] Device matrix
- [ ] AI evaluation
- [ ] Security testing
- [ ] Offline testing
- [ ] Battery/resource testing
- [ ] Data deletion testing
- [ ] Release smoke testing

### Cost
- [ ] Initial cash exposure defined
- [ ] Recurring cost target = ₹0 initially
- [ ] Paid dependencies identified
- [ ] Free/open-source alternatives checked
- [ ] Maximum monthly spend defined
- [ ] Spending approval rule defined

### Release
- [ ] Versioning
- [ ] Signing/key strategy
- [ ] Play release strategy
- [ ] Privacy-policy requirements
- [ ] Terms requirements
- [ ] Crash handling
- [ ] Rollback/recovery plan
- [ ] Support process

## 8. Product scope

### V1 must contain
- onboarding
- local profile/preferences
- text input
- task creation/edit/completion
- priorities and deadlines
- subtasks
- daily planning
- schedule proposal and confirmation
- reminders
- recurring routines
- local memory foundation
- basic voice input
- basic voice output where supported
- calendar read integration if permission is approved
- offline operation
- encrypted local storage
- data export
- data deletion
- permission management
- audit/history of important actions
- battery/resource-aware behavior
- device capability detection
- model abstraction
- graceful degradation when AI is unavailable

### V1 must not require
- website
- cloud account
- cloud database
- cloud storage
- cloud AI
- cross-device sync
- email/WhatsApp integrations
- browser automation
- banking actions
- unrestricted app control
- smart-home control
- autonomous messaging
- autonomous purchases

## 9. Build-everything-once interpretation

Build once:
- interfaces
- security boundaries
- data models/migrations
- AI abstraction
- agent/tool abstraction
- permission engine
- planner/memory interfaces
- feature flags
- test/evaluation harness
- audit model
- network policy
- export/deletion architecture
- release infrastructure

Do not implement every future integration.

## 10. Core architecture

```text
USER
  ↓
ANDROID UI
  ↓
INPUT / VOICE
  ↓
INTENT + CONTEXT
  ↓
AGENT ORCHESTRATOR
  ├── deterministic planner
  ├── memory
  ├── task engine
  ├── routine engine
  ├── scheduler
  └── tool router
          ↓
     POLICY ENGINE
          ↓
     PERMISSION CHECK
          ↓
     ALLOWLISTED TOOL
          ↓
       EXECUTION
          ↓
      VERIFICATION
          ↓
       AUDIT LOG
```

Never allow `AI → unrestricted Android API`.

Correct path:

`AI → structured action → validation → policy → permission → allowlisted tool → execution → verification → audit`.

## 11. Local-first data architecture

Default:

```text
USER DATA
   ↓
LOCAL DATABASE
   ↓
LOCAL ENCRYPTION
   ↓
ANDROID SECURE KEY MANAGEMENT
```

Tasks, calendar-derived context, preferences, routines, memory, voice-derived text, audit history and settings are local by default.

Do not transmit personal data merely because an API exists.

## 12. Network policy

V1 must support an explicit offline/private mode.

### Offline/private
- no network dependency
- no cloud AI
- no external analytics
- no external personal-data transfer

### Optional network
Any network capability must document destination, purpose, data transmitted, retention, user setting/consent, failure behavior and security controls.

The app must never silently upload personal content.

## 13. AI architecture

```text
AiModelProvider
      ↓
AiInferenceRouter
      ↓
Context Builder
      ↓
Prompt/Instruction Version
      ↓
Structured Output
      ↓
Validator
      ↓
Agent / Planner
```

Requirements:
- model/provider abstraction
- capability detection
- version tracking
- resource/token budget
- timeout
- fallback
- malformed-output handling
- prompt-injection defense
- context minimization
- memory-poisoning defense
- golden evaluation set
- regression evaluation

AI output is untrusted.

## 14. Device capability and energy management

Detect where reliably available:
- Android version
- RAM/storage constraints
- processor/AI capability
- battery state
- charging state
- thermal state
- local model capability
- speech capability
- notification capability

Example policy:

| Condition | Behavior |
|---|---|
| Very low battery | deterministic/lightweight behavior |
| Low resources | smallest suitable model / reduced background work |
| Normal | normal behavior |
| Charging + idle | permitted maintenance work |
| Unsupported local AI | deterministic assistant + supported fallback |

AI must be event-driven, not continuously running.

## 15. Memory architecture

Memory types:
- user preferences
- routines
- recurring commitments
- task history
- scheduling preferences
- approved personal facts
- temporary contextual memory

Every memory should have source, status/confidence, timestamps, sensitivity classification, retention policy, visibility and deletion capability.

The AI must not silently convert arbitrary conversation into permanent memory.

## 16. Permission architecture

Every tool must define:
- tool ID
- description
- risk level
- required Android permission
- user permission
- policy requirements
- confirmation requirements
- verification method
- audit requirement

Risk levels:
- L0: read-only/local
- L1: low-risk local action
- L2: external/system action
- L3: sensitive external action
- L4: financial/destructive/high-impact

L3/L4 should require explicit confirmation unless a future approved policy changes this.

## 17. Security requirements

Before release:
- secure local storage
- Android Keystore
- no secrets in Git/logs
- dependency/license and vulnerability scans
- static analysis
- input/output validation
- permission-boundary tests
- prompt-injection tests
- memory-poisoning tests
- malicious-content tests
- offline tests
- export/deletion tests
- backup/privacy review
- network traffic review
- audit review
- crash/error handling

Never claim literal 100% security.

## 18. Testing strategy

### Unit
Planner, scheduling, task state, memory rules, permissions, policy, migrations, encryption helpers, AI-output validators.

### Integration
Database, scheduler, notifications, voice, model abstraction, tool router.

### UI
Onboarding, task creation, daily plan, reminder, completion, edit plan, permissions, privacy, export/delete.

### AI evaluation
Golden set includes normal, ambiguous, incomplete, conflicting, impossible schedules, adversarial inputs, prompt injection, memory poisoning, tool abuse and malformed output.

### Mandatory real scenarios
- fixed appointment + flexible tasks
- conflicting deadlines
- overloaded day
- high-priority task
- changed task
- meeting overrun
- missed task
- recurring routine
- notification permission denied
- device restart
- airplane mode
- unavailable model
- low-resource device
- malicious calendar/task text
- unauthorized tool call
- revoked permission
- invalid tool arguments
- destructive-action confirmation

## 19. AI-generated-code governance

Claude Code may research, design, implement, test, refactor and document.

It may not independently:
- approve production
- declare security acceptance
- grant permissions
- modify payment authority
- bypass tests
- disable security controls to make tests pass
- insert secrets
- silently expand scope
- merge directly to protected production

Pipeline:

`Human requirement → AI design → AI implementation → automated tests → security → human review → QA → staging → release approval`.

## 20. Claude Code commands

### `/precheck`
Mandatory before `/newapp`. Inspect repository/template/inputs/specification; generate pre-build package; identify contradictions; mark PASS/FAIL/NEEDS DECISION; stop on blockers; do not implement.

### `/newapp`
Allowed only after pre-build approval. Implements approved scope on a feature branch.

### `/change`
Changes existing behavior.

### `/update`
Adds an approved feature.

### `/test`
Runs applicable tests, updates bug log and reports.

## 21. Branch strategy

```text
feature/*
    ↓ PR
development
    ↓ PR
staging
    ↓ founder approval
production
```

No direct production development. Protect branches, require checks, use human release approval, immutable tags and rollback.

## 22. Release phases

### Phase 0 — Internal foundation
Build architecture, database, security, AI abstraction, planner, task engine, permission framework and test framework.

Exit: core architecture works offline and passes security baseline.

### Phase 1 — Private alpha
10–20 real users. Text assistant, planning, reminders, routines, basic memory, voice and calendar-read capability.

Measure successful plans, reminder usefulness, failure rate, battery impact, retention and trust.

### Phase 2 — Closed beta
20–100 users. Add only evidence-supported improvements.

Measure activation, retention, use frequency, trust, AI failure rate and willingness to pay.

### Phase 3 — Public Android V1
Only after reliability, privacy, battery, release, support and pricing readiness.

## 23. Monetization

Initial philosophy:

> Privacy is free. Power is paid.

Free may include tasks, reminders, basic planning, basic local AI, basic memory, voice and offline operation.

Paid may include advanced planning/memory, complex routines, conditional automation, advanced voice and approved integrations.

When payment is introduced:

```text
PAYMENT PROVIDER
      ↓
VERIFIED EVENT
      ↓
SERVER-AUTHORITATIVE ENTITLEMENT
      ↓
PREMIUM ACCESS
```

Never grant entitlement from a success URL, local flag or LLM output.

## 24. Cost control

Initial target:
- ₹0 recurring backend cost
- ₹0 mandatory cloud AI cost
- ₹0 mandatory cloud storage
- ₹0 mandatory analytics infrastructure

Conditional costs may include Play registration, domain, test devices, legal/trademark work and optional development tools.

Every new recurring cost requires:
1. reason;
2. expected benefit;
3. cheaper alternative;
4. reversibility;
5. evidence trigger;
6. maximum monthly exposure.

## 25. Metrics

Track:
- activation
- first successful plan
- first successful reminder
- DAU/WAU
- plans created/completed
- replans
- reminder usefulness
- assistant error rate
- permission denial rate
- battery impact
- crash rate
- retention
- willingness to pay
- paid conversion
- refunds
- support burden

North-star behavior:

> User naturally thinks: "I'll ask MyDayCompanion."

## 26. Decision gates

```text
GATE 0 — PROBLEM
 ↓
GATE 1 — DEMAND
 ↓
GATE 2 — PRD + OFFER
 ↓
GATE 3 — THREAT MODEL + DATA MAP
 ↓
GATE 4 — HLD / LLD
 ↓
GATE 5 — AI-ASSISTED BUILD
 ↓
GATE 6 — CI + SECURITY + TESTS
 ↓
GATE 7 — STAGING + QA
 ↓
GATE 8 — PERFORMANCE + AI EVALUATION
 ↓
GATE 9 — RELEASE
 ↓
GATE 10 — PRODUCTION SMOKE
 ↓
GATE 11 — REVENUE + RETENTION
 ↓
IMPROVE / SCALE / REPOSITION / PARK
```

## 27. Product Definition of Done

A feature is complete only when:
- user value is documented
- acceptance criteria pass
- architecture boundary is documented
- privacy impact reviewed
- security impact reviewed
- permission impact reviewed
- AI evaluation exists when applicable
- deterministic logic controls critical rules
- tests pass
- offline behavior defined
- low-resource behavior defined
- errors handled
- audit behavior defined where needed
- documentation updated
- staging validation passes
- release evidence exists

## 28. First build sequence

1. Create private repository from EQUISTeG template.
2. Add this document.
3. Add `product.config.json`.
4. Add mandatory pre-build checklist.
5. Add Android architecture override.
6. Add `/precheck`.
7. Run `/precheck`.
8. Review generated pre-build evidence.
9. Resolve blockers.
10. Approve Gate P0.
11. Create feature branch.
12. Run `/newapp`.
13. Build domain/data/security foundations before UI polish.
14. Build planner, reminders, memory, AI abstraction, agent and permission framework.
15. Build automated tests/evaluation.
16. Run internal alpha.
17. Validate with real users.
18. Add only evidence-supported advanced capabilities.

## 29. Future template evolution

Add a product profile such as:

```json
{
  "product": {
    "type": "personal_ai_assistant",
    "platforms": ["android"],
    "primary_platform": "android",
    "backend_required": false,
    "ai_required": true,
    "ai_mode": "local_first",
    "data_sensitivity": "high",
    "offline_required": true,
    "account_required": false,
    "payment_required": false,
    "storage_required": "local",
    "analytics_policy": "privacy_minimal",
    "cost_ceiling_monthly_inr": 0
  }
}
```

The template should eventually select web/mobile/backend/AI/data/test/deployment components from this profile rather than forcing every product into one stack.

## 30. Immediate founder action

Before production code:
1. Create the private MyDayCompanion repository.
2. Put this specification in `docs/product/MYDAYCOMPANION_BUILD_SPEC.md`.
3. Add `product.config.json`.
4. Add `/precheck`.
5. Run `/precheck`.
6. Review every PASS/FAIL/NEEDS DECISION.
7. Resolve every blocker.
8. Explicitly approve the pre-build gate.
9. Only then begin `/newapp`.

## 31. Final instruction to Claude Code

**Do not start implementing MyDayCompanion merely because this document exists.**

First run the mandatory pre-build process.

Inspect the repository, template, inputs and this specification.

Identify contradictions and missing decisions.

Generate the complete pre-build evidence package.

Mark every item PASS / FAIL / NEEDS DECISION.

Stop on any blocking item.

Do not silently choose a cloud dependency, API, analytics service, paid AI provider, database or external integration.

Do not silently expand V1.

Do not remove a security control to make tests pass.

Do not treat AI output as authoritative.

Do not implement privileged device actions without the policy/permission/tool architecture.

Do not begin feature implementation until the founder explicitly approves the pre-build gate.

After approval, implement in small feature branches, run deterministic tests, perform security checks, open a PR, and preserve the development → staging → production discipline.

## 32. Source relationship

This specification preserves the EQUISTeG blueprint's reusable product-factory philosophy, AI-assisted engineering model, deterministic authority model, security gates, CI/CD lifecycle, testing approach, cost discipline and evidence-gated release model. It intentionally overrides generic template assumptions for MyDayCompanion: Android-first, native Android, local-first, no required backend/cloud AI/account in V1, privacy-first data, device/battery-aware AI, local memory and explicit Android permission/tool security.

The supplied EQUISTeG Master Blueprint explicitly establishes reusable product infrastructure, deterministic authority for payment/entitlement/authorization, AI security controls, evaluation gates and an evidence-based lifecycle.

**Build status: PRE-BUILD.**

The first technical milestone is not a polished application. It is a secure, offline-capable, testable personal-assistant foundation that can evolve without locking EQUISTeG into expensive infrastructure.
