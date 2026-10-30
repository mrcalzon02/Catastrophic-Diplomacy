#!/usr/bin/env bash
# End-to-end *debug APK* smoke: launch libGDX on an emulator, inspect live 200v200
# combat telemetry, and preserve real display screenshots plus full logcat.
set -euo pipefail
apk="${1:?Usage: bash tools/android_verify_demo.sh path/to/debug.apk [report-dir]}"
report="${2:-test-output/android}"
mkdir -p "$report"
test -s "$apk" || { echo "Missing debug APK: $apk" >&2; exit 1; }
package="games.catastrophicdiplomacy"
activity="diplomacy.android.AndroidLauncher"
adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP || true
# Dismiss the operating system's immersive-mode tutorial before framebuffer verification.
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb install -r "$apk"
adb shell am force-stop "$package" || true
adb logcat -c
adb shell am start -W -n "$package/$activity" --ez cd.verify_200v200 true
# Capture a real framebuffer early, and again after the weapon proving ground runs.
sleep 6
adb exec-out screencap -p > "$report/early.png"
completed=0
for attempt in $(seq 1 75); do
    adb logcat -d -v time > "$report/logcat.txt"
    grep 'CD-DEMO-VERIFY' "$report/logcat.txt" > "$report/demo-telemetry.txt" || true
    if grep -q 'CD-DEMO-VERIFY.*FAIL 200v200' "$report/demo-telemetry.txt"; then
        echo "FAIL: in-game assertions failed; inspect demo-telemetry.txt" >&2
        exit 1
    fi
    if grep -q 'CD-DEMO-VERIFY.*PASS 200v200 mechanics+render-loop' "$report/demo-telemetry.txt"; then
        completed=1
        break
    fi
    if grep -qE 'AndroidRuntime.*Process: games\.catastrophicdiplomacy|Force finishing activity.*catastrophicdiplomacy' "$report/logcat.txt"; then
        echo "FAIL: Catastrophic Diplomacy process crashed during demonstration" >&2
        exit 1
    fi
    if grep -q 'Exit zygote because system server.*terminated' "$report/logcat.txt"; then
        echo "FAIL: emulator system_server died (infrastructure failure, not an app exception)" >&2
        exit 1
    fi
    if ! adb shell pidof "$package" >/dev/null 2>&1; then
        echo "FAIL: application exited before live combat verification" >&2
        exit 1
    fi
    sleep 4
done
if (( !completed )); then
    echo "FAIL: 200v200 proof never completed within timeout" >&2
    # Keep the real gameplay gate; add evidence that distinguishes app hangs,
    # failed verification assertions and a very slow but advancing emulator.
    echo "Most recent in-game verifier telemetry:" >&2
    tail -n 15 "$report/demo-telemetry.txt" >&2 || true
    timeout 10s adb shell pidof "$package" > "$report/process-at-timeout.txt" 2>&1 || true
    timeout 15s adb shell dumpsys activity activities > "$report/activity-at-timeout.txt" 2>&1 || true
    timeout 15s adb shell dumpsys gfxinfo "$package" > "$report/gfxinfo-at-timeout.txt" 2>&1 || true
    timeout 15s adb exec-out screencap -p > "$report/timeout.png" 2>/dev/null || true
    exit 1
fi
adb exec-out screencap -p > "$report/late.png"
# Additional *diagnostic* real-device frame: select a living vessel in the
# engagement and zoom in four steps so hull/damage texture quality can be
# inspected at meaningful size. This is evidence, not an aesthetic CI gate.
adb shell input tap 950 420 || true
for step in 1 2 3 4; do adb shell input tap 65 920 || true; done
sleep 2
adb exec-out screencap -p > "$report/late-hull-detail.png" || true
test "$(wc -c < "$report/early.png")" -gt 2000
test "$(wc -c < "$report/late.png")" -gt 2000
if cmp -s "$report/early.png" "$report/late.png"; then
    echo "FAIL: early and late framebuffer captures are identical (possible frozen renderer)" >&2
    exit 1
fi
sha256sum "$apk" "$report/early.png" "$report/late.png" > "$report/sha256.txt"
cat "$report/demo-telemetry.txt"
if grep -q 'ART_PENDING' "$report/demo-telemetry.txt"; then
    echo "WARNING: mechanics and GL loop passed; curated PNG stages are NOT certified in this APK."
else
    echo "NOTE: texture-load logs do not prove correct pixels, effects timing, or device FPS."
fi
echo "PASS: debug APK installed, 400-ship game executed with six weapons, GL rendered changing frames."
