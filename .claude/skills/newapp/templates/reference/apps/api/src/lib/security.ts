// Security middleware: canonical host redirect, CORS allow-list, rate limits, Turnstile.
import type { Context, MiddlewareHandler } from 'hono';
import type { AppEnv, Env, RateLimit } from '../env.ts';
import { HttpError } from './errors.ts';

const MOBILE_ORIGINS = ['<slug>://app'];

export function allowedOrigins(env: Env): string[] {
  const list = [...MOBILE_ORIGINS];
  if (env.CANONICAL_HOST) list.push(`https://${env.CANONICAL_HOST}`);
  if (env.ENVIRONMENT !== 'production') list.push('http://localhost:3000', 'http://127.0.0.1:3000', 'http://localhost:8787');
  return list;
}

/** Production: 301 any other host (www, workers.dev) to the canonical domain. */
export const canonicalHost: MiddlewareHandler<AppEnv> = async (c, next) => {
  const host = c.env.CANONICAL_HOST;
  if (c.env.ENVIRONMENT === 'production' && host) {
    const url = new URL(c.req.url);
    if (url.hostname !== host) {
      url.hostname = host;
      url.protocol = 'https:';
      return c.redirect(url.toString(), 301);
    }
  }
  await next();
};

export const cors: MiddlewareHandler<AppEnv> = async (c, next) => {
  const origin = c.req.header('origin');
  const self = new URL(c.req.url).origin;
  const ok = !!origin && (origin === self || allowedOrigins(c.env).includes(origin));
  if (c.req.method === 'OPTIONS') {
    if (!ok) return c.body(null, 403);
    return c.body(null, 204, {
      'access-control-allow-origin': origin!,
      'access-control-allow-methods': 'GET, POST, OPTIONS',
      'access-control-allow-headers': 'content-type, authorization, x-device-id',
      'access-control-allow-credentials': 'true',
      'access-control-max-age': '600',
      vary: 'origin',
    });
  }
  await next();
  if (ok) {
    c.header('access-control-allow-origin', origin!);
    c.header('access-control-allow-credentials', 'true');
    c.header('vary', 'origin');
  }
};

/**
 * CSRF defence for state-changing requests (OWASP ASVS 4.2.2). Browsers always send Origin on cross-site POSTs:
 * a foreign Origin is refused. Bodies must be JSON, so an HTML form (which can't set application/json without a
 * CORS preflight) can never reach a handler. Server-to-server callers (webhooks, agency API, the Android app) send
 * no Origin and are authenticated by signature, API key or bearer token instead.
 */
export const mutationGuard: MiddlewareHandler<AppEnv> = async (c, next) => {
  const m = c.req.method;
  if (m === 'GET' || m === 'HEAD' || m === 'OPTIONS') return next();
  const origin = c.req.header('origin');
  if (origin && origin !== new URL(c.req.url).origin && !allowedOrigins(c.env).includes(origin)) {
    throw new HttpError(403, 'forbidden_origin', 'Requests from this site are not allowed.');
  }
  const hasBody = Number(c.req.header('content-length') ?? '0') > 0 || c.req.header('transfer-encoding') !== undefined;
  if (hasBody && !/^application\/json\s*(;|$)/i.test(c.req.header('content-type') ?? '')) {
    throw new HttpError(415, 'unsupported_media_type', 'Send the request body as application/json.');
  }
  await next();
};

export const clientIp = (c: Context) => c.req.header('cf-connecting-ip') ?? '0.0.0.0';

// Fallback when the Rate Limiting binding is absent (local tests): fixed window per isolate.
const windows = new Map<string, { n: number; reset: number }>();
function memoryLimit(key: string, limit: number, now = Date.now()) {
  const w = windows.get(key);
  if (!w || w.reset < now) { windows.set(key, { n: 1, reset: now + 60_000 }); return true; }
  w.n++;
  if (windows.size > 10_000) windows.clear();
  return w.n <= limit;
}

export function rateLimit(kind: 'strict' | 'api', route: string): MiddlewareHandler<AppEnv> {
  return async (c, next) => {
    const binding: RateLimit | undefined = kind === 'strict' ? c.env.RL_STRICT : c.env.RL_API;
    const key = `${route}:${clientIp(c)}`;
    const ok = binding ? (await binding.limit({ key })).success : memoryLimit(key, kind === 'strict' ? 5 : 120);
    if (!ok) throw new HttpError(429, 'rate_limited', 'Too many requests. Please wait a minute and try again.');
    await next();
  };
}

/** Cloudflare Turnstile. Dev/staging without a secret accept Cloudflare's test token; production fails closed. */
export async function verifyTurnstile(c: Context<AppEnv>, token: string) {
  const secret = c.env.TURNSTILE_SECRET;
  if (!secret) {
    if (c.env.ENVIRONMENT === 'production') throw new HttpError(503, 'turnstile_unconfigured', 'Verification is not available right now.');
    return;
  }
  const form = new FormData();
  form.append('secret', secret);
  form.append('response', token);
  form.append('remoteip', clientIp(c));
  const res = await fetch('https://challenges.cloudflare.com/turnstile/v0/siteverify', { method: 'POST', body: form });
  const out = (await res.json()) as { success: boolean };
  if (!out.success) throw new HttpError(403, 'turnstile_failed', 'Verification failed. Please try again.');
}
