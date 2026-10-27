// Accessibility: axe-core WCAG 2.2 AA on every key page and every Create step (NFR-009).
import AxeBuilder from '@axe-core/playwright';
import type { Page } from '@playwright/test';
import { expect, test } from '../fixtures';

const PAGES = ['/', '/requirements/', '/requirements/uk-passport-photo-35x45-mm/', '/sizes/', '/sizes/35x45-mm/', '/guides/', '/guides/how-to-take-a-passport-photo-at-home/', '/pricing/', '/faq/', '/about/', '/contact/', '/privacy/', '/privacy-dashboard/', '/restore/', '/delete-data/', '/projects/', '/roadmap/'];

async function axe(page: Page) {
  const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
  return r.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
}

for (const path of PAGES) {
  test(`TC-N-A11Y-001 axe finds no serious or critical issue on ${path} @nfr`, async ({ page }) => {
    await page.goto(path);
    expect(await axe(page)).toEqual([]);
  });
}

test('TC-N-A11Y-002 axe on every Create step @nfr', async ({ create, page }) => {
  await create.open();
  expect(await axe(page), 'step 1').toEqual([]);
  await create.useSample();
  expect(await axe(page), 'step 3').toEqual([]);
  await create.toExport();
  expect(await axe(page), 'step 4').toEqual([]);
});

test('TC-N-A11Y-003 the Create flow works with the keyboard only @nfr', async ({ page, isMobile }) => {
  test.skip(!!isMobile, 'keyboard flow on desktop');
  await page.goto('/create/');
  for (const name of ['2 Photo', 'Plain wall']) {
    const b = page.getByRole('button', { name });
    await b.focus();
    await expect(b).toBeFocused();
    await page.keyboard.press('Enter');
  }
  await expect(page.getByRole('region', { name: 'Editor' })).toBeVisible({ timeout: 20_000 });
  const cont = page.getByRole('button', { name: 'Continue to export' });
  await cont.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('region', { name: 'Digital photo' })).toBeVisible();
});

test('TC-N-A11Y-004 pages stay usable at 200% zoom (reflow) @nfr', async ({ page }) => {
  await page.setViewportSize({ width: 640, height: 900 }); // a 1280 px window at 200%
  for (const path of ['/', '/create/', '/pricing/']) {
    await page.goto(path);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), path).toBe(true);
  }
});
