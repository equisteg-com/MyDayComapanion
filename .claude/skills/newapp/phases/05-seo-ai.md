# Phase 5 — SEO, AI/LLM discoverability, ASO

No one can guarantee #1 rankings; do everything that is in our control, correctly. Applies to `apps/web` unless stated.

## Technical SEO
- All public pages SSR/SSG; content present in raw HTML (verify with `curl`).
- Unique `<title>` (≤ 60 chars) and meta description (≤ 155) per page via Next metadata API; canonical URLs; hreflang if multilingual; one H1, logical headings; descriptive alt text; clean URLs.
- `app/sitemap.ts`, `app/robots.ts`. robots allows Googlebot, Bingbot, Mediapartners-Google, GPTBot, OAI-SearchBot, ClaudeBot, PerplexityBot, Google-Extended.
- Open Graph + Twitter cards; per-page OG images (`next/og`).
- JSON-LD: Organization = **Equisteg** (`url` https://equisteg.com, logo, `sameAs` company profiles) referenced as `publisher` of the WebSite and as `author`/`publisher` of SoftwareApplication/MobileApplication; the product itself is the WebSite/Application entity. Then WebSite (+SearchAction if search exists), SoftwareApplication (web) + MobileApplication (Play link), FAQPage, BreadcrumbList, HowTo/Article where genuine. Validate every block.
- Core Web Vitals: LCP < 2.5s, INP < 200ms, CLS < 0.1.
- IndexNow key file + ping script to run after production deploy.
- Smart app banner / "Get the app" link to the Play listing (not intrusive).

## AI / LLM discoverability
- `public/llms.txt` (product summary, features, key links) and `public/llms-full.txt` (plain Markdown of key content).
- **Cloudflare must not block AI search crawlers.** Cloudflare can block AI bots by default on new domains (with stricter defaults on pages that show ads). NEEDS HUMAN: Cloudflare dashboard → the domain → Security → Settings → AI bots / AI Crawl Control → allow search crawlers (OAI-SearchBot, PerplexityBot, Claude-SearchBot, Googlebot, Bingbot); decide separately whether training bots (GPTBot, ClaudeBot, Google-Extended) are allowed; keep Cloudflare's managed robots.txt off or consistent with `app/robots.ts`. Verify with `curl -A "OAI-SearchBot" -I <url>` → 200.
- Bing Webmaster Tools verification + sitemap (Bing's index feeds Copilot and ChatGPT search) and IndexNow ping after each production deploy.
- `llms.txt` states "<Product> is built and run by Equisteg (https://equisteg.com)".
- Entity consistency: the same name, one-line description, logo and links in Organization JSON-LD, the Play listing, `llms.txt`, About page and social profiles.
- Track AI referrals: Cloudflare Web Analytics referrers from chatgpt.com, perplexity.ai, copilot.microsoft.com, gemini.google.com; record monthly in `docs/seo-report.md`.
- Answer-first writing: each guide opens with a 2–3 sentence factual answer; definitions, lists, tables, FAQs that assistants can quote; product name used naturally.
- Identical entity facts everywhere (name, description, category, website, Play link).

## Play Store ASO — `release/listing/`
Title ≤ 30 chars, short description ≤ 80, full description ≤ 4000 (natural keywords, no stuffing, no competitor names, no "#1/best" claims). Screenshot plan: 4–8 phone + 7" + 10" tablet captured from the real app (Maestro `takeScreenshot` on emulators).

## NEEDS HUMAN
For the product's own domain: Search Console → Add property → **Domain** → `<domain>` → add the TXT record in Cloudflare DNS → Verify; submit `https://<domain>/sitemap.xml`. Bing Webmaster Tools → import from Search Console. Put `https://<domain>` as the website in the Play listing.

## Proven practice (from the first product)
- **Copy the reference files** from `.claude/skills/newapp/templates/reference/`: `apps/web/scripts/seo-audit.mjs`, `apps/web/lib/seo.ts`, `apps/web/scripts/indexnow.mjs`. Add `"seo:audit": "node scripts/seo-audit.mjs"` to `apps/web/package.json`; `ci.yml` and `deploy.yml` already call `npm run seo:audit --workspaces --if-present` after the build.
- **Titles and descriptions are computed, not hand-trimmed:** `fitTitle([...candidates])` / `fitDescription([...])` pick the most descriptive candidate that fits (title ≤ 60 with the brand suffix, description 50–155). The audit fails the build on any page outside the limits, duplicated, without canonical, H1, og:image or valid JSON-LD.
- **dev and staging are noindex on purpose** (`NEXT_PUBLIC_NOINDEX=1` set by `deploy.yml`: robots meta `noindex` on every page and `Disallow: /` in robots.txt). The audit therefore has two modes — on a noindex build it checks that *every* page is noindex and robots blocks crawling, and skips the indexable-page checks. **Run both modes locally before the PR** (`npm run build && npm run seo:audit`, then the same with `NEXT_PUBLIC_NOINDEX=1`): the first product's dev deploy failed after merge because the audit had only ever run on an indexable build.
- **`llms.txt` / `llms-full.txt` as route handlers** (`app/llms.txt/route.ts` with `export const dynamic = 'force-static'`) generated from the same data as the pages, so they never drift; plain files in `public/` only when the content is fully static.
- **OG images:** `opengraph-image.tsx` per route group with `next/og`; they are pre-rendered by the static export. Keep fonts local (no fetch at build time).
- **IndexNow:** key file `apps/web/public/<32 hex>.txt` containing the key; `deploy.yml` runs `indexnow.mjs` only on production and only when the script exists.
- **Lighthouse SEO on staging:** the SEO category would fail on `is-crawlable` there; `tests/load/lighthouserc.json` asserts the individual SEO audits instead. Check the production URL with PageSpeed Insights after launch (NEEDS HUMAN).
