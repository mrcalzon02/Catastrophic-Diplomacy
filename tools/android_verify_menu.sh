#!/usr/bin/env bash
# Functional signed-APK navigation acceptance, not a cosmetic pixel-perfect test.
# Actual taps: free-placed skirmish hull + individual orders, then full campaign route.
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
map_ui(){
  local frame="$1" uix="$2" uiy="$3"
  python3 - "$report/$frame.png" "$uix" "$uiy" <<'PY'
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
}
touch_ui(){
  local x y
  read -r x y <<< "$(map_ui "$1" "$2" "$3")"
  adb shell input tap "$x" "$y"
}
swipe_ui(){
  local from_x from_y to_x to_y
  read -r from_x from_y <<< "$(map_ui "$1" "$2" "$3")"
  read -r to_x to_y <<< "$(map_ui "$1" "$4" "$5")"
  adb shell input swipe "$from_x" "$from_y" "$to_x" "$to_y" 520
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
sleep 3
capture skirmish-deployment
assert_step 'SKIRMISH_DEPLOYMENT CUSTOM'
# Drag a real hull to a different valid coordinate, not an icon-only gesture.
touch_ui skirmish-deployment 500 319
swipe_ui skirmish-deployment 500 319 180 334
sleep 2
capture skirmish-placement
assert_step 'PLACED ship=1'
# Program three ordered commands for that individual vessel.
touch_ui skirmish-placement 1000 171
sleep 2
capture skirmish-ship-orders
touch_ui skirmish-ship-orders 1000 484
touch_ui skirmish-ship-orders 620 370
touch_ui skirmish-ship-orders 1000 413
touch_ui skirmish-ship-orders 1000 344
sleep 2
capture skirmish-orders-programmed
assert_step 'ORDER ship=1 count=3 command=ENGAGE'
touch_ui skirmish-orders-programmed 1050 60
sleep 1
capture skirmish-ready
touch_ui skirmish-ready 1050 62
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
# Relaunch the same installed signed APK and use its real interactive controls.
# This is a state transition/gameplay smoke, not a cosmetic pixel comparison.
adb shell am force-stop "$package"
adb logcat -c
adb shell am start -W -n "$package/diplomacy.android.AndroidLauncher"
sleep 5
capture campaign-main-menu
assert_step MAIN_MENU
# HOME's second item: NEW CAMPAIGN.
touch_ui campaign-main-menu 640 448
sleep 2
capture campaign-faction
assert_step CAMPAIGN_FACTION_SETUP
# Explicitly accept faction/setup (never silently replace an existing save).
touch_ui campaign-faction 1050 60
sleep 2
capture campaign-map
assert_step 'CAMPAIGN_MAP 1'
# First campaign system is unlocked, and its bottom-right action opens deployment.
touch_ui campaign-map 1080 60
sleep 2
capture campaign-deployment
assert_step 'DEPLOYMENT 1'
# Select five real ships and switch from battle line to attack wedge.
touch_ui campaign-deployment 1145 452
touch_ui campaign-deployment 1145 345
sleep 1
capture campaign-deployment-configured
touch_ui campaign-deployment-configured 1050 62
sleep 5
capture campaign-battle
assert_step 'CAMPAIGN_BATTLE 1 5 WEDGE'
echo "PASS: signed APK real skirmish hull drag + three individual queued commands, and campaign five-ship wedge -> multiwave combat."
