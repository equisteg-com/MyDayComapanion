# Template v4: specialist reviewers (20 Oct 2026)

Source: we compared this template with the open-source *agency-agents* collection (a large library of role-based AI agent prompts). We used it only to get ideas. No text, code or file from it was copied. Every file below was written for this template, its stack and its company rules.

## What we looked at and what we took

| Idea in agency-agents | Useful here? | What v4 does instead |
|---|---|---|
| Hundreds of role "personalities" across sales, marketing, finance, games, GIS and more | Mostly no. A founder building one product needs a few reviewers that know *this* stack, not a general roster | Ten reviewers, each tied to our phases, our rules (`CLAUDE.md`, phase 4b, phase 6, zero-cost) and our files |
| Agents installed into Claude Code as subagent files | Yes. Claude Code reads `.claude/agents/*.md` from the repo, so every product gets them with no install step | `.claude/agents/` with read-only tools (no `Edit` or `Write`) so a reviewer can never change code it is judging |
| A skeptical final checker that defaults to "not ready" | Yes. Our `/test` release recommendation and phase 10 were written by the same session that built the product | `agent:release-gate`: starts at NOT READY, needs evidence for the exact commit being promoted |
| A "smallest diff" discipline | Yes. `/change` and auto-fix PRs should not carry drive-by refactors | `agent:scope-guard`: labels every hunk needed / supporting / unrelated, protects workflows, settings and payment code |
| Security review aimed at AI-written code (secrets in public env vars, missing authz, prompt-injection sinks) | Yes, but for our stack: `NEXT_PUBLIC_*`, `EXPO_PUBLIC_*`, Hono, D1, CSP hashes | `agent:security-auditor` and a separate `agent:payments-auditor` that walks the phase 4b trust rules path by path |
| Accessibility and "does the UI look generic" reviewers | Yes. AdSense and Play reviewers, and users, punish look-alike products; we already require distinctness from `existingProducts` | `agent:ux-a11y-reviewer` (WCAG 2.2 AA, finished states, ad placement, distinct from sibling products) |
| Incident response roles | Yes. `auto-debug.yml` jumped straight to fixing | `agent:incident-investigator` gives cause, evidence, reproduction plan and smallest fix before any code changes |
| Structured hand-offs between agents and quality gates with a retry limit | Yes. Without a shared format, results from several reviewers cannot be merged | `docs/standards/agent-reviews.md`: one brief format, one verdict format (PASS / NEEDS WORK / BLOCKED + findings table), evidence rules, maximum three fix rounds then bug log or risk register |
| Linting agent files in CI | Yes. A typo in an agent name would silently skip a review | `tests/tools/lint-agents.mjs` (`npm run lint:agents`, first step of `ci.yml`) |
| Multi-tool converters and installers (Cursor, Copilot, Gemini …), sales/marketing/finance personas, desktop app | No. We use Claude Code only; these add maintenance with no benefit to a product repo | Not taken |

## What changed

| Area | Before (v3) | Now (v4) |
|---|---|---|
| `.claude/agents/` | None | `code-reviewer`, `scope-guard`, `security-auditor`, `payments-auditor`, `ux-a11y-reviewer`, `seo-ai-reviewer`, `store-policy-reviewer`, `test-traceability-auditor`, `release-gate`, `incident-investigator`, plus a roster README |
| Commands | `/newapp`, `/change`, `/update`, `/test` | Plus `/review [area] [fix\|report]`: picks reviewers from the changed paths, runs them in parallel, merges, fixes, saves `docs/reviews/…` |
| `/newapp` | Self-review only in phase 10 | Reviewers per phase (table in the skill); phase 10 runs the full panel and the release gate |
| `/change`, `/update` | Tests, scans, docs, PR | New *Specialist review* step before the PR |
| `/test` | Release recommendation written by the same session | `agent:release-gate` gives the verdict; `agent:test-traceability-auditor` checks every failure maps to a TC and a bug |
| Auto-debug (`INCIDENT_PROMPT.md`, `auto-debug.yml`) | Fix directly | Investigate first, scope-guard (and security/payments when touched) before the PR; `Agent` added to the allowed tools |
| PR template | — | *Specialist review* table and a checklist line |
| `CLAUDE.md` | — | Rule: specialist review before every PR; five commands |
| CI | — | `npm run lint:agents`, `npm run test:guards`, secret-file check |
| `.claude/settings.json` | — | Read-only commands reviewers need: `git show`, `rg`, `python3 tests/tools/{bug_log,export_cases}.py`, `gh pr view/diff` |

## Also in v4: marketing, product categories, repository security

| Area | Now | Why |
|---|---|---|
| `/market` + `agent:marketing-claims-reviewer` | Positioning, launch plan, content calendar, listing refresh, social and opt-in email drafts, monthly growth report in `docs/marketing/`; every draft checked for provable claims, no fake social proof, disclosures, spam and privacy rules, ₹0 tools | Founders asked for marketing support; drafts only, because posting on the company's behalf cannot be undone |
| `docs/standards/product-categories.md` + `agent:category-reviewer` | Rules and red lines for finance, games, utility and tech products; category chosen in phase 0 and checked in phases 0, 1, 7 and 10 | Each category carries its own store, legal and trust risks (money maths and advice, gambling and children, permissions, pasted secrets) |
| `.claude/hooks/guard-secrets.mjs` (with `tests/tools/secret-patterns.mjs`) | Blocks Claude from reading secret files, dumping the environment, reading stored credentials, uploading local files, piping downloads into a shell and sending token-shaped values; tested by `npm run test:guards` in CI | A hidden instruction in a prototype, issue or web page must not be able to leak secrets or code |
| `.claude/settings.json` | Generic `curl` moved from allowed to ask (read-only checks stay allowed); more secret files denied; `git credential`, `gh auth token`, `printenv`, `--no-verify`, making a repo public, creating repos denied | Least privilege |
| `.githooks/pre-commit` + `tests/tools/check-forbidden-files.mjs` | Blocks secret files and live keys before they enter git history; enabled by `npm install`; same check in CI | Removing a secret after it is pushed does not un-leak it |
| `.gitignore` | `.env.*` (except `.env.example`), `*.pem`, `*.pfx`, service-account JSON, `.incident/` | Defence in depth |
| `.github/dependabot.yml` | Monthly grouped npm and Actions update PRs into `development` | Free vulnerability and pinned-SHA updates through the normal review path |
| `SECURITY.md`, `docs/standards/repo-security.md` | How to report a problem; layered protections; click-by-click GitHub, Cloudflare, Google and Claude account settings; what to rotate when someone leaves | Most breaches start from an account or a token, not the code |
| Phase 10 | No more reading the git credential to open a PR without `gh`; compliance matrix has category and repo-security blocks | Removes a step that exposed a token |

**About the source repo:** agency-agents is a folder of plain text prompt files. It was read once, offline, from an uploaded zip. Nothing in this template links to it, installs from it or sends anything to it, and it never had access to this repository.

## Using v4 in a product created from v3
On a `change/template-v4` branch copy `.claude/agents/`, `.claude/skills/`, `.claude/hooks/`, `.githooks/`, `.github/dependabot.yml`, `SECURITY.md`, `docs/standards/{product-categories,repo-security}.md`, `docs/marketing/README.md`, `tests/tools/{secret-patterns,check-forbidden-files,install-githooks}.mjs`, `tests/tools/guard-secrets.test.mjs`, `docs/standards/agent-reviews.md`, `docs/reviews/README.md`, `tests/tools/lint-agents.mjs`, `prompts/INCIDENT_PROMPT.md`, `.github/PULL_REQUEST_TEMPLATE.md` and `.github/workflows/{ci,auto-debug}.yml`; merge the `CLAUDE.md` and `.claude/settings.json` lines by hand; add the `lint:agents`, `test:guards`, `check:secrets` and `prepare` scripts to the root `package.json`, merge `.gitignore`, and set the product's category in `docs/product-brief.md` §1b. Run `npm run lint:agents` and `/review report`, then open a PR into `development`.
