# Changelog

Entries are added by `/newapp` (release), `/change` (CHG-xxx) and `/update` (UPD-xxx) and `/test` bug fixes (FIX-xxx, with the BUG IDs). Newest first.

| Date | ID | Type | Summary | REQs | PR |
|---|---|---|---|---|---|
| 2026-10-09 | UPD-002 | Update | Owner verification and theft protection: app lock with the system fingerprint/face/screen-lock prompt, L3/L4 approvals with the device credential, challenge questions (PBKDF2 hashes) with 15-minute lockout and opt-in wipe, "Delete everything" now deletes the database files and destroys the key; security tools are L4 and user-only; Room schema v3 | REQ-MDC-04, REQ-MDC-05 | pending |
| 2026-10-09 | UPD-001 | Update | Agent OS build document v2; text and time understanding v2 (any time format and position, several days per message, soft times, deadlines, repeats, reminders); planner honours preferred start; Room schema v2 | REQ-MDC-01, REQ-MDC-02 | pending |
| 2026-10-06 | REL-0.1.0-phase0 | Release (internal) | Precheck evidence and Phase 0 Android foundation for MyDayCompanion (not distributed) | REQ-MDC-01..05, NFR-MDC-01..04 | pending |
