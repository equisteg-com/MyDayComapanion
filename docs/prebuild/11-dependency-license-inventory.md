# 11 — Dependency and licence inventory

Source of truth for versions: `gradle/libs.versions.toml`. Every shipped dependency is free, has no telemetry,
and makes no network calls (the app has no INTERNET permission regardless).

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| DL-01 | Inventory complete | PASS | Tables below match the version catalog. |
| DL-02 | Licences acceptable | PASS | Apache-2.0, BSD-style, MIT; EPL-1.0/2.0 only in test scope. |
| DL-03 | No paid / telemetry / network SDKs | PASS | None present. |
| DL-04 | Transitive review | PASS (CI) | `dependency-scan` job writes `app/gradle.lockfile` for the release runtime classpath and runs OSV-Scanner. |

## Shipped in the app

| Library | Version | Licence | Purpose |
|---|---|---|---|
| Kotlin stdlib | 2.2.21 | Apache-2.0 | Language runtime |
| kotlinx-coroutines-core / -android | 1.10.2 | Apache-2.0 | Async |
| kotlinx-serialization-json | 1.9.0 | Apache-2.0 | Structured output parsing, export format |
| androidx.core:core-ktx | 1.16.0 | Apache-2.0 | Permission checks, compat |
| androidx.lifecycle (runtime, viewmodel-compose, runtime-compose) | 2.9.1 | Apache-2.0 | ViewModel, lifecycle-aware state |
| androidx.activity:activity-compose | 1.10.1 | Apache-2.0 | Compose host, file picker |
| androidx.biometric:biometric | 1.1.0 | Apache-2.0 | System fingerprint/face/screen-lock prompt for app lock and approvals (build doc §6.2) |
| androidx.fragment:fragment-ktx | 1.8.5 | Apache-2.0 | FragmentActivity host that BiometricPrompt needs; pinned so activity-result APIs work |
| Compose BOM (ui, material3, material-icons-core, tooling-preview) | 2025.06.00 | Apache-2.0 | UI |
| androidx.room (runtime, ktx; compiler at build time) | 2.7.2 | Apache-2.0 | Local database |
| androidx.sqlite | 2.5.2 | Apache-2.0 | SQLite support API |
| net.zetetic:sqlcipher-android | 4.10.0 | BSD-style (Zetetic community licence) | Database encryption; adds native libs (~3–4 MB per ABI) |

## Build / test only (not shipped)

| Tool | Version | Licence |
|---|---|---|
| Android Gradle Plugin | 8.13.0 | Apache-2.0 |
| Kotlin Gradle plugins (android, jvm, compose, serialization) | 2.2.21 | Apache-2.0 |
| KSP | 2.2.21-2.0.5 | Apache-2.0 |
| Gradle | 8.14.3 | Apache-2.0 |
| JUnit 4 | 4.13.2 | EPL-1.0 |
| kotlin-test | 2.2.21 | Apache-2.0 |
| kotlinx-coroutines-test | 1.10.2 | Apache-2.0 |
| Robolectric | 4.16 | MIT |
| androidx.test core | 1.6.1 | Apache-2.0 |
| androidx.room:room-testing | 2.7.2 | Apache-2.0 |
| Compose ui-test-junit4 / ui-test-manifest / ui-tooling | BOM | Apache-2.0 |
| Gitleaks (CI) | 8.21.2 | MIT |
| OSV-Scanner (CI) | 1.9.2 | Apache-2.0 |

## Rejected / deferred

| Candidate | Reason |
|---|---|
| Firebase (Crashlytics, Analytics) | Network + telemetry; violates local-first |
| Hilt/Dagger | Not needed at this size; manual DI keeps the graph auditable |
| Jetpack Security (EncryptedFile/EncryptedSharedPreferences) | Deprecated by Google; direct Keystore AES-GCM used instead |
| Detekt | Pending Kotlin 2.2 support; Android lint covers Phase 0 |
| On-device LLM runtimes | Deferred to OQ-04 (licence of weights, download path) |
