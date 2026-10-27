#!/usr/bin/env node
// Performance and load testing in one command (no dependencies to install in the repo).
//
//   npm run perf                  Lighthouse (speed, accessibility, best practices, SEO) + k6 smoke, on staging
//   npm run perf -- dev           same, on dev
//   npm run load                  k6 "load" profile on staging
//   npm run load -- stress        smoke | load | stress | spike | soak   (add "dev" for dev)
//   options: --url https://...   --out <folder>   --yes (allow soak / big runs)
//
// Never runs against production. Results: test-results/perf-<time>/summary.md (+ summary.json for /test).
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir, platform } from 'node:os';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const argv = process.argv.slice(2);
const flag = (n) => argv.includes(`--${n}`);
const opt = (n) => (argv.includes(`--${n}`) ? argv[argv.indexOf(`--${n}`) + 1] : undefined);
const words = argv.filter((a, i) => !a.startsWith('--') && !['--url', '--out'].includes(argv[i - 1]));
const PROFILES = ['smoke', 'load', 'stress', 'spike', 'soak'];
const loadOnly = flag('load');
const profile = words.find((w) => PROFILES.includes(w)) ?? (loadOnly ? 'load' : 'smoke');
const env = words.find((w) => ['dev', 'staging', 'production', 'prod'].includes(w)) ?? 'staging';
const win = platform() === 'win32';
const root = fileURLToPath(new URL('../..', import.meta.url));
process.chdir(root);

const say = (m) => console.log(m);
const fail = (m) => { console.error(`\n✖ ${m}`); process.exit(2); };
const sh = (cmd, args, extra = {}) => spawnSync(cmd, args, { stdio: 'inherit', ...extra, shell: win && /^(npx|npm)$/.test(cmd) });
const quiet = (cmd, args) => { const r = spawnSync(cmd, args, { encoding: 'utf8', shell: win && /^(npx|npm)$/.test(cmd) }); return r.status === 0 ? (r.stdout || '').trim() : null; };

if (env === 'production' || env === 'prod') fail('Performance and load tests never run against production. Use staging or dev.');

// ---- Where to test ------------------------------------------------------------
const varName = env === 'dev' ? 'DEV_URL' : 'STAGING_URL';
let url = opt('url') || process.env.BASE_URL || '';
const envFile = 'tests/testdata/shared/environment.json';
if (!url && existsSync(envFile)) {
  try { const e = JSON.parse(readFileSync(envFile, 'utf8')); if ((e.env ?? 'staging') === env) url = e.baseUrl ?? ''; } catch {}
}
if (!url) url = quiet('gh', ['variable', 'get', varName]) ?? '';
if (!url) fail(`No ${env} address. Run with --url https://<${env} address>, or set it once: gh variable set ${varName} --body https://...`);
url = url.replace(/\/$/, '');
const prod = process.env.PRODUCTION_URL || quiet('gh', ['variable', 'get', 'PRODUCTION_URL']);
if (prod && url.replace(/\/$/, '') === prod.replace(/\/$/, '')) fail(`${url} is the production address. Use staging or dev.`);

// ---- Limits (Workers free plan: 100,000 requests/day) ----------------------------
const routes = JSON.parse(readFileSync('tests/testdata/shared/load.json', 'utf8')).routes.length;
const vuSeconds = { smoke: 2 * 30, load: 10 * 90, stress: 30 * 150, spike: 40 * 45, soak: 5 * 1800 }[profile];
const estRequests = Math.round(vuSeconds * routes * 0.9);
if ((profile === 'soak' || estRequests > 20000) && !flag('yes'))
  fail(`The ${profile} profile sends about ${estRequests.toLocaleString()} requests (free limit 100,000/day for all traffic). Re-run with --yes to confirm.`);

const stamp = new Date().toISOString().slice(0, 16).replace(/-/g, '').replace('T', '-').replace(':', ''); // 20261001-1431
const out = resolve(opt('out') || join('test-results', `perf-${stamp}`));
mkdirSync(out, { recursive: true });
say(`\n▶ ${loadOnly ? `Load test (${profile})` : `Performance (Lighthouse) + load smoke`} on ${env}: ${url}`);
say(`  Results: ${relative(root, out)}\n`);

// ---- 1. Lighthouse --------------------------------------------------------------
if (!loadOnly) {
  const cfgPath = 'tests/load/lighthouserc.json';
  const cfg = JSON.parse(readFileSync(cfgPath, 'utf8'));
  const paths = cfg.ci?.collect?.url ?? ['/'];
  cfg.ci.collect = { ...cfg.ci.collect, url: paths.map((p) => (p.startsWith('http') ? p : url + new URL(p, 'http://x').pathname)) };
  delete cfg.ci.collect.staticDistDir;
  cfg.ci.upload = { target: 'filesystem', outputDir: join(out, 'lhci') };
  const tmpCfg = join(out, 'lighthouserc.run.json');
  writeFileSync(tmpCfg, JSON.stringify(cfg, null, 2));
  // Use Playwright's Chromium when Chrome isn't installed.
  const envVars = { ...process.env };
  if (!envVars.CHROME_PATH) {
    const pw = win ? join(process.env.LOCALAPPDATA ?? '', 'ms-playwright') : platform() === 'darwin' ? join(homedir(), 'Library/Caches/ms-playwright') : join(homedir(), '.cache/ms-playwright');
    const dir = existsSync(pw) ? readdirSync(pw).filter((d) => /^chromium-\d+/.test(d)).sort().pop() : null;
    const candidates = dir ? [join(pw, dir, 'chrome-win', 'chrome.exe'), join(pw, dir, 'chrome-win64', 'chrome.exe'), join(pw, dir, 'chrome-linux', 'chrome'), join(pw, dir, 'chrome-mac', 'Chromium.app/Contents/MacOS/Chromium')] : [];
    const found = candidates.find(existsSync);
    if (found) envVars.CHROME_PATH = found;
  }
  say(`● Lighthouse on ${cfg.ci.collect.url.length} page(s) — ${cfgPath}`);
  const r = sh('npx', ['--yes', '@lhci/cli', 'autorun', `--config=${tmpCfg}`], { env: envVars });
  writeFileSync(join(out, 'lhci.exit'), String(r.status ?? 1));
  if (existsSync('.lighthouseci/assertion-results.json')) {
    mkdirSync(join(out, 'lhci'), { recursive: true });
    copyFileSync('.lighthouseci/assertion-results.json', join(out, 'lhci', 'assertion-results.json'));
  }
}

// ---- 2. k6 ------------------------------------------------------------------------
say(`\n● k6 ${profile} — ${routes} route(s), about ${estRequests.toLocaleString()} requests`);
const summary = join(out, `k6-${profile}.json`);
const k6Env = { ...process.env, BASE_URL: url, PROFILE: profile };
let r;
if (quiet('k6', ['version'])) {
  r = sh('k6', ['run', '--quiet', '--summary-export', summary, 'tests/load/load.js'], { env: k6Env });
} else if (quiet('docker', ['info']) !== null) {
  say('  (k6 not installed — using the grafana/k6 Docker image)');
  const user = process.getuid ? ['-u', `${process.getuid()}:${process.getgid()}`] : [];
  r = sh('docker', ['run', '--rm', ...user, '-v', `${root}:/w`, '-w', '/w', '-e', `BASE_URL=${url}`, '-e', `PROFILE=${profile}`,
    'grafana/k6', 'run', '--quiet', '--summary-export', relative(root, summary).replace(/\\/g, '/'), 'tests/load/load.js']);
}
const k6Missing = !r;
if (k6Missing) console.error(`  ✖ k6 skipped — install it (free): ${win ? 'winget install k6 --source winget' : platform() === 'darwin' ? 'brew install k6' : 'see https://grafana.com/docs/k6/latest/set-up/install-k6/'} (or start Docker)`);
else writeFileSync(join(out, `k6-${profile}.exit`), String(r.status ?? 1));

// ---- 3. Summary ---------------------------------------------------------------------
sh('node', ['tests/tools/collect-results.mjs', out, '--env', env, '--base-url', url, '--commit', quiet('git', ['rev-parse', 'HEAD']) ?? '', '--run-id', `perf-${stamp}`]);
const s = JSON.parse(readFileSync(join(out, 'summary.json'), 'utf8'));
say(`\nTo log failures as bugs: python tests/tools/bug_log.py ingest ${relative(root, out).replace(/\\/g, '/')}/summary.json  (or run /test)`);
if (k6Missing) console.error('\nLoad test was NOT run (k6 missing) — see above.');
process.exit(s.totals.failed ? 1 : k6Missing ? 2 : 0);
