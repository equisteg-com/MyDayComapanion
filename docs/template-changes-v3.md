# Template v3 — what changed and why (3 Oct 2026)

Source: everything that had to be fixed, hardened or invented while building the first product from template v2
(all ten phases, PRs merged into its `development` branch). Only the reusable parts are ported; nothing
product-specific (name, domain, IDs, prices, feature data, test cases) is in the template.

| Area | Before (v2) | Now (v3) | Why |
|---|---|---|---|
| Workflows (`ci`, `deploy`, `staging-tests`, `mobile`, `scheduled`, `auto-debug`, `auto-rollback`) | Actions referenced by tag; values interpolated straight into shell | Every action pinned to a commit SHA; untrusted values passed through `env:`; build variables (`NEXT_PUBLIC_SITE_URL`, `NEXT_PUBLIC_NOINDEX`, release id) set per environment; D1 migration guard; SEO audit after build; IndexNow ping on production; MobSF, pinned Maestro and mobile perf in `mobile.yml`; `OPS_TOKEN` for canary and game day | Supply-chain and script-injection hardening; the deploy must build the same way for every environment |
| Product-neutral CI steps | — | `npm run seo:audit --workspaces --if-present`, bundle scan only when `apps/web/out` exists, IndexNow only when the script exists, production URL from the `PRODUCTION_URL` variable | The same workflows work on an empty template and on a finished product |
| `infra/wrangler.example.jsonc` | No `run_worker_first`; no rate-limit bindings | `run_worker_first: ["/api/*"]`, `ratelimits` bindings, `SUPPORT_EMAIL`, staging `GITHUB_REPO`, apex-only route (www → apex is a Redirect Rule), full secrets list, `.dev.vars` note | Only `/api/*` runs the Worker — pages are served as static files (no Worker request, still up when the API is down); free rate limiting; game day needs the repo on staging |
| `turbo.json`, `.gitignore` | Env vars not part of the cache key | `NEXT_PUBLIC_*` in the build hash, CI variables passed through, build caches ignored | Otherwise a cached build made for one environment can be reused for another |
| Test tooling | Result collector matched `TC-X-000` only; Lighthouse single run with category SEO; load test counted 429 as failure | TC IDs with a module part; Lighthouse median of 3 with per-URL budgets (`assertMatrix`) and SEO checked audit by audit (dev/staging are noindex); load routes may expect `[200, 429]`; Playwright workers and timeouts capped; `tests/web/fixtures.ts` (console guard); `export_cases.py` (sheet → Markdown + coverage check); `mobile-perf.sh`; Maestro `config.yaml` and `npm run test:mobile` on the workspace | False failures on staging; flaky parallel runs; missing traceability check |
| Reference implementations | Phases described what to build | `.claude/skills/newapp/templates/reference/` — 25 working files with a README: SEO audit (two modes), title/description fitting, IndexNow, hashed CSP, zod config, security middleware (CORS, CSRF, rate limit, Turnstile), the whole self-healing ops layer with migration and unit tests, web and Android error reporting, security / self-healing / accessibility / links specs | These took the longest to get right; the next product copies and adapts instead of rebuilding |
| Phase instructions 4, 5, 6, 7, 8, 9, 9b, 10 | — | A "Proven practice" section in each | Lessons recorded where the next run will read them |
| Phase 6 | "nonces for inline scripts" | Per-page SHA-256 hashes | A static export has no server render to mint nonces |
| `/test` skill, tests READMEs | `maestro test tests/mobile/flows` | `maestro test tests/mobile` (uses `config.yaml`); new tools listed | Consistency with `mobile.yml` |

## Mistakes this version prevents
| What went wrong in the first product | Prevention now in the template |
|---|---|
| CI `npm ci` failed: a transitive dependency pinned another version of a package | Phase 4 and 10: clean `npm ci` before every PR; scoped `overrides` |
| Dev deploy failed after merge: the SEO audit ran on a noindex build and found "0 indexable pages" | Audit has a noindex mode; phases 5 and 10 run both modes before the PR |
| Service worker never activated (one redirected URL failed the whole precache) | Phase 4 rule + phase 7 must-have check |
| Horizontal scroll at 320 px from hidden elements in scroll boxes | Phase 4 rule + phase 7 must-have check |
| A failed or estimated check displayed as passed; stale file downloaded after changing settings | Phase 7 must-have checks |
| First-load JavaScript too heavy (validation library and state store in the shell) | Phase 4: zod-free entry points, lazy client, islands |
| CSP violation logged on every page by zod | `zod-config.ts` (jitless) |
| Incident never closed; middleware errors attributed to a wildcard route; an issue lost when a label was missing | Fixed in the reference ops layer; noted in phase 9b |

## Not ported (on purpose)
Product name, domain, package ids, prices and plans, feature data, page content, test cases and bug log,
product reports and runbooks, the phase-release script (described in phase 9; each product writes its own small one).

## Using v3 for a product that was created from v2
Copy `.github/workflows/`, `tests/tools/`, `tests/web/fixtures.ts`, `tests/web/playwright.config.ts`,
`tests/load/`, `turbo.json` and `.claude/skills/` from the template on a `change/template-v3` branch, run
`npm run check`, and open a PR into `development`. Compare `apps/api/wrangler.jsonc` with
`infra/wrangler.example.jsonc` by hand (it holds the product's ids).
