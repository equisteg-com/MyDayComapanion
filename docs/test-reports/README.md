# Test reports

One file per test run: `<YYYY-MM-DD>-<env>-<run-id>.md`, written by `/test`.
Each report has the per-suite summary (functional and non-functional), non-functional numbers against their thresholds, new/reopened/open/verified bugs (IDs from `tests/bugs/bug-log.xlsx`), what was not run, and a release recommendation (*Ready* only with no open S1/S2 bug and every P1 test case passing).
