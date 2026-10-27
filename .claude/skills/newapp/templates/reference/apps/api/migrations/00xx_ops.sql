-- Production self-healing (phase 9b, docs/standards/self-healing.md). Counters only: no IPs, emails, tokens or bodies.
-- Cleanup: rows older than 14 days are deleted by the daily retention run (jobs/retention.ts).

-- Per-minute API request counters, flushed in batches from each isolate (ops/stats.ts).
-- h0..h6 = latency histogram buckets: ≤100, ≤250, ≤500, ≤1000, ≤1500, ≤3000, >3000 ms.
CREATE TABLE request_stats (
  minute INTEGER NOT NULL,          -- epoch minute
  route TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  errors5xx INTEGER NOT NULL DEFAULT 0,
  h0 INTEGER NOT NULL DEFAULT 0, h1 INTEGER NOT NULL DEFAULT 0, h2 INTEGER NOT NULL DEFAULT 0, h3 INTEGER NOT NULL DEFAULT 0,
  h4 INTEGER NOT NULL DEFAULT 0, h5 INTEGER NOT NULL DEFAULT 0, h6 INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (minute, route)
);

-- One row per error fingerprint (API, web, Android). sample_stack is scrubbed and truncated before storage.
CREATE TABLE error_events (
  fingerprint TEXT PRIMARY KEY,
  platform TEXT NOT NULL CHECK (platform IN ('api','web','android')),
  release TEXT NOT NULL,
  feature TEXT,
  route TEXT,
  name TEXT NOT NULL,
  first_seen INTEGER NOT NULL,
  last_seen INTEGER NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  sample_rid TEXT,
  sample_stack TEXT
);
CREATE INDEX idx_err_last ON error_events(last_seen);

-- Hourly hit counts per fingerprint (spike detection against the 24-hour baseline).
CREATE TABLE error_hourly (
  fingerprint TEXT NOT NULL,
  hour INTEGER NOT NULL,            -- epoch hour
  count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (fingerprint, hour)
);

-- Incidents the watchdog opened (one per rule or fingerprint), with the GitHub issue that tracks it.
CREATE TABLE incidents (
  key TEXT PRIMARY KEY,             -- 'rule:5xx', 'rule:health', 'fp:<fingerprint>', …
  severity TEXT NOT NULL CHECK (severity IN ('sev1','sev2','sev3')),
  status TEXT NOT NULL CHECK (status IN ('open','closed')),
  title TEXT NOT NULL,
  issue INTEGER,
  release TEXT,
  opened_at INTEGER NOT NULL,
  last_seen INTEGER NOT NULL,
  closed_at INTEGER,
  actions TEXT                      -- JSON list of automatic actions taken
);
CREATE INDEX idx_inc_status ON incidents(status, last_seen);

-- Outbound work that failed (emails) and is retried by the 5-minute cron. payload is encrypted (EMAIL_ENC_KEY).
CREATE TABLE outbox (
  id TEXT PRIMARY KEY,
  kind TEXT NOT NULL CHECK (kind IN ('email')),
  payload TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  next_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  last_error TEXT
);
CREATE INDEX idx_outbox_next ON outbox(next_at);
