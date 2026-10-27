// Search snippet limits (newapp phase 5): titles ≤ 60 characters including the brand suffix, descriptions ≤ 155.
// Each helper takes candidates from most to least descriptive and returns the first that fits, so long document
// names never get cut mid-word by search engines. scripts/seo-audit.mjs checks the built pages.

export const TITLE_MAX = 60;
export const DESC_MAX = 155;
const SUFFIX = ' · <Product>';

const clip = (s: string, max: number) => (s.length <= max ? s : s.slice(0, s.lastIndexOf(' ', max - 1)).replace(/[,:;·–—-]$/, '') + '…');

/** Returns a full title (pass as `title: { absolute }`), with the brand suffix whenever it fits. */
export function fitTitle(...candidates: string[]): string {
  for (const c of candidates) if (c.length + SUFFIX.length <= TITLE_MAX) return c + SUFFIX;
  for (const c of candidates) if (c.length <= TITLE_MAX) return c;
  return clip(candidates.at(-1) ?? '', TITLE_MAX);
}

/** Joins sentences, dropping optional trailing ones until the description fits. */
export function fitDescription(required: string, ...optional: string[]): string {
  let out = clip(required, DESC_MAX);
  for (const o of optional) if (`${out} ${o}`.length <= DESC_MAX) out = `${out} ${o}`;
  return out;
}
