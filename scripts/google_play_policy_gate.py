#!/usr/bin/env python3
"""Google Play source, APK/AAB and mandatory review gates. CI-only signatures are NOT upload keys."""
import datetime
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / "docs/google-play/build-contract.json").read_text())
ERRORS = []
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
FORBIDDEN = {"REQUEST_INSTALL_PACKAGES", "QUERY_ALL_PACKAGES", "MANAGE_EXTERNAL_STORAGE",
             "READ_SMS", "RECEIVE_SMS", "SEND_SMS", "READ_CALL_LOG",
             "WRITE_CALL_LOG", "SYSTEM_ALERT_WINDOW"}

def check(ok, label):
    print(("PASS: " if ok else "FAIL: ") + label)
    if not ok:
        ERRORS.append(label)

def cmd(*args):
    p = subprocess.run(args, capture_output=True, text=True)
    if p.returncode:
        raise RuntimeError(str(args) + " failed: " + p.stderr[-2000:])
    return p.stdout

def source():
    for mod in CONFIG["modules"]:
        gradle = ROOT / mod / "build.gradle"
        manifest = ROOT / mod / "src/main/AndroidManifest.xml"
        check(gradle.is_file() and manifest.is_file(), mod + ": Gradle and manifest exist")
        if not gradle.is_file() or not manifest.is_file():
            continue
        g = gradle.read_text()
        for key in ("compileSdk", "targetSdk"):
            check(bool(re.search(r"\b" + key + r"\s*(?:=)?\s*36\b", g)),
                  mod + ": " + key + " is API 36")
        root = ET.parse(manifest).getroot()
        app = root.find("application")
        check(app is not None, mod + ": application present")
        if app is not None:
            check(app.get(ANDROID + "usesCleartextTraffic") != "true",
                  mod + ": cleartext traffic not enabled")
            for component in app:
                if component.find("intent-filter") is not None:
                    check(component.get(ANDROID + "exported") in ("true", "false"),
                          mod + ": exported explicit on filtered component " +
                          str(component.get(ANDROID + "name")))
    if CONFIG["remove_install_permission"]:
        overlay = ROOT / CONFIG["module"] / "src/play/AndroidManifest.xml"
        check(overlay.is_file(), "Play manifest permission-removal overlay exists")
        if overlay.is_file():
            root = ET.parse(overlay).getroot()
            check(any(x.get(ANDROID + "name") == "android.permission.REQUEST_INSTALL_PACKAGES"
                      and x.get(TOOLS + "node") == "remove"
                      for x in root.findall("uses-permission")),
                  "Play overlay explicitly removes REQUEST_INSTALL_PACKAGES")

def artifact():
    m = CONFIG["module"]
    base = m.split("/")[-1]
    apk = ROOT / m / "build/outputs/apk/play" / (base + "-play.apk")
    aab = ROOT / m / "build/outputs/bundle/play" / (base + "-play.aab")
    check(apk.is_file() and apk.stat().st_size > 0, "Play APK exists")
    check(aab.is_file() and aab.stat().st_size > 0, "Play AAB exists")
    if not apk.is_file() or not aab.is_file():
        return
    with zipfile.ZipFile(apk) as z:
        files = set(z.namelist())
        check({"AndroidManifest.xml", "classes.dex", "resources.arsc"} <= files,
              "APK manifest, dex, and resources packaged")
        check(z.testzip() is None, "APK integrity")
    with zipfile.ZipFile(aab) as z:
        files = set(z.namelist())
        check("base/manifest/AndroidManifest.xml" in files, "Bundle manifest packaged")
        check(any(x.startswith("base/dex/") and x.endswith(".dex") for x in files),
              "Bundle executable dex packaged")
        check(z.testzip() is None, "AAB integrity")
    sdk = pathlib.Path(os.environ.get("ANDROID_SDK_ROOT") or
                       os.environ.get("ANDROID_HOME", "")) / "build-tools/36.0.0"
    aapt, signer = str(sdk / "aapt"), str(sdk / "apksigner")
    badging = cmd(aapt, "dump", "badging", str(apk))
    pkg = re.search(r"^package: name='([^']+)'", badging, re.M)
    target = re.search(r"^targetSdkVersion:'(\d+)'", badging, re.M)
    check(pkg is not None and pkg.group(1) == CONFIG["package"], "Play package ID")
    check(target is not None and int(target.group(1)) >= 36, "Built target SDK 36+")
    perms = cmd(aapt, "dump", "permissions", str(apk))
    found = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", perms))
    bad = sorted(p for p in found if p.split(".")[-1] in FORBIDDEN)
    check(not bad, "Restricted permissions absent: " + str(bad))
    unexpected = sorted(found - set(CONFIG["allowed_permissions"]))
    check(not unexpected, "No undeclared merged permissions: " + str(unexpected))
    sig = cmd(signer, "verify", "--verbose", "--print-certs", str(apk))
    check("Signer #1 certificate SHA-256 digest:" in sig, "APK cryptographic signature verifies")
    analyzer = shutil.which("apkanalyzer")
    if not analyzer:
        for suffix in ("cmdline-tools/latest/bin/apkanalyzer", "tools/bin/apkanalyzer"):
            possible = pathlib.Path(os.environ.get("ANDROID_SDK_ROOT", "")) / suffix
            if possible.is_file():
                analyzer = str(possible)
                break
    check(bool(analyzer), "SDK APK manifest analyzer present")
    if analyzer:
        check(cmd(analyzer, "manifest", "debuggable", str(apk)).strip().lower() == "false",
              "Release artifact not debuggable")

def review():
    ledger = json.loads((ROOT / "docs/google-play/policy-review.json").read_text())
    check(ledger.get("package") == CONFIG["package"], "App-specific policy ledger")
    check(ledger.get("policy_center") == "https://play.google/developer-content-policy/",
          "Official policy-center source recorded")
    entries = ledger.get("policies", [])
    check(len(entries) >= 56 and len({x.get("name") for x in entries}) == len(entries),
          "Complete distinct policy coverage")
    for e in entries:
        ok = e.get("status") in ("compliant", "not-applicable")
        ok = ok and len(str(e.get("evidence", "")).strip()) >= 15
        ok = ok and len(str(e.get("reviewer", "")).strip()) >= 3
        try:
            age = (datetime.date.today() -
                   datetime.date.fromisoformat(e.get("reviewed_on", ""))).days
            ok = ok and 0 <= age <= 90
        except (ValueError, TypeError):
            ok = False
        check(ok, "Reviewed policy: " + str(e.get("name")))
    for key in ("privacy_policy_url", "data_safety_evidence", "content_rating_evidence",
                "target_audience_evidence", "store_listing_evidence", "sdk_inventory_evidence"):
        value = ledger.get(key, "")
        check(isinstance(value, str) and len(value.strip()) >= 12,
              "Required Play Console declaration: " + key)
    check(str(ledger.get("privacy_policy_url", "")).startswith("https://"),
          "HTTPS privacy-policy URL recorded")
    print("This ledger cannot replace human review or Google Play Console approval.")

if __name__ == "__main__":
    phase = sys.argv[1] if len(sys.argv) == 2 else ""
    if phase not in ("source", "artifact", "review"):
        sys.exit("Usage: google_play_policy_gate.py source|artifact|review")
    try:
        {"source": source, "artifact": artifact, "review": review}[phase]()
    except Exception as error:
        check(False, "Validator error: " + str(error))
    print("GOOGLE PLAY " + phase.upper() + ": " + ("FAIL" if ERRORS else "PASS"))
    sys.exit(1 if ERRORS else 0)
