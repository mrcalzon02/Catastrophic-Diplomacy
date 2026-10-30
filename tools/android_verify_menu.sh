#!/usr/bin/env bash
# Functional signed-APK navigation acceptance, not a cosmetic pixel-perfect test.
# Functional signed-APK taps: free-placed skirmish hull + permanent per-ship doctrine, then campaign.
set -euo pipefail
report="${1:-test-output/android}"
mkdir -p "$report"
package=games.catastrophicdiplomacy
python3 -m unittest discover -s tools -p "test_android_native_dialog.py" -q

# The signed sideload updater may legitimately offer published GitHub APKs at
# startup. Its native AlertDialog intercepts game taps; the 2026-10-29 failed
# run retained identical before/after screenshots showing this exact overlay.
# Use the Android accessibility hierarchy to dismiss ONLY its real Later
# button, never a permission prompt, installer, or arbitrary coordinate.
dismiss_optional_updater(){
  local phase="$1" attempt point x y xml="$report/$1-hierarchy.xml"
  for attempt in 1 2 3; do
    if timeout 25s adb shell uiautomator dump /sdcard/cd-navigation.xml \
         > "$report/$phase-uiautomator.log" 2>&1 &&
       timeout 10s adb exec-out cat /sdcard/cd-navigation.xml > "$xml"; then
      if ! point="$(python3 tools/android_native_dialog.py "$xml")"; then
        echo "FAIL: updater dialog visible but safe Later target could not be identified" >&2
        return 1
      fi
      if [[ -n "$point" ]]; then
        read -r x y <<< "$point"
        adb shell input tap "$x" "$y"
        echo "Dismissed the signed app's optional updater dialog via its actual Later control."
        sleep 1
        return 0
      fi
    fi
    sleep 1
  done
  if [[ ! -s "$xml" ]]; then
    echo "FAIL: could not inspect native Android overlays before game navigation" >&2
    return 1
  fi
}

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
dismiss_optional_updater first-launch
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
# Set multiple persistent, simultaneously active combat doctrines on ship 1.
# All controls act on the same selected ship, not a consumed waypoint queue.
touch_ui skirmish-placement 1000 171
sleep 2
capture skirmish-ship-orders
touch_ui skirmish-ship-orders 1120 534
touch_ui skirmish-ship-orders 1120 505
touch_ui skirmish-ship-orders 1120 476
touch_ui skirmish-ship-orders 1120 447
touch_ui skirmish-ship-orders 1120 360
sleep 2
capture skirmish-orders-programmed
assert_step 'STANDING ship=1 setting=AGGRESSION'
assert_step 'STANDING ship=1 setting=CAUTION'
assert_step 'STANDING ship=1 setting=RETREAT BELOW HULL'
assert_step 'STANDING ship=1 setting=FALL BACK BELOW SHIELDS'
assert_step 'STANDING ship=1 setting=POINT DEFENSE'
# The advanced-page controls operate independently on the same hull.
touch_ui skirmish-orders-programmed 900 580
sleep 1
capture skirmish-support-orders
touch_ui skirmish-support-orders 700 534
touch_ui skirmish-support-orders 1120 505
touch_ui skirmish-support-orders 700 476
touch_ui skirmish-support-orders 1120 447
sleep 1
capture skirmish-support-set
assert_step 'STANDING ship=1 setting=ENGINE SPEED LIMIT'
assert_step 'STANDING ship=1 setting=PD INTERCEPTION PLAN'
assert_step 'STANDING ship=1 setting=PD PROTECTION SCOPE'
assert_step 'STANDING ship=1 setting=FIGHTER OPERATIONS'
touch_ui skirmish-support-set 1050 60
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
# Separate small-fleet visual/functional fixture: every canonical hull
# silhouette appears once per team. Capture both fleet-wide and closer views
# from the REAL signed APK, rather than simulated diagrams.
adb shell am force-stop "$package"
adb logcat -c
adb shell am start -W -n "$package/diplomacy.android.AndroidLauncher"
sleep 5
dismiss_optional_updater hull-relaunch
capture hull-home
assert_step MAIN_MENU
touch_ui hull-home 640 384
sleep 2
capture hull-scenarios
assert_step SKIRMISH_SETUP
# HULL_COVERAGE is the tenth row, starting at virtual y=592-9*48.
touch_ui hull-scenarios 285 181
sleep 1
capture hull-coverage-selected
touch_ui hull-coverage-selected 1107 60
sleep 4
capture hull-coverage-battle
assert_step 'BATTLE HULL_COVERAGE'
adb logcat -d -v time > "$report/hull-coverage-logcat.txt"
grep 'CD-HULL-COVERAGE' "$report/hull-coverage-logcat.txt" > "$report/hull-coverage-telemetry.txt" || true
grep -q 'START 11v11 every size, 22 fitted ships, 11 distinct hulls per side' \
     "$report/hull-coverage-telemetry.txt" || {
    echo "FAIL: real small mixed battle does not contain every hull size" >&2
    exit 1
}
# Native zoom controls remain interactive while the heterogeneous fleet fights.
touch_ui hull-coverage-battle 65 100
touch_ui hull-coverage-battle 65 100
sleep 1
capture hull-coverage-detail
echo "PASS: signed APK launched a real 11v11 every-size skirmish and saved zoomed framebuffer proof."

# Relaunch the same installed signed APK and use its real interactive controls.
# This is a state transition/gameplay smoke, not a cosmetic pixel comparison.
adb shell am force-stop "$package"
adb logcat -c
adb shell am start -W -n "$package/diplomacy.android.AndroidLauncher"
sleep 5
dismiss_optional_updater campaign-relaunch
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
# The campaign map opens the commissioned-fleet dock first. Do not skip
# the actual persistent active/reserve roster screen to fabricate deployment.
touch_ui campaign-map 1080 60
sleep 2
capture campaign-dock
assert_step 'CAMPAIGN_DOCK owned=4 active=4 reserve=0'
# A new campaign starts with four real owned, active corvettes and zero credits.
# Enter deployment through the dock's real DEPLOY ACTIVE FLEET control.
touch_ui campaign-dock 1100 60
sleep 2
capture campaign-deployment
assert_step 'DEPLOYMENT 1'
# Switch the genuine four-ship roster from battle line to attack wedge.
# A fifth ship costs credits and cannot be silently spawned in a new campaign.
touch_ui campaign-deployment 1145 345
sleep 1
capture campaign-deployment-configured
touch_ui campaign-deployment-configured 1050 62
sleep 5
capture campaign-battle
assert_step 'CAMPAIGN_BATTLE 1 4 WEDGE'
echo "PASS: signed APK real skirmish hull drag + five persistent standing-order policies, and campaign four-owned-ship wedge -> multiwave combat."
