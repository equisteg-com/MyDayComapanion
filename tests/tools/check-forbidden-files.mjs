#!/usr/bin/env node
// Stops secret files and live credentials from being committed.
//   node tests/tools/check-forbidden-files.mjs           → every tracked file (CI)
//   node tests/tools/check-forbidden-files.mjs --staged  → files staged for the next commit (.githooks/pre-commit)
// Exit 1 lists each problem. Patterns live in tests/tools/secret-patterns.mjs.
// It also stops the founders' private kit (installed by `npm run kit`) from being committed to a product repo.
// This is the quick local net; Gitleaks in CI is the deep scan.

import { execFileSync } from 'node:child_process';
import { readFileSync, statSync } from 'node:fs';
import { isSecretPath, findSecretValue } from './secret-patterns.mjs';

const staged = process.argv.includes('--staged');
const git = (args) => execFileSync('git', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

const files = (staged ? git(['diff', '--cached', '--name-only', '--diff-filter=ACMR']) : git(['ls-files']))
  .split('\n')
  .filter(Boolean);

// Files allowed to contain secret-shaped strings because they describe or test the patterns themselves.
const VALUE_ALLOWLIST = [/^tests\/tools\/secret-patterns\.mjs$/, /^tests\/tools\/.*\.test\.mjs$/];

// Kit paths (same list as tests/tools/kit.mjs). Build-team members can read this repo; the kit stays private.
const KIT_PATHS = [
  /^\.claude\//,
  /^\.claude-kit\//,
  /^CLAUDE(\.local)?\.md$/,
  /^prompts\//,
  /^docs\/kit\//,
  /^docs\/standards\/(agent-reviews|product-categories|repo-security|risks-and-issues|self-healing)\.md$/,
];

const problems = [];
for (const file of files) {
  if (isSecretPath(file)) {
    problems.push(`${file}: secret file must never be committed (keep it local; it is git-ignored)`);
    continue;
  }
  if (KIT_PATHS.some((re) => re.test(file))) {
    problems.push(`${file}: private kit file must never be committed (it comes from the kit repo via \`npm run kit\`; unstage it with \`git rm --cached\`)`);
    continue;
  }
  if (VALUE_ALLOWLIST.some((re) => re.test(file))) continue;
  let text;
  try {
    if (statSync(file).size > 2 * 1024 * 1024) continue; // large binaries: Gitleaks covers them
    text = staged ? git(['show', `:${file}`]) : readFileSync(file, 'utf8');
  } catch {
    continue; // deleted or unreadable
  }
  if (text.includes('\u0000')) continue; // binary
  const value = findSecretValue(text);
  if (value) problems.push(`${file}: contains what looks like a ${value}. Remove it, and rotate it at the provider if it was real.`);
}

if (problems.length) {
  console.error(`Secret check failed (${problems.length}):\n  ${problems.join('\n  ')}`);
  process.exit(1);
}
console.log(`Secret check passed (${files.length} ${staged ? 'staged' : 'tracked'} files).`);
