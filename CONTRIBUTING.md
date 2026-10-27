# Contributing

## Workflow

1. Pull the latest `main`:
   ```
   git checkout main
   git pull origin main
   ```
2. Create a feature branch:
   ```
   git checkout -b feature/short-description
   ```
   Use `fix/` or `chore/` prefixes where appropriate.
3. Make your changes, commit using [Conventional Commits](https://www.conventionalcommits.org/):
   ```
   git commit -m "feat: add login screen"
   git commit -m "fix: correct token refresh bug"
   ```
4. Push your branch:
   ```
   git push origin feature/short-description
   ```
5. Open a Pull Request into `main`.
   - Fill out the PR template
   - CI (lint, type-check, tests) must pass
   - A code owner for the folders you touched must approve
6. Once approved, a code owner/lead merges using **Squash and merge**.

## Rules

- No direct pushes to `main` — everything goes through a reviewed PR.
- Keep PRs focused and small where possible.
- Never commit secrets — use `.env` files (excluded via `.gitignore`).
- Rotate credentials if a team member leaves.

## Access

See the internal Tools Setup & Team Access Guide for role assignments per tool (GitHub, Vercel, Render, Expo/EAS, Sentry, DB access).
