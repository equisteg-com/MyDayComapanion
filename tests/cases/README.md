# Test-case sheet rules (`test-cases.xlsx`)

- One row per **step**. The first row of a test case carries TC ID, Module, REQ ID, Platform, Type, Title, Preconditions and Test data; each following row repeats the TC ID and fills Step No, Test step and Expected result.
- Every step has its own observable expected result.
- Type is Positive, Negative or Edge. Every REQ needs at least one of each, plus end-to-end journey cases that cross screens, payment and expiry.
- Test data cells point to files in `tests/testdata/` instead of pasting values.
- Automation ref = `tests/web/specs/<file>.spec.ts :: <test name>` or `tests/mobile/flows/<file>.yaml`.
- Sheets: `Web`, `Mobile`, `API`, `Payments`, `Non-functional`, `Summary`. Summary counts update by formula.
- `Non-functional` rows (`TC-N-xxx`) use Category and NFR ID instead of Type and REQ ID; Expected result is a measurable threshold ("p95 < 500 ms"), Measured value is the latest number. Every NFR in `docs/product-brief.md` needs at least one row.
- Status (Pass / Fail / Flaky / Blocked / Not run) and Remarks (run ID) are written by `/test` after each staging run. Failures are tracked in `tests/bugs/bug-log.xlsx`.
- Automated tests start their title with the TC ID so results map back to this sheet.
