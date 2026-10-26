#!/usr/bin/env python3
"""Functional packaging preflight for Fire tablets. Not a physical-device certification.

Supports existing Fire OS 7/8/14 families with the shared signed Android APK.
Fire OS 16 Appstore targeting requires targetSdk 36 and independent validation.
"""
from __future__ import annotations

import argparse
import pathlib
import re
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def require(value: bool, error: str) -> None:
    if not value:
        raise SystemExit("FAIL Fire tablet preflight: " + error)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=pathlib.Path, required=True)
    args = parser.parse_args()
    require(args.apk.is_file(), "APK missing")
    require(shutil.which("aapt") is not None, "Android SDK aapt required")
    result = subprocess.run(["aapt", "dump", "badging", str(args.apk)],
                            capture_output=True, text=True, check=True)
    text = result.stdout
    def line(pattern: str) -> str:
        match = re.search(pattern, text, re.MULTILINE)
        require(match is not None, "APK manifest field not found: " + pattern)
        return match.group(1)
    package = line(r"^package: name='([^']+)'")
    minimum = int(line(r"^sdkVersion:'([0-9]+)'"))
    target = int(line(r"^targetSdkVersion:'([0-9]+)'"))
    require(package == "games.catastrophicdiplomacy", "wrong game package ID")
    require(minimum <= 28, "Fire OS 7 tablets would be excluded")
    require(target >= 34, "Fire OS 14 target requirement not met")
    require("arm64-v8a" in text and "armeabi-v7a" in text,
            "Fire tablet ARM32/ARM64 native lib coverage incomplete")
    app_gradle = (ROOT / "android" / "build.gradle").read_text(encoding="utf-8")
    require(not re.search(r"(?i)(play-services-|firebase-|com\.google\.android\.gms)", app_gradle),
            "Google mobile service runtime dependency found in Fire distribution")
    launcher = (ROOT / "android" / "src" / "main" / "java" /
                "diplomacy" / "android" / "AndroidLauncher.java").read_text(encoding="utf-8")
    require("reqGlEsVersion>=0x30000" in launcher and "config.useGL30 = gl3" in launcher,
            "ES2 fallback compatibility is missing")
    print(f"PASS Fire tablet sideload package: {package}, minSdk {minimum}, "
          f"targetSdk {target}, ARM32/ARM64, GLES2 fallback, no Google services")
    if target < 36:
        print("NOT CERTIFIED: Fire OS 16 Appstore requires targetSdk >=36; "
              "hardware testing and store listing are separate gates.")
    else:
        print("NOTE: Fire OS 16 store/device eligibility still requires hardware and review.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
