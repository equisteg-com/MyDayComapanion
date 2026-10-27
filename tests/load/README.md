# Performance and load testing — two commands

| You want to know… | Run | Takes | What it does |
|---|---|---|---|
| Are the pages fast, accessible and SEO-ready? Does the site answer quickly? | `npm run perf` | ~2 min | Lighthouse (mobile) on the key pages + a small load check (2 users, 30 s) |
| Does it hold normal traffic? | `npm run load` | ~2 min | 10 users for 1½ min |
| Where does it break? | `npm run load -- stress` | ~3 min | climbs to 30 users |
| Does it survive a sudden rush? | `npm run load -- spike` | ~1 min | jumps from 2 to 40 users |
| Does it stay stable over time? | `npm run load -- soak --yes` | 30 min | 5 users for 30 min |

- Everything runs against **staging** by default. Add `dev` to test dev (`npm run perf -- dev`). **Production is always refused.**
- The address comes from `--url https://…`, else `tests/testdata/shared/environment.json`, else the GitHub variable `STAGING_URL` / `DEV_URL`.
- **Pass** means: Lighthouse (median of 3 runs) ≥ 90 in Performance, Accessibility and Best Practices and every SEO audit passing except "is crawlable" (dev and staging are noindex on purpose) on every page; 95% of requests answer in under 500 ms; fewer than 1% of requests fail.
- Results go in `test-results/perf-<date>-<time>/`: `summary.md` (read this), the Lighthouse HTML reports in `lhci/`, and the k6 numbers.
- Failures? Run `/test` (or `python tests/tools/bug_log.py ingest test-results/perf-…/summary.json`) to log them as bugs.
- `npm run perf` also runs automatically after every staging deploy (`staging-tests.yml`).

## What to edit for each product
| File | Put in it |
|---|---|
| `tests/load/lighthouserc.json` | the key pages (`"url": ["/", "/pricing", "/tool"]`) and, if needed, stricter scores |
| `tests/testdata/shared/load.json` | the routes the load test calls (pages and API routes), and the limits (`p95Ms`, `maxErrorRate`) |

`tests/load/load.js` holds the five load profiles; normally you don't touch it.

## Needed on your computer (free)
- Node.js 22+ (Lighthouse downloads itself; it uses Chrome, or Playwright's Chromium).
- k6: `winget install k6 --source winget` (Windows), `brew install k6` (macOS) — or a running Docker, which is used automatically.
- `npm run test:preflight` shows what is missing.

## Free-tier care
Each request counts toward the Workers free limit of 100,000 requests/day (shared with real users of that environment). `perf` ≈ 200 requests; `load` ≈ 2,500; `stress` ≈ 12,000; `spike` ≈ 5,000; `soak` ≈ 25,000 (needs `--yes`). Never run several big profiles on the same day.
