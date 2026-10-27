// Client error reports for the watchdog (phase 9b): window errors, unhandled rejections and the route error boundary
// → POST /api/v1/errors. No photo, no personal data: name, message, stack and the page path (the server scrubs again).
// Sampling per fingerprint and tab: the first 5 are sent, then 1 in 10.
const RELEASE = process.env.NEXT_PUBLIC_RELEASE || 'web';
const seen = new Map<string, number>();

export function reportError(e: unknown, extra: { feature?: string } = {}) {
  try {
    const err = e instanceof Error ? e : new Error(typeof e === 'string' ? e : 'Non-error rejection');
    const key = `${err.name}|${(err.stack ?? '').split('\n')[1] ?? err.message}`;
    const n = (seen.get(key) ?? 0) + 1;
    seen.set(key, n);
    if (n > 5 && Math.random() > 0.1) return;
    const body = JSON.stringify({
      platform: 'web', release: RELEASE, name: err.name.slice(0, 120), message: err.message.slice(0, 1000),
      stack: (err.stack ?? '').slice(0, 6000), route: location.pathname.slice(0, 200), ...(extra.feature ? { feature: extra.feature } : {}),
    });
    const url = `${process.env.NEXT_PUBLIC_API_URL ?? ''}/api/v1/errors`;
    const blob = new Blob([body], { type: 'application/json' });
    if (!navigator.sendBeacon?.(url, blob)) void fetch(url, { method: 'POST', body, headers: { 'content-type': 'application/json' }, keepalive: true }).catch(() => undefined);
  } catch { /* reporting must never cause another error */ }
}

let installed = false;
export function installErrorReporting() {
  if (installed || typeof window === 'undefined') return;
  installed = true;
  window.addEventListener('error', (ev) => {
    // only our own scripts: extensions and third-party frames (ads, payment) are not ours to fix
    if (ev.filename && !ev.filename.startsWith(location.origin)) return;
    reportError(ev.error ?? new Error(ev.message));
  });
  window.addEventListener('unhandledrejection', (ev) => reportError(ev.reason));
}
