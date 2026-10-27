// Feature kill switches set by the watchdog (self-healing §2). One KV key holds all of them (one read per 30 s per
// isolate instead of one per feature). Only features in AUTO_DISABLE can be killed; the release switches stay the
// source of truth for everything else, and payments/entitlement code is never switched off this way.
import type { FeatureId } from '@<slug>/shared-types';
import type { Env } from '../env.ts';
import { AUTO_DISABLE } from './ops.config.ts';

export const KILL_KEY = 'killswitches';
export type Kills = Partial<Record<FeatureId, { at: number; reason: string; incident?: string }>>;
let cache: { at: number; kills: Kills } | null = null;

export async function getKills(env: Env, now = Date.now()): Promise<Kills> {
  if (cache && now - cache.at < 30_000) return cache.kills;
  let kills: Kills = {};
  try { kills = (await env.FLAGS.get<Kills>(KILL_KEY, 'json')) ?? {}; } catch { /* KV down: nothing killed */ }
  for (const k of Object.keys(kills) as FeatureId[]) if (!AUTO_DISABLE.includes(k)) delete kills[k];
  cache = { at: now, kills };
  return kills;
}

export async function setKill(env: Env, feature: FeatureId, reason: string, incident?: string, now = Date.now()) {
  if (!AUTO_DISABLE.includes(feature)) return false;
  const kills = { ...(await getKills(env, now)), [feature]: { at: now, reason, incident } };
  await env.FLAGS.put(KILL_KEY, JSON.stringify(kills));
  cache = { at: now, kills };
  return true;
}

export async function clearKill(env: Env, feature: FeatureId, now = Date.now()) {
  const kills = { ...(await getKills(env, now)) };
  if (!kills[feature]) return false;
  delete kills[feature];
  await env.FLAGS.put(KILL_KEY, JSON.stringify(kills));
  cache = { at: now, kills };
  return true;
}

export const resetKillCache = () => { cache = null; };
