#!/usr/bin/env bash
# Functional Android acceptance for the actual production update prompt.
# Opens the SAME dialog through a debug-only intent; no network or mock rendering.
set -euo pipefail
report="${1:-test-output/android}"
mkdir -p "$report"
package=games.catastrophicdiplomacy
activity=diplomacy.android.AndroidLauncher
adb shell am force-stop "$package"
adb shell am start -W -n "$package/$activity" --ez cd.verify_update_dialog true
sleep 3
adb shell uiautomator dump /sdcard/cd-update-dialog.xml > "$report/updater-dialog-uiautomator.log"
adb exec-out cat /sdcard/cd-update-dialog.xml > "$report/updater-dialog-hierarchy.xml"
adb exec-out screencap -p > "$report/updater-dialog.png"
test "$(wc -c < "$report/updater-dialog.png")" -gt 2000

# Check actual Android accessibility controls, not source-string assertions.
# The previous regression rendered only the explanation and LATER, with no
# clickable update choice; this test must fail for that behavior.
point="$(python3 - "$report/updater-dialog-hierarchy.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
nodes = list(root.iter("node"))
labels = [(n.get("text") or "").strip().casefold() for n in nodes]
if "update available" not in labels:
    raise SystemExit("FAIL: live update dialog title missing")
for wanted in ("update", "later", "view release"):
    if not any(n.get("class") == "android.widget.Button" and
               (n.get("text") or "").strip().casefold() == wanted for n in nodes):
        raise SystemExit("FAIL: live updater missing actionable " + wanted + " button")
buttons = [n for n in nodes if n.get("class") == "android.widget.Button" and
           (n.get("text") or "").strip().casefold() == "later"]
if len(buttons) != 1:
    raise SystemExit("FAIL: ambiguous Later button")
bounds = buttons[0].get("bounds", "")
match = re.fullmatch(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]", bounds)
if not match:
    raise SystemExit("FAIL: Later button has invalid bounds")
x0, y0, x1, y1 = map(int, match.groups())
if x1 <= x0 or y1 <= y0:
    raise SystemExit("FAIL: Later button has empty hitbox")
print((x0+x1)//2, (y0+y1)//2)
PY
)"
read -r tap_x tap_y <<< "$point"
adb shell input tap "$tap_x" "$tap_y"
sleep 1
adb shell uiautomator dump /sdcard/cd-update-dismissed.xml > "$report/updater-dismiss-uiautomator.log"
adb exec-out cat /sdcard/cd-update-dismissed.xml > "$report/updater-dismiss-hierarchy.xml"
if grep -qi 'text="Update available"' "$report/updater-dismiss-hierarchy.xml"; then
    echo "FAIL: Later did not dismiss the native update dialog" >&2
    exit 1
fi
adb shell pidof "$package" >/dev/null
echo "PASS: real Android updater displays Update / Later / View release and Later dismisses it."
