#!/usr/bin/env python3
"""Fail-closed Play release CANDIDATE gate; no publication or key management."""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_ID = "games.catastrophicdiplomacy"


def run(argv):
    print("REQUIRED TEST:", " ".join(argv), flush=True)
    subprocess.run(argv, cwd=ROOT, check=True)


def hash_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def tool(name):
    candidate = shutil.which(name)
    if candidate:
        return candidate
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if sdk:
        build_tools = Path(sdk) / "build-tools"
        if build_tools.exists():
            options = sorted(build_tools.glob("*/" + name), reverse=True)
            options += sorted(build_tools.glob("*/" + name + ".bat"), reverse=True)
            if options:
                return str(options[0])
    raise ValueError(f"Required Android signing/package tool not installed: {name}")


def functional_tests():
    # NO stub / bypass: every listed subprocess must pass before proceeding.
    run([sys.executable, "tools/verify_apk_release_gate.py", "--checklist-only"])
    run(["gradle", ":core:smoke", ":desktop:classes", ":android:assembleDebug"])


def verify_artifacts(record_path, apk_path, aab_path, publish):
    record = json.loads(record_path.read_text(encoding="utf-8"))
    if record.get("distribution") != "play":
        raise ValueError("Play candidate evidence MUST declare distribution=play")
    if record.get("application_id") != APP_ID:
        raise ValueError(f"Incorrect Play application package ID; expected {APP_ID}")
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT,
                          check=True, capture_output=True, text=True).stdout.strip()
    if record.get("git_commit") != head:
        raise ValueError("Evidence commit does not match the actual tested source HEAD")
    run([sys.executable, "tools/verify_apk_release_gate.py",
         "--evidence", str(record_path.resolve()), "--apk", str(apk_path.resolve())])
    if not aab_path.is_file() or aab_path.suffix != ".aab":
        raise ValueError("Play candidate must provide a real signed Android App Bundle (.aab)")
    aab_sha = record.get("play_bundle_sha256")
    if not isinstance(aab_sha, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", aab_sha):
        raise ValueError("Play AAB SHA-256 absent from release evidence")
    if hash_file(aab_path) != aab_sha.lower():
        raise ValueError("Play AAB hash does not match the release evidence")
    signed = subprocess.run([tool("apksigner"), "verify", "--verbose", "--print-certs",
                             str(apk_path.resolve())], cwd=ROOT, check=True,
                            text=True, capture_output=True)
    match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F:]{64,95})", signed.stdout, re.I)
    if not match:
        raise ValueError("APK signing certificate fingerprint not available")
    signer = re.sub(r"[^0-9a-fA-F]", "", match.group(1)).lower()
    if signer != str(record.get("apk_signer_sha256", "")).lower():
        raise ValueError("Signed APK certificate differs from expected device-test signer")
    badging = subprocess.run([tool("aapt"), "dump", "badging", str(apk_path.resolve())],
                             cwd=ROOT, check=True, text=True, capture_output=True).stdout
    package_line = next((line for line in badging.splitlines() if line.startswith("package:")), "")
    if f"name='{APP_ID}'" not in package_line:
        raise ValueError("APK package name mismatch")
    version_code = re.search(r"versionCode='([0-9]+)'", package_line)
    version_name = re.search(r"versionName='([^']+)'", package_line)
    if not version_code or str(record.get("android_version_code")) != version_code.group(1):
        raise ValueError("Android versionCode is missing or does not match evidence")
    if not version_name or str(record.get("android_version_name")) != version_name.group(1):
        raise ValueError("Android versionName is missing or does not match evidence")
    sdk_line = next((line for line in badging.splitlines() if "targetSdkVersion:" in line), "")
    if not sdk_line:
        raise ValueError("APK target SDK could not be read")
    if not (record.get("play_target_api_policy_review")
            and record.get("play_console_registration_evidence")
            and record.get("physical_device_upgrade_evidence")):
        raise ValueError("Play API review, Console registration and physical-device upgrade evidence required")
    jarsigner = shutil.which("jarsigner")
    if not jarsigner:
        raise ValueError("JDK jarsigner unavailable for Play upload bundle verification")
    bundle = subprocess.run([jarsigner, "-verify", str(aab_path.resolve())],
                            cwd=ROOT, check=True, text=True, capture_output=True)
    if "jar verified" not in bundle.stdout.lower():
        raise ValueError("AAB jarsigner did not report a verified bundle")
    if publish and not record.get("play_console_approval_evidence"):
        raise ValueError("Publishing requires actual completed Play Console approval evidence")
    if record.get("play_bundle_package_id_verified") != APP_ID:
        raise ValueError("Independent AAB package-ID verification evidence is required")
    if record.get("play_bundle_version_verified") != str(record.get("release_version")):
        raise ValueError("Independent AAB release-version verification evidence required")
    expected_upload_signer = record.get("play_upload_signer_sha256")
    if not isinstance(expected_upload_signer, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", expected_upload_signer):
        raise ValueError("Separate Play upload certificate fingerprint missing")
    keytool = shutil.which("keytool")
    if not keytool:
        raise ValueError("keytool required for AAB upload signing certificate verification")
    cert = subprocess.run([keytool, "-printcert", "-jarfile", str(aab_path.resolve())],
                          cwd=ROOT, check=True, text=True, capture_output=True).stdout
    stamps = re.findall(r"SHA256:\s*([0-9a-fA-F:]{64,95})", cert)
    if not stamps or expected_upload_signer.lower() not in [
            re.sub(r"[^0-9a-fA-F]", "", stamp).lower() for stamp in stamps]:
        raise ValueError("AAB upload-signing certificate SHA-256 differs from expected")
    print("PASS: functional suites and signed APK/AAB candidate checks; no publication performed.")
    print("NOT YET PLAY-APPROVED: Google Play review, public rollout and external evidence still required.")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--preflight", action="store_true", help="Run all functional suites; no artifact acceptance")
    p.add_argument("--publish", action="store_true", help="Require actual Play Console approval in addition to candidate checks")
    p.add_argument("--evidence", type=Path)
    p.add_argument("--apk", type=Path)
    p.add_argument("--aab", type=Path)
    args = p.parse_args()
    if not args.preflight and not (args.evidence and args.apk and args.aab):
        p.error("Use --preflight, or provide ALL of --evidence, --apk and --aab")
    try:
        functional_tests()
        if args.preflight:
            print("PASS: Play-branch source-functional preflight only; release candidate still BLOCKED without artifact/Play/device proof")
        else:
            verify_artifacts(args.evidence, args.apk, args.aab, args.publish)
    except (ValueError, OSError, subprocess.CalledProcessError, json.JSONDecodeError) as exc:
        print(f"BLOCKED: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
