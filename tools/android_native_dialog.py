#!/usr/bin/env python3
"""Locate the actual Android updater's Later button in a UIAutomator XML dump.

Print a physical-screen tap coordinate only when the known updater dialog is
present. Never dismiss unrelated system permission or installation dialogs.
"""
import re
import sys
import xml.etree.ElementTree as ET

TITLE = "Available GitHub APK releases"
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def dismiss_point(xml_text):
    root = ET.fromstring(xml_text)
    nodes = list(root.iter("node"))
    if not any(node.get("text", "").strip() == TITLE for node in nodes):
        return None
    buttons = [node for node in nodes if node.get("text", "").strip().casefold() == "later"
               and (node.get("class", "").endswith("Button")
                    or node.get("resource-id", "") == "android:id/button2")]
    if len(buttons) != 1:
        raise ValueError("Known updater dialog is visible, but its Later button is not unique")
    match = BOUNDS.fullmatch(buttons[0].get("bounds", ""))
    if not match:
        raise ValueError("Known updater Later button has no valid screen bounds")
    left, top, right, bottom = map(int, match.groups())
    if left >= right or top >= bottom:
        raise ValueError("Known updater Later button has empty screen bounds")
    return (left + right) // 2, (top + bottom) // 2


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: android_native_dialog.py uiautomator.xml")
    with open(sys.argv[1], encoding="utf-8") as handle:
        point = dismiss_point(handle.read())
    if point is not None:
        print(*point)


if __name__ == "__main__":
    main()
