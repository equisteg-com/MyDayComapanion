// Fault injection for the staging game day (self-healing §6). KV `fault:<name>` = '500' | 'slow' | 'throw' makes the
// named API route fail. Hard-disabled in production: the check returns before reading KV.
import type { MiddlewareHandler } from 'hono';
import type { AppEnv } from '../env.ts';

export const FAULT_NAMES = ['health', 'release', 'config', 'report', 'requirements', 'events', 'access'] as const;
let cache: { at: number; faults: Record<string, string> } | null = null;

// 'report' targets only the outdated-rule report, so a game day doesn't break requirement data for other tests.
export const faultName = (path: string) => (path === '/api/v1/requirements/report' ? 'report'
  : FAULT_NAMES.find((n) => path === `/api/v1/${n}` || path.startsWith(`/api/v1/${n}/`)) ?? null);

export const faults: MiddlewareHandler<AppEnv> = async (c, next) => {
  if (c.env.ENVIRONMENT === 'production') return next();
  const name = faultName(c.req.path);
  if (!name) return next();
  const now = Date.now();
  if (!cache || now - cache.at > 5000) {
    const faults: Record<string, string> = {};
    await Promise.all(FAULT_NAMES.map(async (n) => { const v = await c.env.FLAGS.get(`fault:${n}`).catch(() => null); if (v) faults[n] = v; }));
    cache = { at: now, faults };
  }
  const f = cache.faults[name];
  if (f === '500') return c.json({ error: { code: 'injected_fault', message: 'Injected fault (staging game day).' } }, 500);
  if (f === 'slow') await new Promise((r) => setTimeout(r, 2000));
  if (f === 'throw') throw new Error(`Injected fault in ${name}`);
  return next();
};

export const resetFaultCache = () => { cache = null; };
