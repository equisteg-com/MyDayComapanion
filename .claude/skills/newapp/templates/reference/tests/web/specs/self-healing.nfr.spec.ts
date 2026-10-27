// Game day (phase 9b §6, docs/standards/self-healing.md): staging only, needs OPS_TOKEN. Injects a fault, lets the
// watchdog detect it and switch the feature off, checks the UI fallback, removes the fault and proves auto-recovery.
// Results: docs/test-reports/. Never runs against production (the ops endpoints refuse it there).
import { deployed, expect, test } from '../fixtures';

const TOKEN = process.env.OPS_TOKEN ?? '';
const auth = { authorization: `Bearer ${TOKEN}`, 'content-type': 'application/json' };

test.describe('self-healing game day @nfr', () => {
  test.skip(() => !(deployed() || process.env.GAME_DAY_LOCAL === '1') || !TOKEN, 'staging (or a local Worker with GAME_DAY_LOCAL=1) + OPS_TOKEN only');
  test.describe.configure({ mode: 'serial' });
  test.setTimeout(240_000);

  test('TC-N-REL-101 fault → incident → kill switch → UI fallback → recovery and auto-close', async ({ request, browser }, info) => {
    test.skip(info.project.name !== 'chromium', 'once per run');
    const killed = async () => ((await (await request.get(`/api/v1/config?t=${Date.now()}`)).json()) as { killed: string[] }).killed;

    // 1. inject: every outdated-rule report now throws inside the Worker
    expect((await request.post('/api/v1/ops/fault', { headers: auth, data: { name: 'report', mode: 'throw' } })).status()).toBe(200);
    for (let i = 0; i < 22; i++) {
      const r = await request.post('/api/v1/requirements/report', { data: { requirement_id: 'gb-passport', message: 'game day', turnstile: 'x' } });
      expect(r.status()).toBe(500);
    }

    // 2. detect + contain: the watchdog sees the error spike in a kill-switchable feature
    const run = await (await request.post('/api/v1/ops/run-watchdog', { headers: auth })).json() as { signals: { key: string; feature?: string }[]; actions: string[] };
    expect(run.signals.some((s) => s.feature === 'rule_reports'), JSON.stringify(run.signals)).toBe(true);
    expect(run.actions.join('\n')).toContain('kill switch: rule_reports off');
    await expect.poll(killed, { timeout: 90_000, intervals: [5_000] }).toContain('rule_reports');

    // 3. UI fallback: the Contact page shows the email alternative instead of the form
    const page = await (await browser.newContext()).newPage();
    await page.goto('/contact/');
    await expect(page.getByText('To report an outdated rule, email support')).toBeVisible({ timeout: 30_000 });

    // 4. remove the fault; a later watchdog run (simulated +65 min) closes the incident and lifts the kill switch
    expect((await request.post('/api/v1/ops/fault', { headers: auth, data: { name: 'report', mode: null } })).status()).toBe(200);
    const later = await (await request.post(`/api/v1/ops/run-watchdog?at=${Date.now() + 65 * 60_000}`, { headers: auth })).json() as { closed: string[]; actions: string[] };
    expect(later.closed.length, JSON.stringify(later)).toBeGreaterThan(0);
    expect(later.actions.join('\n')).toContain('kill switch rule_reports back on');
    await expect.poll(killed, { timeout: 90_000, intervals: [5_000] }).not.toContain('rule_reports');
  });

  test('TC-N-REL-102 ops endpoints refuse requests without the token', async ({ request }) => {
    for (const [method, path] of [['get', '/api/v1/ops/summary'], ['post', '/api/v1/ops/run-watchdog'], ['post', '/api/v1/ops/fault']] as const) {
      expect((await request[method](path, { data: method === 'post' ? {} : undefined })).status(), path).toBe(401);
    }
  });
});
