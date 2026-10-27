// Shared Playwright fixture: every test fails when a page from our own origin logs a console error or warning, or
// throws (audit A6). Third-party frames (payment provider, Turnstile, Google) are ignored: their noise is not ours to
// fix. Import `test` and `expect` from here in every functional spec; add the product's page objects as fixtures.
import { test as base, expect, type ConsoleMessage, type Page } from '@playwright/test';

export const BASE = (process.env.BASE_URL ?? 'http://localhost:8787').replace(/\/$/, '');
const ours = (m: ConsoleMessage) => { const u = m.location().url; return !u || u.startsWith(BASE); };
// Known, harmless messages from our own stack. Keep this list short and explain each entry
// (e.g. diagnostics printed by a vendored WASM library, or a CSP block that is the intended behaviour).
const ALLOWED = [
  /Failed to load resource: the server responded with a status of 4(04|29)/, // negative tests request missing pages / hit rate limits on purpose
  /Download the React DevTools/, // dev builds only
];

type Fixtures = { consoleGuard: void };

export const test = base.extend<Fixtures>({
  consoleGuard: [async ({ page }, use, info) => {
    const problems: string[] = [];
    page.on('console', (m) => {
      if ((m.type() === 'error' || m.type() === 'warning') && ours(m) && !ALLOWED.some((r) => r.test(m.text()))) problems.push(`${m.type()}: ${m.text()}`);
    });
    page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));
    await use();
    // A test that expects console noise (404 page, offline run) opts out:
    //   test.info().annotations.push({ type: 'allow-console', description: 'why' });
    if (!info.annotations.some((a) => a.type === 'allow-console')) expect(problems, problems.join('\n')).toEqual([]);
  }, { auto: true }],
});

export { expect };

/** True when the run targets a deployed dev/staging Worker (payments, rate limits and email exist there). */
export const deployed = () => /workers\.dev/.test(BASE);

export async function noHorizontalScroll(page: Page) {
  return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
}
