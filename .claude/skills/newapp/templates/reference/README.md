# Reference implementations (proven in the first product built from this template, Oct 2026)

Working code for the parts every product needs and that are easy to get subtly wrong. `/newapp` **copies these into
the product and adapts them** in the phase named below — it does not rewrite them from scratch. Paths mirror where
each file goes in the product. `<slug>`, `<Product>` and `<domain>` are placeholders. Everything else that is
product-specific is listed in the "Adapt" column; grep for it before committing.

| File | Phase | What it does | Adapt |
|---|---|---|---|
| `apps/web/scripts/seo-audit.mjs` | 5 | Audits the static export: title ≤ 60, description 50–155, unique, canonical, one H1, og:image, alt text, JSON-LD parses, sitemap ↔ pages, llms.txt. With `NEXT_PUBLIC_NOINDEX=1` (dev/staging) it checks the opposite: every page noindex, robots blocks crawling | The list of required JSON-LD types at the end; add script `"seo:audit": "node scripts/seo-audit.mjs"` to `apps/web/package.json` (CI and deploy call `npm run seo:audit --workspaces --if-present`) |
| `apps/web/lib/seo.ts` | 5 | `fitTitle` / `fitDescription`: pick the most descriptive title/description that fits the limits | Brand suffix |
| `apps/web/scripts/indexnow.mjs` | 5 | Pings IndexNow with every sitemap URL after a production deploy (called by `deploy.yml`) | Generate the key file `apps/web/public/<32 hex>.txt` |
| `apps/web/scripts/csp-hashes.mjs` | 6 | Post-build: per-page `<meta>` CSP with the SHA-256 of each inline script (the site-wide header must keep `'unsafe-inline'` for Next's bootstrap). Build order: `next build && csp-hashes && build-sw` | Nothing; reads `public/_headers` |
| `packages/shared-types/src/zod-config.ts` | 4 / 6 | `z.config({ jitless: true })` — zod's eval probe otherwise raises a CSP violation on every page | Import first in the package index |
| `apps/api/src/lib/security.ts` | 4 / 6 | Canonical host redirect, CORS allow-list, **CSRF guard** (foreign Origin → 403, non-JSON body → 415), rate limits (binding + memory fallback), Turnstile (fails closed in production) | Mobile origin `<slug>://app`; add `hono/body-limit` 64 KB in `index.ts` |
| `apps/api/src/ops/*.ts`, `src/lib/github-issues.ts`, `migrations/00xx_ops.sql`, `test/ops.test.ts` | 9b | The whole self-healing layer: buffered request stats, error fingerprints + PII scrubber, health (third parties = degraded only), kill switches, faults (dev/staging only), breaker, email outbox, watchdog, `/errors`, `/config`, `/ops/summary`, `/ops/run-watchdog`, `/ops/fault` | `ops.config.ts`: `AUTO_DISABLE` and `ROUTE_FEATURE` (this product's features; never payments, restore, entitlement checks or the core flow); `faults.ts` `FAULT_NAMES`; payment provider host in `health.ts`; feature ids in `ops.test.ts`; number the migration |
| `apps/web/lib/error-report.ts` | 9b | `window` error + unhandled rejection + error boundary → `sendBeacon` to `/api/v1/errors`, sampled; load it lazily | — |
| `apps/mobile/src/ops.ts` | 9b | Error boundary + global handler → offline queue; `/config` on start/resume → "Please update" banner | Storage helper import, mobile origin |
| `tests/web/specs/security.nfr.spec.ts` | 6 | 12 checks: headers + hash CSP, probe paths, rate limit, CORS, webhook signature, entitlement, secrets in bundles, CSRF, crawlers, body limit, zero CSP violations | Routes, request bodies, page list, secret patterns for this product's providers |
| `tests/web/specs/self-healing.nfr.spec.ts` | 9b | Game day: fault → watchdog → kill switch → UI fallback → recovery | The faulted route, feature id and fallback text |
| `tests/web/specs/a11y.spec.ts`, `links.spec.ts` | 7 | axe (WCAG 2.2 AA) on key pages and flow steps; every sitemap page and internal link answers 200 | Page list, flow steps |

Wiring the ops layer (all in the product's `apps/api`):
1. `index.ts`: request-id middleware (accept `x-request-id`, echo it), `record()` stats after each `/api/*` request (use the real path when `routePath` contains `*`), `faults` middleware after the CSRF guard, mount `opsPublicRoutes`, run `runWatchdog` from `scheduled`.
2. `lib/errors.ts` `onError`: call `recordError({ platform: 'api', … })` with the real path (not a wildcard route pattern).
3. `lib/release.ts`: filter enabled features through `getKills()`.
4. `routes/ops.ts`: `mountOpsRoutes(opsRoutes, opsAuth)`.
5. Emails: send through `sendEmailReliably` (breaker + encrypted outbox).
6. Retention job: delete `request_stats`, `error_hourly`, `error_events`, closed `incidents`, dead `outbox` rows.
7. `wrangler.jsonc` staging vars: `GITHUB_REPO` (the game day opens `incident-staging` issues).
