---
name: market
description: Plans and drafts zero-cost marketing for an Equisteg product - positioning, launch plan, content calendar from search keywords, store listing refresh, social and community post drafts, opt-in email, and a monthly growth report - then has the drafts checked for truthful, allowed claims. Drafts only; never posts or sends anything. Run only when the user types /market.
argument-hint: "[launch | content | listing | social | email | report | <goal in plain English>]"
---

# /market — get the product in front of the right people, for ₹0

Arguments: `$ARGUMENTS`

Everything goes in `docs/marketing/` on the user's current branch and ends in a PR into `development`, like any other change. **Claude never posts, publishes, emails, comments or messages anyone outside this repo.** It writes drafts; a founder publishes them. Paid ads, paid tools and paid placements are **NEEDS HUMAN** with their cost (zero-cost rule).

## 0. Before anything
1. `git branch --show-current`. On `main`, `development`, `staging` or `production`, stop and ask the user to create their branch: `git switch development && git pull && git switch -c update/marketing-<topic>`. Never create or switch branches yourself.
2. Read `CLAUDE.md`, `docs/product-brief.md` (users, REQs, keywords, category), `docs/competitor-analysis.md`, `docs/brand.md`, `docs/seo-report.md` if present, `release/listing/`, `publisher.config.json`, and the category section of `docs/standards/product-categories.md`.
3. Only claim what is built: list the REQs that are live (from `CHANGELOG.md` and the latest `docs/test-reports/`). Features on a branch or behind a kill switch are "coming soon" or left out.
4. If the product is not live yet, plan for launch day and mark every link that does not exist yet as `<pending: …>`.

## 1. Positioning — `docs/marketing/positioning.md` (create once, update when the product changes)
- Who it is for (one sentence, from the brief), the problem in their words (paraphrased from competitor reviews in phase 1), and the one outcome we promise.
- Three proof points, each tied to a REQ or a measured number (test report, Lighthouse score, offline support).
- How we differ from the top three competitors, factual and dated.
- Words to use and words to avoid (category rules: no advice language for finance, no pressure for games aimed at children, no "nothing is uploaded" unless true for every tool).
- The one-line description used everywhere (must match About page, JSON-LD and `llms.txt`).

## 2. What each argument produces
| Argument | Output in `docs/marketing/` |
|---|---|
| `launch` (default for a product with no marketing yet) | `launch-plan.md`: four weeks around launch day — before (listing ready, 15–30 content pages live, Search Console and Bing verified, social profiles with consistent name and logo), launch day (where to post, in what order, drafts for each), after (respond to reviews, fix top complaints, first content pieces). Each step has an owner (founder or Claude) and a done-when line. |
| `content` | `content-calendar.md`: 12 weeks of guides, how-tos and comparison pages built from the brief's keywords and real questions people ask (search suggestions, forums), one search intent per page, answer-first outline, internal links, the REQ each page shows off. Pages are then built with `/update` like any feature. |
| `listing` | `listing-refresh.md`: new title, short and full description, screenshot captions and feature-graphic text for Play, with the reason for every change and the keyword it targets; nothing applied to `release/listing/` until the founder agrees. |
| `social` | `social/<YYYY-MM>.md`: posts per channel the founders actually use (for example X, LinkedIn, Instagram, YouTube Shorts, Reddit, relevant forums), written for that channel's culture, each with the image or clip to make, the UTM link and the community's self-promotion rule it follows. |
| `email` | `email/<name>.md`: welcome, receipt follow-up and renewal reminder copy for opt-in buyers only (sender Equisteg, unsubscribe line), consistent with phase 4b's transactional emails. No cold email. |
| `report` | `reports/<YYYY-MM>.md`: last month's numbers from free sources the founder exports or Claude can read (Cloudflare Web Analytics, Search Console, Play Console, AdSense/AdMob, AI-assistant referrers from phase 5): visitors, top pages and queries, installs, conversion to paid, revenue per user, what worked, what to change, three actions for next month. Numbers are copied, never estimated; missing ones are listed as NEEDS HUMAN exports. |
| plain-English goal | Pick the outputs above that serve it and say which in one line. |

## 3. Rules for every draft
- Written for the reader's problem first, product second; plain language; one call to action.
- Every link has UTM parameters (`utm_source`, `utm_medium`, `utm_campaign`) and points at the product's own domain.
- No invented numbers, reviews, testimonials, user counts or press mentions. No competitor logos or screenshots.
- Images and videos are made from the real product (Maestro or Playwright screenshots) and brand assets in `brand/`; no stock images of people presented as users.
- Free tools only (Canva free, CapCut free, Buffer free tier, etc. — check the current free limits by web search and record them); anything paid is NEEDS HUMAN.
- For each channel, check its current self-promotion and automation rules by web search before drafting, and note the rule next to the draft.

## 4. Review and finish
1. Run `agent:marketing-claims-reviewer` on everything written or changed, plus `agent:seo-ai-reviewer` for `content`, `agent:store-policy-reviewer` for `listing`, and `agent:category-reviewer` when the product has a category. Fix S1–S3, at most three rounds (`docs/standards/agent-reviews.md`).
2. Add a `CHANGELOG.md` row (`MKT-xxx`), commit `docs(marketing): <what>`, push the branch (permission prompt) and open a PR into `development` with the *Specialist review* section filled. Do not merge.
3. Print: what was written (file list), the top three actions for a founder this week with the exact place to post or click, the reviewer verdicts, and NEEDS HUMAN items (accounts to create, exports to download, anything with a cost).
