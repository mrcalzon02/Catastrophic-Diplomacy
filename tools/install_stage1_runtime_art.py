#!/usr/bin/env python3
"""Install the original verified Stage 1 Terran and beam art with no recompression.

Usage:
  python3 tools/install_stage1_runtime_art.py /path/to/Catastrophic_Diplomacy_Runtime_Art_Stage1.zip
  python3 tools/install_stage1_runtime_art.py --check /path/to/Catastrophic_Diplomacy_Runtime_Art_Stage1.zip

Source is the project Library's signed-off 2026-10-24 archive. Exact archive,
manifest, and individual image digests are checked before any installation.
An existing modified file stops the entire import; no artist pixels overwritten.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
from zipfile import ZipFile, BadZipFile

ROOT=Path(__file__).resolve().parent.parent
ARCHIVE_SHA256="5d1035bb7ca5684dd4027f22e73e46fe043cad2fa14356adb41349da9436aaaa"
MANIFEST_SHA256="d71080b42de1d27f0c19ba9be80c42d846948e5064c90348efe4298a6db0a7e6"
SHIP_NAMES={"interceptor","corvette","missile_corvette","frigate","destroyer",
            "light_cruiser","heavy_cruiser","battlecruiser","carrier","dreadnought"}
MATERIAL_NAMES={"shield_energy_blue","structure_damaged","structure_destroyed"}
BEAM_COLORS={"blue","red","green","violet","gold"}

def digest(data):return hashlib.sha256(data).hexdigest()

def expected_paths():
    paths={"assets/ships/terran/"+name+".png" for name in SHIP_NAMES}
    paths|={"assets/materials/"+name+".png" for name in MATERIAL_NAMES}
    paths|={f"assets/effects/beams/{color}_{number:02d}.png"
            for color in BEAM_COLORS for number in range(1,8)}
    assert len(paths)==48
    return paths

def read_verified(archive):
    raw=archive.read_bytes()
    if digest(raw)!=ARCHIVE_SHA256:
        raise ValueError("Source archive checksum changed; refuse unapproved art")
    with ZipFile(archive) as z:
        if digest(z.read("runtime-art-manifest.json"))!=MANIFEST_SHA256:
            raise ValueError("Source manifest checksum changed")
        manifest=json.loads(z.read("runtime-art-manifest.json"))
        expected=expected_paths()
        if set(manifest["files"])!=expected:
            raise ValueError("Manifest must declare exactly ten Terran ships, 35 beams, 3 materials")
        if set(z.namelist())!=expected|{"README.txt","runtime-art-manifest.json"}:
            raise ValueError("Unexpected or missing archive entries")
        images={}
        for path,record in manifest["files"].items():
            data=z.read(path)
            if len(data)!=record["bytes"] or digest(data)!=record["sha256"]:
                raise ValueError("Corrupted source image "+path)
            if not data.startswith(bytes((137,80,78,71,13,10,26,10))):
                raise ValueError("Not a PNG: "+path)
            # Width/height and RGBA are read directly from the PNG IHDR.
            if len(data)<26:
                raise ValueError("Truncated PNG header: "+path)
            # Original shield/damage materials are indexed-color PNGs (type 3);
            # the Terran hull cutouts and beam frames are transparent RGBA (6).
            acceptable=(3,6) if path.startswith("assets/materials/") else (6,)
            if data[25] not in acceptable:
                raise ValueError("Unexpected original PNG color type: "+path)
            width=int.from_bytes(data[16:20],"big")
            height=int.from_bytes(data[20:24],"big")
            if [width,height]!=record["size"]:
                raise ValueError("Incorrect PNG bounds for "+path)
            images[path]=data
        return images

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check",action="store_true",
                        help="validate source but do not install into repository")
    parser.add_argument("archive",type=Path)
    options=parser.parse_args()
    images=read_verified(options.archive)
    for path,image in images.items():
        target=ROOT/path
        if target.exists() and digest(target.read_bytes())!=digest(image):
            raise ValueError("Existing edited file conflicts; refusing replacement: "+path)
    if options.check:
        print("PASS: all 48 original runtime PNGs verified; source checkout untouched")
        return
    installed=0
    for path,image in images.items():
        target=ROOT/path
        if target.exists():continue
        target.parent.mkdir(parents=True,exist_ok=True)
        temp=target.with_name(target.name+".tmp")
        temp.write_bytes(image)
        temp.replace(target)
        installed+=1
    print(f"PASS installed {installed} original PNGs, {len(images)} checksum-checked total")
    print("To publish: git add assets/ships/terran assets/effects/beams assets/materials")
    print("          git commit -m 'assets: restore original Terran fleet and weapon beam sprites'")
    print("          git push origin main")
    print("Do not claim remote commit before fetching main and verifying its file tree.")

if __name__=="__main__":
    try:main()
    except (OSError,ValueError,KeyError,BadZipFile) as err:
        print("STAGE1 ART IMPORT FAILED: "+str(err),file=sys.stderr)
        sys.exit(1)
