# EQUISTeG — MyDayCompanion Master Build Document

> **Rename note (2026-10-06):** This product was renamed from *MyDayGuide* to *MyDayCompanion* at the founder's request. All references were updated; the content is otherwise unchanged from the founder-supplied source.

**Status:** PRE-BUILD / AUTHORITATIVE SOURCE OF TRUTH  
**Product:** MyDayCompanion  
**Build strategy:** Build the reusable foundation once; implement and release validated capabilities phase-wise.  
**Initial platform:** Android  
**Initial operating model:** Local-first, offline-capable, near-zero recurring infrastructure  
**Document purpose:** This document combines the EQUISTeG operating blueprint, product-factory rules, AI/LLM engineering rules, security/reliability standards, financial guardrails, the EQUISTeG product template, and the MyDayCompanion product-specific architecture into one build instruction.

---

## 1. How to use this document

This is the primary build authority for MyDayCompanion until a newer approved revision replaces it.

The team must not begin implementation merely because this document exists.

**Mandatory order:**

1. Create the private product repository from the EQUISTeG template.
2. Add this document to `docs/product/MYDAYCOMPANION_MASTER_BUILD.md`.
3. Add the product configuration.
4. Run the mandatory `/precheck`.
5. Review the generated evidence and all `FAIL` / `NEEDS DECISION` items.
6. Resolve blockers.
7. Founder approves Gate P0.
8. Only then begin implementation through feature branches and the controlled CI/QA pipeline.

If this document conflicts with the generic EQUISTeG template, the **MyDayCompanion-specific sections in this document take precedence for this product**. The reusable template should be improved later so that future product profiles can express the same variation without copying product-specific logic into the master template.

---

# 2. EQUISTeG operating principles

EQUISTeG is a reusable product factory, not a collection of unrelated applications.

The operating objective is to create products with:

- low initial cash exposure,
- low recurring maintenance,
- reusable engineering assets,
- evidence-based expansion,
- AI-assisted execution,
- deterministic security and business authority,
- measurable revenue potential,
- controlled downside if a product fails.

### Non-negotiables

- Do not build every future product simultaneously.
- Do not spend heavily before evidence.
- Do not introduce recurring infrastructure merely because it is convenient.
- Do not treat AI-generated code as trusted.
- Do not allow an LLM to control payment, entitlement, authorization, secrets, destructive actions, or production release.
- Do not send private data to external AI providers without an explicitly approved data path.
- Do not silently add cloud storage, accounts, analytics, ads, APIs, or paid AI.
- Do not promise literal 100% security.
- Do not confuse traffic with product-market fit.
- Do not scale a product whose contribution economics are negative.
- Do not remove security, testing, export, deletion, or audit controls to accelerate a release.
- Preserve reusable work even if the product is parked.

---

# 3. MyDayCompanion product vision

## 3.1 Product

MyDayCompanion is a private personal AI assistant designed to help a person understand what they need to do, plan a realistic day, remember useful context, remind them at appropriate times, and eventually safely perform approved device actions.

### Core interaction

**Input**
- Text
- Speech

**Output**
- Text
- Speech

### Core capabilities

- Understand tasks and natural-language intentions.
- Extract deadlines, priorities, duration estimates and constraints.
- Combine user tasks with fixed calendar events.
- Create realistic daily plans.
- Schedule reminders.
- Re-plan when the day changes.
- Maintain useful personal memory locally.
- Learn routines and preferences locally.
- Integrate with approved device capabilities.
- Execute only explicitly authorized actions.
- Operate without a mandatory account.
- Operate offline for core functionality.
- Adapt AI/model use to device capability, battery and resource conditions.

### Long-term direction

**Personal Assistant → Personal Agent → Personal Agent OS**

The initial product must not attempt to become a universal agent immediately. The architecture should make the future direction possible without implementing every future integration today.

---

# 4. Product strategy and financial guardrails

The company starts from a bootstrap-first position.

The objective is not to guarantee zero loss — that is impossible — but to **cap cash exposure and maximize salvageable/reusable assets**.

## 4.1 Spending rules

Before any paid expense, answer:

1. Is it required?
2. Is there a free/open-source/manual alternative?
3. Can it be delayed?
4. Does it reduce a meaningful technical, security or commercial risk?
5. Is the expense reversible?
6. What evidence triggers the expense?
7. What is the maximum cash exposure?

Default:

**Existing resources → free tools → small controlled spend → revenue-funded spend → profit-funded scale**

Do not let company cash reach zero merely to keep a weak product alive.

## 4.2 Initial infrastructure target

Target:

**₹0 recurring infrastructure initially**, wherever technically practical.

Paid infrastructure becomes acceptable only when one of these is true:

- measured demand exceeds free-tier capability,
- a security/reliability requirement cannot be met otherwise,
- a required distribution/platform fee is unavoidable,
- revenue clearly supports the expense,
- a paid tool materially reduces risk or development time.

## 4.3 Cost categories

Track separately:

- one-time company setup,
- developer/publisher fees,
- domain,
- optional paid software,
- cloud infrastructure,
- AI/model/API cost,
- payment fees,
- support cost,
- refunds,
- taxes,
- maintenance time.

Team time is also an investment even when cash cost is zero.

## 4.4 Financial kill/scale rule

A product is not scaled because it is technically impressive.

Use:

**Qualified users → Activation → Successful outcome → Retention/reuse → Willingness to pay → Contribution profit → Low support burden → Scale**

If evidence is weak:

**Fix → simplify → reposition → convert to feeder → park**

---

# 5. “Build everything once” interpretation

The desired strategy is accepted with one critical distinction.

### Build once now

Build the reusable machinery:

- architecture boundaries,
- interfaces,
- security model,
- permission model,
- agent/tool model,
- local data model,
- database migration strategy,
- model/provider abstraction,
- planner interfaces,
- memory interfaces,
- scheduler abstraction,
- network policy,
- export/delete,
- audit logging,
- testing/evaluation framework,
- feature flags,
- release pipeline,
- configuration system,
- documentation structure.

### Do NOT fully build now

Do not implement all future features just because the interfaces exist.

Examples to defer until evidence:

- email automation,
- messaging automation,
- browser automation,
- banking,
- smart-home control,
- broad third-party integrations,
- iOS,
- web application,
- cloud synchronization,
- multi-user/team system,
- universal plugin marketplace,
- heavy cloud AI,
- complex autonomous workflows.

**Principle: build the machine once; feed validated capabilities into it one at a time.**

---

# 6. Template strategy

The supplied EQUISTeG template is a reusable product factory containing:

- `apps/web`
- `apps/mobile`
- `apps/api`
- `packages/shared-types`
- `packages/api-client`
- `packages/design-tokens`
- test cases/bugs
- Playwright
- Maestro
- test data
- k6
- standards
- infrastructure
- prompts
- Claude commands
- GitHub workflows
- publisher configuration.

For MyDayCompanion, the template is **adapted, not blindly followed**.

## MyDayCompanion stack override

| Generic template | MyDayCompanion decision |
|---|---|
| Web app | Not required for V1 |
| React Native/Expo | Not the primary V1 stack |
| Cloudflare API | Not required for V1 |
| Cloud D1/KV | Not required for V1 |
| Android | **Kotlin + Jetpack Compose** |
| Local database | **Room + SQLite** |
| Encryption | **Android Keystore-backed encryption** |
| AI | Local/provider abstraction |
| Scheduler | Android-native scheduling/notifications |
| Agent | Local agent orchestration |
| Tools | Explicit allowlisted Android tools |
| Authorization | Deterministic local policy |
| Audit | Local audit log |
| CI/CD | GitHub Actions |
| Distribution | Google Play initially |
| Mandatory account | No |
| Production website | No |
| Cloud backend | No |
| Cloud database | No |
| Cloud storage | No |
| Cloud AI | No |
| Mandatory analytics | No external analytics initially |

The repository should retain future interfaces for cloud/web/cross-device capabilities without implementing those systems in V1.

---

# 7. Repository structure

Recommended product repository:

```text
mydaycompanion/
├── app/
│   ├── ui/
│   ├── navigation/
│   ├── permissions/
│   └── MainActivity.kt
├── domain/
│   ├── agent/
│   ├── planner/
│   ├── memory/
│   ├── tasks/
│   ├── routines/
│   ├── reminders/
│   ├── tools/
│   └── policy/
├── data/
│   ├── db/
│   ├── repository/
│   ├── encryption/
│   ├── ai/
│   ├── calendar/
│   ├── voice/
│   ├── notifications/
│   └── audit/
├── tests/
│   ├── unit/
│   ├── integration/
│   ├── ui/
│   ├── ai/
│   ├── security/
│   └── testdata/
├── docs/
│   ├── product/
│   ├── prebuild/
│   ├── architecture/
│   ├── security/
│   ├── testing/
│   ├── ai/
│   ├── release/
│   └── operations/
├── inputs/
├── prompts/
├── .claude/
│   └── commands/
├── .github/
│   └── workflows/
├── CLAUDE.md
├── CONTRIBUTING.md
├── PROGRESS.md
├── product.config.json
└── publisher.config.json
```

The exact final structure may evolve during `/precheck`, but no structural change may remove the required security/test/release boundaries.

---

# 8. Mandatory pre-build gate

## `/precheck` is mandatory.

It must run before implementation.

### `/precheck` must inspect

- repository,
- EQUISTeG template,
- this master build document,
- `product.config.json`,
- product inputs,
- existing standards,
- dependency choices,
- licensing,
- Android configuration,
- security requirements,
- privacy/data flow,
- AI/model plan,
- cost assumptions,
- testing strategy,
- release strategy.

### `/precheck` must generate

```text
docs/prebuild/
├── 00-prebuild-summary.md
├── 01-product-scope.md
├── 02-demand-assumptions.md
├── 03-architecture-decision.md
├── 04-data-map.md
├── 05-threat-model.md
├── 06-permission-model.md
├── 07-ai-risk-model.md
├── 08-test-strategy.md
├── 09-cost-model.md
├── 10-release-plan.md
├── 11-dependency-license-inventory.md
└── 12-open-questions.md
```

### Each pre-build check must return

- `PASS`
- `FAIL`
- `NEEDS DECISION`

### Hard stop conditions

Stop implementation if:

- critical security issue is unresolved,
- data-flow/privacy model is unclear,
- required permission is unjustified,
- AI model/data path is unclear,
- dependency licensing is unacceptable,
- architecture contradicts local-first requirement,
- mandatory V1 scope is not defined,
- cost exposure is uncontrolled,
- release path is not defined,
- product scope silently expands,
- a future integration has accidentally become a V1 dependency.

---

# 9. Mandatory pre-build checklist

## Product

- [ ] Target user defined.
- [ ] Primary job-to-be-done defined.
- [ ] V1 problem defined.
- [ ] V1 outcome defined.
- [ ] Magic moment defined.
- [ ] V1 non-goals defined.
- [ ] Acceptance criteria defined.
- [ ] Demand assumptions documented.
- [ ] Willingness-to-pay hypothesis documented.
- [ ] Success metrics defined.

## Brand

- [ ] MyDayCompanion name screen completed.
- [ ] `mydaycompanion.com` checked through a live registrar before purchase.
- [ ] Exact/variant search completed.
- [ ] Google Play conflict search completed.
- [ ] App Store conflict search completed.
- [ ] Trademark screening completed for relevant jurisdictions.
- [ ] Social/brand handle screening completed.
- [ ] Domain purchase is normal-priced and within the approved budget.
- [ ] No premium domain purchase without explicit approval.

A search-engine absence is not proof that a domain is available.

## Architecture

- [ ] Android-first decision confirmed.
- [ ] Kotlin + Jetpack Compose confirmed.
- [ ] Room/SQLite confirmed.
- [ ] AI provider abstraction confirmed.
- [ ] Agent/tool boundary confirmed.
- [ ] Permission policy confirmed.
- [ ] Scheduler confirmed.
- [ ] Memory architecture confirmed.
- [ ] Audit architecture confirmed.
- [ ] Offline architecture confirmed.
- [ ] Network policy confirmed.
- [ ] Export/delete architecture confirmed.
- [ ] Migration strategy confirmed.
- [ ] Feature flag strategy confirmed.

## Privacy

- [ ] Data inventory complete.
- [ ] Data classification complete.
- [ ] Local-first behavior verified.
- [ ] Network calls explicitly listed.
- [ ] Sensitive data paths identified.
- [ ] Retention policy defined.
- [ ] Deletion policy defined.
- [ ] Export path defined.
- [ ] No mandatory account.
- [ ] No silent upload.

## Security

- [ ] Android Keystore design complete.
- [ ] Secrets excluded from source.
- [ ] Permission matrix complete.
- [ ] Tool allowlist complete.
- [ ] Agent authorization flow complete.
- [ ] Prompt injection threat model complete.
- [ ] Memory poisoning threat model complete.
- [ ] Malicious content handling defined.
- [ ] Dependency/license inventory complete.
- [ ] Vulnerability scanning planned.
- [ ] Static analysis planned.
- [ ] Audit log defined.

## AI

- [ ] Model/provider abstraction defined.
- [ ] Local model strategy defined.
- [ ] Device capability detection defined.
- [ ] Energy policy defined.
- [ ] Structured output defined where needed.
- [ ] AI output validation defined.
- [ ] Golden dataset defined.
- [ ] Evaluation metrics defined.
- [ ] Fallback defined.
- [ ] Input/output limits defined.
- [ ] AI data privacy policy defined.

## Testing

- [ ] Unit tests.
- [ ] Integration tests.
- [ ] UI tests.
- [ ] Agent evaluation.
- [ ] Security tests.
- [ ] Offline tests.
- [ ] Permission tests.
- [ ] Battery/resource tests.
- [ ] Data deletion tests.
- [ ] Export tests.
- [ ] Device compatibility tests.
- [ ] Regression suite.
- [ ] Release smoke test.

## Cost

- [ ] Initial cash exposure defined.
- [ ] Recurring-cost target defined.
- [ ] Free alternatives evaluated.
- [ ] Paid dependency list identified.
- [ ] AI compute cost model defined.
- [ ] Play Store/publisher cost identified.
- [ ] Domain cost identified.
- [ ] Cost-trigger thresholds defined.

## Release

- [ ] Internal build path.
- [ ] Alpha path.
- [ ] Beta path.
- [ ] Production path.
- [ ] Rollback plan.
- [ ] Crash monitoring plan.
- [ ] Release notes.
- [ ] Privacy documentation.
- [ ] User support process.
- [ ] Store compliance checklist.

---

# 10. V1 scope

## V1 must contain

### Core assistant

- Text input.
- Basic speech input.
- Text output.
- Basic speech output.
- Natural-language task capture.
- Task extraction.
- Deadline extraction.
- Priority.
- Duration estimate.
- Task/subtask support.
- Daily planning.
- Fixed-event awareness.
- Reminders.
- Notifications.
- Replanning.
- Basic routines.
- Basic local memory.
- Calendar integration.
- Offline core behavior.
- Local encrypted storage.
- Export.
- Full deletion.
- Audit history.
- Basic settings.
- Permission management.

## Magic moment

Example:

User:

> “Tomorrow I need to finish the proposal, call Raj, work out, buy groceries, and I have a meeting at 11.”

MyDayCompanion should:

1. Understand the tasks.
2. Identify the fixed meeting.
3. Estimate task durations.
4. Consider constraints.
5. Produce a realistic proposed schedule.
6. Ask for confirmation where appropriate.
7. Schedule reminders.
8. Re-plan when the user falls behind or changes the day.

---

# 11. Explicit V1 non-goals

Do not make these V1 dependencies:

- iOS.
- Web app.
- Cloud synchronization.
- Mandatory login.
- Cloud database.
- Cloud storage.
- Cloud AI.
- Email automation.
- WhatsApp/messaging automation.
- Browser automation.
- Banking actions.
- Smart-home control.
- Universal plugin marketplace.
- Autonomous unrestricted device control.
- Team collaboration.
- Complex multi-user architecture.
- Always-on AI processing.

Interfaces may exist for future expansion, but the implementation should remain minimal.

---

# 12. Core architecture

```text
USER
  ↓
ANDROID UI
  ↓
TEXT / VOICE INPUT
  ↓
INPUT NORMALIZATION
  ↓
INTENT + CONTEXT
  ↓
AGENT ORCHESTRATOR
  ↓
AI / DETERMINISTIC PLANNER
  ↓
POLICY ENGINE
  ↓
PERMISSION CHECK
  ↓
ALLOWLISTED TOOL
  ↓
DEVICE EXECUTION
  ↓
VERIFICATION
  ↓
AUDIT LOG
  ↓
USER
```

### Trust rule

**Probabilistic intelligence surrounds deterministic authority.**

The AI is useful for understanding and proposing.

The deterministic system controls what is actually allowed.

---

# 13. Required interfaces

The architecture should expose stable interfaces similar to:

```text
AiModelProvider
AiInferenceRouter
AgentTool
ToolAuthorizationPolicy
MemoryStore
PlannerEngine
CalendarProvider
VoiceProvider
NotificationScheduler
SyncProvider
CloudBackend
ExternalIntegration
```

Future interfaces may initially have no production implementation.

Their purpose is to prevent future architecture rewrites.

---

# 14. AI architecture

## AI is not trusted

AI output must be treated as untrusted data.

Required flow:

```text
INPUT
 ↓
CONTEXT BUILDER
 ↓
MODEL PROVIDER
 ↓
STRUCTURED OUTPUT
 ↓
SCHEMA VALIDATION
 ↓
DOMAIN VALIDATION
 ↓
POLICY CHECK
 ↓
USER CONFIRMATION WHEN REQUIRED
 ↓
EXECUTION
```

Never:

```text
AI → unrestricted device action
```

## AI responsibilities

AI may:

- interpret natural language,
- classify intent,
- summarize,
- estimate task duration,
- suggest plans,
- suggest reminders,
- identify routines,
- propose memory candidates,
- generate structured plans,
- explain decisions,
- assist development/testing/documentation.

AI must not independently:

- grant permissions,
- authorize privileged actions,
- change security policy,
- access secrets,
- decide payment/entitlement,
- delete user data without the appropriate deterministic path,
- execute arbitrary device commands,
- release production software.

---

# 15. Local AI/provider architecture

Create a provider abstraction so the product is not permanently coupled to one model.

Example conceptual interface:

```text
AiModelProvider
  - capabilities()
  - estimateCost()
  - generate()
  - supportsStructuredOutput()
  - supportsStreaming()
  - availability()
```

The application should be able to select an appropriate provider/model based on:

- device capability,
- model availability,
- battery,
- thermal/resource state,
- latency,
- task complexity,
- privacy policy,
- model capability.

No model provider may bypass the policy layer.

---

# 16. Energy and device-resource policy

MyDayCompanion must not behave like an always-running AI process.

### Suggested policy

**Very low battery**
- deterministic scheduling,
- lightweight logic,
- no unnecessary model calls,
- postpone non-critical maintenance.

**Moderate battery**
- small/local model where appropriate,
- normal reminders/planning.

**Normal**
- normal AI operations within limits.

**Charging + idle**
- optional heavier maintenance only where useful and permitted.

AI work must be event-driven wherever possible.

---

# 17. Agent/tool security model

Every action must pass:

```text
AI proposal
 ↓
Structured action
 ↓
Argument validation
 ↓
Policy engine
 ↓
Permission check
 ↓
Allowlisted tool
 ↓
Execution
 ↓
Verification
 ↓
Audit
```

## Permission risk levels

### L0 — No-risk
Internal calculations and local state reads.

### L1 — Low-risk
Non-sensitive local operations.

### L2 — Moderate
Operations involving user data or meaningful state changes.

### L3 — High
External communication, consequential device actions or sensitive data operations.

### L4 — Critical
Financial, destructive, security-sensitive or highly consequential actions.

L3/L4 require explicit user confirmation unless a later, explicitly designed trusted workflow establishes a narrower safe authorization mechanism.

---

# 18. Memory architecture

Memory is a product capability and a security boundary.

Each memory item should have concepts equivalent to:

- source,
- confidence,
- status,
- sensitivity,
- retention,
- visibility,
- creation/update time,
- deletion state.

Examples:

- user preference,
- routine,
- recurring task,
- historical behavior,
- explicit instruction,
- temporary context.

The assistant must not silently convert every conversation into permanent memory.

Memory should be:

- explainable,
- reviewable,
- deletable,
- exportable,
- bounded by retention policy.

Memory poisoning must be included in security testing.

---

# 19. Data architecture

Default:

```text
DEVICE
├── encrypted local database
├── local memory
├── local audit
├── local settings
├── local task state
└── local model/provider state
```

No backend is required for V1.

### Data principles

- Store locally by default.
- Minimize data collection.
- Encrypt sensitive local data.
- Do not upload merely because an external service exists.
- Do not silently synchronize.
- Do not log private payloads unnecessarily.
- Provide export.
- Provide complete deletion.
- Document any network call.

---

# 20. Network policy

The application must distinguish between:

### Private/offline path

- Local planning.
- Local task storage.
- Local memory.
- Local reminders.
- Local scheduling.
- Local deterministic logic.

### Optional network path

Only if explicitly required and approved.

Every network dependency must document:

- purpose,
- destination,
- data sent,
- data received,
- retention,
- failure behavior,
- privacy implication,
- cost.

There must be no silent upload path.

---

# 21. Security requirements

Minimum security baseline:

- Android Keystore-backed protection.
- No hard-coded secrets.
- No secrets in Git.
- Dependency inventory.
- License inventory.
- Vulnerability scanning.
- Static analysis.
- Secure logging.
- Permission minimization.
- Network traffic review.
- Prompt injection testing.
- Indirect injection testing.
- Memory poisoning testing.
- Malicious content testing.
- Offline-mode testing.
- Export/deletion verification.
- Audit log verification.
- Backup/data leakage review.
- Release signing protection.

Do not claim “100% secure.”

---

# 22. AI/LLM security

Required controls:

- Treat user input as untrusted.
- Treat retrieved content as untrusted.
- Treat model output as untrusted.
- Threat-model direct prompt injection.
- Threat-model indirect prompt injection.
- Never expose secrets to the model.
- Allowlist tools.
- Validate tool arguments independently.
- Use structured outputs where practical.
- Keep destructive/business/security rules outside the LLM.
- Minimize sensitive data before inference.
- Limit input size.
- Limit model output.
- Prevent cross-user context leakage if multi-user capability is ever added.
- Avoid unnecessary private-data logging.
- Define provider/model failure behavior.
- Run evaluation after material model/prompt/tool changes.

---

# 23. AI evaluation

Create a golden evaluation dataset before public release.

Include:

- normal inputs,
- ambiguous inputs,
- edge cases,
- malformed inputs,
- adversarial inputs,
- long inputs,
- conflicting constraints,
- impossible schedules,
- reminder conflicts,
- privacy-sensitive requests,
- permission-denied cases,
- tool misuse attempts.

Measure at minimum:

- intent accuracy,
- extraction accuracy,
- plan validity,
- schedule conflict rate,
- unsafe-action rejection,
- structured-output validity,
- fallback correctness,
- latency,
- resource use,
- model cost where applicable.

Do not use an LLM judge as the sole authority for critical correctness.

---

# 24. Testing strategy

## Unit

Deterministic business logic:

- task parsing,
- priority,
- duration,
- scheduling,
- conflict resolution,
- reminders,
- permissions,
- policy,
- memory rules.

## Integration

- Room.
- encryption.
- repositories.
- scheduler.
- calendar.
- notification system.
- AI provider abstraction.
- tool execution.

## UI

- onboarding.
- task capture.
- plan display.
- reminder interaction.
- permission screens.
- settings.
- export/delete.

## AI evaluation

- golden dataset,
- regression,
- adversarial inputs.

## Security

- unauthorized tool call,
- malicious input,
- prompt injection,
- memory poisoning,
- permission bypass,
- data leakage,
- export/deletion,
- network leakage.

## Reliability

- offline,
- low battery,
- app restart,
- device reboot,
- scheduler recovery,
- corrupted/incomplete model output,
- provider failure,
- database migration.

## Device compatibility

Test across representative Android versions and hardware profiles before wider release.

---

# 25. AI-generated-code governance

AI may generate:

- code,
- tests,
- documentation,
- refactors,
- architecture drafts,
- security test cases.

AI-generated code must never go directly to production.

Required path:

```text
Human requirement
 ↓
AI design/implementation
 ↓
Automated tests
 ↓
Security checks
 ↓
Human review
 ↓
QA
 ↓
Staging
 ↓
Release approval
```

AI is an accelerator, not the authority.

---

# 26. EQUISTeG AI factory workflow

Use AI extensively where it is safe:

```text
RESEARCH
 ↓
PRODUCT
 ↓
UX
 ↓
ARCHITECTURE
 ↓
IMPLEMENTATION
 ↓
TESTING
 ↓
SECURITY REVIEW
 ↓
DOCUMENTATION
 ↓
RELEASE PREPARATION
 ↓
OPERATIONS ANALYSIS
```

At every stage:

**AI output → deterministic checks → human/process approval**

---

# 27. Development workflow

```text
FEATURE BRANCH
 ↓
PULL REQUEST
 ↓
FORMAT/LINT
 ↓
UNIT TESTS
 ↓
INTEGRATION TESTS
 ↓
SECRET SCAN
 ↓
DEPENDENCY/VULNERABILITY SCAN
 ↓
STATIC ANALYSIS
 ↓
BUILD
 ↓
AI EVALUATION WHEN APPLICABLE
 ↓
DEVELOPMENT
 ↓
STAGING
 ↓
PLAYWRIGHT/MAESTRO + MANUAL QA
 ↓
SECURITY/PERFORMANCE/ABUSE TEST
 ↓
RELEASE APPROVAL
 ↓
IMMUTABLE RELEASE
 ↓
PRODUCTION
 ↓
SMOKE TEST
 ↓
MONITOR
```

---

# 28. Claude/AI commands

The template should support at minimum:

### `/precheck`

Inspect everything required by this document. Generate evidence. Do not implement.

### `/newapp`

Initialize approved product foundation only after precheck approval.

### `/change`

Implement a defined approved change without silently expanding scope.

### `/update`

Update documentation/configuration/architecture according to an approved change.

### `/test`

Run appropriate tests, produce evidence, classify failures and create bug records where needed.

---

# 29. Mandatory `/precheck` behavior

The instruction to the coding agent must include:

> Do not implement merely because the product specification exists. First inspect the repository, EQUISTeG template, product inputs, product configuration and MyDayCompanion Master Build Document. Identify contradictions, missing decisions, risky dependencies, privacy issues, security gaps, licensing problems, cost exposure and scope expansion. Generate all required pre-build artifacts. Mark every check PASS, FAIL or NEEDS DECISION. Stop on blockers. Do not silently introduce cloud APIs, cloud databases, cloud storage, cloud AI, analytics, paid services, external integrations or privileged device actions. Do not remove security controls. Do not give the AI authority over permissions or device actions. Do not begin implementation until the founder-approved pre-build gate is passed.

---

# 30. Branch strategy

Use:

```text
feature/*
   ↓
development
   ↓
staging
   ↓
production
```

Production must only contain approved, tested releases.

---

# 31. Release phases

## Phase 0 — Internal foundation

Goal:

- repository,
- architecture,
- local DB,
- security foundation,
- AI abstraction,
- agent/tool abstraction,
- planner foundation,
- testing foundation,
- export/delete,
- audit,
- CI.

No public release.

## Phase 1 — Private assistant alpha

Target:

**10–20 users**

Capabilities:

- task capture,
- daily planning,
- reminders,
- basic memory,
- routines,
- calendar,
- voice,
- offline operation.

Collect qualitative evidence.

## Phase 2 — Closed beta

Target:

**20–100 users**

Focus:

- reliability,
- battery,
- scheduling quality,
- reminders,
- memory quality,
- permission experience,
- crashes,
- retention,
- repeated use.

## Phase 3 — Public Android V1

Only after release criteria pass.

Initial product remains:

- Android-first,
- local-first,
- no mandatory account,
- no required cloud backend,
- no required cloud AI.

---

# 32. Product metrics

Measure only what helps decisions.

### Product

- activation,
- first successful plan,
- successful reminder,
- daily active use,
- weekly active use,
- plans created,
- replans,
- reminder usefulness,
- task completion assistance,
- error rate.

### Reliability

- crash rate,
- failed schedules,
- missed reminders,
- battery impact,
- startup time,
- memory/CPU behavior,
- device compatibility.

### Trust

- permission acceptance/denial,
- unsafe action rejection,
- export success,
- deletion success,
- privacy-related complaints.

### Commercial

- willingness to pay,
- paid conversion,
- refunds,
- support burden,
- contribution profit.

Do not optimize vanity metrics.

---

# 33. Monetization philosophy

Working principle:

**Privacy is free. Power is paid.**

Do not make basic privacy a premium feature.

Possible future paid boundaries:

- advanced planning,
- deeper memory,
- advanced routines,
- higher automation capability,
- premium local models,
- advanced device actions,
- cross-device sync,
- optional cloud capabilities,
- advanced integrations.

Do not finalize pricing until demand and willingness-to-pay evidence exists.

Use subscriptions only where recurring value is real.

---

# 34. Commercial decision gates

The decision engine is:

```text
QUALIFIED USERS?
    NO → distribution/problem validation
    YES
      ↓
ACTIVATION?
    NO → UX/value problem
    YES
      ↓
REPEAT USE?
    NO → product/value problem
    YES
      ↓
WILLINGNESS TO PAY?
    NO → offer/positioning/price test
    YES
      ↓
POSITIVE CONTRIBUTION?
    NO → price/cost/refund/compute problem
    YES
      ↓
LOW SUPPORT + GOOD RETENTION?
    NO → simplify/automate/reposition
    YES → scale
```

---

# 35. Failure strategy

If MyDayCompanion fails commercially, do not automatically throw away the engineering work.

Salvage:

- local agent framework,
- tool authorization,
- permission engine,
- memory layer,
- planner,
- AI provider abstraction,
- testing/evaluation framework,
- Android foundation,
- security controls,
- reusable UI components,
- product-factory improvements.

The product may be:

- improved,
- repositioned,
- narrowed,
- converted into another product,
- parked,
- or discontinued.

The objective is to minimize irreversible loss.

---

# 36. Future architecture interfaces

The product should leave controlled extension points for:

- `SyncProvider`
- cloud synchronization,
- account/authentication,
- web client,
- iOS client,
- external AI providers,
- email,
- messaging,
- browser automation,
- document workflows,
- travel,
- shopping,
- smart-home,
- cross-device agent workflows.

These are **interfaces and future decisions, not V1 commitments**.

---

# 37. Product-factory evolution

The EQUISTeG template will change over time.

Do not make MyDayCompanion a permanent fork that becomes impossible to maintain.

Future `product.config.json` should allow the master template to select capabilities based on product profile, for example:

```json
{
  "product": "MyDayCompanion",
  "platform": "android",
  "stack": "kotlin-compose",
  "architecture": "local-first",
  "backend": false,
  "cloud_ai": false,
  "account_required": false,
  "local_database": "room",
  "encryption": "android-keystore",
  "agent": true,
  "local_ai": true,
  "voice": true,
  "calendar": true,
  "payments": "future",
  "analytics": "minimal",
  "precheck_required": true
}
```

This configuration is a starting structure and must be validated during `/precheck`.

The long-term objective is:

**One evolving EQUISTeG template → many product profiles → reusable secure factory.**

---

# 38. Definition of Done

A feature is not done merely because it works on the developer device.

### Product

- [ ] User value is defined.
- [ ] Acceptance criteria pass.
- [ ] Edge cases handled.
- [ ] No silent scope expansion.

### Engineering

- [ ] Code reviewed.
- [ ] Tests added.
- [ ] Migration handled if needed.
- [ ] Error handling exists.
- [ ] Offline behavior defined.

### AI

- [ ] Use case justified.
- [ ] Data classification complete.
- [ ] Prompt/model versioned.
- [ ] Input limits exist.
- [ ] Output schema exists where practical.
- [ ] Tools are allowlisted.
- [ ] Arguments validated.
- [ ] AI output is not treated as authority.
- [ ] Evaluation exists.
- [ ] Regression threshold exists.
- [ ] Fallback exists.

### Security

- [ ] Threat model updated where required.
- [ ] Permission model reviewed.
- [ ] Secrets protected.
- [ ] Dependency/license checks pass.
- [ ] Security tests pass.
- [ ] No critical issue remains.

### Privacy

- [ ] Data flow documented.
- [ ] Network behavior documented.
- [ ] Retention documented.
- [ ] Deletion works.
- [ ] Export works.

### Release

- [ ] CI passes.
- [ ] Staging passes.
- [ ] QA passes.
- [ ] Release artifact is reproducible.
- [ ] Rollback path exists.
- [ ] Authorized release approval obtained.

---

# 39. Unified lifecycle

```text
GATE 0  PROBLEM
   ↓
GATE 1  DEMAND
   ↓
GATE 2  PRD + OFFER
   ↓
GATE 3  THREAT MODEL + DATA MAP
   ↓
GATE 4  HLD / LLD
   ↓
GATE 5  AI-ASSISTED BUILD
   ↓
GATE 6  CI + SECURITY + TESTS
   ↓
GATE 7  STAGING + MANUAL QA
   ↓
GATE 8  PERFORMANCE + ABUSE + AI EVALUATION
   ↓
GATE 9  RELEASE + IMMUTABLE TAG
   ↓
GATE 10 PRODUCTION + SMOKE
   ↓
GATE 11 REVENUE + SUPPORT + RELIABILITY REVIEW
   ↓
IMPROVE / SCALE / REPOSITION / FEEDER / PARK
```

---

# 40. First implementation sequence

Do not jump directly into product features.

### Step 1 — Repository

- Create private repository from EQUISTeG template.
- Protect branches.
- Add this document.
- Add `product.config.json`.
- Add `PROGRESS.md`.

### Step 2 — Precheck tooling

Create:

```text
.claude/commands/precheck.md
```

Implement the mandatory inspection/evidence workflow.

### Step 3 — Run `/precheck`

Generate all required `docs/prebuild/*` documents.

### Step 4 — Resolve decisions

Resolve every:

- FAIL,
- NEEDS DECISION,
- security blocker,
- licensing blocker,
- cost blocker.

### Step 5 — Founder Gate P0

Explicitly approve the pre-build package.

### Step 6 — Android foundation

Implement:

- Kotlin,
- Jetpack Compose,
- project modules,
- dependency management,
- CI,
- static analysis,
- test framework.

### Step 7 — Local data foundation

Implement:

- Room,
- migrations,
- repositories,
- encryption,
- deletion,
- export,
- audit.

### Step 8 — Domain foundation

Implement:

- tasks,
- subtasks,
- priorities,
- deadlines,
- routines,
- calendar abstraction,
- reminder abstraction.

### Step 9 — AI foundation

Implement:

- model abstraction,
- inference router,
- context builder,
- structured output,
- validation,
- fallback,
- evaluation harness.

### Step 10 — Agent foundation

Implement:

- orchestrator,
- tool interface,
- tool registry,
- authorization policy,
- permission matrix,
- verification,
- audit.

### Step 11 — Planner

Implement deterministic scheduling logic first.

AI should assist interpretation and proposals; deterministic code should validate schedule constraints.

### Step 12 — User experience

Build:

- task capture,
- plan view,
- schedule view,
- reminder interaction,
- memory/routine controls,
- permissions,
- settings.

### Step 13 — Voice

Add speech input/output behind provider abstractions.

### Step 14 — Internal testing

Run:

- unit,
- integration,
- UI,
- AI evaluation,
- security,
- offline,
- battery/resource,
- deletion/export,
- device compatibility.

### Step 15 — Alpha

Release to the controlled internal/alpha group.

Do not add major features merely because users suggest them. Classify feedback by frequency, severity and evidence.

---

# 41. Release authority

The following are deterministic/process authorities:

| Decision | Authority |
|---|---|
| Permission | Policy + Android permission system |
| Tool authorization | Deterministic policy |
| Data deletion | Application logic + verified deletion |
| AI output | Untrusted until validated |
| Security release | CI/security gates + human approval |
| Production release | Authorized human/process |
| Payment status | Future payment provider + verified event |
| Entitlement | Future deterministic entitlement engine |
| Calculations | Deterministic/versioned code |
| User data | Defined local data policy |

---

# 42. Final build command to the engineering agent

Use the following as the governing instruction:

> You are building MyDayCompanion inside the EQUISTeG product factory.
>
> This document is the authoritative product build specification.
>
> First run `/precheck`. Do not implement before the mandatory pre-build gate passes.
>
> Inspect the repository, EQUISTeG template, product configuration, product inputs, architecture, security, privacy, licensing, testing and cost assumptions.
>
> MyDayCompanion V1 is Android-first, Kotlin + Jetpack Compose, local-first, offline-capable, Room/SQLite, Android Keystore-backed protection, local/provider AI abstraction, deterministic planner, explicit agent/tool authorization, local audit, export and complete deletion.
>
> Do not silently introduce a backend, cloud database, cloud storage, cloud AI, analytics service, external integration, mandatory account, paid API or recurring infrastructure.
>
> Do not implement future integrations merely because interfaces exist.
>
> AI is not an authority. AI-generated code is not trusted. AI must never independently authorize privileged device actions, access secrets, alter security policy, grant entitlement, or release production.
>
> Every device action must pass structured validation, deterministic policy, permission checks, allowlisted tool execution, verification and audit.
>
> Do not remove required security, privacy, testing, export, deletion or audit controls to accelerate development.
>
> Use feature branches, pull requests, automated tests, security checks, staging and authorized release.
>
> When a requirement is ambiguous, create a `NEEDS DECISION` item rather than silently guessing.
>
> When a security, privacy, licensing, cost or architectural blocker exists, stop.
>
> When implementation is approved, build the reusable foundation first and implement V1 capabilities incrementally.
>
> The objective is not maximum code. The objective is a reliable, private, maintainable and financially disciplined product foundation that can later evolve into a personal agent platform.

---

# 43. Immediate action checklist

**Do these in order.**

- [ ] Create private MyDayCompanion repository from EQUISTeG template.
- [ ] Add this master document.
- [ ] Add `product.config.json`.
- [ ] Add/update `CLAUDE.md`.
- [ ] Add `/precheck`.
- [ ] Add pre-build output directories.
- [ ] Add branch protection.
- [ ] Add CI baseline.
- [ ] Run `/precheck`.
- [ ] Review all evidence.
- [ ] Resolve blockers.
- [ ] Complete brand/domain verification.
- [ ] Approve Gate P0.
- [ ] Create first feature branch.
- [ ] Begin Android foundation.
- [ ] Do not begin feature expansion until the foundation gates pass.

---

# 44. Final principle

The first milestone is **not** a huge AI assistant.

The first milestone is:

**one trustworthy user outcome + one secure reusable agent foundation + one disciplined release process + controlled cash exposure.**

Build the foundation once.

Release capabilities phase-wise.

Measure reality.

Spend only when evidence earns the right to spend.

Reuse everything.

If MyDayCompanion wins, deepen it.

If it does not, preserve the machinery and move the machinery to the next opportunity.

**EQUISTeG → reusable factory → MyDayCompanion → evidence → revenue → reinvestment → next product.**
