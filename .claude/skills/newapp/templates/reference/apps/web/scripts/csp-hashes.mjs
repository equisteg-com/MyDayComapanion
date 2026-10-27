// Runs after `next build`, before build-sw.mjs. The static host sends one CSP header for every page (public/_headers),
// which has to allow Next's inline bootstrap scripts with 'unsafe-inline'. This step adds a per-page <meta> CSP whose
// script-src lists the SHA-256 hash of each inline script on that page instead. Browsers enforce both policies, so
// an injected inline script fails the meta policy even though the header allows it (OWASP ASVS 14.4.3).
// Hosts come from the header's own script-src, so the two lists never drift apart.
import { createHash } from 'node:crypto';
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const out = fileURLToPath(new URL('../out', import.meta.url));
const headers = readFileSync(fileURLToPath(new URL('../public/_headers', import.meta.url)), 'utf8');
const csp = headers.match(/Content-Security-Policy:\s*(.+)/)?.[1] ?? '';
const scriptSrc = csp.split(';').map((d) => d.trim()).find((d) => d.startsWith('script-src '));
if (!scriptSrc) throw new Error('script-src not found in public/_headers');
const sources = scriptSrc.split(/\s+/).slice(1).filter((s) => s !== "'unsafe-inline'");

// Inline, executable scripts only: no src, not JSON-LD / JSON data blocks.
const INLINE = /<script(?![^>]*\bsrc=)(?![^>]*type="application\/(?:ld\+)?json")[^>]*>([\s\S]*?)<\/script>/g;

let pages = 0;
let hashes = 0;
const walk = (dir) => {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) { if (f !== '_next' && f !== 'models') walk(p); continue; }
    if (!f.endsWith('.html')) continue;
    let html = readFileSync(p, 'utf8');
    if (html.includes('http-equiv="Content-Security-Policy"')) continue;
    const list = new Set();
    for (const m of html.matchAll(INLINE)) list.add(`'sha256-${createHash('sha256').update(m[1], 'utf8').digest('base64')}'`);
    const policy = `script-src ${[...sources, ...list].join(' ')}; object-src 'none'; base-uri 'self'`;
    const meta = `<meta http-equiv="Content-Security-Policy" content="${policy}"/>`;
    // Right after the charset meta, before any script, so it applies to every script on the page.
    const at = html.search(/<meta charSet="utf-8"\/>/i);
    if (at < 0) throw new Error(`no charset meta in ${p}`);
    const end = html.indexOf('>', at) + 1;
    html = html.slice(0, end) + meta + html.slice(end);
    writeFileSync(p, html);
    pages++;
    hashes += list.size;
  }
};
walk(out);
console.log(`csp: ${pages} pages, ${hashes} inline script hashes`);
