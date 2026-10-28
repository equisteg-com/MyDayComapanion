# 02 — Demand assumptions

No market evidence exists yet. Everything here is a hypothesis to test in alpha/beta, not a fact.

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| DA-01 | Demand assumptions documented | PASS | H1–H6 below. |
| DA-02 | Willingness-to-pay hypothesis documented | PASS | H5. Pricing deliberately not set (master §33). |
| DA-03 | Success metrics defined | PASS | Metrics table. |
| DA-04 | Kill criteria defined | PASS (provisional) | Thresholds below; founder to confirm before alpha. |
| DA-05 | Metrics collectable without analytics | PASS | Local-only counters + opt-in summary the user shares manually; interviews. No SDK. |
| DA-06 | Market/competitor scan | NEEDS DECISION (non-blocking) | Founder to decide whether to run a competitor scan before alpha. Not required for Phase 0 code. |

## Hypotheses

| # | Hypothesis | How we test | Evidence that counts |
|---|---|---|---|
| H1 | People want a plan, not another list | Alpha interviews + plans confirmed/proposals | ≥60% of proposals confirmed (possibly after edit) |
| H2 | "Works without an account, nothing leaves the phone" is a real reason to choose it | Interview question; beta landing copy test | ≥30% of beta users cite privacy unprompted |
| H3 | Rule-based extraction is good enough to start | Edit rate on proposed tasks | <25% of proposed tasks edited/deleted |
| H4 | Replanning when the day changes is the retention hook | Replans per active day | ≥1 replan on ≥30% of active days |
| H5 | Some users pay for "power" (advanced planning, routines, local model) | Beta: fake-door/pre-order question | ≥10% say they would pay at a stated price |
| H6 | Battery cost is acceptable | Battery Historian on 3 devices | <1% battery/day attributable to the app |

## Metrics (from master §32, collected locally)

Activation (first confirmed plan within first session), first successful reminder (Phase 1), DAU/WAU from
local open counts, plans created/confirmed, replans, reminder usefulness (thumbs on notification), error rate
(`ai.extract` failures in audit), permission denials, crash rate (Play vitals), retention (D1/D7/D30 from
alpha check-ins), willingness to pay (survey). Metrics stay on the device; alpha users share a summary file
voluntarily.

## Kill / pivot criteria (provisional)

- Alpha (10–20 users, 3 weeks): if <40% create a second plan within 7 days → fix/simplify the capture-to-plan flow.
- Beta (20–100 users, 6 weeks): if D30 retention <15% **and** H5 <10% → reposition or convert to feeder.
- Any phase: if support load >2 hours/week per 20 users → simplify before growing.
- Parking preserves the reusable machinery (master §35).
