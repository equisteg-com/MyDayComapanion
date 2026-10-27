## What changed (plain English)


## Command / type
- [ ] /newapp (new product)
- [ ] /change — CHG-___
- [ ] /update — UPD-___
- [ ] /test — bugs fixed: BUG-___ · report: docs/test-reports/___
- [ ] /review — review file: docs/reviews/___
- [ ] /market — MKT-___ (drafts only, nothing published)
- [ ] Promotion: development → staging, or staging → production

## Traceability
REQ: ___  ·  Test cases (tests/cases/test-cases.xlsx): ___  ·  Automation: ___

## Platforms touched
- [ ] Web  - [ ] Android  - [ ] API / Worker  - [ ] Shared packages

## How was this tested?
Unit: __ passed · Web E2E: __ passed · Mobile (Maestro): __ passed
Non-functional: Lighthouse __ / k6 p95 __ ms, errors __% / ZAP High __ Medium __ / axe serious+critical __
Promotion PRs: latest staging report ___ (release recommendation: Ready / Not ready) · open S1/S2 bugs: ___

## Specialist review
Review file: docs/reviews/___ (`docs/standards/agent-reviews.md`)
| Reviewer | Verdict (PASS / NEEDS WORK / BLOCKED) | Rounds | Open S1–S3 (→ BUG / RSK id) |
|---|---|---|---|
| code-reviewer |  |  |  |
| scope-guard |  |  |  |
|  |  |  |  |

## Checklist
- [ ] Lint, typecheck, unit tests and security scans green in CI
- [ ] Specialist reviewers run for the paths changed; no open S1–S3 finding without a BUG/RSK id
- [ ] Test-case rows added/updated (one row per step, expected result each), including `Non-functional` rows when an NFR is affected
- [ ] Bugs fixed here are set to `In PR` in `tests/bugs/bug-log.xlsx`, each with a regression test
- [ ] Test data only in `tests/testdata/`
- [ ] No secrets (`npm run check:secrets` passes); guards and deny rules not weakened; no new paid service (zero-cost rule)
- [ ] Category rules met (`docs/standards/product-categories.md`) for what this PR touches
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
