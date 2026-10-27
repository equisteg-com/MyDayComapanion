# Phase 4b — Payments, access, usage and auto-revoke WITHOUT sign-up

Goal: a customer pays without creating an account, gets access within seconds, can restore it on another device, is limited to what they paid for, and loses access automatically when it expires or is refunded. Skip this phase only if the product has no paid features (record that in PROGRESS.md).

If the company platform Worker `equisteg-platform` already exists, call it through a service binding and only add the product's SKUs. Otherwise build it in `apps/api` exactly as below so it can be moved later.

## 1. Trust rules
- Browser and app are untrusted. Prices, SKUs and durations live in server config (`apps/api/src/config/skus.ts`), never in the request.
- Access is granted ONLY by a signature-verified payment webhook that the server re-confirms with the provider API (status = captured/paid, amount and currency match the SKU).
- A success redirect, query string, `localStorage`, a mobile flag or an LLM can never grant access.
- Every premium request is checked on the server against D1. Tokens are short-lived so revocation takes effect quickly.

## 2. Purchase flow (web: Razorpay or Stripe hosted checkout; Android: Google Play Billing)
1. Client creates or reads an anonymous `device_id` (random UUID): web → `HttpOnly; Secure; SameSite=Lax` cookie set by the API; mobile → `expo-secure-store`.
2. `POST /api/v1/checkout { sku }` (Turnstile token required on web) → server creates the provider order with the server price, stores `orders(id, sku, device_id, status='created')`, returns the hosted checkout details. The provider collects the buyer's email/phone; we never ask for a password.
3. Provider → `POST /api/v1/webhooks/payment`:
   - verify the signature with `PAYMENT_WEBHOOK_SECRET` (constant-time compare) on the raw body;
   - reject replays: `INSERT INTO webhook_events(event_id) …` must succeed (unique key);
   - fetch the payment from the provider API and check status, amount, currency, order id;
   - create the entitlement and licence key (section 4), mark the order `paid`, email the receipt + licence key via Resend — sent from the company domain (`<Product> <no-reply@equisteg.com>`, reply-to `support@<domain>`), because Resend free verifies only 3 domains.
4. Client polls `GET /api/v1/checkout/status?order=<id>` (max 60 s). When the order is `paid` AND the request's `device_id` matches the order, the server binds the device and returns an access token.
5. **Android in-app purchases follow Google Play's Payments policy**: digital features bought inside the Play app use Google Play Billing (`react-native-iap`), and the purchase token is verified server-side with the Google Play Developer API before the same entitlement is created. Do not link Android users to web checkout from inside the app. Web-check the current policy (including India user-choice billing) at build time and record it.

## 3. Tokens and devices
- Access token: HMAC-SHA256 signed (key `SESSION_SIGNING_KEY`), claims `{ ent, dev, exp }`, lifetime 24 h. Web: `HttpOnly; Secure; SameSite=Strict` cookie. Mobile: `expo-secure-store`.
- Silent refresh: `POST /api/v1/access/refresh` re-checks D1 and issues a new token only if the entitlement is still active and the device is still bound.
- Device limit per SKU (default 3). Binding a 4th device unbinds the oldest and notifies the buyer by email.

## 4. Restore on a new device (still no sign-up)
- **Licence key:** 25-character key shown once on the success screen and in the receipt email. Store only `SHA-256(key + pepper)`. `POST /api/v1/access/restore { key }` (Turnstile, 5 tries per IP per hour).
- **Magic link:** `POST /api/v1/access/email-link { email }` always answers "If a purchase exists, we sent a link" (no account enumeration). Link token: 32 random bytes, stored hashed, valid 15 minutes, single use.
- Emails are stored encrypted (AES-GCM, key `EMAIL_ENC_KEY`) plus a peppered SHA-256 hash for lookup. Delete them on request or 90 days after the last entitlement ends.
- **Data deletion (Play + privacy law):** `POST /api/v1/data/delete-request { email }` sends a single-use link (same rules as the magic link); confirming it deletes the email, devices and usage events and anonymises orders kept for tax records. Reachable from the web deletion page and the app's *Delete my data* (audit B4).

## 5. Usage metering
- Packs and credits: `UPDATE entitlements SET used = used + 1 WHERE id = ? AND used < quota AND status = 'active' AND (expires_at IS NULL OR expires_at > ?)` — the row count decides whether the action runs (atomic, no double-spend). Log `usage_events(ent_id, action, at)`.
- Show remaining credits and expiry date in the UI from `GET /api/v1/access/me`.

## 6. Expiry and auto-revoke
- Real time: every premium check compares `expires_at` and `status` in D1, so access stops the moment it expires.
- Refund, chargeback, cancellation webhooks set `status = 'revoked'` or set `expires_at` to period end.
- Daily job at 01:00 UTC in `apps/api`: mark expired rows, unbind their devices, send a renewal reminder 3 days before expiry, purge old webhook events and expired link tokens. It runs inside the product's single Cron Trigger `*/5 * * * *` (the same trigger runs the phase 9b watchdog) — in `scheduled()`, run the daily jobs only when the scheduled time is 01:00–01:04 UTC; record each run in D1 `job_runs`.
- When access ends, the client returns to the free state and may load ads again (ads load only after the entitlement check says "free").

## 7. D1 schema — `apps/api/migrations/0001_payments.sql`
```sql
CREATE TABLE orders (id TEXT PRIMARY KEY, sku TEXT NOT NULL, device_id TEXT NOT NULL, provider_order_id TEXT UNIQUE, status TEXT NOT NULL, created_at INTEGER NOT NULL);
CREATE TABLE entitlements (id TEXT PRIMARY KEY, sku TEXT NOT NULL, email_hash TEXT NOT NULL, email_enc TEXT NOT NULL, licence_hash TEXT UNIQUE NOT NULL, provider TEXT NOT NULL, provider_ref TEXT UNIQUE NOT NULL, status TEXT NOT NULL CHECK (status IN ('active','expired','revoked')), quota INTEGER, used INTEGER NOT NULL DEFAULT 0, starts_at INTEGER NOT NULL, expires_at INTEGER, device_limit INTEGER NOT NULL DEFAULT 3);
CREATE INDEX idx_ent_email ON entitlements(email_hash);
CREATE INDEX idx_ent_expiry ON entitlements(status, expires_at);
CREATE TABLE devices (ent_id TEXT NOT NULL, device_id TEXT NOT NULL, bound_at INTEGER NOT NULL, PRIMARY KEY (ent_id, device_id));
CREATE TABLE usage_events (ent_id TEXT NOT NULL, action TEXT NOT NULL, at INTEGER NOT NULL);
CREATE TABLE webhook_events (event_id TEXT PRIMARY KEY, received_at INTEGER NOT NULL);
CREATE TABLE magic_links (token_hash TEXT PRIMARY KEY, email_hash TEXT NOT NULL, expires_at INTEGER NOT NULL, used INTEGER NOT NULL DEFAULT 0);
```

## 8. Required tests (add to tests/cases and automation)
Webhook with bad signature → 401; replayed event → ignored; amount mismatch → no access; success URL opened without payment → no access; token tampered → 401; token expired → refresh only if active; credits at quota → blocked; concurrent use of last credit → only one succeeds; refund webhook → access gone on next request; cron expiry → devices unbound; 4th device → oldest unbound; magic link reused → rejected; licence brute force → rate limited; paid user → no ad script requested; Android purchase token invalid → no access.
