#!/usr/bin/env node
// Last gate of the Claude command bot (.github/workflows/claude-commands.yml): nothing the bot is about to
// publish (its reply and the code change) may contain text from the private kit or a secret.
//   node kit-leak-check.mjs --kit <kit dir> --baseline <trusted checkout> --reply <file> [--reply2 <file>] --patch <file>
// Exit 0 = safe to publish. Exit 1 = blocked; the reason is printed without quoting the matched text.
// It catches copying (12-word runs shared with the kit, reflowed or not) and secrets. It cannot catch a
// paraphrase, which is why the bot also has no shell, no web access and a prompt that forbids describing the kit.

import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, relative } from 'node:path';
import { findSecretValue } from './secret-patterns.mjs';

const arg = (name) => {
  const i = process.argv.indexOf(name);
  return i > -1 ? process.argv[i + 1] : undefined;
};
const KIT = arg('--kit');
const BASELINE = arg('--baseline');
const REPLIES = [arg('--reply'), arg('--reply2')].filter(Boolean);
const PATCH = arg('--patch');
if (!KIT || !existsSync(KIT)) {
  console.error('kit-leak-check: --kit <dir> is required');
  process.exit(2);
}

const N = 12; // words per shingle
const MAX_HITS = 2; // more shared runs than this = blocked
const MAX_FILE = 1024 * 1024;

// Templates and reference implementations in the kit are meant to be copied into products, so they are not private text.
const SKIP_KIT = [/^\.git\//, /^kit\.json$/, /(^|\/)\.claude\/skills\/newapp\/templates\//];
// The baseline never counts installed kit files (a founder's checkout has them) as shared text.
const SKIP_BASELINE = [
  /^\.git\//, /^node_modules\//, /^package-lock\.json$/, /^\.claude-kit\//, /^\.claude\//, /^CLAUDE(\.local)?\.md$/,
  /^prompts\//, /^docs\/kit\//, /^docs\/standards\/(agent-reviews|product-categories|repo-security|risks-and-issues|self-healing)\.md$/,
];

function* files(root, skip) {
  const stack = [root];
  while (stack.length) {
    const dir = stack.pop();
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      const rel = relative(root, full).replace(/\\/g, '/');
      if (skip.some((re) => re.test(rel) || re.test(`${rel}/`))) continue;
      const st = statSync(full);
      if (st.isDirectory()) stack.push(full);
      else if (st.size <= MAX_FILE) yield full;
    }
  }
}

const text = (file) => {
  const t = readFileSync(file, 'utf8');
  return t.includes('\u0000') ? '' : t;
};
const words = (t) => t.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim().split(' ').filter(Boolean);
function shingles(t, into = new Set()) {
  const w = words(t);
  for (let i = 0; i + N <= w.length; i++) into.add(w.slice(i, i + N).join(' '));
  return into;
}

// Private text = kit shingles that do not also appear in the product repo itself (shared boilerplate).
const kit = new Set();
for (const f of files(KIT, SKIP_KIT)) shingles(text(f), kit);
if (BASELINE && existsSync(BASELINE)) {
  const common = new Set();
  for (const f of files(BASELINE, SKIP_BASELINE)) shingles(text(f), common);
  for (const s of common) kit.delete(s);
}

// What would be published: the reply, and only the lines the patch adds.
const outputs = [];
for (const r of REPLIES) if (existsSync(r)) outputs.push(['reply', readFileSync(r, 'utf8')]);
if (PATCH && existsSync(PATCH)) {
  const added = readFileSync(PATCH, 'utf8')
    .split('\n')
    .filter((l) => l.startsWith('+') && !l.startsWith('+++'))
    .map((l) => l.slice(1))
    .join('\n');
  outputs.push(['code change', added]);
}

const problems = [];
const exact = [process.env.CLAUDE_CODE_OAUTH_TOKEN, process.env.CLAUDE_KIT_TOKEN].filter((v) => v && v.length >= 8);
for (const [name, out] of outputs) {
  if (exact.some((v) => out.includes(v))) problems.push(`${name} contains a workflow secret`);
  const secret = findSecretValue(out);
  if (secret) problems.push(`${name} contains what looks like a ${secret}`);
  let hits = 0;
  for (const s of shingles(out)) if (kit.has(s)) hits++;
  if (hits > MAX_HITS) problems.push(`${name} repeats private kit text (${hits} matching passages)`);
}

if (problems.length) {
  console.error(`kit-leak-check: blocked\n  ${problems.join('\n  ')}`);
  process.exit(1);
}
console.log(`kit-leak-check: passed (${outputs.length} outputs checked)`);
