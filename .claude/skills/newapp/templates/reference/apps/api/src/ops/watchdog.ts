// The watchdog (docs/standards/self-healing.md §1–2, §5). Runs every 5 minutes from the cron trigger. Plain code, no
// AI: it detects, contains (kill switch, rollback request) and opens/updates/closes one GitHub issue per incident.
// Every step is isolated; the watchdog never throws.
import type { FeatureId } from '@<slug>/shared-types';
import type { Env } from '../env.ts';
import { addLabels, closeIssue, commentIssue, openIssue } from '../lib/github-issues.ts';
import { healthChecks } from './health.ts';
import { clearKill, getKills, setKill } from './killswitch.ts';
import { AUTO_DISABLE, OPS, ROUTE_FEATURE } from './ops.config.ts';
import { MAX_ATTEMPTS, processOutbox } from './outbox.ts';
import { flush, minuteOf, windowStats } from './stats.ts';

export interface Signal { key: string; severity: 'sev1' | 'sev2' | 'sev3'; title: string; detail: string; feature?: FeatureId; rollback?: boolean; needsFounder?: boolean }
interface IncidentRow { key: string; severity: string; status: string; title: string; issue: number | null; opened_at: number; last_seen: number; actions: string | null }
export interface WatchdogReport { at: number; release: string; signals: Signal[]; actions: string[]; closed: string[]; errors: string[] }

const H = 3_600_000;
export const label = (env: Env) => (env.ENVIRONMENT === 'production' ? 'incident' : env.ENVIRONMENT === 'staging' ? 'incident-staging' : null);
const featureOfRoute = (route: string) => ROUTE_FEATURE.find(([p]) => route.startsWith(p))?.[1];

/** When the current release started serving (first watchdog run that saw it). */
export async function releaseStart(env: Env, now: number): Promise<number> {
  const release = env.RELEASE ?? 'local';
  const cur = await env.FLAGS.get<{ release: string; at: number }>('release:start', 'json').catch(() => null);
  if (cur?.release === release) return cur.at;
  await env.FLAGS.put('release:start', JSON.stringify({ release, at: now })).catch(() => undefined);
  return now;
}

export async function collectSignals(env: Env, now: number, startedAt: number, errors: string[]): Promise<Signal[]> {
  const out: Signal[] = [];
  const step = async (name: string, fn: () => Promise<void>) => { try { await fn(); } catch (e) { errors.push(`${name}: ${e instanceof Error ? e.message : 'failed'}`); } };
  const m = minuteOf(now);

  await step('error-rate', async () => {
    const w = await windowStats(env, m - OPS.errorRate.windowMin, m);
    const rate = w.requests ? w.errors5xx / w.requests : 0;
    if (w.requests >= OPS.errorRate.minRequests && rate > OPS.errorRate.max5xx) {
      out.push({ key: 'rule:5xx', severity: 'sev1', rollback: true, title: `API 5xx rate ${(rate * 100).toFixed(1)}% over ${OPS.errorRate.windowMin} min`, detail: `${w.errors5xx} of ${w.requests} API requests failed with 5xx in the last ${OPS.errorRate.windowMin} minutes.` });
    }
  });
  await step('latency', async () => {
    const w = await windowStats(env, m - OPS.latency.windowMin, m);
    if (w.requests >= OPS.latency.minRequests && w.p95ms > OPS.latency.p95Ms) {
      out.push({ key: 'rule:latency', severity: 'sev2', rollback: true, title: `API p95 above ${OPS.latency.p95Ms} ms`, detail: `p95 ≈ ${w.p95ms} ms over ${w.requests} requests in the last ${OPS.latency.windowMin} minutes.` });
    }
  });
  await step('health', async () => {
    const h = await healthChecks(env, now);
    const fails = h.ok ? 0 : Number((await env.FLAGS.get('wd:healthfails')) ?? 0) + 1;
    await env.FLAGS.put('wd:healthfails', String(fails), { expirationTtl: 3600 });
    if (fails >= OPS.health.failuresInARow) out.push({ key: 'rule:health', severity: 'sev1', rollback: true, title: `Health check failing: ${h.failed.join(', ')}`, detail: `${fails} failed health checks in a row (${h.failed.join(', ')}).` });
    for (const d of h.degraded) out.push({ key: `rule:third-party:${d}`, severity: 'sev2', title: `Third party unreachable: ${d}`, detail: 'Our release is fine; a provider is down. Payments fail closed (no access granted without verification) and free features keep working. No rollback.' });
  });
  await step('new-errors', async () => {
    // New = first seen within 60 min of this release; active = still being hit in the last 10 minutes.
    const rows = (await env.DB.prepare(`SELECT fingerprint, platform, route, feature, name, count, first_seen, sample_stack FROM error_events
      WHERE first_seen >= ? AND first_seen <= ? AND count >= ? AND last_seen >= ?`).bind(startedAt, startedAt + OPS.newError.withinMs, OPS.newError.minHits, now - OPS.newError.activeMs)
      .all<{ fingerprint: string; platform: string; route: string | null; feature: string | null; name: string; count: number; first_seen: number; sample_stack: string | null }>()).results;
    for (const r of rows) {
      const feature = (r.feature ?? featureOfRoute(r.route ?? '')) as FeatureId | undefined;
      out.push({ key: `fp:${r.fingerprint}`, severity: r.platform === 'api' ? 'sev1' : 'sev2', rollback: true, feature, title: `New ${r.platform} error after release: ${r.name}`,
        detail: `${r.count} hits since ${new Date(r.first_seen).toISOString()} on ${r.route || 'unknown route'}${feature ? ` (feature ${feature})` : ''}.\n\n\`\`\`\n${(r.sample_stack ?? '').slice(0, 1500)}\n\`\`\`` });
    }
  });
  await step('spikes', async () => {
    const hour = Math.floor(now / H);
    const rows = (await env.DB.prepare(`SELECT h.fingerprint, h.count, (SELECT COALESCE(SUM(count),0) FROM error_hourly b WHERE b.fingerprint = h.fingerprint AND b.hour BETWEEN ? AND ?) base,
      e.platform, e.route, e.feature, e.name FROM error_hourly h JOIN error_events e ON e.fingerprint = h.fingerprint WHERE h.hour = ? AND h.count >= ?`)
      .bind(hour - 24, hour - 1, hour, OPS.spike.minHits).all<{ fingerprint: string; count: number; base: number; platform: string; route: string | null; feature: string | null; name: string }>()).results;
    for (const r of rows) {
      if (r.count < Math.max(1, r.base / 24) * OPS.spike.factor) continue;
      if (out.some((s) => s.key === `fp:${r.fingerprint}`)) continue;
      const feature = (r.feature ?? featureOfRoute(r.route ?? '')) as FeatureId | undefined;
      out.push({ key: `fp:${r.fingerprint}`, severity: 'sev2', feature, title: `Error spike: ${r.name} (${r.platform})`, detail: `${r.count} hits this hour vs ≈${(r.base / 24).toFixed(1)}/hour over the last day.` });
    }
  });
  await step('payments', async () => {
    const r = await env.DB.prepare(`SELECT COUNT(*) n FROM orders o WHERE o.status = 'paid' AND o.paid_at < ?
      AND NOT EXISTS (SELECT 1 FROM entitlements e WHERE e.order_id = o.id)`).bind(now - OPS.payments.paidNotGrantedAfterMs).first<{ n: number }>();
    if ((r?.n ?? 0) > 0) out.push({ key: 'rule:payments', severity: 'sev1', needsFounder: true, title: `${r!.n} paid order(s) without access`, detail: 'Orders are marked paid for more than 10 minutes but no entitlement exists. Never auto-grant or auto-refund: a founder checks the provider dashboard (runbook payments.md).' });
  });
  await step('cron', async () => {
    if (env.ENVIRONMENT !== 'production') return;
    const first = Number((await env.FLAGS.get('wd:first')) ?? 0) || now;
    if (!(await env.FLAGS.get('wd:first'))) await env.FLAGS.put('wd:first', String(now));
    if (now - first < OPS.cron.staleMs) return;
    for (const job of OPS.cron.jobs) {
      const r = await env.DB.prepare('SELECT MAX(ran_at) t FROM job_runs WHERE job = ? AND ok = 1').bind(job).first<{ t: number | null }>();
      if (!r?.t || now - r.t > OPS.cron.staleMs) out.push({ key: `rule:cron:${job}`, severity: 'sev2', title: `Daily job ${job} has not succeeded for 26 h`, detail: `Last success: ${r?.t ? new Date(r.t).toISOString() : 'never'}. Jobs are idempotent; the next run retries (runbook cron-missed.md).` });
    }
  });
  await step('free-tier', async () => {
    const day = Math.floor(now / 86_400_000) * 1440;
    const w = await windowStats(env, day, m);
    const share = w.requests / OPS.freeTier.workersRequestsPerDay;
    if (share >= OPS.freeTier.warnAt) out.push({ key: 'rule:free-tier', severity: 'sev3', title: `Workers requests at ${Math.round(share * 100)}% of the free daily limit`, detail: `${w.requests} API requests today (UTC). Static pages are not counted (free). Runbook free-tier-limit.md.` });
  });
  await step('outbox', async () => {
    const r = await env.DB.prepare('SELECT COUNT(*) n FROM outbox WHERE attempts >= ?').bind(MAX_ATTEMPTS).first<{ n: number }>();
    if ((r?.n ?? 0) > 0) out.push({ key: 'rule:outbox', severity: 'sev2', title: `${r!.n} email(s) could not be delivered after ${MAX_ATTEMPTS} tries`, detail: 'Receipts or links may not have arrived. Users can still restore with their licence key. Check the email provider (runbook third-party-outage.md).' });
  });
  return out;
}

function issueBody(env: Env, s: Signal, actions: string[]) {
  return [
    `**What:** ${s.title}`, '', s.detail, '',
    `**Environment:** ${env.ENVIRONMENT} · **Release:** \`${env.RELEASE ?? 'local'}\``,
    `**Automatic actions:** ${actions.length ? actions.join('; ') : 'none needed'}`,
    s.needsFounder ? '\n**Needs a founder:** payments are involved; the agent may propose a fix but never grants, refunds or changes secrets.' : '',
    '', `Runbooks: \`docs/runbooks/\`. This issue was opened by the Worker watchdog; it closes itself when the signal is gone.`,
    `<!-- ${s.key.startsWith('fp:') ? s.key : `key:${s.key}`} -->`, `fp:${s.key.replace(/^fp:/, '')}`,
  ].join('\n');
}

export async function runWatchdog(env: Env, now = Date.now()): Promise<WatchdogReport> {
  const report: WatchdogReport = { at: now, release: env.RELEASE ?? 'local', signals: [], actions: [], closed: [], errors: [] };
  try {
    await flush(env); // count this isolate's buffered requests too
    const startedAt = await releaseStart(env, now);
    report.signals = await collectSignals(env, now, startedAt, report.errors);
    const lbl = label(env);
    const open = (await env.DB.prepare("SELECT * FROM incidents WHERE status = 'open'").all<IncidentRow>()).results;

    for (const s of report.signals) {
      try {
        const existing = open.find((i) => i.key === s.key);
        if (existing) {
          await env.DB.prepare('UPDATE incidents SET last_seen = ? WHERE key = ?').bind(now, s.key).run();
          continue;
        }
        const actions: string[] = [];
        if (s.feature && AUTO_DISABLE.includes(s.feature) && (await setKill(env, s.feature, s.title, s.key, now))) actions.push(`kill switch: ${s.feature} off`);
        const wantsRollback = s.rollback && s.severity === 'sev1' && now - startedAt <= OPS.rollbackWithinMs && env.ENVIRONMENT === 'production';
        if (wantsRollback) actions.push('rollback requested (label rollback-needed → auto-rollback.yml)');
        let issue: number | null = null;
        if (lbl) {
          // Created with its severity first; the trigger labels follow in one call, so `labeled` starts auto-debug.yml
          // (incident) and auto-rollback.yml (rollback-needed) exactly once.
          issue = await openIssue(env, `INC (${env.ENVIRONMENT}): ${s.title}`, issueBody(env, s, actions), [s.severity, ...(s.needsFounder ? ['needs-founder'] : [])]);
          if (issue) await addLabels(env, issue, [lbl, ...(wantsRollback ? ['rollback-needed'] : [])]);
        }
        await env.DB.prepare(`INSERT INTO incidents (key, severity, status, title, issue, release, opened_at, last_seen, actions) VALUES (?, ?, 'open', ?, ?, ?, ?, ?, ?)
          ON CONFLICT(key) DO UPDATE SET status = 'open', severity = excluded.severity, title = excluded.title, issue = excluded.issue, release = excluded.release,
          opened_at = excluded.opened_at, last_seen = excluded.last_seen, closed_at = NULL, actions = excluded.actions`)
          .bind(s.key, s.severity, s.title, issue, env.RELEASE ?? 'local', now, now, JSON.stringify(actions)).run();
        report.actions.push(...actions.map((a) => `${s.key}: ${a}`), `${s.key}: incident opened${issue ? ` #${issue}` : ''}`);
      } catch (e) { report.errors.push(`incident ${s.key}: ${e instanceof Error ? e.message : 'failed'}`); }
    }

    // Close incidents whose signal has been quiet long enough; turn their kill switch back on.
    const active = new Set(report.signals.map((s) => s.key));
    const kills = await getKills(env, now);
    for (const i of open) {
      if (active.has(i.key)) continue;
      try {
        let quiet = now - i.last_seen >= OPS.closeRuleAfterQuietMs;
        if (i.key.startsWith('fp:')) {
          const e = await env.DB.prepare('SELECT last_seen FROM error_events WHERE fingerprint = ?').bind(i.key.slice(3)).first<{ last_seen: number }>();
          quiet = !e || now - e.last_seen >= OPS.closeFingerprintAfterQuietMs || (env.ENVIRONMENT !== 'production' && now - e.last_seen >= OPS.closeRuleAfterQuietMs);
        }
        if (!quiet) continue;
        for (const [f, k] of Object.entries(kills)) if (k?.incident === i.key) { await clearKill(env, f as FeatureId, now); report.actions.push(`${i.key}: kill switch ${f} back on`); }
        await env.DB.prepare("UPDATE incidents SET status = 'closed', closed_at = ? WHERE key = ?").bind(now, i.key).run();
        if (i.issue) await closeIssue(env, i.issue, `Resolved automatically: no new signal since ${new Date(i.last_seen).toISOString()} (release \`${env.RELEASE ?? 'local'}\`).`);
        report.closed.push(i.key);
      } catch (e) { report.errors.push(`close ${i.key}: ${e instanceof Error ? e.message : 'failed'}`); }
    }
    // Remind once a day on long-running incidents.
    for (const i of open) {
      if (i.issue && active.has(i.key) && now - i.opened_at > 24 * H && Math.floor((now - i.opened_at) / (24 * H)) !== Math.floor((now - 5 * 60_000 - i.opened_at) / (24 * H))) {
        await commentIssue(env, i.issue, `Still active after ${Math.floor((now - i.opened_at) / H)} h (release \`${env.RELEASE ?? 'local'}\`).`).catch(() => undefined);
      }
    }
    const out = await processOutbox(env, now).catch((e) => { report.errors.push(`outbox: ${e instanceof Error ? e.message : 'failed'}`); return null; });
    if (out?.sent || out?.failed) report.actions.push(`outbox: ${out.sent} sent, ${out.failed} retried later`);
  } catch (e) {
    report.errors.push(`watchdog: ${e instanceof Error ? e.message : 'failed'}`);
  }
  try {
    await env.DB.prepare('INSERT OR REPLACE INTO job_runs (job, ran_at, ok, detail) VALUES (?, ?, ?, ?)')
      .bind('watchdog', now, report.errors.length ? 0 : 1, JSON.stringify({ signals: report.signals.map((s) => s.key), actions: report.actions, errors: report.errors }).slice(0, 4000))
      .run();
  } catch { /* D1 down: the error log line below and the uptime monitor still report it */ }
  if (report.errors.length) console.error(JSON.stringify({ level: 'error', msg: 'watchdog step failed', errors: report.errors }));
  return report;
}

