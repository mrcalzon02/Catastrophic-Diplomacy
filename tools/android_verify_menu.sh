#!/usr/bin/env bash
# Functional signed-APK navigation acceptance, not a cosmetic pixel-perfect test.
# Real taps must lead MAIN_MENU -> SKIRMISH_SETUP -> BATTLE on the same APK.
set -euo pipefail
report="${1:-test-output/android}"
mkdir -p "$report"
package=games.catastrophicdiplomacy
capture(){
  adb exec-out screencap -p > "$report/$1.png"
  test "$(wc -c < "$report/$1.png")" -gt 2000
}
# FitViewport(1280x720) maps our virtual UI into the actual landscape surface.
# Decode only the 24-byte PNG header; no OCR or image matching heuristics.
touch_ui(){
  local frame="$1" uix="$2" uiy="$3"
  local point
  point="$(python3 - "$report/$frame.png" "$uix" "$uiy" <<'PY'
import struct,sys
with open(sys.argv[1],'rb') as png:
    header=png.read(24)
assert header[:8]==b'\x89PNG\r\n\x1a\n'
w,h=struct.unpack('>II',header[16:24])
scale=min(w/1280,h/720)
ox=(w-1280*scale)/2
oy=(h-720*scale)/2
print(round(ox+float(sys.argv[2])*scale),round(h-oy-float(sys.argv[3])*scale))
PY
)"
  read -r x y <<< "$point"
  adb shell input tap "$x" "$y"
}
assert_step(){
  adb logcat -d -v time > "$report/signed-menu-logcat.txt"
  grep 'CD-GAME-FLOW' "$report/signed-menu-logcat.txt" > "$report/signed-menu-telemetry.txt" || true
  if ! grep -q "CD-GAME-FLOW.*$1" "$report/signed-menu-telemetry.txt"; then
    echo "FAIL: signed APK did not reach game state $1" >&2
    exit 1
  fi
  if ! adb shell pidof "$package" >/dev/null 2>&1; then
    echo "FAIL: signed APK exited before $1" >&2
    exit 1
  fi
}
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb logcat -c
adb shell am force-stop "$package"
adb shell am start -W -n "$package/diplomacy.android.AndroidLauncher"
sleep 5
capture main-menu
assert_step MAIN_MENU
touch_ui main-menu 640 384
sleep 3
capture skirmish-menu
assert_step SKIRMISH_SETUP
touch_ui skirmish-menu 1107 60
sleep 6
capture skirmish-battle
assert_step 'BATTLE CUSTOM'
if cmp -s "$report/main-menu.png" "$report/skirmish-menu.png"; then
  echo "FAIL: menu navigation produced an unchanged screen" >&2
  exit 1
fi
if cmp -s "$report/skirmish-menu.png" "$report/skirmish-battle.png"; then
  echo "FAIL: battle navigation produced an unchanged screen" >&2
  exit 1
fi
echo "PASS: signed APK launched a normal main menu, entered skirmish and began real battle."
