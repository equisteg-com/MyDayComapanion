#!/usr/bin/env node
// Checks everything /test needs before it runs, and how to get what is missing (free).
// No dependencies. Usage: node tests/tools/preflight.mjs [--env staging|dev] [--json]
// Prints a table; --json prints { items, blocking: { ci, localWeb, localAndroid }, details } for Claude.
import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync } from 'node:fs';
import { homedir, platform } from 'node:os';
import { join } from 'node:path';

const args = process.argv.slice(2);
const env = args.includes('--env') ? args[args.indexOf('--env') + 1] : 'staging';
const win = platform() === 'win32';
const mac = platform() === 'darwin';
const run = (cmd, a = []) => {
  // A shell is only needed for .bat/.cmd launchers on Windows (it would mangle quoted args otherwise).
  const r = /\.(bat|cmd)$/i.test(cmd) ? spawnSync(`"${cmd}" ${a.join(' ')}`, { encoding: 'utf8', shell: true, timeout: 20000 })
    : spawnSync(cmd, a, { encoding: 'utf8', timeout: 20000 });
  return { ok: r.status === 0, out: `${r.stdout ?? ''}${r.stderr ?? ''}`.trim() };
};
const ver = (cmd, a = ['--version']) => {
  const r = run(cmd, a);
  return r.ok ? r.out.split('\n')[0].slice(0, 60) : null;
};
const pick = (w, m, l) => (win ? w : mac ? m : l);
const items = [];
// need: which runs it blocks — 'ci' (results from GitHub Actions), 'localWeb', 'localAndroid', or 'optional'
const add = (name, needFor, need, ok, found, fix) => items.push({ name, needFor, need, status: ok ? 'ok' : 'missing', found: found ?? '', fix: ok ? '' : fix });

// ---- Tools -------------------------------------------------------------------
add('Node.js 22+', 'collect results, Playwright', ['ci', 'localWeb'], Number(process.versions.node.split('.')[0]) >= 22, process.version, pick('winget install OpenJS.NodeJS.LTS', 'brew install node@22', 'use nvm: nvm install 22'));
const git = ver('git');
add('git', 'find the deployed commit, branches', ['ci', 'localWeb', 'localAndroid'], !!git, git, pick('winget install Git.Git', 'xcode-select --install', 'sudo apt install git'));

const gh = ver('gh');
const ghAuth = gh ? run('gh', ['auth', 'status']) : { ok: false, out: '' };
add('GitHub CLI', 'start CI runs, download results, open PRs', ['ci'], !!gh, gh, pick('winget install --id GitHub.cli', 'brew install gh', 'see https://cli.github.com'));
if (gh) add('GitHub CLI login', 'same as above', ['ci'], ghAuth.ok, ghAuth.ok ? 'logged in' : '', 'run: gh auth login   (GitHub.com → HTTPS → login with browser)');

let py = null;
for (const c of win ? ['python', 'py'] : ['python3', 'python']) if (!py && ver(c)) py = c;
add('Python 3', 'update the bug log and test-case sheets', ['ci', 'localWeb', 'localAndroid'], !!py, py ? ver(py) : '', pick('winget install Python.Python.3.12', 'brew install python', 'sudo apt install python3 python3-pip'));
if (py) {
  const ox = run(py, ['-c', 'import openpyxl,sys;print(openpyxl.__version__)']);
  add('openpyxl (Python)', 'read/write .xlsx sheets', ['ci', 'localWeb', 'localAndroid'], ox.ok, ox.ok ? ox.out : '', `run: ${py} -m pip install openpyxl`);
}

let pwOk = false;
try { pwOk = existsSync(join(process.cwd(), 'node_modules', '@playwright', 'test')); } catch {}
add('Playwright (npm)', 'web functional + accessibility tests', ['localWeb'], pwOk, pwOk ? 'installed' : '', 'run: npm install');
const pwDir = pick(join(process.env.LOCALAPPDATA ?? '', 'ms-playwright'), join(homedir(), 'Library', 'Caches', 'ms-playwright'), join(homedir(), '.cache', 'ms-playwright'));
const chromium = existsSync(pwDir) && readdirSync(pwDir).some((d) => d.startsWith('chromium'));
add('Playwright Chromium browser', 'web tests (also used by Lighthouse locally)', ['localWeb'], chromium, chromium ? pwDir : '', 'run: npx playwright install chromium');

const docker = ver('docker');
const dockerUp = docker ? run('docker', ['info']).ok : false;
add('Docker (running)', 'k6 load test and OWASP ZAP scan locally', ['optional'], dockerUp, docker ? (dockerUp ? docker : `${docker} — not running`) : '',
  docker ? 'start Docker Desktop' : pick('winget install Docker.DockerDesktop (free for small companies) — or skip: CI runs k6 and ZAP', 'brew install --cask docker — or skip: CI runs k6 and ZAP', 'sudo apt install docker.io — or skip: CI runs k6 and ZAP'));

const k6 = ver('k6', ['version']);
add('k6 (or Docker running)', 'npm run perf / npm run load on this computer', ['localWeb'], !!k6 || dockerUp, k6 ?? (dockerUp ? 'via Docker' : ''), pick('winget install k6 --source winget', 'brew install k6', 'see https://grafana.com/docs/k6/latest/set-up/install-k6/'));

// ---- Android -----------------------------------------------------------------
const java = run('java', ['-version']);
add('Java 17 (JDK)', 'build the Android APK locally', ['localAndroid'], java.ok && /version "(17|21)/.test(java.out), java.ok ? java.out.split('\n')[0] : '', pick('winget install EclipseAdoptium.Temurin.17.JDK', 'brew install --cask temurin@17', 'sudo apt install openjdk-17-jdk'));
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || pick(join(process.env.LOCALAPPDATA ?? '', 'Android', 'Sdk'), join(homedir(), 'Library', 'Android', 'sdk'), join(homedir(), 'Android', 'Sdk'));
const adbPath = existsSync(join(sdk, 'platform-tools', win ? 'adb.exe' : 'adb')) ? join(sdk, 'platform-tools', win ? 'adb.exe' : 'adb') : 'adb';
const adb = run(adbPath, ['version']);
add('Android SDK + adb', 'install and drive the app on an emulator/phone', ['localAndroid'], adb.ok, adb.ok ? sdk : '', pick('winget install Google.AndroidStudio, open it once (installs the SDK), then set ANDROID_HOME', 'brew install --cask android-studio, open it once, set ANDROID_HOME', 'install Android Studio, open it once, set ANDROID_HOME'));
if (adb.ok) {
  const devs = run(adbPath, ['devices']).out.split('\n').filter((l) => /\tdevice$/.test(l));
  const emu = join(sdk, 'emulator', win ? 'emulator.exe' : 'emulator');
  const avds = existsSync(emu) ? run(emu, ['-list-avds']).out.split('\n').filter(Boolean) : [];
  add('Android device or emulator', 'run Maestro flows', ['localAndroid'], devs.length > 0 || avds.length > 0,
    devs.length ? `${devs.length} connected` : avds.length ? `emulators: ${avds.join(', ')} (start one)` : '',
    'Android Studio → Device Manager → Create device → Pixel 7, API 34 → start it (or plug in a phone with USB debugging on)');
}
const maestroBin = join(homedir(), '.maestro', 'bin', win ? 'maestro.bat' : 'maestro');
const maestro = ver(existsSync(maestroBin) ? maestroBin : 'maestro');
add('Maestro CLI', 'Android end-to-end flows', ['localAndroid'], !!maestro, maestro, pick('see https://docs.maestro.dev (Windows: download the zip, add bin to PATH)', 'curl -fsSL https://get.maestro.mobile.dev | bash', 'curl -fsSL https://get.maestro.mobile.dev | bash'));

// ---- Repository settings (GitHub) -------------------------------------------
const urlVar = env === 'dev' ? 'DEV_URL' : 'STAGING_URL';
let vars = [], secrets = [], url = process.env.BASE_URL ?? '';
if (gh && ghAuth.ok) {
  const v = run('gh', ['variable', 'list', '--json', 'name,value']);
  if (v.ok) try { vars = JSON.parse(v.out); } catch {}
  const s = run('gh', ['secret', 'list', '--json', 'name']);
  if (s.ok) try { secrets = JSON.parse(s.out).map((x) => x.name); } catch {}
  url = url || vars.find((x) => x.name === urlVar)?.value || '';
  add(`Repo variable ${urlVar}`, `the ${env} address every test runs against`, ['ci', 'localWeb', 'localAndroid'], !!vars.find((x) => x.name === urlVar), url,
    `GitHub → repo → Settings → Secrets and variables → Actions → Variables → New: ${urlVar} = https://<${env} address, no trailing slash>  (or: gh variable set ${urlVar} --body https://...)`);
  for (const name of ['CLOUDFLARE_API_TOKEN', 'CLOUDFLARE_ACCOUNT_ID'])
    add(`Repo secret ${name}`, 'deploys (tests need a deployed environment)', ['ci'], secrets.includes(name), secrets.includes(name) ? 'set' : '', `you set it yourself (never paste it in chat): gh secret set ${name}`);
}

// ---- Environment reachable ----------------------------------------------------
if (url) {
  for (const path of ['/', '/api/v1/health']) {
    let ok = false, code = '';
    try { const r = await fetch(url + path, { signal: AbortSignal.timeout(10000) }); ok = r.ok; code = String(r.status); } catch (e) { code = e.code ?? e.name; }
    add(`${env} ${path} reachable`, path === '/' ? 'web tests' : 'API, payments and Android tests', path === '/' ? ['ci', 'localWeb'] : ['optional'], ok, `${url}${path} → ${code}`,
      path === '/' ? `deploy ${env} first (merge into ${env === 'dev' ? 'development' : 'staging'}) and check the URL` : 'no API Worker yet, or it is down — API/payment tests will fail');
  }
}

// ---- Test assets in this repo -------------------------------------------------
const count = (dir, re) => (existsSync(dir) ? readdirSync(dir).filter((f) => re.test(f)).length : 0);
const assets = [
  ['Web app (apps/web)', existsSync('apps/web/package.json'), 'run /newapp first'],
  ['Web specs (tests/web/specs)', count('tests/web/specs', /\.spec\.ts$/) > 0, 'phase 7 writes them; nothing to run for web yet'],
  ['Android app (apps/mobile)', existsSync('apps/mobile/app.config.ts'), 'no mobile app yet — Android is skipped'],
  ['Maestro flows (tests/mobile/flows)', count('tests/mobile/flows', /\.ya?ml$/) > 0, 'phase 7 writes them; nothing to run for Android yet'],
  ['Lighthouse pages (tests/load/lighthouserc.json)', existsSync('tests/load/lighthouserc.json'), 'restore it from the template'],
  ['Load routes (tests/testdata/shared/load.json)', existsSync('tests/testdata/shared/load.json'), 'restore it from the template'],
];
for (const [name, ok, fix] of assets) add(name, 'what gets tested', ['optional'], ok, ok ? 'present' : '', fix);

// ---- Details only a person can give ------------------------------------------
// Claude asks for these (never for secrets) and records the answers in tests/testdata or the repo variables.
const details = [
  `${env} address (${urlVar})${url ? ` — currently ${url}` : gh && ghAuth.ok ? ' — not set' : ' — unknown until the GitHub CLI is logged in'}`,
  'Payment provider is in TEST mode on this environment, and which test card / UPI test id to use (public test values only)',
  'Google Play Billing: a licence-tester Gmail on the test device (Play Console → Settings → License testing) — Android purchase tests',
  'Ads: AdSense/AdMob test IDs active on this environment (never live ads)',
  'Any test login or fixture data the product needs (goes in tests/testdata/, never real customer data)',
];

const blocking = Object.fromEntries(['ci', 'localWeb', 'localAndroid'].map((m) => [m, items.filter((i) => i.status !== 'ok' && i.need.includes(m)).map((i) => i.name)]));

if (args.includes('--json')) {
  console.log(JSON.stringify({ env, url, items, blocking, details }, null, 2));
} else {
  console.log(`Preflight for /test on ${env}${url ? ` (${url})` : ''}\n`);
  console.log('| Status | Item | Needed for | Found | How to fix (free) |\n|---|---|---|---|---|');
  for (const i of items) console.log(`| ${i.status === 'ok' ? 'OK' : i.need.includes('optional') ? 'optional' : 'MISSING'} | ${i.name} | ${i.needFor} | ${i.found} | ${i.fix} |`);
  console.log('\nCan run now:');
  console.log(`- Results from CI (web + Android on GitHub): ${blocking.ci.length ? 'NO — ' + blocking.ci.join(', ') : 'yes'}`);
  console.log(`- Web tests on this computer: ${blocking.localWeb.length ? 'NO — ' + blocking.localWeb.join(', ') : 'yes'}`);
  console.log(`- Android tests on this computer: ${blocking.localAndroid.length ? 'NO — ' + blocking.localAndroid.join(', ') : 'yes'}`);
  console.log('\nDetails to confirm with a person:\n' + details.map((d) => `- ${d}`).join('\n'));
}
