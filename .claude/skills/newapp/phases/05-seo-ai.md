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
- Answer-first writing: each guide opens with a 2–3 sentence factual answer; definitions, lists, tables, FAQs that assistants can quote; product name used naturally.
- Identical entity facts everywhere (name, description, category, website, Play link).

## Play Store ASO — `release/listing/`
Title ≤ 30 chars, short description ≤ 80, full description ≤ 4000 (natural keywords, no stuffing, no competitor names, no "#1/best" claims). Screenshot plan: 4–8 phone + 7" + 10" tablet captured from the real app (Maestro `takeScreenshot` on emulators).

## NEEDS HUMAN
Verify domain in Google Search Console and Bing Webmaster Tools, submit sitemap, put the website URL in the Play listing.
