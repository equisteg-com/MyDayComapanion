# Product categories — extra rules for finance, games, utility and tech products

Every product picks **one primary category** in phase 0 (`docs/product-brief.md` §1b) and follows that section below on top of every other company rule. A product that clearly spans two (for example a budgeting game) follows both, and the stricter rule wins. `agent:category-reviewer` checks the section that applies.

Store and ad policies change. Rules here say *what to check*; facts with numbers or dates (declarations, age ratings, required disclosures) are verified by web search at build time and the source URL is recorded in `docs/product-brief.md`.

---

## 1. Finance (calculators, budgeting, trackers, tax and investment information, converters)

**Red lines — STOP and report NEEDS HUMAN**
- Lending, credit lines, crypto exchanges or wallets, trading, money transfer, insurance sales, or anything that holds or moves users' money. These need licences and Play's financial-services declarations; they are outside the template.
- Personalised investment advice or "tips" (in India this is regulated investment-advisory work). Educational and calculation tools are fine.

**Build rules**
- **Money maths:** never binary floating point for amounts. Use integer minor units (paise/cents) or a decimal library; round only at display, with the rounding rule written in `docs/LLD.md`. Rates and percentages carry their precision explicitly.
- **Formulas are sourced:** every financial formula (EMI, SIP, CAGR, compound interest, tax slab) cites its source in a code comment and in `docs/LLD.md`; tax rules carry the financial year and the date they were last checked, and a yearly re-check is a row in `docs/risk-register.md`.
- **Golden tests:** each formula has a table of reference cases checked against an independent source (bank/regulator calculator, published examples), including edge cases (0 %, 0 tenure, very large amounts, leap years). These are P1 test cases.
- **Disclaimer** on every result screen and page: information only, not financial, tax or legal advice; figures are estimates; check with a qualified professional. Not hidden, not in grey 10 px text.
- **Personal financial data** (income, balances, holdings) stays on the device by default (local-first). If it must reach the server, it is encrypted at rest, never logged, never sent to analytics or ad SDKs, and listed in data safety.
- **Ads:** no ads that look like part of the result; no ads inside the calculation flow; financial-services ads are allowed by AdSense/AdMob only within their policy — never write copy that endorses an advertiser.
- **SEO:** finance pages are "Your Money or Your Life" content: author/publisher is Equisteg with an About and editorial-policy page, date updated on every guide, sources linked, no guaranteed-returns language.

**Extra tests (Non-functional sheet)**: golden-case accuracy (100 % match), rounding consistency web vs Android, disclaimer visible on every result, no financial input in logs/analytics (Privacy).

---

## 2. Games (casual, puzzle, word, trivia, idle, educational)

**Red lines — STOP and report NEEDS HUMAN**
- Real-money gambling, betting, or anything where money or items with real-world value can be won. Simulated casino games need special Play rules and an 18+ rating; avoid them.
- Loot boxes or random paid rewards without published odds.

**Build rules**
- **Audience:** decide in phase 0 whether children are in the audience. If yes (or the art style appeals to children), Play Families policy applies: only Families-certified ad SDKs, child-directed ad requests (`tagForChildDirectedTreatment`), no personalised ads, no external links without a parent gate, no data collection beyond what is needed.
- **Fair monetisation:** prices shown in local currency before purchase; no dark patterns (fake timers, confusing currencies, "are you sure you want to lose your progress?" pressure to buy). Rewarded ads are opt-in only and give exactly what they promise. Interstitials only between levels, frequency capped.
- **Performance:** 60 fps target on a mid-range device for the main loop; no jank on level load; memory stable over a 10-minute play loop (Maestro). Web games keep first load small; heavy assets load after the first screen.
- **Save data:** progress saved locally after every level and survives app kill; restore of purchases works (phase 4b); clearing data is explained.
- **Accessibility:** colour is never the only cue; adjustable or no time limits where possible; sound and haptics can be turned off; large touch targets.
- **Originality:** no characters, names, sounds or art from other games, films or brands; game mechanics may be common, presentation must be ours.
- **Leaderboards / user content:** only with moderation and report/block tools; otherwise leave them out of scope.

**Extra tests**: fps/jank on the emulator profile, save/restore after kill, ad frequency cap, child-directed flags when applicable, purchase restore.

---

## 3. Utility (converters, timers, scanners, file tools, generators, everyday helpers)

**Build rules**
- **Permissions minimalism:** request a permission only at the moment the feature needs it, explain why first, and keep working (with a fallback) when it is denied. Never ask for SMS, call log, contacts, all-files access, accessibility service, location in the background or "query all packages" — they trigger Play declarations and rejections. Justify every permission in `docs/LLD.md`; block the rest in `app.config.ts`.
- **Do the work on the device:** files, images and documents are processed locally (browser or phone) unless the brief says otherwise; nothing a user opens is uploaded without a clear notice. This is both a privacy promise and a zero-cost rule.
- **Offline first:** the core tool works with no network; the UI says so.
- **Accuracy:** units, conversions and generated outputs have reference tests (like finance golden tests); file outputs open in the target app (PDF opens, CSV parses, image dimensions correct).
- **Background work:** timers and reminders use local notifications and exact-alarm rules only if essential (Play declaration); battery use stays low.
- **Distinct value:** utilities are crowded and Play rejects near-duplicates. Phase 1 must name at least three differentiators from real user complaints, and the product must not be a thin wrapper of a website or of another Equisteg product.

**Extra tests**: permission denied path for every permission, offline core flow, output files validated, large-file limits (no crash, clear message).

---

## 4. Tech and developer tools (formatters, validators, encoders, API/regex/JSON tools, AI helpers)

**Build rules**
- **Users paste sensitive things** (tokens, JSON with personal data, code). Process them in the browser by default and say so on the page ("Runs in your browser; nothing is uploaded"). If a feature needs the server, it never logs bodies, keeps nothing after the response, and the page says exactly what is sent.
- **Never execute what users paste** on our server (no `eval`, no running code, no fetching arbitrary URLs — that is server-side request forgery). URL-fetching features need an allow-list, `https:` only, size and time limits, and a block on private IP ranges.
- **Safe rendering:** pasted HTML/Markdown/SVG is shown escaped or in a sandboxed iframe; never `dangerouslySetInnerHTML` with user input.
- **AI features:** follow the AI rules in `agent:security-auditor` — user text is never part of the system prompt, model output never grants access or changes prices, usage is rate-limited and capped to stay inside free tiers; label AI output as AI-generated and possibly wrong.
- **Correctness is the product:** each tool has a reference test set (valid, invalid, edge, very large input) and shows clear error positions for invalid input.
- **Content:** tutorials and reference pages are the SEO engine — accurate, runnable examples, versions stated, no copied documentation.

**Extra tests**: no request body in logs (Privacy), pasted-script XSS attempts rendered inert, SSRF attempts blocked (`http://169.254.169.254`, `http://localhost`), large input limits, AI rate limit → 429.

---

## 5. Applying it
- Phase 0 records the category and the rules that apply; phase 1 uses them in scope; phase 6 and 7 add the extra security and test rows; phase 10's compliance matrix has a *Category rules* block.
- `/change` and `/update` re-check the category rules for what they touch.
- A new category (health, education for children, news, social) is **NEEDS HUMAN** before building: those carry their own Play and legal rules and need a new section here first.
