#!/usr/bin/env python3
"""Fail-closed requirement: Play branch must contain current main, not old gameplay."""
import subprocess
import sys

def git(*args):
    p = subprocess.run(["git", *args], capture_output=True, text=True)
    if p.returncode:
        raise RuntimeError("git " + " ".join(args) + ": " + p.stderr.strip())
    return p.stdout.strip()

try:
    branch = git("rev-parse", "--abbrev-ref", "HEAD")
    if branch not in ("google-play", "HEAD"):
        raise RuntimeError("Play parity must run on google-play or CI detached HEAD")
    git("fetch", "--no-tags", "origin", "+refs/heads/main:refs/remotes/origin/main")
    main_sha = git("rev-parse", "refs/remotes/origin/main")
    head = git("rev-parse", "HEAD")
    status = subprocess.run(["git", "merge-base", "--is-ancestor", main_sha, head])
    if status.returncode == 0:
        print("PASS: google-play includes authoritative main " + main_sha)
        sys.exit(0)
    behind = git("rev-list", "--count", "HEAD..refs/remotes/origin/main")
    print("FAIL: GOOGLE_PLAY_MAIN_PARITY: Play branch does not contain main " + main_sha)
    print("FAIL: mainline behind by " + behind + " main commits at checked revision " + head)
    print("FIX: merge the latest main into google-play while resolving variant-specific Gradle/manifest/workflow conflicts; rerun functional and Play artifact gates. NEVER reset google-play to main or overwrite its distribution-specific changes.")
    sys.exit(1)
except Exception as error:
    print("FAIL: GOOGLE_PLAY_MAIN_PARITY_UNVERIFIED: " + str(error))
    print("FIX: fetch authoritative origin/main with complete history and retry ancestry verification; do not bypass.")
    sys.exit(1)
