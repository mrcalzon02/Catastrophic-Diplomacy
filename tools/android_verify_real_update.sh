#!/usr/bin/env bash
# Release blocker: drive the real production Android updater end-to-end.
# The old/current updaterTest APKs use the production package and signer but
# are never published; debuggability exists only so CI can hash app-private state.
set -Eeuo pipefail

report="${1:?Report directory required}"
old_apk="${2:?Old updaterTest APK required}"
verifier_apk="${3:?Current updaterTest APK required}"
candidate_apk="${4:?Current release APK required}"
package=games.catastrophicdiplomacy
activity=diplomacy.android.AndroidLauncher
mkdir -p "$report"

version_code(){
  adb shell dumpsys package "$package" 2>/dev/null |
    sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n1 | tr -d '\r'
}
hash_state(){
  adb shell "run-as $package sh -c 'sha256sum \
files/campaign/authoritative-ledger-v1.dat \
files/network-profile/network.profile \
files/network-profile/player.identity \
shared_prefs/catastrophic-diplomacy-performance.xml'" |
    tr -d '\r' | sort
}
wait_log(){
  local pattern="$1" limit="$2" out="$3"
  for _ in $(seq 1 "$limit"); do
    adb logcat -d -v brief > "$out"
    if grep -qE "$pattern" "$out"; then return 0; fi
    sleep 1
  done
  return 1
}
installer_button(){
  local xml="$1"
  python3 - "$xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
try:
    root=ET.parse(sys.argv[1]).getroot()
except Exception:
    raise SystemExit(1)
for node in root.iter("node"):
    text=(node.get("text") or "").strip().casefold()
    if node.get("class")=="android.widget.Button" and text in {"update","install"}:
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",node.get("bounds",""))
        if m:
            x0,y0,x1,y1=map(int,m.groups())
            if x1>x0 and y1>y0:
                print((x0+x1)//2,(y0+y1)//2)
                raise SystemExit(0)
raise SystemExit(1)
PY
}

adb uninstall "$package" >/dev/null 2>&1 || true
adb install "$old_apk" > "$report/fixture-install.txt"
before_code="$(version_code)"
if [[ "$before_code" != "1693" ]]; then
  echo "FAIL: updater fixture versionCode is $before_code, expected 1693" >&2
  exit 1
fi

adb logcat -c
adb shell am start -W -n "$package/$activity" --ez cd.seed_update_fixture true   > "$report/fixture-seed-start.txt"
wait_log 'CD-UPDATER-E2E.*SEEDED' 30 "$report/fixture-seed-logcat.txt" || {
  echo "FAIL: updater fixture did not seed production persistence state" >&2
  exit 1
}
hash_state > "$report/state-before.sha256"
test "$(wc -l < "$report/state-before.sha256")" -eq 4

# Grant the exact per-app permission that a user would enable in Android's
# "Install unknown apps" screen. No package is installed by adb during the
# production-updater segment itself.
adb shell appops set "$package" REQUEST_INSTALL_PACKAGES allow ||
  adb shell appops set "$package" android:request_install_packages allow
adb shell appops get "$package" > "$report/install-packages-appop.txt" || true

adb shell am force-stop "$package"
adb logcat -c
adb shell am start -W -n "$package/$activity" --ez cd.verify_real_update true   > "$report/production-update-start.txt"

coords=""
for attempt in $(seq 1 180); do
  adb shell uiautomator dump /sdcard/cd-real-updater.xml     > "$report/installer-poll-$attempt.txt" 2>&1 || true
  adb exec-out cat /sdcard/cd-real-updater.xml > "$report/installer.xml" 2>/dev/null || true
  if [[ -s "$report/installer.xml" ]]; then
    coords="$(installer_button "$report/installer.xml" 2>/dev/null || true)"
    if [[ -n "$coords" ]]; then break; fi
  fi
  sleep 2
done
if [[ -z "$coords" ]]; then
  adb logcat -d -v time > "$report/production-update-failure-logcat.txt" || true
  adb exec-out screencap -p > "$report/production-update-failure.png" 2>/dev/null || true
  echo "FAIL: production updater never reached Android Package Installer" >&2
  exit 1
fi
adb exec-out screencap -p > "$report/package-installer.png"
read -r install_x install_y <<< "$coords"
adb shell input tap "$install_x" "$install_y"

public_code="$before_code"
for _ in $(seq 1 120); do
  candidate="$(version_code)"
  if [[ "$candidate" =~ ^[0-9]+$ ]] && (( candidate > before_code )); then
    public_code="$candidate"
    break
  fi
  sleep 1
done
if (( public_code <= before_code )); then
  adb logcat -d -v time > "$report/package-installer-failure-logcat.txt" || true
  echo "FAIL: Package Installer did not apply a newer published APK" >&2
  exit 1
fi
printf 'fixtureVersionCode=%s\npublicUpdaterVersionCode=%s\n'   "$before_code" "$public_code" > "$report/version-transition.txt"

# The downloaded public build must be able to reopen the seeded authoritative
# campaign before CI installs any verifier/candidate APK.
adb logcat -c
adb shell am start -W -n "$package/$activity" > "$report/public-reopen-start.txt"
adb shell pidof "$package" > "$report/public-reopen-pid.txt"
wait_log 'CAMPAIGN.*Loaded atomic campaign generation 1' 30   "$report/public-reopen-logcat.txt" || {
  echo "FAIL: public updater target could not reopen seeded campaign" >&2
  exit 1
}

# Restore debuggability with the current-source, same-signer verifier build and
# compare the exact user-state files. This install is only for inspection; the
# updater download/install above was performed solely by production app code
# plus Android Package Installer.
adb install -r "$verifier_apk" > "$report/verifier-install.txt"
hash_state > "$report/state-after.sha256"
diff -u "$report/state-before.sha256" "$report/state-after.sha256"   > "$report/state-hash-diff.txt" || {
  cat "$report/state-hash-diff.txt" >&2
  echo "FAIL: campaign/research/fleet/settings/network identity changed across updater" >&2
  exit 1
}

# Finally put the actual release candidate over the preserved state and require
# it to launch and read the same campaign. This keeps candidate acceptance
# separate from the non-shipping verifier variant.
adb install -r "$candidate_apk" > "$report/candidate-install.txt"
if [[ -n "${CD_ANDROID_BUILD_SEQUENCE:-}" ]]; then
  expected=$((1000+CD_ANDROID_BUILD_SEQUENCE))
  actual="$(version_code)"
  [[ "$actual" == "$expected" ]] || {
    echo "FAIL: release candidate versionCode $actual != expected $expected" >&2
    exit 1
  }
fi
adb logcat -c
adb shell am start -W -n "$package/$activity" > "$report/candidate-reopen-start.txt"
sleep 4
adb shell pidof "$package" > "$report/candidate-reopen-pid.txt"
wait_log 'CAMPAIGN.*Loaded atomic campaign generation 1' 30   "$report/candidate-reopen-logcat.txt" || {
  echo "FAIL: release candidate could not reopen updater-preserved campaign" >&2
  exit 1
}
adb exec-out screencap -p > "$report/candidate-reopened.png"

echo "PASS: production updater metadata -> download -> signer/archive verification -> Package Installer -> reopen; exact campaign/research/fleet/settings/network files preserved"
