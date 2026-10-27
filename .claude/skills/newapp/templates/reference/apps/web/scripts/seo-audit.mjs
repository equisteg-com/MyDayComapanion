// SEO audit of the static export (newapp phase 5). Run after `npm run build`: `npm run seo:audit -w @<slug>/web`.
// Fails (exit 1) on any page that breaks the rules below, so CI catches regressions before deploy.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

const OUT = new URL('../out/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const SITE = (process.env.NEXT_PUBLIC_SITE_URL || 'https://<domain>').replace(/\/$/, '');
const SKIP = new Set(['404', '_not-found', 'offline']); // not indexable

const decode = (s) => s.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#x27;|&#39;/g, "'");
const attr = (html, re) => { const m = html.match(re); return m ? decode(m[1]) : null; };

function pages(dir) {
  const found = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) { if (!name.startsWith('_next')) found.push(...pages(p)); }
    else if (name === 'index.html') found.push(p);
  }
  return found;
}

// Dev and staging builds (NEXT_PUBLIC_NOINDEX=1) must never be indexed: there the audit checks exactly that —
// every page carries noindex and robots.txt blocks crawling — instead of the production SEO rules.
if (process.env.NEXT_PUBLIC_NOINDEX === '1') {
  const all = pages(OUT);
  const open = all.filter((f) => !/<meta name="robots" content="noindex/.test(readFileSync(f, 'utf8'))).map((f) => relative(OUT, f));
  const robots = existsSync(join(OUT, 'robots.txt')) ? readFileSync(join(OUT, 'robots.txt'), 'utf8') : '';
  const problems = [...open.map((f) => `${f}: missing noindex`), ...(/Disallow: \/\s*$/m.test(robots) ? [] : ['robots.txt does not disallow everything'])];
  console.log(`SEO audit (noindex build): ${all.length} pages, ${all.length - open.length} noindex, robots.txt ${problems.length && !/Disallow: \/\s*$/m.test(robots) ? 'OPEN' : 'blocks crawling'}`);
  if (problems.length) { console.error(`${problems.length} problem(s):\n- ${problems.slice(0, 50).join('\n- ')}`); process.exit(1); }
  console.log('SEO audit passed: this environment cannot be indexed.');
  process.exit(0);
}

const errors = [];
const titles = new Map();
const descriptions = new Map();
const types = new Set();
let count = 0;

for (const file of pages(OUT)) {
  const rel = relative(OUT, file).split(sep).slice(0, -1).join('/');
  if (SKIP.has(rel.split('/')[0])) continue;
  const path = rel ? `/${rel}/` : '/';
  const html = readFileSync(file, 'utf8');
  if (/<meta name="robots" content="noindex/.test(html)) continue;
  count++;
  const err = (m) => errors.push(`${path}: ${m}`);

  const title = attr(html, /<title>([^<]*)<\/title>/);
  if (!title) err('missing <title>');
  else {
    if (title.length > 60) err(`title ${title.length} chars (max 60): "${title}"`);
    titles.set(title, [...(titles.get(title) ?? []), path]);
  }
  const desc = attr(html, /<meta name="description" content="([^"]*)"/);
  if (!desc) err('missing meta description');
  else {
    if (desc.length > 155 || desc.length < 50) err(`description ${desc.length} chars (50–155)`);
    descriptions.set(desc, [...(descriptions.get(desc) ?? []), path]);
  }
  const canonical = attr(html, /<link rel="canonical" href="([^"]*)"/);
  if (canonical !== `${SITE}${path}`) err(`canonical ${canonical} ≠ ${SITE}${path}`);
  const h1 = (html.match(/<h1[\s>]/g) ?? []).length;
  if (h1 !== 1) err(`${h1} <h1> elements (want 1)`);
  if (!/<meta property="og:image" content="https:\/\//.test(html)) err('missing absolute og:image');
  for (const img of html.match(/<img\b[^>]*>/g) ?? []) if (!/\balt="/.test(img)) err(`img without alt: ${img.slice(0, 80)}`);
  for (const m of html.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)) {
    try {
      const ld = JSON.parse(m[1]);
      for (const node of ld['@graph'] ?? [ld]) {
        if (!node['@type']) err('JSON-LD node without @type');
        types.add(node['@type']);
      }
    } catch (e) { err(`JSON-LD does not parse: ${e.message}`); }
  }
}

for (const [t, at] of titles) if (at.length > 1) errors.push(`duplicate title "${t}": ${at.join(', ')}`);
for (const [d, at] of descriptions) if (at.length > 1) errors.push(`duplicate description on ${at.join(', ')}: "${d.slice(0, 60)}…"`);

// sitemap ↔ files, robots, llms.txt
const sitemap = existsSync(join(OUT, 'sitemap.xml')) ? readFileSync(join(OUT, 'sitemap.xml'), 'utf8') : '';
if (!sitemap) errors.push('sitemap.xml missing');
for (const [, loc] of sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)) {
  const p = loc.replace(SITE, '').replace(/^\//, '');
  if (!existsSync(join(OUT, p, 'index.html'))) errors.push(`sitemap URL without a page: ${loc}`);
}
if (!existsSync(join(OUT, 'robots.txt'))) errors.push('robots.txt missing');
const llms = existsSync(join(OUT, 'llms.txt')) ? readFileSync(join(OUT, 'llms.txt'), 'utf8') : '';
if (!/is built and run by Equisteg \(https:\/\/equisteg\.com\)/.test(llms)) errors.push('llms.txt missing or without the Equisteg attribution');
if (!existsSync(join(OUT, 'llms-full.txt'))) errors.push('llms-full.txt missing');
for (const t of ['Organization', 'WebSite', 'SoftwareApplication', 'FAQPage', 'BreadcrumbList', 'HowTo', 'Article']) if (!types.has(t)) errors.push(`no page carries ${t} JSON-LD`);

console.log(`SEO audit: ${count} indexable pages, ${sitemap.match(/<loc>/g)?.length ?? 0} sitemap URLs, JSON-LD types: ${[...types].sort().join(', ')}`);
if (errors.length) {
  console.error(`${errors.length} problem(s):\n- ${errors.join('\n- ')}`);
  process.exit(1);
}
console.log('SEO audit passed.');
