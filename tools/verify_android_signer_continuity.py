#!/usr/bin/env python3
"""Fail a release if its APK signing certificate differs from our first public APK.

Only public apksigner --print-certs output is read; no signing secrets or keys.
The reference is downloaded from the immutable, already-published
cd-v0.1.4-rc.1 release. Do not replace that reference with the newest
release: an accidental key rotation could otherwise become the new baseline.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

CERT = re.compile(r"^Signer #(\d+) certificate SHA-256 digest: ([0-9a-fA-F]{64})$")


def certificate_digests(text: str) -> tuple[str, ...]:
    signers: dict[int, str] = {}
    for line in text.splitlines():
        line = line.strip()
        if "certificate SHA-256 digest:" not in line:
            continue
        matched = CERT.fullmatch(line)
        if not matched:
            raise ValueError("Malformed APK certificate digest line")
        number = int(matched.group(1))
        if number in signers:
            raise ValueError("Duplicate APK signer")
        signers[number] = matched.group(2).lower()
    if not signers or sorted(signers) != list(range(1, len(signers) + 1)):
        raise ValueError("Missing or invalid APK signer certificate digests")
    return tuple(signers[n] for n in sorted(signers))


def check(reference: Path, candidate: Path) -> None:
    expected = certificate_digests(reference.read_text(encoding="utf-8"))
    actual = certificate_digests(candidate.read_text(encoding="utf-8"))
    if actual != expected:
        raise ValueError(
            "APK signing identity changed: in-place Android upgrades from "
            "cd-v0.1.4-rc.1 are incompatible. Restore the ORIGINAL release "
            "keystore/secrets; do NOT generate a new signing key or remove "
            "installed applications. Reference SHA-256: "
            + ",".join(expected) + "; candidate SHA-256: " + ",".join(actual)
        )
    print("PASS: release APK signer matches earliest published Android certificate "
          "(" + ",".join(actual) + ")")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--candidate", required=True, type=Path)
    args = parser.parse_args()
    try:
        check(args.reference, args.candidate)
    except (OSError, UnicodeError, ValueError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
