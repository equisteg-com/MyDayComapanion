#!/usr/bin/env node
// Collects every automation result in one folder into summary.json + summary.md.
// No dependencies. Usage:
//   node tests/tools/collect-results.mjs <results-dir> [--env staging] [--base-url URL] [--commit SHA] [--run-id ID] [--run-url URL]
// Reads, when present:
//   playwright.json            Playwright JSON reporter (functional E2E + @nfr specs such as axe, offline)
//   maestro*.xml               Maestro JUnit (Android)
//   lhci/assertion-results.json + lhci.exit   Lighthouse CI assertions
//   k6-<name>.json + k6-<name>.exit           k6 --summary-export per load script
//   zap.json                   OWASP ZAP baseline JSON report
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const args = process.argv.slice(2);
const dir = args[0] ?? 'test-results';
const opt = (name) => {
  const i = args.indexOf(`--${name}`);
  return i > -1 ? args[i + 1] : process.env[name.toUpperCase().replace(/-/g, '_')] ?? '';
};
// TC-W-001, and grouped ids such as TC-N-SEC-001 / TC-W-SEO-001
const TC = /\bTC-[A-Z]-(?:[A-Z]+-)?\d{3,}\b/;
const readJson = (f) => (existsSync(f) ? JSON.parse(readFileSync(f, 'utf8')) : null);
const readExit = (f) => (existsSync(f) ? Number(readFileSync(f, 'utf8').trim()) : null);
const clip = (s, n = 600) => (s ?? '').replace(/\u001b\[[0-9;]*m/g, '').trim().slice(0, n);

const results = [];
const suites = [];
const add = (suite, kind, r) => results.push({ suite, kind, tcId: r.tcId ?? (r.title?.match(TC)?.[0] ?? null), ...r });

// ---- Playwright -----------------------------------------------------------
const pw = readJson(join(dir, 'playwright.json'));
if (pw) {
  const walk = (s, file) => {
    for (const spec of s.specs ?? []) {
      for (const t of spec.tests ?? []) {
        const last = t.results?.at(-1) ?? {};
        const status = { expected: 'pass', unexpected: 'fail', flaky: 'flaky', skipped: 'skipped' }[t.status] ?? 'fail';
        const nfr = /@nfr\b/.test(spec.title) || (spec.tags ?? []).includes('@nfr');
        add('web-e2e', nfr ? 'non-functional' : 'functional', {
          title: spec.title,
          platform: `Web (${t.projectName})`,
          status,
          ref: `tests/web/specs/${spec.file ?? file} :: ${spec.title}`,
          error: clip(last.error?.message ?? last.errors?.[0]?.message),
          evidence: (last.attachments ?? []).filter((a) => a.path).map((a) => a.path).join(', '),
        });
      }
    }
    for (const child of s.suites ?? []) walk(child, child.file ?? file);
  };
  for (const s of pw.suites ?? []) walk(s, s.file);
}

// ---- Maestro (JUnit) ------------------------------------------------------
for (const f of existsSync(dir) ? readdirSync(dir).filter((n) => /^maestro.*\.xml$/.test(n)) : []) {
  const xml = readFileSync(join(dir, f), 'utf8');
  for (const m of xml.matchAll(/<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g)) {
    const attr = (k) => m[1].match(new RegExp(`${k}="([^"]*)"`))?.[1] ?? '';
    const body = m[3] ?? '';
    const name = attr('name') || attr('id');
    const failed = /<(failure|error)\b/.test(body);
    add('mobile-e2e', 'functional', {
      title: name,
      platform: 'Android',
      status: failed ? 'fail' : /<skipped\b/.test(body) ? 'skipped' : 'pass',
      ref: `tests/mobile/flows/${name}.yaml`,
      error: clip(body.match(/<(?:failure|error)[^>]*?(?:message="([^"]*)")?[^>]*>([\s\S]*?)<\/(?:failure|error)>/)?.slice(1).join(' ')),
      evidence: '',
    });
  }
}

// ---- Lighthouse CI --------------------------------------------------------
const lh = readJson(join(dir, 'lhci', 'assertion-results.json'));
const lhExit = readExit(join(dir, 'lhci.exit'));
// manifest.json (lhci filesystem upload) has every page's category scores, so passes are counted too;
// assertion-results.json lists only the assertions that failed.
const lhManifest = readJson(join(dir, 'lhci', 'manifest.json'));
if (lh || lhExit !== null || lhManifest) {
  const failed = new Set((lh ?? []).filter((a) => !a.passed).map((a) => `${a.url}|${a.auditId}`));
  for (const run of (lhManifest ?? []).filter((m) => m.isRepresentativeRun !== false)) {
    for (const [cat, score] of Object.entries(run.summary ?? {})) {
      if (score === null || failed.has(`${run.url}|categories:${cat}`)) continue; // failures are added below
      add('lighthouse', 'non-functional', {
        title: `categories:${cat} on ${run.url}: ${Math.round(score * 100)}`,
        platform: 'Web (Lighthouse mobile)',
        status: 'pass',
        ref: `lighthouse :: categories:${cat}`,
        error: '',
        evidence: run.htmlPath ?? '',
        metrics: { score: Math.round(score * 100) },
      });
    }
  }
  for (const a of lh ?? []) {
    if (a.level !== 'error' && a.level !== 'warn') continue;
    add('lighthouse', 'non-functional', {
      title: `${a.auditId}${a.auditProperty ? '.' + a.auditProperty : ''} on ${a.url}`,
      platform: 'Web (Lighthouse mobile)',
      status: a.passed ? 'pass' : a.level === 'error' ? 'fail' : 'warn',
      ref: `lighthouse :: ${a.auditId}`,
      error: a.passed ? '' : `expected ${a.operator ?? ''} ${a.expected}, actual ${a.actual}`,
      evidence: 'lhci report link in the run artifacts',
    });
  }
  if (!lh?.length && lhExit) add('lighthouse', 'non-functional', { title: 'Lighthouse CI run', platform: 'Web', status: 'fail', ref: 'lighthouse', error: `lhci exited ${lhExit}`, evidence: '' });
}

// ---- k6 -------------------------------------------------------------------
for (const f of existsSync(dir) ? readdirSync(dir).filter((n) => /^k6-.*\.json$/.test(n)) : []) {
  const name = f.slice(3, -5);
  const s = readJson(join(dir, f));
  const exit = readExit(join(dir, `k6-${name}.exit`));
  const p95 = s?.metrics?.http_req_duration?.['p(95)'];
  const errRate = s?.metrics?.http_req_failed?.value ?? s?.metrics?.http_req_failed?.rate;
  add('k6', 'non-functional', {
    title: `k6 ${name}: p95 ${p95?.toFixed?.(0) ?? '?'} ms, error rate ${errRate !== undefined ? (errRate * 100).toFixed(2) + '%' : '?'}`,
    platform: 'API / Web',
    status: exit === 0 ? 'pass' : 'fail',
    ref: `tests/load/load.js :: ${name}`,
    error: exit === 0 ? '' : `k6 thresholds failed (exit ${exit}); p95=${p95} ms, http_req_failed=${errRate}`,
    evidence: `k6-${name}.json`,
    metrics: { p95, errRate },
  });
}

// ---- OWASP ZAP baseline ---------------------------------------------------
const zap = readJson(join(dir, 'zap.json'));
if (zap) {
  const risk = { 3: 'High', 2: 'Medium', 1: 'Low', 0: 'Info' };
  for (const site of zap.site ?? []) {
    for (const a of site.alerts ?? []) {
      const rc = Number(a.riskcode);
      if (rc < 1) continue;
      add('zap', 'non-functional', {
        title: `ZAP ${risk[rc]}: ${a.name ?? a.alert}`,
        platform: 'Web / API',
        status: rc >= 2 ? 'fail' : 'warn',
        ref: `zap-baseline :: ${a.pluginid}`,
        error: clip(`${a.count ?? a.instances?.length ?? ''} instance(s), e.g. ${a.instances?.[0]?.uri ?? ''}. ${(a.solution ?? '').replace(/<[^>]+>/g, '')}`),
        evidence: 'zap.html in the run artifacts',
      });
    }
  }
}

// ---- Summary ----------------------------------------------------------------
for (const name of [...new Set(results.map((r) => r.suite))]) {
  const rs = results.filter((r) => r.suite === name);
  const c = (s) => rs.filter((r) => r.status === s).length;
  suites.push({ name, kind: rs[0].kind, total: rs.length, passed: c('pass'), failed: c('fail'), flaky: c('flaky'), warn: c('warn'), skipped: c('skipped') });
}
const sum = (k) => suites.reduce((n, s) => n + s[k], 0);
const summary = {
  env: opt('env') || 'staging',
  baseUrl: opt('base-url'),
  commit: opt('commit'),
  runId: opt('run-id') || `local-${new Date().toISOString().slice(0, 16).replace(/[:T]/g, '')}`,
  runUrl: opt('run-url'),
  generatedAt: new Date().toISOString(),
  totals: { total: sum('total'), passed: sum('passed'), failed: sum('failed'), flaky: sum('flaky'), warn: sum('warn'), skipped: sum('skipped') },
  suites,
  results,
};
writeFileSync(join(dir, 'summary.json'), JSON.stringify(summary, null, 2));

const md = [
  `# Test run ${summary.runId} — ${summary.env}`,
  `${summary.baseUrl} · commit ${summary.commit.slice(0, 7)} · ${summary.generatedAt}${summary.runUrl ? ` · [run](${summary.runUrl})` : ''}`,
  '',
  '| Suite | Kind | Total | Pass | Fail | Flaky | Warn | Skipped |',
  '|---|---|---|---|---|---|---|---|',
  ...suites.map((s) => `| ${s.name} | ${s.kind} | ${s.total} | ${s.passed} | ${s.failed} | ${s.flaky} | ${s.warn} | ${s.skipped} |`),
  `| **Total** | | ${summary.totals.total} | ${summary.totals.passed} | ${summary.totals.failed} | ${summary.totals.flaky} | ${summary.totals.warn} | ${summary.totals.skipped} |`,
  '',
  results.some((r) => r.status === 'fail' || r.status === 'flaky') ? '## Failures and flaky tests' : '## No failures',
  ...results
    .filter((r) => r.status === 'fail' || r.status === 'flaky')
    .map((r) => `- **${r.status.toUpperCase()}** \`${r.suite}\` ${r.tcId ?? ''} ${r.title} — ${r.platform}\n  ${r.error.split('\n')[0]}`),
].join('\n');
writeFileSync(join(dir, 'summary.md'), md + '\n');
console.log(md);
if (!results.length) console.log('\nNo result files found in', dir);
