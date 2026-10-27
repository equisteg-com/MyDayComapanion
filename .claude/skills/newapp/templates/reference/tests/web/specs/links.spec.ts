// Every sitemap URL and every internal link on those pages answers 200 (audit B2).
import { BASE, expect, test } from '../fixtures';

test('TC-W-035 every sitemap page and internal link answers 200', async ({ request }, info) => {
  test.skip(info.project.name !== 'chromium', 'request-level, once per run');
  test.setTimeout(300_000);
  const xml = await (await request.get('/sitemap.xml')).text();
  const pages = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => new URL(m[1]!).pathname);
  expect(pages.length).toBeGreaterThan(50);
  const links = new Set<string>(pages);
  for (const p of pages) {
    const r = await request.get(p);
    expect(r.status(), p).toBe(200);
    for (const m of (await r.text()).matchAll(/href="(\/[^"#?]*)/g)) if (!m[1]!.startsWith('/_next/') && !m[1]!.startsWith('/api/')) links.add(m[1]!);
  }
  const bad: string[] = [];
  for (const l of links) { const s = (await request.get(l)).status(); if (s !== 200) bad.push(`${l} → ${s}`); }
  expect(bad, `${BASE}: ${bad.join(', ')}`).toEqual([]);
});
