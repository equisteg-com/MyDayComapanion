# MyDayCompanion

A private, local-first Android day planner with an on-device assistant. You type what your day looks like; it
pulls out the tasks, proposes a plan around your fixed events, and only acts after you confirm.

Status: **Phase 0 foundation** (internal, not distributed). See [PROGRESS.md](PROGRESS.md).

## Principles

- No backend, no cloud AI, no accounts, no analytics, no ads, no paid services. The app has no INTERNET permission.
- AI output is untrusted. A deterministic policy decides what any tool may do; risky actions need a confirmation
  bound to the exact arguments.
- Everything is stored in an encrypted on-device database (Room + SQLCipher, key wrapped by Android Keystore).
  Backups are off. Full export and verified full deletion are built in.

## Layout

```
domain/   pure Kotlin: planner, task extraction, AI router, tools, authorization policy, memory, audit, privacy
data/     Android library: Room schema, SQLCipher, Keystore key, device state, permissions, export targets
app/      Jetpack Compose UI (Plan, History, Settings)
docs/     product/ (build docs), prebuild/ (precheck evidence 00-12), standards/, release/ …
tests/    test-cases.xlsx, bug-log.xlsx, golden AI eval data, test case notes
```

`apps/`, `packages/` and the npm/turbo files come from the EQUISTeG product template and are placeholders for this
product; see [docs/standards/template-overview.md](docs/standards/template-overview.md).

## Build and test

JDK 17+ and the Android SDK (compileSdk 36) are needed.

```
./gradlew test lintDebug assembleDebug
```

The golden AI evaluation report is written to `domain/build/reports/ai-eval/task-extraction.md`.

## Working on this repo

Read [CLAUDE.md](CLAUDE.md) and [CONTRIBUTING.md](CONTRIBUTING.md). Work happens on `feature/*` branches with a PR into
`development`, then `staging`, then `production`.
