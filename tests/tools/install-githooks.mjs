#!/usr/bin/env node
// Runs on `npm install` (root "prepare" script): points git at .githooks/ so the pre-commit secret check runs.
// Safe everywhere: does nothing outside a git checkout (CI tarballs, Docker builds) and never fails the install.
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';

try {
  if (existsSync('.git') && existsSync('.githooks/pre-commit')) {
    execFileSync('git', ['config', 'core.hooksPath', '.githooks'], { stdio: 'ignore' });
  }
} catch {
  // git missing or not a repo: skip quietly
}
