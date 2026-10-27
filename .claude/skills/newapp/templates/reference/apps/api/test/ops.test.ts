// Phase 9b self-healing: error capture, ops endpoints, faults, watchdog rules, kill switches, outbox.
import assert from 'node:assert/strict';
import { afterEach, beforeEach, test } from 'node:test';
import { app } from '../src/index.ts';
import { resetFlagCache } from '../src/lib/flags.ts';
import { resetReleaseCache } from '../src/lib/release.ts';
import { fingerprintOf, firstAppFrame, recordError, scrub } from '../src/ops/errors.ts';
import { resetFaultCache } from '../src/ops/faults.ts';
import { resetHealthCache } from '../src/ops/health.ts';
import { getKills, resetKillCache, setKill } from '../src/ops/killswitch.ts';
import { processOutbox, sendEmailReliably } from '../src/ops/outbox.ts';
import { flush, minuteOf, record, resetStatsBuffer, windowStats } from '../src/ops/stats.ts';
import { runWatchdog } from '../src/ops/watchdog.ts';
import { makeD1, makeKV } from './helpers.ts';

const OPS_TOKEN = 'ops-test-token-0123456789abcdef';
const env = (over: Record<string, unknown> = {}) => ({ DB: makeD1().db, FLAGS: makeKV().kv, ENVIRONMENT: 'staging', RELEASE: 'rel1', OPS_TOKEN, ...over }) as never as Parameters<typeof runWatchdog>[0];
const call = (e: unknown, path: string, init: RequestInit = {}) => app.request(`https://product-staging.example.workers.dev${path}`, init, e as never);
const ip = () => '10.1.0.' + Math.floor(Math.random() * 250);
const report = (body: object) => ({ method: 'POST', body: JSON.stringify(body), headers: { 'content-type': 'application/json', 'cf-connecting-ip': ip() } });
const original = globalThis.fetch;

beforeEach(() => { resetFlagCache(); resetReleaseCache(); resetKillCache(); resetFaultCache(); resetHealthCache(); resetStatsBuffer(); });
afterEach(() => { globalThis.fetch = original; });

test('fingerprints are stable per platform, error and app frame, and differ by route', async () => {
  const stack = 'TypeError: x is undefined\n    at render (webpack://app/node_modules/react-dom/x.js:1:1)\n    at StepExport (https://<domain>/_next/static/chunks/abc.js:10:20)';
  assert.equal(firstAppFrame(stack), 'StepExport (https://<domain>/_next/static/chunks/abc.js');
  const a = await fingerprintOf('web', 'TypeError', stack, '/create/');
  assert.equal(a, await fingerprintOf('web', 'TypeError', stack, '/create/'));
  assert.notEqual(a, await fingerprintOf('web', 'TypeError', stack, '/pricing/'));
  assert.notEqual(a, await fingerprintOf('android', 'TypeError', stack, '/create/'));
});

test('scrubber removes emails, licence keys, card and phone numbers, tokens and query strings', () => {
  const s = scrub('user a.b+c@mail.example.com key ABCDE-12345-FGHIJ-67890-KLMNO card 4111 1111 1111 1111 call +91 98765 43210 Bearer eyJabc.def tok a1b2c3d4e5f6a7b8c9d0e1f2a3 at /restore/?t=secret');
  for (const leak of ['a.b+c@mail', 'ABCDE-12345', '4111 1111', '98765', 'eyJabc', 'a1b2c3d4e5f6a7b8c9d0e1f2a3', 't=secret']) assert.ok(!s.includes(leak), `${leak} leaked: ${s}`);
});

test('POST /errors: 202, scrubbed before storage; > 8 KB → 413; unknown fields → 400; over the rate limit → 429', async () => {
  const e = env();
  const ok = await call(e, '/api/v1/errors', report({ platform: 'web', release: 'rel1', name: 'TypeError', message: 'failed for x@y.com', stack: 'TypeError\n at f (a.js:1:1)', route: '/create/' }));
  assert.equal(ok.status, 202);
  const row = await (e as { DB: D1Database }).DB.prepare('SELECT sample_stack FROM error_events').first<{ sample_stack: string }>();
  assert.ok(row && !row.sample_stack.includes('x@y.com'));
  assert.equal((await call(e, '/api/v1/errors', report({ platform: 'web', release: 'r', name: 'E', stack: 'x'.repeat(9000) }))).status, 413);
  assert.equal((await call(e, '/api/v1/errors', report({ platform: 'web', release: 'r', name: 'E', photo: 'data:image/png;base64,AAAA' }))).status, 400);
  const same = { method: 'POST', body: JSON.stringify({ platform: 'web', release: 'r', name: 'E' }), headers: { 'content-type': 'application/json', 'cf-connecting-ip': '10.9.9.9' } };
  const codes: number[] = [];
  for (let i = 0; i < 7; i++) codes.push((await call(e, '/api/v1/errors', same)).status);
  assert.ok(codes.includes(429), codes.join(','));
});

test('ops endpoints need the OPS token; run-watchdog is refused in production; config exposes no secrets', async () => {
  const e = env({ RESEND_API_KEY: 're_secret_value', SESSION_SIGNING_KEY: 'signing-secret' });
  assert.equal((await call(e, '/api/v1/ops/summary')).status, 401);
  assert.equal((await call(e, '/api/v1/ops/summary', { headers: { authorization: 'Bearer wrong' } })).status, 401);
  const s = await call(e, '/api/v1/ops/summary', { headers: { authorization: `Bearer ${OPS_TOKEN}` } });
  assert.equal(s.status, 200);
  const body = await s.json() as Record<string, unknown>;
  for (const k of ['release', 'windows', 'newFingerprints', 'health', 'incidents', 'killSwitches', 'breakers', 'jobs', 'usage']) assert.ok(k in body, k);
  const prod = env({ ENVIRONMENT: 'production' });
  const r = await app.request('https://<domain>/api/v1/ops/run-watchdog', { method: 'POST', headers: { authorization: `Bearer ${OPS_TOKEN}` } }, prod as never);
  assert.equal(r.status, 403);
  const cfg = await (await call(e, '/api/v1/config')).text();
  assert.ok(!cfg.includes('re_secret_value') && !cfg.includes('signing-secret'));
  assert.match(cfg, /"enabled":\[/);
});

test('faults: staging honours fault:health=500; production ignores the same flag', async () => {
  const e = env();
  await (e as { FLAGS: KVNamespace }).FLAGS.put('fault:health', '500');
  assert.equal((await call(e, '/api/v1/health')).status, 500);
  resetFaultCache();
  const prod = env({ ENVIRONMENT: 'production' });
  await (prod as { FLAGS: KVNamespace }).FLAGS.put('fault:health', '500');
  const r = await app.request('https://<domain>/api/v1/health', {}, prod as never);
  assert.equal(r.status, 200);
});

test('stats: buffered counts flush in batches and give an approximate p95', async () => {
  const e = env();
  const now = Date.UTC(2026, 9, 3, 12, 0, 30);
  for (let i = 0; i < 40; i++) record(e, '/api/v1/release', i < 2 ? 500 : 200, i < 38 ? 80 : 1200, now);
  await flush(e);
  const w = await windowStats(e, minuteOf(now) - 1, minuteOf(now));
  assert.equal(w.requests, 40);
  assert.equal(w.errors5xx, 2);
  assert.equal(w.p95ms, 100);
});

test('watchdog: 5xx breach opens one incident; in production within an hour of a deploy it requests a rollback', async () => {
  const e = env({ ENVIRONMENT: 'production', RELEASE: 'relA' });
  const now = Date.now();
  for (let i = 0; i < 30; i++) record(e, '/api/v1/release', i < 6 ? 500 : 200, 50, now);
  const r = await runWatchdog(e, now);
  assert.ok(r.signals.some((s) => s.key === 'rule:5xx'), JSON.stringify(r.signals));
  assert.ok(r.actions.some((a) => a.includes('rollback requested')), r.actions.join('\n'));
  const again = await runWatchdog(e, now + 60_000);
  assert.ok(!again.actions.some((a) => a.includes('incident opened')), 'no duplicate incident');
  const open = await (e as { DB: D1Database }).DB.prepare("SELECT COUNT(*) n FROM incidents WHERE status = 'open'").first<{ n: number }>();
  assert.equal(open?.n, 1);
});

test('watchdog: a new error in a kill-switchable feature turns it off, and back on once quiet', async () => {
  const e = env();
  const t0 = Date.now();
  await runWatchdog(e, t0); // records the release start
  for (let i = 0; i < 6; i++) await recordError(e, { platform: 'api', release: 'rel1', name: 'TypeError', message: 'boom', stack: 'TypeError\n at count (events.ts:1:1)', route: '/api/v1/events' }, t0 + 1000 + i);
  const r = await runWatchdog(e, t0 + 5 * 60_000);
  assert.ok(r.actions.some((a) => a.includes('kill switch: analytics_events off')), r.actions.join('\n'));
  resetReleaseCache();
  const rel = await (await call(e, '/api/v1/release')).json() as { enabled: string[] };
  assert.ok(!rel.enabled.includes('analytics_events'));
  const later = await runWatchdog(e, t0 + 5 * 60_000 + 31 * 60_000); // staging: quiet for 30 min closes it
  assert.ok(later.closed.length === 1 && later.actions.some((a) => a.includes('back on')), JSON.stringify(later));
  resetKillCache();
  assert.deepEqual(Object.keys(await getKills(e)), []);
});

test('kill switches can never turn off payments, restore or Create', async () => {
  const e = env();
  for (const f of ['checkout', 'restore', 'pro_pass', 'create', 'digital_export'] as const) assert.equal(await setKill(e, f, 'test'), false, f);
});

test('watchdog: paid order without access → sev1 incident for a founder, no kill switch and no rollback', async () => {
  const e = env({ ENVIRONMENT: 'production' });
  const now = Date.now();
  await (e as { DB: D1Database }).DB.prepare(`INSERT INTO orders (id, sku, device_id, provider, provider_order_id, status, amount_minor, currency, created_at, paid_at)
    VALUES ('o1', 'pack_5', 'd', 'razorpay', 'order_x', 'paid', 14900, 'INR', ?, ?)`).bind(now - 30 * 60_000, now - 20 * 60_000).run();
  const r = await runWatchdog(e, now);
  const s = r.signals.find((x) => x.key === 'rule:payments');
  assert.ok(s && s.severity === 'sev1' && s.needsFounder && !s.rollback && !s.feature);
});

test('watchdog never throws, even when D1 is broken', async () => {
  const broken = { ...(env() as object), DB: { prepare: () => { throw new Error('d1 down'); }, batch: async () => { throw new Error('d1 down'); } } };
  const r = await runWatchdog(broken as never, Date.now());
  assert.ok(r.errors.length > 0);
});

test('outbox: a failed email is queued encrypted and sent by the next run', async () => {
  const e = env({ RESEND_API_KEY: 're_x', EMAIL_ENC_KEY: Buffer.alloc(32, 3).toString('base64url') });
  globalThis.fetch = (async () => new Response('down', { status: 503 })) as typeof fetch;
  assert.equal(await sendEmailReliably(e, 'buyer@example.com', 'Receipt', 'Your licence'), 'queued');
  const row = await (e as { DB: D1Database }).DB.prepare('SELECT payload FROM outbox').first<{ payload: string }>();
  assert.ok(row && !row.payload.includes('buyer@example.com'));
  globalThis.fetch = (async () => Response.json({ id: 'ok' })) as typeof fetch;
  await (e as { FLAGS: KVNamespace }).FLAGS.delete('breaker:email');
  const out = await processOutbox(e, Date.now() + 6 * 60_000);
  assert.equal(out.sent, 1);
});

test('game-day endpoints: fault on/off works on staging and both endpoints are refused in production', async () => {
  const e = env();
  const auth = { authorization: `Bearer ${OPS_TOKEN}`, 'content-type': 'application/json' };
  assert.equal((await call(e, '/api/v1/ops/fault', { method: 'POST', headers: auth, body: JSON.stringify({ name: 'health', mode: '500' }) })).status, 200);
  assert.equal((await call(e, '/api/v1/health')).status, 500);
  assert.equal((await call(e, '/api/v1/ops/fault', { method: 'POST', headers: auth, body: JSON.stringify({ name: 'health', mode: null }) })).status, 200);
  assert.equal((await call(e, '/api/v1/health')).status, 200);
  assert.equal((await call(e, '/api/v1/ops/fault', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ name: 'health', mode: '500' }) })).status, 401);
  const prod = env({ ENVIRONMENT: 'production' });
  const r = await app.request('https://<domain>/api/v1/ops/fault', { method: 'POST', headers: auth, body: JSON.stringify({ name: 'health', mode: '500' }) }, prod as never);
  assert.equal(r.status, 403);
});
