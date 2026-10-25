#!/usr/bin/env python3
"""Validate APK release checklist coverage and artifact-bound evidence (not Play approval)."""
import argparse
import datetime as dt
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECKLIST = ROOT / "docs" / "APK_RELEASE_COMPLIANCE_GATE.md"
IDS = ["GP-ID-01","GP-ID-02","GP-ID-03","GP-ID-04","GP-ID-05","GP-DS-01","GP-DS-02","GP-DS-03","GP-DS-04","GP-DS-05","GP-DS-06","GP-DS-07","GP-DS-08","GP-DS-09","GP-DS-10","GP-MOD-01","GP-MOD-02","GP-MOD-03","GP-MOD-04","GP-PI-01","GP-PI-02","GP-PI-03","GP-PI-04","GP-PI-05","GP-POL-01","GP-POL-02","GP-PUB-01","GP-PUB-02","GP-PUB-03","GP-PUB-04","GP-PUB-05","GP-PUB-06","GP-PUB-07","GP-OPS-01","GP-OPS-02","GP-OPS-03","GP-OPS-04","GP-OPS-05"]
PLAY_CORE = {"GP-ID-01", "GP-ID-02", "GP-ID-04", "GP-DS-01", "GP-DS-02", "GP-DS-03",
             "GP-DS-04", "GP-DS-05", "GP-DS-06", "GP-DS-08", "GP-POL-01",
             "GP-PUB-01", "GP-PUB-02", "GP-PUB-05", "GP-PUB-07"}


def error(message):
    raise ValueError(message)


def validate_checklist():
    text = CHECKLIST.read_text(encoding="utf-8")
    discovered = re.findall(r"^- \[ \] (GP-(?:ID|DS|MOD|PI|POL|PUB|OPS)-\d\d):", text, re.M)
    if len(discovered) != len(IDS) or len(set(discovered)) != len(IDS) or set(discovered) != set(IDS):
        error("Checklist coverage changed: expected all 38 distinct canonical control IDs")
    if "13ITYsIxm-LV6xGN3FLlMfDQqOBCZwC8LnMvyBiqm0Ic" not in text:
        error("Canonical master checklist link missing")
    print("PASS: all 38 indexed Android release checklist controls present (coverage only)")


def validate_release(path, apk_path):
    obj = json.loads(path.read_text(encoding="utf-8"))
    required = ("application_id", "release_version", "git_commit", "artifact_sha256",
                "distribution", "verified_at", "verifier", "controls")
    for key in required:
        if not obj.get(key):
            error(f"Missing evidence field: {key}")
    if obj["distribution"] not in ("play", "sideload", "internal"):
        error("distribution must be play, sideload or internal")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", str(obj["git_commit"])):
        error("git_commit must be an actual full 40-digit SHA, not an unverified branch name")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", str(obj["artifact_sha256"])):
        error("artifact_sha256 must be a SHA-256 digest")
    try:
        dt.date.fromisoformat(str(obj["verified_at"]))
    except ValueError:
        error("verified_at must be an ISO calendar date")
    if not isinstance(obj["controls"], dict):
        error("controls must be a JSON object")
    if set(obj["controls"]) != set(IDS):
        error(f"Missing/unknown controls: {sorted(set(IDS) ^ set(obj['controls']))}")
    failures = []
    for cid in IDS:
        row = obj["controls"][cid]
        if not isinstance(row, dict):
            failures.append(f"{cid}: invalid evidence record")
            continue
        status = row.get("status")
        evidence = row.get("evidence")
        if status not in ("PASS", "NOT_APPLICABLE") or not isinstance(evidence, str) or not evidence.strip():
            failures.append(f"{cid}: needs PASS with specific evidence, or reasoned NOT_APPLICABLE")
        elif status == "NOT_APPLICABLE":
            if not isinstance(row.get("reason"), str) or not row["reason"].strip():
                failures.append(f"{cid}: NOT_APPLICABLE needs a reason")
            if obj["distribution"] == "play" and cid in PLAY_CORE:
                failures.append(f"{cid}: essential Play control cannot be marked NOT_APPLICABLE")
    if failures:
        error("Incomplete APK release evidence:\n" + "\n".join(failures))
    if not apk_path.is_file():
        error(f"APK not found: {apk_path}")
    if apk_path.suffix.lower() != ".apk":
        error("Only an .apk artifact can be checked by this APK-specific gate")
    h = hashlib.sha256()
    with apk_path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    if h.hexdigest().lower() != obj["artifact_sha256"].lower():
        error("APK SHA-256 differs from the evidence record")
    print(f"PASS: complete evidence record for {obj['application_id']} / {obj['release_version']} "
          f"({obj['distribution']}), 38 controls, matched APK SHA-256")
    print("NOT YET CERTIFIED: human review, apksigner/aapt and hardware/Play Console verification still required.")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--checklist-only", action="store_true")
    p.add_argument("--evidence", type=Path)
    p.add_argument("--apk", type=Path)
    a = p.parse_args()
    if not a.checklist_only and (a.evidence is None or a.apk is None):
        p.error("For release verification supply BOTH --evidence and --apk; otherwise use --checklist-only")
    try:
        validate_checklist()
        if not a.checklist_only:
            validate_release(a.evidence, a.apk)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
