# Bug log (`bug-log.xlsx`)

Every failure found by automation on staging (or dev) is a row here. `/test` fills it; nobody deletes rows.

| Sheet | What |
|---|---|
| `Bugs` | One row per bug: ID, status, decision, severity, platform, suite, functional/non-functional, TC ID, REQ/NFR ID, steps, expected, actual, evidence, first/last seen, occurrences, fix PR, verified run |
| `Runs` | One row per test run: environment, commit, pass/fail/flaky counts, new/reopened/open/verified bugs, report link |
| `Summary` | Counts by status and open bugs by severity (formulas) |
| `Read me` | Status flow and severity rules |

**Status flow:** New → Open (Decision = Fix now) → In progress → In PR → Fixed (merged into `development`) → Verified (passes on staging) → Closed.
Side states: Deferred, Won't fix, Not a bug (test or environment problem — still logged), Duplicate, Reopened (failed again after a fix).

**Severity:** S1 payments, security, data loss or app unusable · S2 main feature broken, no workaround · S3 partly broken or workaround exists · S4 cosmetic, flaky test, minor.

Helper (needs `pip install openpyxl`):

```bash
python tests/tools/bug_log.py ingest test-results/<run>/summary.json --report docs/test-reports/<file>.md
python tests/tools/bug_log.py list            # open bugs as JSON
python tests/tools/bug_log.py set BUG-0003 "Status=In PR" "Fix branch / PR=https://github.com/..."
```

The same failure in a later run updates its existing row (matched by `Signature` = suite | TC ID), so the log never fills with duplicates.
Reports for each run are in `docs/test-reports/`.
