// Thresholds and policies for the watchdog (docs/standards/self-healing.md §1–2). Change here, not in code.
import type { FeatureId } from '@<slug>/shared-types';

export const OPS = {
  // §1 signals
  errorRate: { windowMin: 5, minRequests: 20, max5xx: 0.02 },
  newError: { minHits: 5, withinMs: 60 * 60_000, activeMs: 10 * 60_000 },
  spike: { factor: 5, minHits: 20 },
  latency: { p95Ms: 1500, windowMin: 10, minRequests: 20 },
  health: { failuresInARow: 2 },
  payments: { paidNotGrantedAfterMs: 10 * 60_000 },
  cron: { staleMs: 26 * 3_600_000, jobs: ['retention', 'payments_daily'] as const },
  freeTier: { workersRequestsPerDay: 100_000, warnAt: 0.7 },
  // §2 containment
  rollbackWithinMs: 60 * 60_000, // an incident this soon after a deploy asks for a rollback
  closeRuleAfterQuietMs: 30 * 60_000,
  closeFingerprintAfterQuietMs: 24 * 3_600_000,
  breaker: { failures: 5, windowMs: 60_000, openMs: 60_000 },
  // client error reports
  errorReport: { maxBytes: 8 * 1024 },
} as const;

/**
 * Features the watchdog may switch off by itself when their errors concentrate. Payment verification, checkout,
 * restore, entitlement checks and the Create flow are never on this list: they fail closed in code instead.
 */
export const AUTO_DISABLE: readonly FeatureId[] = ['feedback_prompt', 'live_guidance', 'batch', 'agency_api', 'rule_reports', 'analytics_events'];

/** API route prefix → feature, so a server error can be tied to a kill-switchable feature. */
export const ROUTE_FEATURE: [prefix: string, feature: FeatureId][] = [
  ['/api/v1/events', 'analytics_events'],
  ['/api/v1/requirements/report', 'rule_reports'],
  ['/api/v1/agency', 'agency_api'],
];

/** Error names never worth an incident (user aborts, offline). */
export const IGNORE_ERRORS = [/^AbortError$/, /NetworkError/, /Failed to fetch/, /Load failed/, /ResizeObserver loop/];
