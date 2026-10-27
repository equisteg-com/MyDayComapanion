import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './specs',
  retries: process.env.CI ? 1 : 0,
  // Screenshots, traces and videos of failures — referenced as evidence in tests/bugs/bug-log.xlsx.
  outputDir: '../../test-results/artifacts',
  reporter: [
    ['html', { outputFolder: '../../playwright-report', open: 'never' }],
    // Read by tests/tools/collect-results.mjs. Put the TC ID in every test title ("TC-W-001 buys a pack");
    // tag non-functional specs with @nfr ("TC-N-004 axe on every page @nfr").
    ['json', { outputFile: '../../test-results/playwright.json' }],
    ['list'],
  ],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:8787',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
    { name: 'pixel7', use: { ...devices['Pixel 7'] } },
    { name: 'iphone14', use: { ...devices['iPhone 14'] } },
  ],
});
