#!/usr/bin/env bash
# Mobile performance NFR on the CI emulator (mobile.yml): Android cold start (median of 5, `am start -S -W` TotalTime) and APK size.
# Writes test-results/mobile-perf.json. Budgets: cold start < 2000 ms, APK < 40 MB.
set -u
# app id = android.package in apps/mobile/app.config.ts (or pass APP=<id>)
APP="${APP:-$(grep -oE "package: '[^']+'" apps/mobile/app.config.ts | head -1 | cut -d"'" -f2)}"
[ -n "$APP" ] || { echo 'mobile-perf: android package not found'; exit 0; }
APK=apps/mobile/android/app/build/outputs/apk/release/app-release.apk
mkdir -p test-results
: > test-results/coldstart.txt
for i in 1 2 3 4 5; do
  adb shell am force-stop "$APP"; sleep 2
  adb shell am start -S -W -n "$APP/.MainActivity" | sed -n 's/^TotalTime: //p' | tr -d '
' >> test-results/coldstart.txt
done
median=$(sort -n test-results/coldstart.txt | sed -n 3p)
apk_bytes=$(stat -c %s "$APK" 2>/dev/null || echo 0)
runs=$(paste -sd, test-results/coldstart.txt)
ok=true
[ "${median:-99999}" -lt 2000 ] || ok=false
[ "$apk_bytes" -lt 41943040 ] || ok=false
printf '{"cold_start_ms_median": %s, "cold_start_runs": [%s], "apk_bytes": %s, "budget": {"cold_start_ms": 2000, "apk_bytes": 41943040}, "pass": %s}
' "${median:-0}" "$runs" "$apk_bytes" "$ok" | tee test-results/mobile-perf.json
