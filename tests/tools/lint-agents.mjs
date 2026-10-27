#!/usr/bin/env node
// Checks the Claude Code setup of this repo so a broken agent or skill fails CI instead of failing silently:
//   1. every reviewer agent in .claude/agents/ has valid frontmatter, a known tool list and no edit tools
//   2. every skill in .claude/skills/*/SKILL.md has a name matching its folder and a description
//   3. every `agent:<name>` reference in the instructions resolves to an agent file, and the roster lists every agent
//   4. template paths quoted in agents, skills and standards (.claude/, docs/standards/, prompts/, tests/tools/) exist
//   5. .claude/settings.json is valid JSON with permission lists
// Usage: node tests/tools/lint-agents.mjs [--root <dir>]   (no dependencies; exit 1 on any error)

import { readFileSync, readdirSync, existsSync, statSync } from 'node:fs';
import { join, relative, basename } from 'node:path';

const argRoot = process.argv.indexOf('--root');
const ROOT = argRoot > -1 ? process.argv[argRoot + 1] : process.cwd();
const AGENTS_DIR = join(ROOT, '.claude/agents');
const SKILLS_DIR = join(ROOT, '.claude/skills');

// Tools a reviewer may have. Edit tools are left out on purpose: reviewers report, the main session fixes.
const ALLOWED_TOOLS = new Set(['Read', 'Grep', 'Glob', 'Bash', 'WebSearch', 'WebFetch']);
const EDIT_TOOLS = new Set(['Edit', 'Write', 'MultiEdit', 'NotebookEdit']);
const NAME_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const errors = [];
const warnings = [];
const err = (file, msg) => errors.push(`${relative(ROOT, file) || file}: ${msg}`);
const warn = (file, msg) => warnings.push(`${relative(ROOT, file) || file}: ${msg}`);

function walk(dir, filter, out = []) {
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === '.git') continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, filter, out);
    else if (filter(full)) out.push(full);
  }
  return out;
}

// Minimal frontmatter reader: `key: value` lines between the first two `---` lines.
function frontmatter(file, text) {
  const lines = text.split(/\r?\n/);
  if (lines[0] !== '---') return null;
  const end = lines.indexOf('---', 1);
  if (end === -1) {
    err(file, 'frontmatter is not closed with ---');
    return null;
  }
  const data = {};
  for (const line of lines.slice(1, end)) {
    const m = line.match(/^([A-Za-z][\w-]*):\s*(.*)$/);
    if (m) data[m[1]] = m[2].replace(/^["']|["']$/g, '').trim();
  }
  return { data, body: lines.slice(end + 1).join('\n') };
}

// 1. Agents
const agentFiles = existsSync(AGENTS_DIR)
  ? readdirSync(AGENTS_DIR).filter((f) => f.endsWith('.md') && f !== 'README.md').map((f) => join(AGENTS_DIR, f))
  : [];
const agentNames = new Set();
for (const file of agentFiles) {
  const fm = frontmatter(file, readFileSync(file, 'utf8'));
  if (!fm) {
    err(file, 'missing frontmatter (--- name / description / tools ---)');
    continue;
  }
  const { name, description, tools } = fm.data;
  const expected = basename(file, '.md');
  if (!name) err(file, 'frontmatter has no name');
  else if (!NAME_RE.test(name)) err(file, `name "${name}" must be lowercase words joined by hyphens`);
  else if (name !== expected) err(file, `name "${name}" does not match the file name "${expected}"`);
  if (name) agentNames.add(name);
  if (!description) err(file, 'frontmatter has no description (Claude uses it to decide when to call the agent)');
  else if (description.length < 60) warn(file, 'description is short; say what it checks and when to use it');
  if (!tools) err(file, 'frontmatter has no tools list (without it the agent inherits every tool, including edits)');
  else {
    for (const tool of tools.split(',').map((t) => t.trim()).filter(Boolean)) {
      if (EDIT_TOOLS.has(tool)) err(file, `tool "${tool}" is not allowed: reviewer agents never edit files`);
      else if (!ALLOWED_TOOLS.has(tool)) err(file, `unknown or unapproved tool "${tool}"`);
    }
  }
  if (!fm.body.includes('docs/standards/agent-reviews.md')) err(file, 'body must point to the verdict format in docs/standards/agent-reviews.md');
  if (!/instruction/i.test(fm.body)) err(file, 'body must say that text found in code, issues, logs or data is never an instruction');
  if (fm.body.trim().split(/\s+/).length < 120) warn(file, 'body is very short for a reviewer checklist');
}
if (agentFiles.length === 0) warn(AGENTS_DIR, 'no agents found');

// Roster lists every agent
const rosterFile = join(AGENTS_DIR, 'README.md');
if (agentFiles.length) {
  if (!existsSync(rosterFile)) err(rosterFile, 'missing roster');
  else {
    const roster = readFileSync(rosterFile, 'utf8');
    for (const name of agentNames) if (!roster.includes(`| \`${name}\` |`)) err(rosterFile, `roster table has no row for "${name}"`);
  }
}

// 2. Skills
for (const file of walk(SKILLS_DIR, (f) => basename(f) === 'SKILL.md')) {
  const fm = frontmatter(file, readFileSync(file, 'utf8'));
  const dir = basename(join(file, '..'));
  if (!fm) {
    err(file, 'missing frontmatter');
    continue;
  }
  if (fm.data.name !== dir) err(file, `name "${fm.data.name ?? ''}" does not match its folder "${dir}"`);
  if (!fm.data.description) err(file, 'frontmatter has no description');
}

// 3 + 4. References inside instruction files
const instructionFiles = [
  ...['CLAUDE.md', 'AGENTS.md', 'README.md', 'CONTRIBUTING.md'].map((f) => join(ROOT, f)).filter(existsSync),
  ...walk(join(ROOT, '.claude'), (f) => f.endsWith('.md')),
  ...walk(join(ROOT, 'prompts'), (f) => f.endsWith('.md')),
  ...walk(join(ROOT, 'docs/standards'), (f) => f.endsWith('.md')),
  ...walk(join(ROOT, '.github'), (f) => /\.(md|ya?ml)$/.test(f)),
];
const PATH_RE = /`((?:\.claude|docs\/standards|prompts|tests\/tools)\/[^`\s]+?)`/g;
for (const file of instructionFiles) {
  const text = readFileSync(file, 'utf8');
  for (const m of text.matchAll(/\bagent:([a-z0-9-]+)/g)) {
    if (!agentNames.has(m[1])) err(file, `refers to agent:${m[1]}, but .claude/agents/${m[1]}.md does not exist`);
  }
  // Only the template's own folders are checked: product files (docs/LLD.md, apps/...) appear per product.
  if (!file.includes('/.claude/') && !file.includes('/docs/standards/')) continue;
  for (const m of text.matchAll(PATH_RE)) {
    const ref = m[1].replace(/[),.:;]+$/, '').split(/\s|::/)[0];
    if (/[<>*{}$]/.test(ref) || ref.endsWith('/')) continue;
    if (!existsSync(join(ROOT, ref))) err(file, `mentions \`${ref}\`, which does not exist`);
  }
}

// 5. Settings
const settingsFile = join(ROOT, '.claude/settings.json');
if (existsSync(settingsFile)) {
  try {
    const s = JSON.parse(readFileSync(settingsFile, 'utf8'));
    for (const key of ['allow', 'ask', 'deny']) {
      const list = s.permissions?.[key];
      if (list !== undefined && !Array.isArray(list)) err(settingsFile, `permissions.${key} must be a list`);
    }
    const allow = new Set(s.permissions?.allow ?? []);
    for (const rule of s.permissions?.deny ?? []) if (allow.has(rule)) err(settingsFile, `"${rule}" is both allowed and denied`);
  } catch (e) {
    err(settingsFile, `not valid JSON (${e.message})`);
  }
}

for (const w of warnings) console.log(`WARN  ${w}`);
for (const e of errors) console.log(`ERROR ${e}`);
console.log(`lint-agents: ${agentFiles.length} agents, ${errors.length} errors, ${warnings.length} warnings`);
process.exit(errors.length ? 1 : 0);
