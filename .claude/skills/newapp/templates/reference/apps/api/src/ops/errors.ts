// Error capture for API, web and Android (docs/standards/self-healing.md §1). Reports are scrubbed of personal data
// before anything is stored; only a fingerprint, counts and one truncated sample stack are kept.
import { z } from 'zod';
import type { Env } from '../env.ts';
import { IGNORE_ERRORS } from './ops.config.ts';

/** Removes emails, phone and card-like numbers, licence keys, tokens and query strings. */
export function scrub(s: string): string {
  return s
    .replace(/[\w.+-]+@[\w-]+\.[\w.-]+/g, '[email]')
    .replace(/\b[A-Z0-9]{5}(?:-[A-Z0-9]{5}){4}\b/g, '[licence]')
    .replace(/\b(?:\d[ -]?){13,19}\b/g, '[number]')
    .replace(/\+?\d[\d\s().-]{8,}\d/g, '[phone]')
    .replace(/\bBearer\s+\S+/gi, 'Bearer [token]')
    .replace(/\b(?=[A-Za-z0-9_-]*\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{24,}\b/g, '[token]')
    .replace(/\?[^\s)'"]*/g, '?[query]');
}

/** First stack frame that belongs to <Product> (skips the error line and vendor frames). */
export function firstAppFrame(stack: string): string {
  const frames = stack.split('\n').slice(1).map((l) => l.trim()).filter(Boolean);
  const app = frames.find((f) => !/node_modules|react-dom|chunks\/framework|webpack|turbopack|<anonymous>/.test(f)) ?? frames[0] ?? '';
  return app.replace(/:\d+:\d+\)?$/, '').replace(/^at\s+/, '').slice(0, 200);
}

export async function fingerprintOf(platform: string, name: string, stack: string, route: string): Promise<string> {
  const data = new TextEncoder().encode(`${platform}|${name}|${firstAppFrame(stack)}|${route}`);
  const h = new Uint8Array(await crypto.subtle.digest('SHA-256', data));
  return [...h.slice(0, 12)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export const ErrorReport = z
  .object({
    platform: z.enum(['web', 'android']),
    release: z.string().max(64),
    name: z.string().max(120),
    message: z.string().max(1000).default(''),
    stack: z.string().max(6000).default(''),
    route: z.string().max(200).default(''),
    feature: z.string().max(64).optional(),
    rid: z.string().max(64).optional(),
    app_version: z.string().max(32).optional(),
    os: z.string().max(64).optional(),
  })
  .strict();
export type ErrorReport = z.infer<typeof ErrorReport>;

export interface ErrorRecord { platform: 'api' | 'web' | 'android'; release: string; name: string; message: string; stack: string; route: string; feature?: string; rid?: string }

/** Upserts the fingerprint and its hourly counter. Returns the fingerprint, or null when ignored. */
export async function recordError(env: Env, e: ErrorRecord, now = Date.now()): Promise<string | null> {
  if (IGNORE_ERRORS.some((r) => r.test(e.name) || r.test(e.message))) return null;
  const route = scrub(e.route).slice(0, 200);
  const fp = await fingerprintOf(e.platform, e.name, e.stack, route);
  const sample = scrub(`${e.name}: ${e.message}\n${e.stack}`).slice(0, 2000);
  const hour = Math.floor(now / 3_600_000);
  await env.DB.batch([
    env.DB.prepare(`INSERT INTO error_events (fingerprint, platform, release, feature, route, name, first_seen, last_seen, count, sample_rid, sample_stack)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
      ON CONFLICT(fingerprint) DO UPDATE SET last_seen = excluded.last_seen, count = count + 1, release = excluded.release`)
      .bind(fp, e.platform, e.release, e.feature ?? null, route, e.name.slice(0, 120), now, now, e.rid ?? null, sample),
    env.DB.prepare('INSERT INTO error_hourly (fingerprint, hour, count) VALUES (?, ?, 1) ON CONFLICT(fingerprint, hour) DO UPDATE SET count = count + 1')
      .bind(fp, hour),
  ]);
  return fp;
}
