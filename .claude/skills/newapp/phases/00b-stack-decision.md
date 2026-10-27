# Phase 0b — Brief the user and confirm the stack (the one required question)

## 1. Brief
Print a brief of the input in at most 10 lines: product idea, target users, screens found, REQ count, backend needed (yes/no and why), paid features, ads, risks.

## 2. Score the stack options for THIS product
Score each option 1–5 on: output quality on web, output quality on mobile, load it can take on the free tier, scale-up path, security, ease of finding and fixing errors, team skill fit (TypeScript-first team), cost (must be ₹0). Use current facts (web-search versions and free limits).

| Option | Web | Mobile | API | Notes to evaluate |
|---|---|---|---|---|
| **A. TypeScript everywhere (recommended default)** | Next.js static export on Cloudflare | React Native + Expo | Hono on Cloudflare Workers + D1 | One language, shared zod types web/mobile/api, best SEO, largest ecosystem |
| B. Flutter | Flutter web | Flutter | Hono on Workers | One UI codebase; weak SEO on web (canvas), larger web bundle |
| C. Native mobile | Next.js | Kotlin (Android) + Swift (iOS) | Hono on Workers | Best device performance; two extra codebases, slowest to build |
| D. Python backend | Next.js | React Native + Expo | FastAPI (needs a free host; cold starts) | Only if heavy Python-only libraries are essential |

Load guidance to state honestly: on Cloudflare Workers Free the API takes 100,000 requests/day with 10 ms CPU each and static pages are served from the edge; moving to Workers Paid lifts this without code changes. No option can promise "unlimited" or "100% secure".

## 3. Ask
Use the AskUserQuestion tool (one question) with the options above, the scores table, and Option A marked "Recommended". In autonomous mode with no answer available, take Option A and record it as an assumption.

## 4. Save
`docs/stack-decision.md`: the brief, the scores table, the choice, who chose it and when, and what would trigger a change (for example sustained traffic above the free tier).
