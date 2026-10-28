# 11 — Dependency and licence inventory

Source of truth for versions: `gradle/libs.versions.toml`. Every shipped dependency is free, has no telemetry,
and makes no network calls (the app has no INTERNET permission regardless).

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| DL-01 | Inventory complete | PASS | Tables below match the version catalog. |
| DL-02 | Licences acceptable | PASS (one founder-approved exception) | Apache-2.0, BSD-style, MIT; EPL-1.0/2.0 only in test scope. Exception: ML Kit GenAI Prompt API under the ML Kit Terms of Service (Google APIs Terms), accepted by the founder on 2026-10-28 for Gemini Nano (D-03). |
| DL-03 | No paid / telemetry / network SDKs | PASS with note | No paid SDK. ML Kit brings Google's usage-logging library (`com.google.android.datatransport` transport-runtime and transport-backend-cct, plus firebase-encoders and play-services-basement/base/tasks), confirmed in the CI dependency tree on 2026-10-28. Its INTERNET and ACCESS_NETWORK_STATE requests are removed by the app manifest and the APK is checked in CI, so the app cannot send anything. Whether its upload scheduling fails quietly without ACCESS_NETWORK_STATE on Android 14+ is OQ-20. Owners can switch Gemini Nano off in Settings (then ML Kit is not called at all); turning it off for everyone needs a release with `local_llm` off. The model itself is run and downloaded by Android's AICore system service. |
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
| com.google.mlkit:common, genai-common, genai-schema (transitive via genai-prompt) | 18.11.0 / 1.0.0-beta4 / 1.0.0-alpha1 | ML Kit Terms of Service (founder-approved D-03) | Parts of the Prompt API |
| com.google.android.gms:play-services-basement / -base / -tasks (transitive via ML Kit) | 18.9.0 / 18.5.0 / 18.2.0 | Android Software Development Kit License (free; covered by the D-03 approval) | Google Play services client glue that ML Kit needs |
| com.google.android.datatransport:transport-api / -runtime / -backend-cct (transitive via ML Kit) | 2.2.1 / 2.2.6 / 2.3.3 | Apache-2.0 | Google's usage-logging transport; cannot send anything without INTERNET (OQ-20) |
| com.google.firebase:firebase-encoders / -encoders-json / -components / -annotations (transitive via ML Kit) | 16.1.0 / 17.1.0 / 16.1.0 / 16.0.0 | Apache-2.0 | Serialisation used by the logging transport |
| com.google.mlkit:genai-prompt | 1.0.0-beta4 | ML Kit Terms of Service (proprietary, free; founder-approved 2026-10-28) | Gemini Nano on supported phones (D-03). beta5 needs Kotlin 2.3. Output is untrusted and schema-validated; the rule extractor is the fallback |

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
