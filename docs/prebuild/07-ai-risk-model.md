# 07 — AI risk model

| ID | Check | Status | Evidence / resolution |
|---|---|---|---|
| AI-01 | Model/provider abstraction | PASS | `AiModelProvider` (capabilities, estimateCost, generate, availability, structured/streaming support). |
| AI-02 | Supported model classes documented | PASS | DETERMINISTIC, SMALL_LOCAL, LARGE_LOCAL, CLOUD (CLOUD blocked by `PrivacyMode.ON_DEVICE_ONLY`). |
| AI-03 | Local model strategy | PASS (Phase 0) / NEEDS DECISION (Phase 1) | Phase 0 ships the deterministic `RuleBasedTaskExtractor` only. A real on-device LLM needs a decision (OQ-04): candidates are Gemini Nano via Android AICore (device-limited, Google terms) or an open-weight small model via MediaPipe/LiteRT (weights licence + 0.5–2 GB download, which needs a network path or a large APK). |
| AI-04 | Device capability detection | PASS | `AndroidDeviceStateProvider` (battery, charging, idle, thermal, RAM, power-save). |
| AI-05 | Energy policy | PASS | `EnergyPolicy`: ≤15% battery or severe thermal → rules only; ≤40%/power-save/low RAM/elevated thermal → small models only; charging+idle → maintenance allowed. Event-driven, no background AI. |
| AI-06 | Prompt versioning | PASS | `PromptRegistry` (id + version); audit records provider id. |
| AI-07 | Structured output | PASS | `task-extraction/v2` (per-item dates, preferred start, repeat, reminder time), strict JSON (unknown keys rejected). Prompt `task-extraction` v2, extractor `rule-extractor/2`. |
| AI-08 | Output validation | PASS | Schema → domain (dates, ranges, sizes, safety) → planner validation. |
| AI-09 | Golden dataset | PASS | `tests/testdata/golden/task-extraction.v2.json` (75 cases: the 25 v1 cases unchanged plus messy text, time formats, soft times, deadlines, durations, dates, several days, repeats, reminders and one more injection case). Extraction threshold raised from 0.90 to 0.95. The v1 file is kept for history. |
| AI-10 | Evaluation thresholds | PASS | Structured-output validity 100%, extraction accuracy ≥90%, plan validity 100%, unsafe-action rejection 100%. Current: 100% / 100% / 100% / 100%. Judged by deterministic rules, never an LLM judge. |
| AI-11 | Fallback | PASS | Any provider failure, timeout, exception or invalid output → deterministic extractor; if that fails, "not understood" and nothing happens. |
| AI-12 | Voice data path | PASS (decision recorded) | Phase 1 voice uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31+) / offline-only TTS. Devices without on-device recognition get no voice input rather than a cloud fallback. |
| AI-13 | Token/resource budgets | PASS | Input ≤2,000 chars; output ≤4,000 chars per request (16,000 hard cap in validator); timeout 5 s; ≤25 tasks / ≤15 events. |
| AI-14 | AI data privacy policy | PASS | Inference on device only; sensitive memories excluded from context; nothing logged. |
| AI-15 | AI cannot authorize actions | PASS | Model output never produces a `UserConfirmation` or a tool call; see 06. |
| AI-16 | Re-evaluation trigger | PASS | Any change to prompts, extractor, validator or provider must keep `GoldenEvaluationTest` green (CI). |

## Residual AI risks

- Rule-based extraction will mis-split unusual phrasing ("salt and pepper" becomes two tasks). Mitigated by the
  review-before-save proposal; measured by edit rate in alpha (02 H3).
- Duration estimates are heuristics (keyword table). Users can edit; alpha data will tune the table.
- The instruction detector is heuristic. It is defence in depth only; authority comes from the policy engine.
