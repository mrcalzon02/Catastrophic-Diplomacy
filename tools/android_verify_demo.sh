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
# Interpret live simulator telemetry as a separately reported PERFORMANCE
# DIAGNOSTIC. The software-emulated GPU is not comparable to actual Android
# hardware, so bad measured timings must not masquerade as 30-FPS certification
# or become an arbitrary release blocker. Invalid/missing 400-ship progression
# is already rejected above by the objective runtime assertions.
python3 - "$report/demo-telemetry.txt" "$report/performance-diagnostic.json" <<'PY'
import json, math, pathlib, re, sys
source = pathlib.Path(sys.argv[1]).read_text(errors="replace")
success = [line for line in source.splitlines()
           if "PASS 200v200 mechanics+render-loop" in line]
if len(success) != 1:
    raise SystemExit("FAIL: expected exactly one in-game verifier PASS receipt")
receipt = success[0]
values = {}
for key in ("tick", "frames", "renderCpuAvgMs", "renderCpuWorstMs", "avgDraws", "avgMatBinds", "spriteShips"):
    matches = re.findall(r"(?:^|[ ])" + key + r"=([0-9]+(?:[.][0-9]+)?)", receipt)
    if len(matches) != 1:
        raise SystemExit(f"FAIL: missing or ambiguous real telemetry field {key}")
    values[key] = float(matches[0])
if not all(math.isfinite(v) and v >= 0 for v in values.values()):
    raise SystemExit("FAIL: invalid performance telemetry")
if values["frames"] < 2 or values["tick"] <= 0 or values["spriteShips"] < 400:
    raise SystemExit("FAIL: demo did not report live 400-ship rendered progression")
avg = values["renderCpuAvgMs"]
values["cpu_limited_fps_upper_bound"] = round(1000 / avg, 3) if avg > 0 else None
values["emulator"] = "pixel_2 Android 34 software graphics (not physical-device certification)"
values["interpretation"] = "render-thread CPU time only; excludes GPU, frame scheduling, and physical-device FPS"
values["target_frame_budget_ms"] = 1000 / 30
values["performance_status"] = ("ABOVE_30_FPS_CPU_BUDGET" if avg > 1000 / 30
                                else "WITHIN_30_FPS_CPU_BUDGET_NOT_FPS_CERTIFIED")
pathlib.Path(sys.argv[2]).write_text(json.dumps(values, indent=2, sort_keys=True) + chr(10))
print(f"PERFORMANCE DIAGNOSTIC: CPU render avg={avg:.1f} ms, worst={values['renderCpuWorstMs']:.1f} ms; "
      f"{values['frames']:.0f} rendered frames, {values['tick']:.0f} simulation ticks.")
if avg > 1000 / 30:
    print(f"WARNING: 30 FPS target NOT DEMONSTRATED on emulator; "
          f"render-thread CPU upper-bound rate only {values['cpu_limited_fps_upper_bound']:.2f} FPS. "
          "This is a diagnostic, not a cosmetic release blocker.")
else:
    print("NOTE: CPU budget is necessary, not sufficient, for 30 FPS on real devices.")
PY
if grep -q 'ART_PENDING' "$report/demo-telemetry.txt"; then
    echo "WARNING: mechanics and GL loop passed; curated PNG stages are NOT certified in this APK."
else
    echo "NOTE: texture-load logs do not prove correct pixels, effects timing, or device FPS."
fi
echo "PASS: debug APK installed, 400-ship game executed with six weapons, GL rendered changing frames."
