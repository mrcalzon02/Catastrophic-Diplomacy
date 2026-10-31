#!/usr/bin/env bash
# Release blocker: real signed APK campaign creation and gameplay on Android 10.
# Tests every faction and two important transitions: new and saved campaign.
set -Eeuo pipefail
report="$1"
mkdir -p "$report"
package="games.catastrophicdiplomacy"
activity="$package/diplomacy.android.AndroidLauncher"
current="startup"
on_failure(){
  local rc="$1"
  echo "FAIL: API29 campaign playability at $current (exit $rc)" >&2
  adb logcat -d -v time > "$report/failure-$current-logcat.txt" || true
  adb shell dumpsys activity activities > "$report/failure-activity.txt" || true
}
trap 'on_failure "$?"' ERR
capture(){ adb exec-out screencap -p > "$report/$1.png"; }
point(){
  python3 - "$report/$1.png" "$2" "$3" <<'PY'
import struct,sys
with open(sys.argv[1],"rb") as stream:
    header=stream.read(24)
if header[:8]!=b"\x89PNG\r\n\x1a\n":
    raise SystemExit("Missing Android screen framebuffer for tap mapping")
w,h=struct.unpack(">II",header[16:24])
scale=min(w/1280,h/720)
ox=(w-1280*scale)/2
oy=(h-720*scale)/2
print(round(ox+float(sys.argv[2])*scale),round(h-oy-float(sys.argv[3])*scale))
PY
}
touch(){
  local x y
  read -r x y <<< "$(point "$1" "$2" "$3")"
  adb shell input tap "$x" "$y"
}
live(){ adb shell pidof "$package" >/dev/null 2>&1; }
wait_log(){
  local marker="$1" timeout_seconds="$2" second
  for second in $(seq 1 "$timeout_seconds"); do
    if ! live; then
      echo "FAIL: campaign app process exited before $marker" >&2
      return 1
    fi
    if adb logcat -d -v brief | grep -qE "$marker"; then return 0; fi
    sleep 1
  done
  echo "FAIL: no actual campaign telemetry: $marker" >&2
  return 1
}
assert_no_crash(){
  adb logcat -d -v time > "$report/$current-logcat.txt"
  if grep -E 'FATAL EXCEPTION:|NoSuchMethodError|NoClassDefFoundError|java.lang.VerifyError' "$report/$current-logcat.txt" \
      > "$report/$current-errors.txt"; then
    echo "FAIL: fatal Android runtime error in campaign $current" >&2
    cat "$report/$current-errors.txt" >&2
    return 1
  fi
  live
}
dismiss_updater(){
  local xml="$report/$current-uiautomator.xml" at x y
  for at in 1 2; do
    if timeout 25s adb shell uiautomator dump /sdcard/cd-campaign-window.xml \
         > "$report/$current-uiautomator.txt" 2>&1 &&
       timeout 10s adb exec-out cat /sdcard/cd-campaign-window.xml > "$xml"; then
      local target
      target="$(python3 tools/android_native_dialog.py "$xml")"
      if [[ -n "$target" ]]; then
        read -r x y <<< "$target"
        adb shell input tap "$x" "$y"
        sleep 1
      fi
      return 0
    fi
    sleep 1
  done
  echo "FAIL: cannot inspect native update overlay over campaign" >&2
  return 1
}
start_game(){
  adb shell am force-stop "$package"
  adb logcat -c
  adb shell am start -W -n "$activity" > "$report/$current-start.txt"
  wait_log 'CD-GAME-FLOW.*MAIN_MENU' 20
  dismiss_updater
  capture "$current-home"
}
adb shell pm clear "$package" > "$report/clear-package.txt"
factions=(TERRAN DRAXIAN CARNELIAN ZIPPLEDROW UZIPT MANDRALLY
          VAERITH_PRISM ZELTHAR_LUMINARI AUREATE_SYNOD NYX_BIOLITH UMBRA_FOUNDRY)
for idx in $(seq 0 10); do
  faction="$(printf '%s' "${factions[$idx]}")"
  current="$idx-$faction"
  start_game
  touch "$current-home" 930 512
  wait_log 'CD-GAME-FLOW.*CAMPAIGN_FACTION_SETUP' 15
  capture "$current-faction"
  if (( idx<6 )); then x=180; y=$((518-idx*62));
  else x=740; y=$((518-(idx-6)*62)); fi
  touch "$current-faction" "$x" "$y"
  capture "$current-choice"
  touch "$current-choice" 1050 60
  wait_log "CD-GAME-FLOW.*CAMPAIGN_MAP 1" 20
  wait_log "CD-CAMPAIGN-RUNTIME.*MENU_RENDER CAMPAIGN:$faction:1" 20
  capture "$current-map"
  touch "$current-map" 1080 60
  wait_log "CD-GAME-FLOW.*CAMPAIGN_DOCK owned=1 active=1 reserve=0" 20
  wait_log "CD-CAMPAIGN-RUNTIME.*MENU_RENDER DOCK:$faction:1" 20
  capture "$current-dock"
  touch "$current-dock" 1100 60
  wait_log 'CD-GAME-FLOW.*DEPLOYMENT 1' 20
  wait_log "CD-CAMPAIGN-RUNTIME.*MENU_RENDER DEPLOYMENT:$faction:1" 20
  capture "$current-deployment"
  touch "$current-deployment" 1050 62
  wait_log 'CD-GAME-FLOW.*CAMPAIGN_BATTLE 1 1 ' 20
  wait_log 'CD-CAMPAIGN-RUNTIME.*BATTLE_FRAME mission=1 tick=[1-9][0-9]* ' 35
  capture "$current-real-battle"
  assert_no_crash
  echo "PASS: Android 10 $faction -> map -> dock -> deployment -> actual drawn combat"
  if (( idx==0 )); then
    current="resume-$faction"
    start_game
    touch "$current-home" 210 512
    wait_log "CD-CAMPAIGN-RUNTIME.*MENU_RENDER CAMPAIGN:$faction:1" 20
    capture "$current-reopened-map"
    assert_no_crash
    echo "PASS: Android 10 persisted campaign loads into management interface"
  fi
done
echo "PASS: Android 10 campaign + live mission rendering for all 11 factions"
