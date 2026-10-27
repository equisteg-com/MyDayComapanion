# Phase 5 — SEO, AI/LLM discoverability, ASO

No one can guarantee #1 rankings; do everything that is in our control, correctly. Applies to `apps/web` unless stated.

## Technical SEO
- All public pages SSR/SSG; content present in raw HTML (verify with `curl`).
- Unique `<title>` (≤ 60 chars) and meta description (≤ 155) per page via Next metadata API; canonical URLs; hreflang if multilingual; one H1, logical headings; descriptive alt text; clean URLs.
- `app/sitemap.ts`, `app/robots.ts`. robots allows Googlebot, Bingbot, Mediapartners-Google, GPTBot, OAI-SearchBot, ClaudeBot, PerplexityBot, Google-Extended.
- Open Graph + Twitter cards; per-page OG images (`next/og`).
- JSON-LD: Organization, WebSite (+SearchAction if search exists), SoftwareApplication (web) + MobileApplication (Play link), FAQPage, BreadcrumbList, HowTo/Article where genuine. Validate every block.
- Core Web Vitals: LCP < 2.5s, INP < 200ms, CLS < 0.1.
- IndexNow key file + ping script to run after production deploy.
- Smart app banner / "Get the app" link to the Play listing (not intrusive).

## AI / LLM discoverability
- `public/llms.txt` (product summary, features, key links) and `public/llms-full.txt` (plain Markdown of key content).
- **Cloudflare must not block AI search crawlers.** Cloudflare can block AI bots by default on new domains (with stricter defaults on pages that show ads). NEEDS HUMAN: Cloudflare dashboard → the domain → Security → Settings → AI bots / AI Crawl Control → allow search crawlers (OAI-SearchBot, PerplexityBot, Claude-SearchBot, Googlebot, Bingbot); decide separately whether training bots (GPTBot, ClaudeBot, Google-Extended) are allowed; keep Cloudflare's managed robots.txt off or consistent with `app/robots.ts`. Verify with `curl -A "OAI-SearchBot" -I <url>` → 200.
- Bing Webmaster Tools verification + sitemap (Bing's index feeds Copilot and ChatGPT search) and IndexNow ping after each production deploy.
- Entity consistency: the same name, one-line description, logo and links in Organization JSON-LD, the Play listing, `llms.txt`, About page and social profiles.
- Track AI referrals: Cloudflare Web Analytics referrers from chatgpt.com, perplexity.ai, copilot.microsoft.com, gemini.google.com; record monthly in `docs/seo-report.md`.
- Answer-first writing: each guide opens with a 2–3 sentence factual answer; definitions, lists, tables, FAQs that assistants can quote; product name used naturally.
- Identical entity facts everywhere (name, description, category, website, Play link).

## Play Store ASO — `release/listing/`
Title ≤ 30 chars, short description ≤ 80, full description ≤ 4000 (natural keywords, no stuffing, no competitor names, no "#1/best" claims). Screenshot plan: 4–8 phone + 7" + 10" tablet captured from the real app (Maestro `takeScreenshot` on emulators).

## NEEDS HUMAN
Verify domain in Google Search Console and Bing Webmaster Tools, submit sitemap, put the website URL in the Play listing.
