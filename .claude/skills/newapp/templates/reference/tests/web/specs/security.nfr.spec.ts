// Security and firewall checks against a deployed environment (BASE_URL = staging in staging-tests.yml).
// Request-level only, so they run once (chromium project). Test cases: tests/cases/test-cases.xlsx → Non-functional.
import { expect, test } from '@playwright/test';

test.beforeEach(({}, info) => test.skip(info.project.name !== 'chromium', 'request-level checks run once'));

const PROBES = ['/.env', '/.env.production', '/.git/config', '/wp-admin/', '/wp-login.php', '/phpmyadmin/', '/xmlrpc.php'];
// Patterns of real secrets; public ids (Razorpay key_id, Turnstile site key, AdSense pub id) are allowed by design.
const SECRET = /-----BEGIN [A-Z ]*PRIVATE KEY-----|"private_key"\s*:|sk_live_[0-9a-zA-Z]{10,}|AKIA[0-9A-Z]{16}|ghp_[0-9A-Za-z]{30,}|github_pat_[0-9A-Za-z_]{30,}|\bre_[0-9A-Za-z]{8}_[0-9A-Za-z]{16,}|whsec_[0-9A-Za-z]{20,}|SESSION_SIGNING_KEY\s*[:=]\s*["'][^"']{8,}/;

test('TC-N-SEC-002 security headers on pages and API @nfr', async ({ request }) => {
  const page = await request.get('/');
  const h = page.headers();
  expect(h['content-security-policy']).toContain("frame-ancestors 'none'");
  expect(h['content-security-policy']).toContain("object-src 'none'");
  expect(h['strict-transport-security']).toContain('max-age=31536000');
  expect(h['x-content-type-options']).toBe('nosniff');
  expect(h['referrer-policy']).toBe('strict-origin-when-cross-origin');
  expect(h['permissions-policy']).toContain('microphone=()');
  // Per-page CSP pins every inline script by hash (apps/web/scripts/csp-hashes.mjs).
  expect(await page.text()).toMatch(/<meta http-equiv="Content-Security-Policy" content="script-src [^"]*'sha256-/);
  const api = await request.get('/api/v1/health');
  expect(api.headers()['content-security-policy']).toContain("default-src 'none'");
  expect(api.headers()['x-content-type-options']).toBe('nosniff');
});

test('TC-N-SEC-003 probe paths are blocked or absent @nfr', async ({ request }) => {
  for (const p of PROBES) {
    const r = await request.get(p, { maxRedirects: 0 });
    expect([403, 404], `${p} → ${r.status()}`).toContain(r.status());
    expect(await r.text()).not.toMatch(SECRET);
  }
});

test('TC-N-SEC-004 strict rate limit answers 429 @nfr', async ({ request }) => {
  const statuses: number[] = [];
  for (let i = 0; i < 15; i++) {
    statuses.push((await request.post('/api/v1/data/delete-request', { data: {}, headers: { 'content-type': 'application/json' } })).status());
  }
  expect(statuses, statuses.join(',')).toContain(429);
});

test('TC-N-SEC-005 CORS refuses unknown origins @nfr', async ({ request }) => {
  const pre = await request.fetch('/api/v1/health', { method: 'OPTIONS', headers: { origin: 'https://evil.example', 'access-control-request-method': 'POST' } });
  expect(pre.status()).toBe(403);
  const get = await request.get('/api/v1/health', { headers: { origin: 'https://evil.example' } });
  expect(get.headers()['access-control-allow-origin']).toBeUndefined();
});

test('TC-N-SEC-006 payment webhook with a bad signature is rejected @nfr', async ({ request }) => {
  const r = await request.post('/api/v1/webhooks/razorpay', {
    data: { event: 'payment.captured', payload: {} },
    headers: { 'content-type': 'application/json', 'x-razorpay-signature': 'bad' },
  });
  expect(r.status()).toBe(401);
});

test('TC-N-SEC-007 premium export without entitlement is refused @nfr', async ({ request }) => {
  const r = await request.post('/api/v1/export/authorize', {
    data: { feature: 'print_sheet', requirement_id: 'gb-passport', session_id: '00000000-0000-4000-8000-000000000007' },
    headers: { 'content-type': 'application/json' },
  });
  expect([401, 402, 403]).toContain(r.status());
  expect(await r.text()).not.toContain('signature');
});

test('TC-N-SEC-008 no secrets in the HTML or JavaScript bundles @nfr', async ({ request }) => {
  const html = await (await request.get('/create/')).text();
  expect(html).not.toMatch(SECRET);
  const chunks = [...html.matchAll(/src="(\/_next\/static\/[^"]+\.js)"/g)].map((m) => m[1]);
  expect(chunks.length).toBeGreaterThan(0);
  for (const c of chunks) expect(await (await request.get(c)).text(), c).not.toMatch(SECRET);
});

test('TC-N-SEC-009 cross-site requests cannot change state (CSRF) @nfr', async ({ request }) => {
  const foreign = await request.post('/api/v1/events', { data: { name: 'export_digital', props: {} }, headers: { origin: 'https://evil.example' } });
  expect(foreign.status()).toBe(403);
  const form = await request.post('/api/v1/events', { data: '{"name":"export_digital","props":{}}', headers: { 'content-type': 'text/plain' } });
  expect(form.status()).toBe(415);
});

test('TC-N-SEC-010 search and ads crawlers are not blocked @nfr', async ({ request }) => {
  for (const ua of ['Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)', 'Mediapartners-Google', 'Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)', 'Mozilla/5.0 (compatible; OAI-SearchBot/1.0; +https://openai.com/searchbot)']) {
    expect((await request.get('/', { headers: { 'user-agent': ua } })).status(), ua).toBe(200);
  }
});

test('TC-N-SEC-011 oversized API bodies are refused @nfr', async ({ request }) => {
  const r = await request.post('/api/v1/events', { data: { name: 'x'.repeat(70 * 1024) }, headers: { 'content-type': 'application/json' } });
  expect(r.status()).toBe(413);
});

test('TC-N-SEC-012 key pages and client navigation run with zero CSP violations @nfr', async ({ page }) => {
  const problems: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') problems.push(m.text()); });
  page.on('pageerror', (e) => problems.push(e.message));
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (e) => console.error(`CSP violation: ${e.violatedDirective} ${e.blockedURI} ${e.sourceFile}:${e.lineNumber}:${e.columnNumber}`));
  });
  for (const path of ['/', '/create/', '/requirements/', '/requirements/us-visa-photo-2x2-inch/', '/sizes/35x45-mm/', '/pricing/', '/faq/', '/restore/', '/privacy-dashboard/', '/guides/why-passport-photos-get-rejected/']) {
    await page.goto(path, { waitUntil: 'load' });
    await page.waitForTimeout(400);
  }
  // client-side navigation after hydration
  await page.goto('/');
  await page.getByRole('link', { name: /requirements/i }).first().click();
  await page.waitForURL(/requirements/);
  await page.waitForTimeout(400);
  expect(problems, problems.join('\n')).toEqual([]);
});
