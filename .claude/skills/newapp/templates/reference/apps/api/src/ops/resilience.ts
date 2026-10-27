// Timeouts, retries and circuit breakers for calls to third parties (self-healing §2). Retries are for idempotent
// calls only. Breaker state lives in KV `breaker:<service>` so every isolate sees an open circuit.
import type { Env } from '../env.ts';
import { OPS } from './ops.config.ts';

export class TimeoutError extends Error { constructor(ms: number) { super(`timed out after ${ms} ms`); this.name = 'TimeoutError'; } }
export class CircuitOpenError extends Error { constructor(svc: string) { super(`${svc} circuit open`); this.name = 'CircuitOpenError'; } }

export function withTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
  return Promise.race([p, new Promise<T>((_, rej) => setTimeout(() => rej(new TimeoutError(ms)), ms))]);
}

/** Exponential backoff with jitter: 200 ms, 400 ms (+ up to 100 ms). Idempotent calls only. */
export async function retry<T>(fn: () => Promise<T>, tries = 3, baseMs = 200): Promise<T> {
  let last: unknown;
  for (let i = 0; i < tries; i++) {
    try { return await fn(); } catch (e) {
      last = e;
      if (i < tries - 1) await new Promise((r) => setTimeout(r, baseMs * 2 ** i + Math.random() * 100));
    }
  }
  throw last;
}

type Breaker = { fails: number[]; openUntil: number };

export async function breakerState(env: Env, svc: string): Promise<Breaker> {
  try { return (await env.FLAGS.get<Breaker>(`breaker:${svc}`, 'json')) ?? { fails: [], openUntil: 0 }; } catch { return { fails: [], openUntil: 0 }; }
}

/** Runs fn unless the circuit is open; 5 failures within a minute open it for 60 s, then one trial call is let through. */
export async function withBreaker<T>(env: Env, svc: string, fn: () => Promise<T>, now = Date.now()): Promise<T> {
  const b = await breakerState(env, svc);
  if (b.openUntil > now) throw new CircuitOpenError(svc);
  try {
    const out = await fn();
    if (b.fails.length || b.openUntil) await env.FLAGS.put(`breaker:${svc}`, JSON.stringify({ fails: [], openUntil: 0 })).catch(() => undefined);
    return out;
  } catch (e) {
    const fails = [...b.fails.filter((t) => now - t < OPS.breaker.windowMs), now];
    const openUntil = fails.length >= OPS.breaker.failures ? now + OPS.breaker.openMs : 0;
    await env.FLAGS.put(`breaker:${svc}`, JSON.stringify({ fails: openUntil ? [] : fails, openUntil }), { expirationTtl: 3600 }).catch(() => undefined);
    throw e;
  }
}
