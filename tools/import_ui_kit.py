#!/usr/bin/env python3
"""Verified, non-destructive import of both approved Catastrophic Diplomacy UI sheets.

Usage:
  python tools/import_ui_kit.py --zip /path/to/Catastrophic_Diplomacy_UI_Kit_v1.zip --check
  python tools/import_ui_kit.py --zip /path/to/Catastrophic_Diplomacy_UI_Kit_v1.zip --import

Keep original sheets under art_source/ui (not bundled in Android assets). Only
the selected tactical sprites, blank buttons and status bars enter assets/ui.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import zipfile

EXPECTED_PACKAGE_SHA256 = "8220b70e36e7133f8bbb5d1d89dacc0a1b14ee12671c481d822454901f80a170"
EXPECTED_SOURCES = {
    "art_source/ui/ui_sheet_01_systems.png": "3b74777d6c4a6dc8448fa0958f161b1b44967ef5a159a768bd36ca223e36417c",
    "art_source/ui/ui_sheet_02_combat.png": "7bca86a5ba6cb908f5985e34293157ccad0cb4fb23e4700112afc2f0b2622d26",
    "assets/ui/tactical/icons.png": "1bc99703f65496727ef3c08db82ea660db22f0581f811c1cf9c20c6fc14dc293",
}
PREFIXES = ("assets/ui/tactical/", "art_source/ui/")

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--zip", type=Path, required=True, help="Approved source UI kit ZIP")
    ap.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
    modes = ap.add_mutually_exclusive_group(required=True)
    modes.add_argument("--check", action="store_true")
    modes.add_argument("--import", dest="install", action="store_true")
    args = ap.parse_args()
    bundle = args.zip.read_bytes()
    if sha(bundle) != EXPECTED_PACKAGE_SHA256:
        ap.error("Unexpected UI kit ZIP checksum; refusing import")
    root = args.repo_root.resolve()
    with zipfile.ZipFile(args.zip) as z:
        index = json.loads(z.read("UI_ASSET_MANIFEST.json"))
        if index.get("schema") != "catastrophic-diplomacy-ui-v1" or index.get("approved_count") != 87:
            ap.error("Unexpected UI kit manifest/schema/count")
        chosen = [n for n in z.namelist() if n.startswith(PREFIXES)]
        if not chosen or len(set(chosen)) != len(chosen):
            ap.error("Missing or duplicate approved art entries")
        for n in chosen:
            p = Path(n)
            if p.is_absolute() or ".." in p.parts or "\\" in n or z.getinfo(n).is_dir():
                ap.error("Unsafe ZIP path: " + n)
        extracted = {n: z.read(n) for n in chosen}
        for n, digest in EXPECTED_SOURCES.items():
            if n not in extracted or sha(extracted[n]) != digest:
                ap.error("Source sprite checksum mismatch: " + n)
        already = 0
        collisions = []
        for n, data in extracted.items():
            destination = root / n
            if destination.exists():
                if not destination.is_file() or sha(destination.read_bytes()) != sha(data):
                    collisions.append(n)
                else:
                    already += 1
        if collisions:
            ap.error("Refusing to overwrite edited artwork: " + ", ".join(collisions))
        if args.check:
            print(f"CHECK OK: {len(extracted)} approved files; {already} already installed; no changes")
            return
        for n, data in extracted.items():
            out = root / n
            if out.exists():
                continue
            out.parent.mkdir(parents=True, exist_ok=True)
            fd, temp = tempfile.mkstemp(prefix=".ui-kit-", dir=out.parent)
            try:
                with os.fdopen(fd, "wb") as f:
                    f.write(data)
                os.replace(temp, out)
            finally:
                if os.path.exists(temp):
                    os.unlink(temp)
        print(f"IMPORTED: {len(extracted)} approved files; {already} unchanged")

if __name__ == "__main__":
    main()
