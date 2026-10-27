#!/usr/bin/env node
// Installs the founders' private kit (Claude settings, skills, agents and internal standards) into this
// checkout. The kit lives in a separate private repo, so people who can only build and test never see it.
//   npm run kit            → install or update the kit (needs read access to the kit repo)
//   npm run kit -- status  → show whether the kit is installed and at which commit
//   npm run kit:check      → validate the installed kit against this repo
// Kit files are git-ignored and blocked by the pre-commit check, so they never land in this repo.
// In CI set CLAUDE_KIT_TOKEN (read-only token for the kit repo) and pass --strict.

import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, readFileSync, rmSync } from 'node:fs';
import { join, normalize, isAbsolute } from 'node:path';

const ROOT = process.cwd();
const CACHE = join(ROOT, '.claude-kit');
const pkg = JSON.parse(readFileSync(join(ROOT, 'package.json'), 'utf8'));
const REPO = process.env.CLAUDE_KIT_REPO || pkg.claudeKit?.repo || 'equisteg-com/claude-kit';
const REF = process.env.CLAUDE_KIT_REF || pkg.claudeKit?.ref || 'main';
// Optional product-specific overlay in the kit (products/<name>/), for products with their own Claude rules.
const PRODUCT = process.env.CLAUDE_KIT_PRODUCT || pkg.claudeKit?.product || '';
const args = process.argv.slice(2);
const strict = args.includes('--strict');
const cmd = args.find((a) => !a.startsWith('--')) || 'install';

// The kit may only write these paths. All are git-ignored here, and tests/tools/check-forbidden-files.mjs
// (same list) stops them being committed.
const KIT_PATHS = [
  /^\.claude\//,
  /^CLAUDE\.md$/,
  /^prompts\//,
  /^docs\/kit\//,
  /^docs\/standards\/(agent-reviews|product-categories|repo-security|risks-and-issues|self-healing)\.md$/,
];

const git = (gitArgs, opts = {}) => {
  // The token goes in a header for this one command only: never in the URL, .git/config or logs.
  const token = process.env.CLAUDE_KIT_TOKEN;
  const auth = token
    ? ['-c', `http.https://github.com/.extraheader=AUTHORIZATION: basic ${Buffer.from(`x-access-token:${token}`).toString('base64')}`]
    : [];
  return execFileSync('git', [...auth, ...gitArgs], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, GIT_TERMINAL_PROMPT: '0' },
    ...opts,
  });
};

function fetchKit() {
  const url = /^(\/|\w+:\/\/)/.test(REPO) ? REPO : `https://github.com/${REPO}.git`; // a path or URL is used as is (tests)
  if (existsSync(join(CACHE, '.git'))) {
    git(['-C', CACHE, 'fetch', '--depth', '1', url, REF]);
    git(['-C', CACHE, 'reset', '--hard', 'FETCH_HEAD']);
  } else {
    rmSync(CACHE, { recursive: true, force: true });
    git(['clone', '--depth', '1', '--branch', REF, url, CACHE]);
  }
}

function install() {
  try {
    fetchKit();
  } catch {
    rmSync(CACHE, { recursive: true, force: true });
    const msg = `Kit not installed: no read access to ${REPO}. Build and test commands work without it.`;
    if (strict) {
      console.error(msg);
      process.exit(1);
    }
    console.log(msg);
    return;
  }
  // Company kit first, then the product's own overlay on top of it.
  const sources = [CACHE];
  if (PRODUCT) {
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(PRODUCT) || !existsSync(join(CACHE, 'products', PRODUCT, 'kit.json'))) {
      console.error(`The kit has no product overlay "${PRODUCT}"; installing the company kit only.`);
    } else sources.push(join(CACHE, 'products', PRODUCT));
  }
  for (const src of sources) {
    const manifest = JSON.parse(readFileSync(join(src, 'kit.json'), 'utf8'));
    for (const entry of manifest.files) {
      const rel = normalize(entry).replace(/\\/g, '/').replace(/\/$/, '');
      if (isAbsolute(rel) || rel.split('/').includes('..') || !KIT_PATHS.some((re) => re.test(rel) || re.test(`${rel}/`))) {
        console.error(`kit.json lists ${entry}, which the kit may not write. Skipped.`);
        continue;
      }
      if (!existsSync(join(src, rel))) continue;
      rmSync(join(ROOT, rel), { recursive: true, force: true }); // files removed from the kit disappear too
      cpSync(join(src, rel), join(ROOT, rel), { recursive: true });
    }
  }
  const sha = git(['-C', CACHE, 'rev-parse', '--short', 'HEAD']).trim();
  console.log(`Kit installed from ${REPO}@${REF} (${sha})${sources.length > 1 ? ` with the ${PRODUCT} overlay` : ''}.`);
}

function status() {
  if (!existsSync(join(CACHE, '.git'))) {
    console.log('Kit not installed. Founders: run `npm run kit`.');
    return;
  }
  const sha = git(['-C', CACHE, 'rev-parse', '--short', 'HEAD']).trim();
  console.log(`Kit installed from ${REPO}@${REF} (${sha}).`);
}

if (cmd === 'install' || cmd === 'update') install();
else if (cmd === 'status') status();
else {
  console.error('Usage: npm run kit [-- install|update|status] [--strict]');
  process.exit(1);
}
