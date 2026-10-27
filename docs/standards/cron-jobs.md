# Cron jobs — where they run and how to set them up

Two free schedulers, used for different things. Times are UTC (IST = UTC + 5:30).

## 1. Cloudflare Cron Triggers — jobs that touch app data
Used for: expiring entitlements, unbinding devices, renewal reminders, cleaning old webhook events and magic links, daily usage summaries.

1. In `apps/api/wrangler.jsonc`, production environment only:
   ```jsonc
   "triggers": { "crons": ["0 1 * * *"] }   // 01:00 UTC = 06:30 IST, daily
   ```
2. In `apps/api/src/index.ts` export a `scheduled` handler next to `fetch`:
   ```ts
   export default {
     fetch: app.fetch,
     async scheduled(event: ScheduledController, env: Env, ctx: ExecutionContext) {
       ctx.waitUntil(runDailyJobs(env));   // expire, unbind, remind, cleanup — all in one trigger
     },
   };
   ```
3. Test locally: `npx wrangler dev --test-scheduled`, then open `http://localhost:8787/__scheduled?cron=0+1+*+*+*`.
4. Deploy as usual (merging into `production`). Check runs in Cloudflare → Workers & Pages → the Worker → Settings → Triggers, and logs under Observability.
5. Make every job idempotent (safe to run twice) and log a one-line summary (`expired=3 reminded=5 purged=120`).

Limit: 5 Cron Triggers per **account** on the Free plan, shared by all products. Budget them:
- 1 for `equisteg-platform` (expiry, reminders and cleanup for every product's entitlements, once payments move there),
- 1 for `equisteg-monitor` (uptime, section 3),
- up to 3 for products with their own data jobs.
Never add a cron to `dev` or `staging`. If more jobs are needed, one trigger fans out to other Workers through service bindings or Queues. Run many jobs inside one trigger.

## 2. GitHub Actions schedule — jobs about the code and the site
Already in `.github/workflows/scheduled.yml`:
- Weekly (Mon 02:30 UTC): OSV-Scanner dependency audit and Gitleaks secret audit.
- Daily (03:00 UTC): production health, `robots.txt`, `sitemap.xml`, `llms.txt`, `ads.txt` reachable, and an AI-search crawler check. Opens an `INC:` issue on failure.

Set repository variable `PRODUCTION_URL` (Settings → Secrets and variables → Actions → Variables). GitHub may delay scheduled runs by some minutes and pauses schedules in repos with no activity for 60 days.

## 3. Uptime every 5 minutes (optional)
A tiny shared `equisteg-monitor` Worker with `"crons": ["*/5 * * * *"]` that calls each product's `/api/v1/health` and emails through Resend on failure. Uses 1 of the 5 account cron triggers and ~288 requests/day per product.
