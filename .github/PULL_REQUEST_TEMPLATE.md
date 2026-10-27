## What changed (plain English)


## Type
- [ ] New product
- [ ] Change — CHG-___
- [ ] New feature — UPD-___
- [ ] Bug fixes from testing — BUG-___ · report: docs/test-reports/___
- [ ] Review fixes — review file: docs/reviews/___
- [ ] Marketing drafts — MKT-___ (drafts only, nothing published)
- [ ] Promotion: development → staging, or staging → production

## Traceability
REQ: ___  ·  Test cases (tests/cases/test-cases.xlsx): ___  ·  Automation: ___

## Platforms touched
- [ ] Web  - [ ] Android  - [ ] API / Worker  - [ ] Shared packages

## How was this tested?
Unit: __ passed · Web E2E: __ passed · Mobile (Maestro): __ passed
Non-functional: Lighthouse __ / k6 p95 __ ms, errors __% / ZAP High __ Medium __ / axe serious+critical __
Promotion PRs: latest staging report ___ (release recommendation: Ready / Not ready) · open S1/S2 bugs: ___

## Review
Review file: docs/reviews/___ (founders' PRs)
| Reviewer | Verdict (PASS / NEEDS WORK / BLOCKED) | Rounds | Open S1–S3 (→ BUG / RSK id) |
|---|---|---|---|
|  |  |  |  |

## Checklist
- [ ] Lint, typecheck, unit tests and security scans green in CI
- [ ] Reviewed; no open S1–S3 finding without a BUG/RSK id
- [ ] Test-case rows added/updated (one row per step, expected result each), including `Non-functional` rows when an NFR is affected
- [ ] Bugs fixed here are set to `In PR` in `tests/bugs/bug-log.xlsx`, each with a regression test
- [ ] Test data only in `tests/testdata/`
- [ ] No secrets (`npm run check:secrets` passes); secret checks not weakened; no new paid service (zero-cost rule)
- [ ] Product category rules met for what this PR touches
- [ ] Payments/entitlement: server-side checks only, tests from phase 4b pass (if touched)
- [ ] HLD/LLD (.md + .docx) and CHANGELOG.md updated
- [ ] SEO / AI search files still valid (robots, sitemap, llms.txt, JSON-LD) if public pages changed
- [ ] NEEDS HUMAN items listed (`release/NEEDS-HUMAN.md`)

## Risks and issues
| What | How we tackle it | Done in this PR | Automatic response in production | Residual |
|---|---|---|---|---|
|  |  |  |  |  |

## Rollback
How to undo this if it fails in production:
