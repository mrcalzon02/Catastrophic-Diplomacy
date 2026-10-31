#!/usr/bin/env python3
"""Prove all approved hull, fighter, beam and material PNGs are inside the actual desktop JAR / APK.

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
    if len(result)!=50:raise ValueError("must have exactly 50 approved alien hull paths")
    original=ROOT/"asset_packs/stage1-runtime/approved.zip"
    if hashlib.sha256(original.read_bytes()).hexdigest()!=(
            "5d1035bb7ca5684dd4027f22e73e46fe043cad2fa14356adb41349da9436aaaa"):
        raise ValueError("Original Terran/beam archive SHA-256 mismatch")
    with ZipFile(original) as source:
        manifest=json.loads(source.read("runtime-art-manifest.json"))
        if manifest.get("schema")!="cd-runtime-art-stage1-v1" or len(manifest["files"])!=48:
            raise ValueError("Unexpected approved Terran/beam archive inventory")
        for path,record in manifest["files"].items():
            if not path.startswith(("assets/ships/terran/","assets/effects/beams/",
                                    "assets/materials/")):
                raise ValueError("Unexpected source path "+path)
            raw=source.read(path)
            if hashlib.sha256(raw).hexdigest()!=record["sha256"]:
                raise ValueError("Source PNG hash mismatch "+path)
            short=path.removeprefix("assets/")
            if short in result:raise ValueError("duplicate runtime asset "+short)
            result[short]=record["sha256"]
    if len(result)!=98:
        raise ValueError("Expected 50 alien hulls, 10 Terran hulls, 35 beams, 3 materials")
    parts=[ROOT/"asset_packs/fighter-craft"/f"part-{i:02d}.bin" for i in range(13)]
    original_fighters=b"".join(part.read_bytes() for part in parts)
    if hashlib.sha256(original_fighters).hexdigest()!=(
            "11d9bd179f7cdb891c0a8aafdb83eb16d635a9d012c2de8b3996125015bbf2eb"):
        raise ValueError("Original 50-fighter archive checksum mismatch")
    import io
    with ZipFile(io.BytesIO(original_fighters)) as source:
        manifest=json.loads(source.read("manifest.json"))
        if manifest.get("format")!="cd-fighters-v1" or manifest.get("count")!=50 or len(manifest["ships"])!=50:
            raise ValueError("Unexpected fighter sprite manifest")
        for ship in manifest["ships"]:
            role=ship["role"]
            faction=ship["faction"]
            name=f"{faction}/{role}.png"
            if ship["file"]!=name:
                raise ValueError("Unexpected original fighter path "+name)
            short="ships/fighters/"+name
            if short in result:
                raise ValueError("Duplicate approved fighter path "+short)
            result[short]=hashlib.sha256(source.read(name)).hexdigest()
    if len(result)!=148:
        raise ValueError("Expected 50 alien hulls, 10 Terran hulls, 35 beams, 3 materials, 50 fighters")
    # The five founding civilizations have their OWN ten-hull source sheets.
    # Their runtime crops are generated during asset preparation, and the
    # fighter bridge selects seven of these per civilization. Validate that
    # the exact generated pixels (not some other faction's art) ship in the
    # final archive as well.
    manifest=json.loads((ART/"legacy-sheets.json").read_text(encoding="utf-8"))
    if len(manifest["factions"])!=5 or len(manifest["roles"])!=10:
        raise ValueError("Unexpected founding-faction sprite sheet inventory")
    for faction in manifest["factions"]:
        for role in manifest["roles"]:
            relative=f"ships/legacy_factions/{faction['id']}/{role}.png"
            original=ROOT/"build/generated/cd-legacy-factions"/relative
            if not original.is_file():
                raise ValueError("Founding-faction sprite crop not generated: "+str(original))
            raw=original.read_bytes()
            if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
                raise ValueError("Invalid original founding-faction image: "+relative)
            if relative in result:
                raise ValueError("Duplicate founding-faction image path: "+relative)
            result[relative]=hashlib.sha256(raw).hexdigest()
    if len(result)!=198:
        raise ValueError("Expected 148 existing approved assets + 50 founding-faction hulls")
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
            if not image.startswith(bytes((137,80,78,71,13,10,26,10))):
                raise ValueError("invalid packaged PNG "+archive_name)
            if hashlib.sha256(image).hexdigest()!=source_digest:
                raise ValueError(f"{kind} package corrupted or replaced {archive_name}")
    print(f"PASS {kind}: all {len(approved)} original approved hull/fighter/FX images embedded in {path}")

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
