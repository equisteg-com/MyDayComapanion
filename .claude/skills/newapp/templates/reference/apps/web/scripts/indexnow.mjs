// Pings IndexNow (Bing, Yandex, Seznam, Naver…; Bing feeds Copilot and ChatGPT search) with every sitemap URL.
// Runs in deploy.yml after a healthy production deploy; manual: `node apps/web/scripts/indexnow.mjs https://<domain>`.
// The key is public by design: IndexNow verifies ownership by fetching https://<host>/<key>.txt (apps/web/public).
import { readdirSync } from 'node:fs';

const site = (process.argv[2] || process.env.SITE_URL || 'https://<domain>').replace(/\/$/, '');
const keyFile = readdirSync(new URL('../public/', import.meta.url)).find((f) => /^[0-9a-f]{32}\.txt$/.test(f));
if (!keyFile) throw new Error('IndexNow key file missing in apps/web/public');
const key = keyFile.slice(0, -4);

const sitemap = await (await fetch(`${site}/sitemap.xml`)).text();
const urlList = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]).filter((u) => u.startsWith(site));
if (!urlList.length) throw new Error(`no URLs in ${site}/sitemap.xml`);

const res = await fetch('https://api.indexnow.org/indexnow', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json; charset=utf-8' },
  body: JSON.stringify({ host: new URL(site).host, key, keyLocation: `${site}/${keyFile}`, urlList: urlList.slice(0, 10000) }),
});
// 200 OK and 202 Accepted are success; 403 = key file not reachable; 422 = URLs not on this host.
console.log(`IndexNow: ${res.status} for ${urlList.length} URLs`);
if (res.status !== 200 && res.status !== 202) process.exit(1);
