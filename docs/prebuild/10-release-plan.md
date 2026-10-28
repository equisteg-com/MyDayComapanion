# 10 — Release plan

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| RP-01 | Versioning | PASS | SemVer `versionName` (`0.1.0-phase0`); monotonically increasing `versionCode`; tags `vX.Y.Z` on `production` are immutable. |
| RP-02 | Internal build path | PASS | CI builds a debug APK on every PR (artifact); sideload to founder/test devices. |
| RP-03 | Alpha path | PASS (plan) | Play Console closed testing, 10–20 invited testers. Needs Play account (OQ-05). |
| RP-04 | Beta path | PASS (plan) | Play closed testing track, 20–100 testers. |
| RP-05 | Production path | PASS (plan) | Staged rollout 5% → 20% → 50% → 100% with vitals check at each step. |
| RP-06 | Signing/key strategy | PASS (plan) | Play App Signing; upload key generated and held offline by founder; injected into release CI from encrypted secrets; never in Git. |
| RP-07 | Rollback plan | PASS | Halt staged rollout; fix forward with a higher `versionCode` (Play cannot downgrade). DB migrations must be additive so a fix-forward build can open any newer schema. |
| RP-08 | Crash monitoring | PASS | Play Console Android vitals (no SDK). A local error log the user can choose to include in an export is planned for Phase 1. |
| RP-09 | Release notes | PASS (plan) | Root `CHANGELOG.md` (template format) per tag; Play "What's new" from it. |
| RP-10 | Privacy documentation | NEEDS DECISION (blocks alpha, not Phase 0) | Play needs a public privacy-policy URL and Data safety form. Proposed: a page on the existing EQUISTeG site. Data safety answers: no data collected or shared; data encrypted on device; user can delete (OQ-06). Since Gemini Nano (D-03), confirm these against Google's ML Kit and AICore data disclosures before submitting (OQ-20). |
| RP-11 | Terms requirements | PASS (plan) | Simple terms page alongside the privacy policy before beta. |
| RP-12 | User support process | PASS (plan) | One support email; alpha via a shared chat group; triage by frequency/severity/evidence. |
| RP-13 | Store compliance checklist | PASS (plan) | Target API level current (36), Data safety, content rating, permissions declarations (none in Phase 0), no misleading claims ("100% secure" is banned wording). |
| RP-14 | Branch protection | NEEDS DECISION (non-blocking) | Protected branches/rulesets on private repos need GitHub Pro or Team. Until then: PR-only convention + required CI by policy. (OQ-08) |

## Branch flow

`feature/*` → PR → `development` → PR → `staging` → founder approval → `production` + tag.
