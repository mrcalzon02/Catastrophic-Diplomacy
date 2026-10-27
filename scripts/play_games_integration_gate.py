#!/usr/bin/env python3
"""Fail-closed Play identity/save release eligibility with actionable error codes."""
import json
import os
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
CONFIG = json.loads((ROOT / "docs/google-play/build-contract.json").read_text())
STATE = json.loads((ROOT / "docs/google-play/integration-readiness.json").read_text())
FINDINGS = []

def check(ok, code, reason, fix):
    if ok:
        print("PASS: " + code + ": " + reason)
        return
    FINDINGS.append((code, reason, fix))
    print("FAIL: " + code + ": " + reason)
    print("  SOLUTION: " + fix)
    print("::error title=" + code + "::" + reason + " | Fix: " + fix)

def main():
    app = CONFIG["module"]
    gradle = (ROOT / app / "build.gradle").read_text()
    pkg = ("diplomacy.android" if CONFIG["package"] == "games.catastrophicdiplomacy"
           else CONFIG["package"])
    source = ROOT / app / "src/play/java" / Path(pkg.replace(".", "/")) / "PlayGamesApplication.java"
    manifest = ROOT / app / "src/play/AndroidManifest.xml"
    check("playImplementation 'com.google.android.gms:play-services-games-v2:22.1.0'" in gradle,
          "PGS-SDK-001", "Play Games v2 SDK must be Play-variant only",
          "Add playImplementation for the verified Play Games v2 SDK to the Play build type.")
    check(source.is_file() and all(token in source.read_text() for token in
          ["PlayGamesSdk.initialize", "isAuthenticated()", "getCurrentPlayer()"]),
          "PGS-AUTH-002", "Play-only automatic platform authentication is implemented",
          "Initialize the v2 SDK in the Play Application, check isAuthenticated, and retrieve the platform player without making it primary game identity.")
    check(manifest.is_file() and "com.google.android.gms.games.APP_ID" in manifest.read_text(),
          "PGS-ID-003", "Play Games project metadata is registered",
          "Register app metadata com.google.android.gms.games.APP_ID referencing @string/game_services_project_id.")
    check(STATE["app_id"] == CONFIG["package"],
          "PGS-APP-004", "Integration ledger matches this application's package ID",
          "Use the real Play Console app identity and a project-specific integration ledger.")

    project_id = os.environ.get("PLAY_GAMES_PROJECT_ID", "")
    check(bool(re.fullmatch(r"[1-9][0-9]{5,23}", project_id)),
          "PGS-CONSOLE-005", "No real Google Play Games project ID verified",
          "Create or configure Play Games Services for this game in Play Console. Set repository variable PLAY_GAMES_PROJECT_ID and confirm linked OAuth clients and testing accounts; do not reuse an unrelated app ID.")

    signin = STATE["in_game_identity"]
    check(signin["oauth_client_id"] != "pending" and
          signin["account_link_security_test"] != "pending",
          "PGS-ACCOUNT-006", "Primary in-game Sign in with Google / OpenID identity not verified",
          "Implement and run actual OpenID account authentication with server-validated tokens, stable game-account binding, account switching and account deletion; record test evidence. A Play Games player ID is not an in-game account.")

    save = STATE["cloud_save"]
    missing = [key for key in ("remote_backend", "serializer_tests", "restore_tests",
                               "conflict_resolution_tests", "account_switch_isolation_tests")
               if save.get(key, "pending") == "pending"]
    check(not missing, "PGS-SAVE-007",
          "Cloud save serialization, restores and conflict tests incomplete: " + ", ".join(missing),
          "Connect the actual game persistence format to verified cloud snapshots/backend; preserve local save backups and test divergent device revisions and account ownership before enabling automatic sync.")

    devices = STATE["cross_device"]
    pending = [key for key, value in devices.items() if value == "pending"]
    check(not pending, "PGS-DEVICE-008",
          "Cross-device/reinstall acceptance missing: " + ", ".join(pending),
          "Test signed Play packages on two authorized devices, reinstall restore, offline edits, account switches and desktop linked-account progression. Record run IDs and checksums.")
    print("PLAY IDENTITY AND CLOUD RELEASE ELIGIBILITY: " +
          ("FAIL (" + str(len(FINDINGS)) + " causes)" if FINDINGS else "PASS"))
    return 1 if FINDINGS else 0

if __name__ == "__main__":
    sys.exit(main())
