# Phase 0 — Intake: derive the product brief from the prototype

The user will NOT describe the product. Everything comes from the attached prototype.

1. Copy every input file to `docs/source/` (traceability).
2. Read each input completely.
   - HTML: every screen/section, visible text, forms, buttons, links, `<script>` interactions, colors, fonts, images.
   - Claude artifact (.jsx/.tsx): components, state, handlers, mock data, Tailwind classes (colors/spacing), imported libraries.
   - Images/PDF: describe screens, extract text.
3. Fill `templates/product-brief.md` (this skill folder) → save as `docs/product-brief.md`:
   - Product idea and target users inferred from content and wording.
   - Non-functional requirements → section 4b (NFR-xxx) with measurable thresholds; start from the defaults in the template and tighten where the prototype implies it (heavy media, payments, many users).
   - Every interactive element or screen → at least one REQ-xxx. Hard-coded/mock data implies a real data feature (mark "inferred").
   - **Backend decision** (record the reasoning):
     - No per-user cloud data and no paid features → local-first (IndexedDB on web, SQLite/MMKV on mobile); `apps/api` only serves the static site and health check.
     - Any server need (payments, entitlement, forms, proxies, small CRUD, cron) → `apps/api` Hono Worker on Cloudflare with D1/KV.
     - Heavy/long-running jobs → Cloudflare Queues + Cron first; a separate FastAPI service only if Phase 0b chose it.
   - No sign-up by default. Paid access uses Phase 4b (payment → licence key / magic link). Login only if the product truly needs synced personal data (each login adds Play obligations: account deletion, data safety).
   - Paid boundary: which REQs are free, which are paid, and the SKU type (one-time, pack with credits, time-limited pass, subscription).
   - Region/languages: from prototype text; else `publisher.config.json` defaults.
   - 10 search keywords real users would type.
   - Brand hints: prototype palette and typography.
4. **Category.** Pick the primary category (finance, games, utility, tech) and fill §1b of the brief from `docs/standards/product-categories.md`: rules that apply, extra NFRs and test rows to add, red lines. A red line crossed, or a category not in that file, is a STOP with **NEEDS HUMAN**.
4b. Policy risk check vs Google Play restricted content and AdSense prohibited content. If disallowed, STOP and explain.
5. Originality check vs `existingProducts`; note what must make this one distinct.
6. The prototype is the source of truth for intent and look-and-feel, not code quality. Log every deviation.
