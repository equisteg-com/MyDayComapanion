#!/usr/bin/env node
// Claude Code PreToolUse hook (registered in .claude/settings.json).
// Blocks a tool call that would read, print or send a secret, even when a prompt hidden in a file,
// issue, web page or prototype tries to make Claude do it. Exit 2 = blocked (the reason goes back to Claude).
// It is a safety net on top of the deny list in settings.json, not a replacement for it.

import { isSecretPath, findSecretValue } from '../../tests/tools/secret-patterns.mjs';

const block = (reason) => {
  process.stderr.write(`Blocked by .claude/hooks/guard-secrets.mjs: ${reason}. Secrets stay out of chat, logs and network calls (docs/standards/repo-security.md). Give the user the command to run themselves instead.\n`);
  process.exit(2);
};

// Words in a shell command that may be file paths.
const words = (cmd) => cmd.split(/[\s|;&<>()`'"=]+/).filter(Boolean);

export function checkBash(cmd) {
  if (!cmd) return null;
  for (const w of words(cmd)) if (isSecretPath(w)) return `the command touches a secret file (${w})`;
  const value = findSecretValue(cmd);
  if (value) return `the command contains what looks like a ${value}`;

  // Dumping the environment prints every secret the shell holds.
  if (/(^|[;&|]\s*)(printenv|env|export -p|set)\s*($|[;&|>])/.test(cmd)) return 'the command prints all environment variables';
  if (/\$\{?(\w*(TOKEN|SECRET|PASSWORD|API_KEY|PRIVATE_KEY)\w*)\}?/i.test(cmd)) {
    if (/\b(echo|printf|cat|tee)\b/.test(cmd)) return 'the command would print a secret environment variable';
    // Sending a token to the local Worker (wrangler dev, game day) is fine; anywhere else is not.
    if (/\b(curl|wget)\b/.test(cmd) && !/https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?\//.test(cmd)) return 'the command would send a secret environment variable over the network';
  }

  // Reading stored credentials.
  if (/\bgit\s+credential\s+(fill|get)\b/.test(cmd)) return 'the command reads stored git credentials';
  if (/\bgh\s+auth\s+token\b/.test(cmd)) return 'the command prints the GitHub CLI token';
  if (/\bwrangler\s+secret\s+(list|get)\b.*--(json|show)/.test(cmd)) return 'the command reads Worker secrets';

  // Sending local files out, or running code fetched from the internet.
  if (/\b(curl|wget|http|httpie)\b/.test(cmd)) {
    if (/(\s-d|\s--data(-binary|-raw|-urlencode)?|\s-F|\s--form)\s*=?\s*['"]?[^'"\s]*@/.test(cmd) || /\s(-T|--upload-file|--post-file)\b/.test(cmd))
      return 'the command uploads a local file';
    if (/\|\s*(sudo\s+)?(ba|z|da)?sh\b|\|\s*(node|python3?|pwsh|powershell)\b/.test(cmd)) return 'the command runs a script downloaded from the internet';
  }
  if (/(^|[;&|]\s*)(nc|ncat|netcat|scp|sftp|ftp|telnet)\s/.test(cmd)) return 'the command opens a raw network transfer';
  return null;
}

export function checkTool(name, input = {}) {
  if (name === 'Bash') return checkBash(input.command);
  const paths = [input.file_path, input.path, input.notebook_path, input.pattern].filter(Boolean);
  if (['Read', 'Edit', 'Write', 'MultiEdit', 'NotebookEdit'].includes(name)) {
    for (const p of paths) if (isSecretPath(p)) return `${name} on a secret file (${p})`;
  }
  if (name === 'Grep' || name === 'Glob') {
    for (const p of [input.path, input.glob].filter(Boolean)) if (isSecretPath(p)) return `${name} inside a secret file (${p})`;
  }
  if (['Write', 'Edit', 'MultiEdit'].includes(name)) {
    const value = findSecretValue(`${input.content ?? ''}\n${input.new_string ?? ''}\n${(input.edits ?? []).map((e) => e.new_string).join('\n')}`);
    if (value && !/\.(test|spec)\.[jt]s$|secret-patterns\.mjs$/.test(input.file_path ?? '')) return `writing what looks like a ${value} into a file`;
  }
  if (name === 'WebFetch' || name === 'WebSearch') {
    const value = findSecretValue(`${input.url ?? ''} ${input.query ?? ''} ${input.prompt ?? ''}`);
    if (value) return `sending what looks like a ${value} to the internet`;
  }
  return null;
}

// Run as a hook: read the tool call from stdin.
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('guard-secrets.mjs')) {
  let raw = '';
  process.stdin.setEncoding('utf8');
  process.stdin.on('data', (c) => (raw += c));
  process.stdin.on('end', () => {
    let call;
    try {
      call = JSON.parse(raw || '{}');
    } catch {
      process.exit(0); // not a tool call we understand; the deny list in settings.json still applies
    }
    const reason = checkTool(call.tool_name, call.tool_input);
    if (reason) block(reason);
    process.exit(0);
  });
}
