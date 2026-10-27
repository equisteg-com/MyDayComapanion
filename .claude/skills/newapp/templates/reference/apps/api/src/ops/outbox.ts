// Emails that could not be sent right away are queued in D1 `outbox` (payload encrypted) and retried by the 5-minute
// cron with backoff: 5, 10, 20 … minutes, up to 8 tries (~21 h). The watchdog reports anything stuck.
import type { Env } from '../env.ts';
import { decrypt, encrypt } from '../lib/crypto.ts';
import { sendEmail } from '../lib/email.ts';
import { withBreaker } from './resilience.ts';

export const MAX_ATTEMPTS = 8;
const backoff = (attempts: number) => 5 * 60_000 * 2 ** Math.max(0, attempts - 1);

/** Sends now through the breaker; on failure queues it. Never throws (the user flow must not fail on email). */
export async function sendEmailReliably(env: Env, to: string, subject: string, text: string, now = Date.now()): Promise<'sent' | 'queued' | 'skipped'> {
  if (!env.RESEND_API_KEY) return 'skipped';
  try {
    const ok = await withBreaker(env, 'email', () => sendEmail(env, to, subject, text), now);
    if (ok) return 'sent';
  } catch { /* queued below */ }
  if (!env.EMAIL_ENC_KEY) return 'skipped';
  const payload = await encrypt(env.EMAIL_ENC_KEY, JSON.stringify({ to, subject, text }));
  await env.DB.prepare('INSERT INTO outbox (id, kind, payload, attempts, next_at, created_at) VALUES (?, ?, ?, 0, ?, ?)')
    .bind(crypto.randomUUID(), 'email', payload, now + backoff(1), now).run();
  return 'queued';
}

export async function processOutbox(env: Env, now = Date.now(), limit = 20) {
  if (!env.EMAIL_ENC_KEY) return { sent: 0, failed: 0 };
  const due = (await env.DB.prepare('SELECT id, payload, attempts FROM outbox WHERE next_at <= ? AND attempts < ? ORDER BY next_at LIMIT ?')
    .bind(now, MAX_ATTEMPTS, limit).all<{ id: string; payload: string; attempts: number }>()).results;
  let sent = 0, failed = 0;
  for (const row of due) {
    try {
      const m = JSON.parse(await decrypt(env.EMAIL_ENC_KEY, row.payload)) as { to: string; subject: string; text: string };
      const ok = await withBreaker(env, 'email', () => sendEmail(env, m.to, m.subject, m.text), now);
      if (!ok) throw new Error('email not sent');
      await env.DB.prepare('DELETE FROM outbox WHERE id = ?').bind(row.id).run();
      sent++;
    } catch (e) {
      failed++;
      await env.DB.prepare('UPDATE outbox SET attempts = attempts + 1, next_at = ?, last_error = ? WHERE id = ?')
        .bind(now + backoff(row.attempts + 2), (e instanceof Error ? e.name : 'error').slice(0, 60), row.id).run();
    }
  }
  return { sent, failed };
}
