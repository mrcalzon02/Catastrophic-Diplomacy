#!/usr/bin/env bash
# Nonblocking visual QA evidence: REAL debug APK GL2/GL3 ship renderer with
# four fitted approved ships. Damage states come from DirectionalDefense;
# these images are NOT mockups or proof of player-fired ammunition.
set -euo pipefail
report="${1:-test-output/android}"
mkdir -p "$report"
package="games.catastrophicdiplomacy"
activity="diplomacy.android.AndroidLauncher"
adb shell am force-stop "$package" || true
adb logcat -c
adb shell am start -W -n "$package/$activity" --ez cd.verify_surface_damage true
: > "$report/armor-stage-telemetry.txt"
# The authored hull/material atlas can take longer than a fixed five-second
# delay to finish on software-rendered emulators. Poll for the game-reported
# real review scene, not an arbitrary menu framebuffer. All optional: release
# gates remain functional-only.
ready=false
for attempt in $(seq 1 30); do
    adb logcat -d -v time > "$report/armor-stage-logcat.txt"
    grep 'CD-SURFACE-VERIFY' "$report/armor-stage-logcat.txt" > "$report/armor-stage-telemetry.txt" || true
    if grep -q 'READY 4 fitted original sprites' "$report/armor-stage-telemetry.txt"; then
        ready=true
        break
    fi
    if ! adb shell pidof "$package" >/dev/null 2>&1; then
        echo "DIAGNOSTIC UNAVAILABLE: review activity exited before reaching READY" >&2
        exit 1
    fi
    sleep 1
done
if [[ "$ready" != true ]]; then
    echo "DIAGNOSTIC UNAVAILABLE: Android review never reported READY; see captured logcat" >&2
    exit 1
fi
if ! adb shell pidof "$package" >/dev/null 2>&1; then
    echo "DIAGNOSTIC UNAVAILABLE: Android app exited before stage capture" >&2
    exit 1
fi
adb exec-out screencap -p > "$report/armor-stage-review.png"
if [[ "$(wc -c < "$report/armor-stage-review.png")" -lt 2000 ]]; then
    echo "DIAGNOSTIC UNAVAILABLE: real game framebuffer capture too small" >&2
    exit 1
fi
sha256sum "$report/armor-stage-review.png" > "$report/armor-stage-sha256.txt"
echo "DIAGNOSTIC: captured REAL Android renderer stages — intact, scuffed armor, exposed metal, penetrated."
echo "NOT a cosmetic release approval. Inspect the PNG for correct source art and legible damage."
