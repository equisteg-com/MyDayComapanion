# Phase 0 — Intake: derive the product brief from the prototype

The user will NOT describe the product. Everything comes from the attached prototype.

1. Copy every input file to `docs/source/` (traceability).
2. Read each input completely.
   - HTML: every screen/section, visible text, forms, buttons, links, `<script>` interactions, colors, fonts, images.
   - Claude artifact (.jsx/.tsx): components, state, handlers, mock data, Tailwind classes (colors/spacing), imported libraries.
   - Images/PDF: describe screens, extract text.
3. Fill `templates/product-brief.md` (this skill folder) → save as `docs/product-brief.md`:
   - Product idea and target users inferred from content and wording.
   - Every interactive element or screen → at least one REQ-xxx. Hard-coded/mock data implies a real data feature (mark "inferred").
   - **Backend decision** (record the reasoning):
     - No per-user cloud data → local-first (IndexedDB on web, SQLite/MMKV on mobile), no `apps/api`.
     - Light server needs (forms, proxies, small CRUD) → Next.js route handlers in `apps/web` on Vercel, no `apps/api`.
     - Heavy/long-running backend, websockets, jobs → `apps/api` (Express/NestJS).
   - Login only if the product truly needs synced personal data (each login adds Play obligations: account deletion, data safety).
   - Region/languages: from prototype text; else `publisher.config.json` defaults.
   - 10 search keywords real users would type.
   - Brand hints: prototype palette and typography.
4. Policy risk check vs Google Play restricted content and AdSense prohibited content. If disallowed, STOP and explain.
5. Originality check vs `existingProducts`; note what must make this one distinct.
6. The prototype is the source of truth for intent and look-and-feel, not code quality. Log every deviation.
