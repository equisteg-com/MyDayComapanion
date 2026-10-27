---
name: newapp
description: Builds a complete new Equisteg product in this monorepo (Next.js web on Cloudflare + React Native/Expo Android app, Cloudflare Worker API, no-signup payments, AdSense/AdMob, SEO and AI search, security, firewall, tests, docs, Play Store pack) from an attached HTML or Claude-artifact prototype, at zero cost. Run only when the user types /newapp.
argument-hint: "[optional: path to prototype] [optional: review]"
---

# /newapp — prototype → production product (Equisteg monorepo)

Arguments: `$ARGUMENTS`

## Step 1 — Find the input
Use the first that exists:
1. A file path given in the arguments.
2. Files the user attached or @-mentioned in this message.
3. Every file in `inputs/` except `inputs/README.md`.

Accepted: .html .htm .jsx .tsx .js .md .txt .pdf and images. If nothing is found, reply with exactly one line: "Put the prototype in inputs/ (or attach it) and run /newapp again." and stop.

## Step 2 — Resume or start
- If `PROGRESS.md` exists, read it and continue from the first phase not marked ✅. Never redo finished phases.
- Otherwise:
  - Copy `templates/PROGRESS.md` (this skill folder) to the repo root.
  - Check that `development`, `staging` and `production` exist on the remote (`git ls-remote --heads origin`). If any is missing, list it as a one-time **NEEDS HUMAN** setup step with the exact commands; do not create it yourself.
- **Branch:** work only on the branch the user created and checked out (`git branch --show-current`). Never create, switch or rename branches. If the current branch is `main`, `development`, `staging` or `production`, stop with one line: "Create your branch first: `git switch development && git pull && git switch -c feature/<name>`, then run /newapp again."
- Read `CLAUDE.md` and `publisher.config.json` once at the start.

## Step 3 — Run the phases in order
Read each phase file only when you start that phase (keeps context small):

| # | File | Output |
|---|---|---|
| 0 | phases/00-intake.md | docs/product-brief.md (auto-derived from the prototype) + short brief printed to the user |
| 0b | phases/00b-stack-decision.md | docs/stack-decision.md — ASKS the user to confirm the stack |
| 1 | phases/01-competitors.md | docs/competitor-analysis.md, final scope |
| 2 | phases/02-brand-design.md | name, logo, web + Expo icons, design tokens |
| 3 | phases/03-architecture.md | HLD + LLD (.md + .docx) |
| 4 | phases/04-build.md | apps/web, apps/mobile, apps/api, packages |
| 4b | phases/04b-payments-no-signup.md | payment, entitlement, usage, auto-revoke, cron |
| 5 | phases/05-seo-ai.md | SEO, structured data, llms.txt, ASO |
| 6 | phases/06-security-firewall.md | security + firewall reports |
| 7 | phases/07-testing.md | tests/cases/test-cases.xlsx (per-step) + tests/web + tests/mobile + tests/testdata + CI |
| 8 | phases/08-performance.md | Lighthouse CI, k6, mobile perf report |
| 9 | phases/09-release.md | user manual + Play/AdSense/AdMob pack |
| 9b | phases/09b-production-ops.md | self-healing: watchdog, rollback, kill switches, error capture, auto-debug agent, runbooks, risk register, game day |
| 10 | phases/10-self-review.md | compliance matrix, fix loop, PR, final report |

After every phase:
- Run the phase's reviewers (table below) in parallel, fix S1–S3 and re-run, at most three rounds (`docs/standards/agent-reviews.md`). Record each final verdict in `PROGRESS.md`.
- Update `PROGRESS.md` (status ✅, key outputs, assumptions, NEEDS HUMAN items).
- Commit with a Conventional Commit message, e.g. `feat(web): phase 4 build core screens` or `docs: phase 3 HLD and LLD`.
- Print one short status line.

## Specialist reviewers per phase
| Phase | Reviewers |
|---|---|
| 0 | `agent:store-policy-reviewer` (policy risk, originality), `agent:category-reviewer` (category chosen, red lines) |
| 1 | `agent:category-reviewer` (scope respects the category rules) |
| 2 | `agent:ux-a11y-reviewer` (tokens contrast, component states, distinctness) |
| 4 | `agent:code-reviewer`, `agent:ux-a11y-reviewer` |
| 4b | `agent:payments-auditor`, `agent:security-auditor` |
| 5 | `agent:seo-ai-reviewer` |
| 6 | `agent:security-auditor` |
| 7 | `agent:test-traceability-auditor`, `agent:category-reviewer` (category tests present) |
| 9 | `agent:store-policy-reviewer`, `agent:marketing-claims-reviewer` (listing copy) |
| 9b | `agent:code-reviewer`, `agent:security-auditor` (ops routes, tokens, fault injection off in production) |
| 10 | full panel, then `agent:release-gate` (see phase 10) |

## Operating mode
- **Default: autonomous**, with ONE required question: the stack confirmation in Phase 0b (it has a recommended default). Resolve every other ambiguity with the most sensible assumption and record it in the brief's assumptions table.
- **If arguments contain `review`:** pause after Phase 1 (brief + scope), after Phase 4 (preview URL) and after Phase 10 (before PR/production) for approval.
- Stop and ask only if truly blocked (unreadable prototype, or the idea violates Google policy — real-money gambling, adult content, impersonation, etc.).
- Pushing the feature branch and opening the PR into `development` require the user's permission prompt; promotion to `staging`/`production` and Play submission require explicit confirmation.
- Zero-cost rule: never add a paid service; see `docs/standards/zero-cost.md`.

## Context management
When a phase ends and context is getting large: commit, update PROGRESS.md, and tell the user
"Phase <n> done. Type /clear then /newapp to continue." The next run resumes automatically.

## Finish
When Phase 10 is ✅, print the final report defined in phases/10-self-review.md, and suggest `/market launch` as the next command.
