// One k6 script for every load profile. Run it through `npm run load` / `npm run perf`
// (tests/tools/perf.mjs), never directly against production.
//   PROFILE  smoke | load | stress | spike | soak   (default smoke)
//   BASE_URL dev or staging address
// Routes and pass/fail limits live in tests/testdata/shared/load.json.
import http from 'k6/http';
import { check, sleep } from 'k6';

const cfg = JSON.parse(open('../testdata/shared/load.json'));
const BASE = (__ENV.BASE_URL || '').replace(/\/$/, '');
const PROFILE = __ENV.PROFILE || 'smoke';

// Small on purpose: every request counts toward the Workers free 100,000 requests/day.
const profiles = {
  smoke: { executor: 'constant-vus', vus: 2, duration: '30s' },
  load: { executor: 'ramping-vus', stages: [{ duration: '30s', target: 10 }, { duration: '1m', target: 10 }, { duration: '30s', target: 0 }] },
  stress: { executor: 'ramping-vus', stages: [{ duration: '30s', target: 10 }, { duration: '1m', target: 30 }, { duration: '1m', target: 30 }, { duration: '30s', target: 0 }] },
  spike: { executor: 'ramping-vus', stages: [{ duration: '10s', target: 2 }, { duration: '10s', target: 40 }, { duration: '30s', target: 40 }, { duration: '10s', target: 0 }] },
  soak: { executor: 'constant-vus', vus: 5, duration: '30m' },
};
if (!profiles[PROFILE]) throw new Error(`unknown PROFILE ${PROFILE}`);
if (!BASE) throw new Error('BASE_URL is not set');

export const options = {
  scenarios: { [PROFILE]: profiles[PROFILE] },
  thresholds: {
    http_req_duration: [`p(95)<${cfg.p95Ms ?? 500}`],
    http_req_failed: [`rate<${cfg.maxErrorRate ?? 0.01}`],
    checks: ['rate>0.99'],
  },
};

export default function () {
  for (const r of cfg.routes) {
    const url = BASE + r.path;
    const params = { tags: { name: r.path }, headers: { 'Content-Type': 'application/json' } };
    const res = (r.method || 'GET').toUpperCase() === 'POST' ? http.post(url, JSON.stringify(r.body ?? {}), params) : http.get(url, params);
    check(res, { [`${r.path} -> ${r.expect ?? 200}`]: (x) => x.status === (r.expect ?? 200) });
  }
  sleep(1);
}
