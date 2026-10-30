#!/usr/bin/env python3
"""Prove all 50 approved ship sprites are inside the actual desktop JAR / APK.

This checks runtime deliverables rather than merely testing whether the PNGs
exist in the source checkout. Desktop libGDX assets live at the JAR root;
Android bundles its assets under assets/. Exact hashes preserve artist pixels.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/"assets/ships/factions"

def expected():
    approved=json.loads((ART/"approved-sprites.json").read_text(encoding="utf-8"))
    result={}
    for faction in approved["factions"]:
        for vessel in faction["ships"]:
            path=f"ships/factions/{faction['id']}/{vessel['hull']}.png"
            if path in result:raise ValueError("duplicate approved sprite "+path)
            result[path]=vessel["sha256"]
    if len(result)!=50:raise ValueError("must have exactly 50 approved ship PNG paths")
    return result

def verify(path,kind,approved):
    if not path.is_file():raise ValueError("missing "+str(path))
    prefix="assets/" if kind=="android" else ""
    with ZipFile(path) as z:
        present=set(z.namelist())
        for path_name,source_digest in approved.items():
            archive_name=prefix+path_name
            if archive_name not in present:
                raise ValueError(f"{kind} package missing approved sprite {archive_name}")
            image=z.read(archive_name)
            if not image.startswith(b"\\x89PNG\\r\\n\\x1a\\n"):
                raise ValueError("invalid packaged PNG "+archive_name)
            if hashlib.sha256(image).hexdigest()!=source_digest:
                raise ValueError(f"{kind} package corrupted or replaced {archive_name}")
    print(f"PASS {kind}: all 50 verified original sprite images embedded in {path}")

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--desktop-dir",type=Path)
    parser.add_argument("--android-apk",type=Path)
    args=parser.parse_args()
    if not args.desktop_dir and not args.android_apk:
        parser.error("Specify --desktop-dir and/or --android-apk")
    approved=expected()
    if args.desktop_dir:
        candidates=sorted(args.desktop_dir.glob("*.jar"))
        if len(candidates)!=1:
            raise ValueError(f"Expected exactly one desktop JAR in {args.desktop_dir}; found {len(candidates)}")
        verify(candidates[0],"desktop",approved)
    if args.android_apk:
        verify(args.android_apk,"android",approved)

if __name__=="__main__":
    try:main()
    except (OSError,ValueError,KeyError) as err:
        print("SHIP ART PACKAGE GATE FAILED: "+str(err),file=sys.stderr)
        sys.exit(1)
