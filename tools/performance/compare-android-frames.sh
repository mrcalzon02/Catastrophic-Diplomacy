#!/usr/bin/env bash
# Run on one booted emulator. Keep renderer, OS, storage and APK verifier identical.
set -euo pipefail
apks=${1:?folder with base.apk and candidate.apk}
output=${2:?output directory}
mkdir -p "$output"
adb shell getprop ro.build.fingerprint > "$output/system-image.txt"
adb shell dumpsys SurfaceFlinger | awk '/GLES:/{print; found=1} END{if (!found) print "GLES info unavailable"}' > "$output/graphics.txt"
adb shell wm size > "$output/display.txt"
sha256sum "$apks/base.apk" "$apks/candidate.apk" | sed 's| [^ ]*/| |' > "$output/apks.sha256"
for trial in base-1 candidate-1 candidate-2 base-2; do
    side=${trial%-*}
    adb uninstall games.catastrophicdiplomacy >/dev/null 2>&1 || true
    bash tools/android_verify_demo.sh "$apks/$side.apk" "$output/$trial"
done
