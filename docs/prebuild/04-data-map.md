# 04 — Data map and privacy

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| DM-01 | Data inventory complete | PASS | Table below matches `data/.../db/Entities.kt`. |
| DM-02 | Data classification complete | PASS | Column "Class". |
| DM-03 | Local storage policy | PASS | All data in one SQLCipher DB + one wrapped key file. No SharedPreferences, no external storage. |
| DM-04 | Encryption design | PASS | 03 AR-04. |
| DM-05 | Local-first behaviour verified | PASS | No INTERNET permission (`ManifestSecurityTest`, CI APK check). |
| DM-06 | Network calls explicitly listed | PASS | None in V1 (§Network). |
| DM-07 | Sensitive data paths identified | PASS | §Sensitive paths. |
| DM-08 | Retention policy defined | PASS | Column "Retention". Memory and History pruning run in the nightly maintenance (2026-10-28). |
| DM-09 | Deletion policy defined | PASS | §Deletion; `PrivacyTest`, `RoomStorageTest`. |
| DM-10 | Export path defined | PASS | §Export. |
| DM-11 | No mandatory account | PASS | No account code; `accounts` flag locked off. |
| DM-12 | No silent upload | PASS | No network path exists. |
| DM-13 | Backup/privacy behaviour reviewed | PASS | `allowBackup=false`; `data_extraction_rules.xml` excludes all domains from cloud backup and device transfer; key in `noBackupFilesDir`. |
| DM-14 | Logging of private payloads | PASS | No `Log` calls with user content; audit stores no titles/text (`AgentOrchestratorTest`). |

## Inventory

| Store (table) | Fields | Class | Source | Retention | Export | Delete |
|---|---|---|---|---|---|---|
| tasks | title, priority, duration, date, deadline, status, parentId, timestamps | Personal | User text / extraction | Until user deletes | Yes | Yes |
| fixed_events | title, date, start, duration, source | Personal | User text; calendar (Phase 1, read-only, not copied unless planned) | Until user deletes | Yes | Yes |
| plans, plan_blocks | date, block titles/times, issues, planner version | Personal | Planner | Until user deletes | Yes | Yes |
| routines | title, frequency, preferred start, duration | Personal | User | Until user deletes | Yes | Yes |
| memories | content, type, source, confidence, status, sensitivity, retention | Personal / Sensitive | User (Memory screen) or assistant proposal (habits noticed from finished, non-personal tasks) | Per item: suggestions 365 d; a turned-down suggestion keeps only a one-way hash of its text, 365 d; kept and owner-written memories until deleted; expired items removed nightly (`memory.purgeExpired`) | Yes | Yes (Memory screen, one at a time; Delete everything) |
| audit_log | time, actor, action id, risk, decision, outcome, reason code, non-personal detail | Operational | Orchestrator | 180 days by default; owner chooses 30 / 90 / 180 / 365 on History; removed nightly (`history.prune`) | Yes | Yes |
| preferences | day start/end, buffer, speech output | Low | User | Until user deletes | Yes | Yes |
| Backup file (`.mdcbackup`, outside the app) | everything in the rows above, plus security settings and challenge-question hashes; not the database key | Personal / Sensitive | Owner (Settings → Back up) | Where the owner saved it (Storage Access Framework); the app keeps no copy | — | Owner deletes the file |
| device_settings (SharedPreferences) | calendar, Gemini Nano and lock-screen switches, History retention, wrong-backup-passphrase count and lock time | Low | User / app | Life of install; the restore lock survives "Delete everything" | No | Uninstall |
| db.key.v1.enc | wrapped DB passphrase | Secret | App | Life of install | No (useless off-device) | Removed with app data |
| Keystore key | AES-256 wrapping key | Secret | AndroidKeyStore | Life of install | Never extractable | Removed on uninstall |

Voice audio (Phase 1) is never stored; only the recognised text enters the pipeline as user input.

## Sensitive paths

1. Free text → extractor/model: may contain anything (health, finance). Stays in memory, normalised, never logged.
2. Memories: assistant cannot store SENSITIVE items; sensitive memories are excluded from model context.
3. Export file: plaintext JSON in a location the user chooses. The confirmation dialog says so.
4. Backup file: AES-256-GCM, key from the owner's passphrase (PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte salt);
   the header is authenticated. A backup is untrusted input when restored: files over 20 MB, unknown fields, unknown
   versions, weaker key settings, rule-like titles or memories and broken references are refused whole. Ten wrong
   passphrases close Restore for an hour. There is no recovery without the passphrase.
4. Calendar (Phase 1): read on demand for the plan day; only events the user plans are stored.

## Network

V1 has **no network path**. The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` even if a library adds them,
and CI fails if the built APK requests either. Any future network capability must document purpose,
destination, data sent/received, retention, consent, failure behaviour, privacy implication and cost (master §20)
and pass a founder decision.

Note for Phase 1 voice: Android's default `SpeechRecognizer` may route audio to a cloud service inside another
app's process, which would be an indirect network path. Voice must use the on-device recognizer only (07 AI-12).

## Export

Settings → Export all data → system file picker (no storage permission) → confirmation (L3) → JSON
(`mydaycompanion-export`, schema v1) with every table and preferences → verified by byte count → audited.

## Deletion

Settings → Delete everything → confirmation (L4) → every `ErasableStore` cleared → reminders cancelled →
each store re-counted; success only if all are zero → one non-personal audit receipt (`data.deleteAll`) remains.
SQLite `secure_delete` is on, so freed pages are overwritten. Uninstalling removes the DB, key file and Keystore key.
