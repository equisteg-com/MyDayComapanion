// Ops endpoints. Public: POST /errors (client error reports), GET /config (what is on, minimum app version).
// Founder/automation only (Bearer OPS_TOKEN): GET /ops/summary, POST /ops/run-watchdog (dev/staging only).
import type { OpsSummary, PublicConfig } from '@<slug>/shared-types';
import { Hono, type MiddlewareHandler } from 'hono';
import type { AppEnv } from '../env.ts';
import { HttpError } from '../lib/errors.ts';
import { background } from '../lib/log.ts';
import { getRelease } from '../lib/release.ts';
import { rateLimit } from '../lib/security.ts';
import { z } from 'zod';
import { ErrorReport, recordError } from './errors.ts';
import { FAULT_NAMES, resetFaultCache } from './faults.ts';
import { healthChecks } from './health.ts';
import { getKills } from './killswitch.ts';
import { OPS } from './ops.config.ts';
import { breakerState } from './resilience.ts';
import { flush, minuteOf, windowStats } from './stats.ts';
import { runWatchdog } from './watchdog.ts';

export const opsPublicRoutes = new Hono<AppEnv>();

/** Web and Android error reports: 8 KB max, 5 per minute per IP, scrubbed before storage, answered 202. */
opsPublicRoutes.post('/errors', rateLimit('strict', 'errors'), async (c) => {
  const raw = await c.req.text();
  if (raw.length > OPS.errorReport.maxBytes) throw new HttpError(413, 'payload_too_large', 'Error report is too large.');
  const r = ErrorReport.parse(JSON.parse(raw));
  await background(c, recordError(c.env, { platform: r.platform, release: r.release, name: r.name, message: r.message, stack: r.stack, route: r.route, feature: r.feature, rid: r.rid })
    .then(() => undefined).catch(() => undefined));
  return c.body(null, 202);
});

opsPublicRoutes.get('/config', async (c) => {
  const [release, kills, cfg] = await Promise.all([
    getRelease(c.env),
    getKills(c.env),
    c.env.FLAGS.get<{ minAndroidVersion?: string; message?: string }>('config', 'json').catch(() => null),
  ]);
  const body: PublicConfig = { enabled: release.enabled, killed: Object.keys(kills), minAndroidVersion: cfg?.minAndroidVersion ?? null, message: cfg?.message ?? null };
  c.header('cache-control', 'public, max-age=30');
  return c.json(body);
});

const iso = (t?: number | null) => (t ? new Date(t).toISOString() : null);

export async function buildSummary(env: AppEnv['Bindings'], now = Date.now()): Promise<OpsSummary> {
  await flush(env);
  const m = minuteOf(now);
  const [w5, w15, w60, health, kills, incidents, fps, jobs, today] = await Promise.all([
    windowStats(env, m - 5, m), windowStats(env, m - 15, m), windowStats(env, m - 60, m),
    healthChecks(env, now), getKills(env, now),
    env.DB.prepare("SELECT key, severity, title, issue, opened_at, last_seen FROM incidents WHERE status = 'open' ORDER BY opened_at DESC LIMIT 50")
      .all<{ key: string; severity: string; title: string; issue: number | null; opened_at: number; last_seen: number }>().then((r) => r.results),
    env.DB.prepare('SELECT fingerprint, platform, route, count, first_seen, release FROM error_events WHERE first_seen >= ? ORDER BY first_seen DESC LIMIT 50')
      .bind(now - 60 * 60_000).all<{ fingerprint: string; platform: string; route: string | null; count: number; first_seen: number; release: string }>().then((r) => r.results),
    env.DB.prepare("SELECT job, MAX(ran_at) t FROM job_runs WHERE ok = 1 AND job IN ('watchdog','retention','payments_daily') GROUP BY job")
      .all<{ job: string; t: number }>().then((r) => r.results),
    windowStats(env, Math.floor(now / 86_400_000) * 1440, m),
  ]);
  const breakers: OpsSummary['breakers'] = {};
  for (const svc of ['email', 'razorpay', 'play']) {
    const b = await breakerState(env, svc);
    if (b.openUntil || b.fails.length) breakers[svc] = { openUntil: iso(b.openUntil > now ? b.openUntil : null), recentFailures: b.fails.length };
  }
  return {
    release: env.RELEASE ?? 'local',
    env: env.ENVIRONMENT,
    windows: { '5m': w5, '15m': w15, '60m': w60 },
    newFingerprints: fps.map((f) => ({ fingerprint: f.fingerprint, platform: f.platform, route: f.route ?? '', count: f.count, firstSeen: iso(f.first_seen)!, release: f.release })),
    health: { ok: health.ok, failed: health.failed, degraded: health.degraded },
    incidents: incidents.map((i) => ({ key: i.key, severity: i.severity, title: i.title, issue: i.issue, openedAt: iso(i.opened_at)!, lastSeen: iso(i.last_seen)! })),
    killSwitches: Object.fromEntries(Object.entries(kills).map(([k, v]) => [k, { at: iso(v!.at)!, reason: v!.reason }])),
    breakers,
    jobs: Object.fromEntries(['watchdog', 'retention', 'payments_daily'].map((j) => [j, iso(jobs.find((x) => x.job === j)?.t)])),
    usage: { workers_requests_today_pct: Math.round((today.requests / OPS.freeTier.workersRequestsPerDay) * 1000) / 10 },
  };
}

export function mountOpsRoutes(ops: Hono<AppEnv>, auth: MiddlewareHandler<AppEnv>) {
  ops.get('/ops/summary', auth, async (c) => c.json(await buildSummary(c.env)));
  // Game day / staging only: production runs the watchdog from its cron trigger only.
  ops.post('/ops/run-watchdog', auth, async (c) => {
    if (c.env.ENVIRONMENT === 'production') throw new HttpError(403, 'forbidden', 'The watchdog runs from the cron trigger in production.');
    // ?at=<epoch ms> simulates a later run so the game day can prove auto-close without waiting (non-production only)
    const at = Number(c.req.query('at')) || Date.now();
    return c.json(await runWatchdog(c.env, at));
  });
  // Game day: turn a fault on or off (body {"name":"requirements","mode":"throw"|"500"|"slow"|null}). Never in production.
  ops.post('/ops/fault', auth, async (c) => {
    if (c.env.ENVIRONMENT === 'production') throw new HttpError(403, 'forbidden', 'Faults cannot be injected in production.');
    const { name, mode } = z.object({ name: z.enum(FAULT_NAMES), mode: z.enum(['500', 'slow', 'throw']).nullable() }).strict().parse(await c.req.json());
    if (mode) await c.env.FLAGS.put(`fault:${name}`, mode, { expirationTtl: 3600 }); // expires by itself after an hour
    else await c.env.FLAGS.delete(`fault:${name}`);
    resetFaultCache();
    return c.json({ name, mode });
  });
}
