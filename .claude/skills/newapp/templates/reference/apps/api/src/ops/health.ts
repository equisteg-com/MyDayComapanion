// Health checks shared by GET /api/v1/health and the watchdog. Hard checks (D1, KV) decide `ok`; third parties
// (payment provider) only report `degraded`: a Razorpay outage must open a third-party incident, never trigger a
// rollback of our own release (self-healing §2).
import type { Env } from '../env.ts';

export interface Health { ok: boolean; checks: Record<string, boolean>; failed: string[]; degraded: string[] }

const timed = (p: Promise<unknown>, ms = 2000) => Promise.race([p, new Promise((_, r) => setTimeout(() => r(new Error('timeout')), ms))]);
let payCache: { at: number; ok: boolean } | null = null;

async function paymentReachable(env: Env, now: number) {
  if (!env.PAYMENT_KEY_ID) return true; // not configured here: nothing to check
  if (payCache && now - payCache.at < 60_000) return payCache.ok;
  let ok: boolean;
  try { const r = (await timed(fetch('https://api.razorpay.com/v1/', { method: 'GET' }))) as Response; ok = r.status < 500; } catch { ok = false; }
  payCache = { at: now, ok };
  return ok;
}

export async function healthChecks(env: Env, now = Date.now()): Promise<Health> {
  const checks: Record<string, boolean> = {};
  const run = async (name: string, fn: () => Promise<unknown>) => { try { await timed(fn()); checks[name] = true; } catch { checks[name] = false; } };
  await Promise.all([run('kv', () => env.FLAGS.get('flags')), run('d1', () => env.DB.prepare('SELECT 1').first())]);
  const failed = Object.entries(checks).filter(([, v]) => !v).map(([k]) => k);
  const degraded = (await paymentReachable(env, now)) ? [] : ['payment_provider'];
  return { ok: failed.length === 0, checks, failed, degraded };
}

export const resetHealthCache = () => { payCache = null; };
