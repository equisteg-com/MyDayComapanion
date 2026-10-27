// Phase 9b on Android: crash/error reports (queued offline, sent when online) and runtime config (minimum version,
// message). No photo or personal data: error name, message, stack and the screen path only; the server scrubs again.
import NetInfo from '@react-native-community/netinfo';
import Constants from 'expo-constants';
import { useEffect } from 'react';
import { AppState, Platform } from 'react-native';
import { create } from 'zustand';
import { API_URL } from './api';
import { loadPref, savePref } from './storage';

const APP_VERSION = Constants.expoConfig?.version ?? '0.0.0';
const RELEASE = `android-${APP_VERSION}`;
const QUEUE = 'error-queue';
type Report = { platform: 'android'; release: string; name: string; message: string; stack: string; route: string; app_version: string; os: string };

const post = (path: string, body: unknown) =>
  fetch(`${API_URL}/api/v1/${path}`, { method: 'POST', headers: { 'content-type': 'application/json', origin: '<slug>://app' }, body: JSON.stringify(body) });

export function reportError(e: unknown, route = '') {
  try {
    const err = e instanceof Error ? e : new Error(String(e));
    const queue = loadPref<Report[]>(QUEUE, []).slice(-19); // keep at most 20 until sent
    queue.push({ platform: 'android', release: RELEASE, name: err.name.slice(0, 120), message: err.message.slice(0, 1000), stack: (err.stack ?? '').slice(0, 6000), route, app_version: APP_VERSION, os: `${Platform.OS} ${Platform.Version}` });
    savePref(QUEUE, queue);
    void flushErrors();
  } catch { /* never throw from the reporter */ }
}

let flushing = false;
export async function flushErrors() {
  if (flushing) return;
  flushing = true;
  try {
    const queue = loadPref<Report[]>(QUEUE, []);
    while (queue.length) {
      const r = await post('errors', queue[0]).catch(() => null);
      if (!r || (r.status >= 500 && r.status !== 503) || r.status === 429) break; // offline or busy: try later
      queue.shift(); // sent, or rejected for good (4xx)
      savePref(QUEUE, queue);
    }
  } finally { flushing = false; }
}

/** Catches uncaught JS errors app-wide; chains to the default handler so fatal crashes still behave normally. */
export function installGlobalHandler() {
  const g = globalThis as unknown as { ErrorUtils?: { getGlobalHandler(): (e: unknown, fatal?: boolean) => void; setGlobalHandler(h: (e: unknown, fatal?: boolean) => void): void } };
  const eu = g.ErrorUtils;
  if (!eu) return;
  const prev = eu.getGlobalHandler();
  eu.setGlobalHandler((e, fatal) => { reportError(e, fatal ? 'fatal' : 'global'); prev(e, fatal); });
}

const newer = (a: string, b: string) => {
  const pa = a.split('.').map(Number), pb = b.split('.').map(Number);
  for (let i = 0; i < 3; i++) { if ((pa[i] ?? 0) !== (pb[i] ?? 0)) return (pa[i] ?? 0) > (pb[i] ?? 0); }
  return false;
};

export const useRemoteConfig = create<{ updateNeeded: boolean; message: string | null }>(() => ({ updateNeeded: false, message: null }));

async function loadConfig() {
  try {
    const r = await fetch(`${API_URL}/api/v1/config`, { headers: { accept: 'application/json' } });
    if (!r.ok) return;
    const c = (await r.json()) as { minAndroidVersion?: string | null; message?: string | null };
    useRemoteConfig.setState({ updateNeeded: !!c.minAndroidVersion && newer(c.minAndroidVersion, APP_VERSION), message: c.message ?? null });
  } catch { /* offline: keep the last state */ }
}

/** Root hook: config on start and on resume, error queue flushed when the network returns. */
export function useOps() {
  useEffect(() => {
    installGlobalHandler();
    void loadConfig();
    void flushErrors();
    const app = AppState.addEventListener('change', (s) => { if (s === 'active') { void loadConfig(); void flushErrors(); } });
    const net = NetInfo.addEventListener((s) => { if (s.isConnected) void flushErrors(); });
    return () => { app.remove(); net(); };
  }, []);
}
