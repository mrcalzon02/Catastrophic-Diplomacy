#!/usr/bin/env python3
"""Safely install only QA-approved Catastrophic Diplomacy weather effect PNGs.

Validates the exact 49 expected sprites and all manifest SHA256 hashes before
any files are written. The source ZIP is data and never executed.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

GROUPS = {
    "asteroid_rock": 8, "asteroid_impact": 8, "asteroid_shockwave": 7,
    "solar_beam": 5, "neutron_beam": 5, "ion_lightning": 8,
    "radiation_motes": 4, "nebula_haze": 4,
}

def validated(archive):
    try:
        manifest = json.loads(archive.read("manifest.json"))
    except (KeyError, ValueError) as exc:
        raise ValueError("Missing or invalid manifest.json") from exc
    if manifest.get("format") != "CD-WEATHER-VFX-QA-2":
        raise ValueError("Only the reviewed and corrected VFX pack is accepted")
    assets = manifest.get("assets", {})
    expected = {f"{name}_{i:02d}" for name, count in GROUPS.items()
                for i in range(count)}
    if set(assets) != expected:
        raise ValueError("Expected exactly 49 reviewed sprites, no extras or omissions")
    payloads = {}
    for stem in sorted(expected):
        info = assets[stem]
        filename = "frames/" + stem + ".png"
        if info.get("file") != filename:
            raise ValueError(f"Unexpected source mapping: {stem}")
        raw = archive.read(filename)
        if hashlib.sha256(raw).hexdigest() != info.get("sha256"):
            raise ValueError(f"SHA256 mismatch: {filename}")
        if len(raw) < 33 or raw[:8] != bytes([137,80,78,71,13,10,26,10]):
            raise ValueError(f"Not a valid PNG: {filename}")
        width, height, depth, color = struct.unpack_from(">IIBB", raw, 16)
        if [width, height] != info.get("size") or depth != 8 or color != 6:
            raise ValueError(f"Unexpected dimensions or non-RGBA pixels: {filename}")
        if info.get("safemargin_px", 0) < 8:
            raise ValueError(f"Insufficient transparent gutter: {filename}")
        payloads[stem + ".png"] = raw
    return payloads

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help="catastrophic_weather_fx_verified.zip")
    parser.add_argument("--output", type=Path,
                        default=Path("assets/effects/weather"))
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    with zipfile.ZipFile(args.archive) as archive:
        payloads = validated(archive)
    if args.verify_only:
        print(f"PASS: all {len(payloads)} verified VFX sprites; nothing installed")
        return
    args.output.mkdir(parents=True, exist_ok=True)
    for name, raw in payloads.items():
        (args.output / name).write_bytes(raw)
    print(f"Installed {len(payloads)} vetted sprites in {args.output}")

if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, zipfile.BadZipFile) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        sys.exit(1)
