// Per-minute request counters with a latency histogram (D1 `request_stats`). Each isolate buffers its counts and
// writes them in one batch when the minute changes or the buffer grows, so a busy minute costs a few D1 writes, not
// one per request (D1 free tier: 100,000 rows written/day).
import type { Env } from '../env.ts';

export const BOUNDS = [100, 250, 500, 1000, 1500, 3000]; // ms; bucket 6 = slower
type Row = { minute: number; route: string; count: number; e5: number; h: number[] };
const buffer = new Map<string, Row>();
let pending = 0;

export const minuteOf = (t: number) => Math.floor(t / 60_000);
const bucket = (ms: number) => { const i = BOUNDS.findIndex((b) => ms <= b); return i < 0 ? BOUNDS.length : i; };

/** Records one request; returns a flush promise when the buffer should be written now, else null. */
export function record(env: Env, route: string, status: number, ms: number, now = Date.now()): Promise<void> | null {
  const minute = minuteOf(now);
  const key = `${minute}|${route}`;
  const row = buffer.get(key) ?? { minute, route, count: 0, e5: 0, h: [0, 0, 0, 0, 0, 0, 0] };
  row.count++;
  if (status >= 500) row.e5++;
  row.h[bucket(ms)]!++;
  buffer.set(key, row);
  pending++;
  const stale = [...buffer.values()].some((r) => r.minute < minute);
  return stale || pending >= 50 ? flush(env) : null;
}

export async function flush(env: Env) {
  if (!buffer.size) return;
  const rows = [...buffer.values()];
  buffer.clear();
  pending = 0;
  const sql = `INSERT INTO request_stats (minute, route, count, errors5xx, h0, h1, h2, h3, h4, h5, h6) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(minute, route) DO UPDATE SET count = count + excluded.count, errors5xx = errors5xx + excluded.errors5xx,
    h0 = h0 + excluded.h0, h1 = h1 + excluded.h1, h2 = h2 + excluded.h2, h3 = h3 + excluded.h3, h4 = h4 + excluded.h4, h5 = h5 + excluded.h5, h6 = h6 + excluded.h6`;
  try {
    await env.DB.batch(rows.map((r) => env.DB.prepare(sql).bind(r.minute, r.route, r.count, r.e5, ...r.h)));
  } catch (e) {
    console.error(JSON.stringify({ level: 'error', msg: 'request_stats flush failed', error: e instanceof Error ? e.message : 'unknown' }));
  }
}

export interface Window { requests: number; errors5xx: number; p95ms: number }

/** Totals and an approximate p95 (upper bound of the bucket holding the 95th percentile) since `fromMinute`. */
export async function windowStats(env: Env, fromMinute: number, toMinute = Number.MAX_SAFE_INTEGER): Promise<Window> {
  const r = await env.DB.prepare(`SELECT COALESCE(SUM(count),0) c, COALESCE(SUM(errors5xx),0) e, COALESCE(SUM(h0),0) h0, COALESCE(SUM(h1),0) h1,
    COALESCE(SUM(h2),0) h2, COALESCE(SUM(h3),0) h3, COALESCE(SUM(h4),0) h4, COALESCE(SUM(h5),0) h5, COALESCE(SUM(h6),0) h6
    FROM request_stats WHERE minute >= ? AND minute <= ?`).bind(fromMinute, toMinute).first<Record<string, number>>();
  const c = r?.c ?? 0, h = [r?.h0, r?.h1, r?.h2, r?.h3, r?.h4, r?.h5, r?.h6].map((x) => x ?? 0);
  let acc = 0, p95ms = 0;
  if (c) for (let i = 0; i < h.length; i++) { acc += h[i]!; if (acc >= c * 0.95) { p95ms = BOUNDS[i] ?? 5000; break; } }
  return { requests: c, errors5xx: r?.e ?? 0, p95ms };
}

export const resetStatsBuffer = () => { buffer.clear(); pending = 0; };
