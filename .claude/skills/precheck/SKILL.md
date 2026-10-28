---
name: precheck
description: Mandatory pre-build gate for MyDayCompanion. Run only when the user types /precheck or before /newapp, /change or /update would add scope. Mandatory pre-build gate. Inspect everything, write docs/prebuild/00-12 with PASS / FAIL / NEEDS DECISION per item. Never implements.
---

# /precheck — MyDayCompanion pre-build gate

Do not implement merely because the product specification exists. First inspect the repository, EQUISTeG
template, product inputs, product configuration and the MyDayCompanion master build document. Identify
contradictions, missing decisions, risky dependencies, privacy issues, security gaps, licensing problems, cost
exposure and scope expansion. Generate all required pre-build artifacts. Mark every check PASS, FAIL or NEEDS
DECISION. Stop on blockers. Do not silently introduce cloud APIs, cloud databases, cloud storage, cloud AI,
analytics, paid services, external integrations or privileged device actions. Do not remove security controls.
Do not give the AI authority over permissions or device actions. Do not begin implementation until the
founder-approved pre-build gate is passed.

## Inputs to inspect

1. `docs/product/MYDAYCOMPANION_MASTER_BUILD.md` (wins on conflict) and `docs/product/MYDAYCOMPANION_BUILD_SPEC.md`
2. `product.config.json`, `publisher.config.json`
3. Everything in `inputs/`
4. The EQUISTeG template repository, if available to this session
5. The repository itself: `gradle/libs.versions.toml`, module build files, `AndroidManifest.xml`, `.github/workflows/`
6. Existing `docs/prebuild/*` (on re-runs, update rather than rewrite; keep decision history)

## Steps

1. For every checklist item in master §9 and spec §7, decide PASS / FAIL / NEEDS DECISION and cite the evidence
   (file path, section, or test name). PASS means the evidence exists now, not that it is planned, unless the item
   itself is "planned/defined".
2. List every contradiction between the two product documents and how it is resolved.
3. Check every hard-stop condition in master §8. Any hit is a FAIL and a blocker.
4. Dependencies: every library in `gradle/libs.versions.toml` must appear in `11-dependency-license-inventory.md`
   with licence, purpose, network behaviour and cost. Reject anything with network/telemetry, copyleft runtime
   licences, or a paid tier.
5. Permissions: every `uses-permission` must be justified in `06-permission-model.md` with phase and risk.
6. Data: every stored field must appear in `04-data-map.md` with classification, retention, export and deletion.
7. Write/refresh these files, each starting with a status table:

   ```
   docs/prebuild/
   00-prebuild-summary.md        01-product-scope.md          02-demand-assumptions.md
   03-architecture-decision.md   04-data-map.md               05-threat-model.md
   06-permission-model.md        07-ai-risk-model.md          08-test-strategy.md
   09-cost-model.md              10-release-plan.md           11-dependency-license-inventory.md
   12-open-questions.md
   ```

8. `00-prebuild-summary.md` ends with the gate verdict: BLOCKED (any FAIL or blocking NEEDS DECISION),
   READY FOR FOUNDER APPROVAL, or APPROVED (only when the founder has said so, with date and their words).
9. Do not edit source code during `/precheck`. Report the verdict and the list of founder decisions.

## Status vocabulary

- **PASS** — evidence exists and is linked.
- **FAIL** — contradicts a requirement or a hard-stop condition. Blocks the gate.
- **NEEDS DECISION** — only the founder can resolve (brand, money, accounts, legal). Mark whether it blocks the
  current phase.
