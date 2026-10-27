# Standing rules for every Equisteg product built from this template

## Monorepo layout (do not change the top-level structure)
- `apps/web`    → Next.js (App Router) + React + TypeScript strict + Tailwind. Deployed on Vercel (project Root Directory = `apps/web`). SSR/SSG for every public page (needed for SEO and AdSense).
- `apps/mobile` → React Native + Expo (TypeScript, Expo Router, development builds via EAS — not Expo Go, because AdMob needs native code).
- `apps/api`    → Node.js (Express, or NestJS if the domain is large). Create it ONLY when the product needs a backend that Next.js route handlers in `apps/web` can't cover. Otherwise leave the `.gitkeep`.
- `packages/shared-types` → TypeScript types and zod schemas shared by web, mobile, api (API contracts).
- `packages/api-client`   → typed fetch client used by web and mobile.
- `packages/design-tokens` → (create when needed) colors, spacing, typography used by both Tailwind and React Native.
- `infra/` → env templates (`.env.example` per app), Docker only if `apps/api` is deployed outside Vercel.
- Root uses npm workspaces + Turborepo; root scripts `lint`, `typecheck`, `test`, `build` must run across all workspaces.

## Git workflow (company rules — never break)
- Never commit or push to `main`. Work on `feature/<product-slug>` (or `fix/`, `chore/`).
- Conventional Commits: `feat(web): …`, `feat(mobile): …`, `test: …`, `docs: …`, `chore: …`.
- Finish with a Pull Request into `main` using `.github/PULL_REQUEST_TEMPLATE.md`. Never merge it yourself — a code owner approves and squash-merges.

## Company config
Read `publisher.config.json` for company name, emails, package prefix, publisher IDs, region and existing products.

## Never do
- Never copy another brand's name, logo, characters, screenshots or text.
- Never fake reviews, ratings, install counts or testimonials. Never keyword-stuff or hide text.
- Web ads = AdSense only; mobile ads = AdMob only (`react-native-google-mobile-ads`).
- Never place ads on splash screens, over content, beside navigation/buttons, or mid-task.
- Never commit secrets. Remember `EXPO_PUBLIC_*` and `NEXT_PUBLIC_*` vars are PUBLIC — never put secrets in them.
- Never run load/stress tests against production. Never click live ads.
- Never deploy to production or submit to Play without the user's confirmation.
- Never mark anything done without evidence (command output, file path, URL).

## Always do
- Each product must be clearly distinct from `existingProducts` (name, features, UI, content).
- Google-certified consent (UMP) before ads/analytics in EEA/UK/CH — web and mobile.
- Keep `PROGRESS.md` current; commit after every phase.
- Mark anything needing console access or a real device as **NEEDS HUMAN** with click-by-click steps.
- Verify time-sensitive policy facts (Play target API level, closed-testing tester rule, AdSense/AdMob rules, Expo SDK version) by web search at build time.
- Hosting: ad revenue is commercial use — Vercel Hobby does not allow it; the web project must be on the Pro team.
