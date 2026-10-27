#!/usr/bin/env python3
"""Verify and install the exact approved graphics kit without destroying edited art.

  python tools/install_graphics_kit.py --archive Catastrophic_Diplomacy_Graphics_Integration_Kit.zip --check
  python tools/install_graphics_kit.py --archive Catastrophic_Diplomacy_Graphics_Integration_Kit.zip --install

The three worst clipped large-hull crops are quarantined by default. Override only
for visual prototyping with --allow-provisional-large-hulls.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import struct
import tempfile
import zipfile

APPROVED_SHA256 = "ed71aea2d831489ac325397741164c6a703d3321a014a34354459ac46bbbd391"
SOURCE_SCHEMA = "cd-graphics-1"
QUARANTINED = frozenset({"battlecruiser.png", "carrier.png", "dreadnought.png"})

def digest(data):
    return hashlib.sha256(data).hexdigest()

def allowed_path(name):
    path = PurePosixPath(name)
    if not name or name.startswith("/") or "\\" in name or ".." in path.parts:
        return False
    if path.parts[:3] == ("assets", "ships", "terran"):
        return len(path.parts) == 4 and path.suffix == ".png"
    if path.parts[:3] == ("assets", "effects", "beams"):
        return len(path.parts) == 4 and path.suffix == ".png"
    if path.parts[:4] == ("assets", "ui", "tactical", "buttons"):
        return len(path.parts) == 5 and path.suffix == ".png"
    return name in {"assets/ui/tactical/icons.png", "assets/ui/tactical/icons.tsv"}

def png_header(data, path):
    if len(data) < 33 or not data.startswith(b"\x89PNG\r\n\x1a\n") or data[12:16] != b"IHDR":
        raise ValueError("Invalid PNG: " + path)
    width,height = struct.unpack(">II",data[16:24])
    if width < 16 or height < 16 or width > 4096 or height > 4096:
        raise ValueError("Unexpected image dimensions: " + path)
    return width,height

def inspect(archive):
    if not archive.is_file():
        raise ValueError("Archive missing: " + str(archive))
    if digest(archive.read_bytes()) != APPROVED_SHA256:
        raise ValueError("Graphics archive SHA-256 differs from approved original")
    with zipfile.ZipFile(archive) as z:
        names=z.namelist()
        if len(set(names)) != len(names):
            raise ValueError("Duplicate ZIP members")
        source=json.loads(z.read("GRAPHICS_SHA256_MANIFEST.json"))
        if source.get("schema") != SOURCE_SCHEMA:
            raise ValueError("Unexpected graphics manifest schema")
        entries=source.get("entries")
        if not isinstance(entries,dict) or set(names) != set(entries) | {"GRAPHICS_SHA256_MANIFEST.json"}:
            raise ValueError("ZIP entries do not match manifest")
        selected={}
        for name in names:
            if name == "GRAPHICS_SHA256_MANIFEST.json":
                continue
            info=z.getinfo(name)
            if info.is_dir() or info.file_size > 10_000_000:
                raise ValueError("Unexpected ZIP member: " + name)
            data=z.read(name)
            if digest(data) != entries[name]:
                raise ValueError("Graphic hash mismatch: " + name)
            if not allowed_path(name):
                if name == "GRAPHICS_INSTALL_README.txt":
                    continue
                raise ValueError("Unsafe or unexpected runtime path: " + name)
            if name.endswith(".png"):
                png_header(data,name)
            selected[name]=data
        ships=[p for p in selected if p.startswith("assets/ships/terran/")]
        beams=[p for p in selected if p.startswith("assets/effects/beams/")]
        buttons=[p for p in selected if p.startswith("assets/ui/tactical/buttons/")]
        if len(ships)!=10 or len(beams)!=35 or len(buttons)!=10:
            raise ValueError("Approved 10/35/10 asset counts changed")
        if png_header(selected["assets/ui/tactical/icons.png"],"icons.png")!=(800,720):
            raise ValueError("UI atlas must be 800x720")
        lines=[p for p in selected["assets/ui/tactical/icons.tsv"].decode("utf-8").splitlines()
               if p.strip() and not p.startswith("#")]
        if len(lines)!=87:
            raise ValueError("UI icon registry must have 87 entries")
        return selected

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive",type=Path,required=True)
    parser.add_argument("--repo-root",type=Path,default=Path(__file__).resolve().parents[1])
    modes=parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--check",action="store_true")
    modes.add_argument("--install",action="store_true")
    parser.add_argument("--allow-provisional-large-hulls",action="store_true")
    args=parser.parse_args()
    approved=inspect(args.archive)
    total=len(approved)
    skipped=[]
    if not args.allow_provisional_large_hulls:
        for name in list(approved):
            if name.startswith("assets/ships/") and PurePosixPath(name).name in QUARANTINED:
                skipped.append(name)
                del approved[name]
    root=args.repo_root.resolve()
    changes=[]
    identical=0
    # Preflight all destinations so no collision leaves a partially installed set.
    for name,data in sorted(approved.items()):
        out=root.joinpath(*PurePosixPath(name).parts)
        if out.exists():
            if not out.is_file() or digest(out.read_bytes())!=digest(data):
                raise ValueError("Refusing to overwrite modified graphic: " + str(out))
            identical+=1
        else:
            changes.append((out,data))
    print("PASS: {} source graphics, {} unchanged, {} new, {} quarantined".format(
        total,identical,len(changes),len(skipped)))
    if skipped:
        print("QUARANTINED: " + ", ".join(PurePosixPath(p).name for p in skipped))
    if args.check:
        return
    for out,data in changes:
        out.parent.mkdir(parents=True,exist_ok=True)
        fd,temp=tempfile.mkstemp(prefix=".cd-graphic-",dir=out.parent)
        try:
            with os.fdopen(fd,"wb") as stream:
                stream.write(data)
            os.replace(temp,out)
        finally:
            if os.path.exists(temp):
                os.unlink(temp)
    print("INSTALLED {} approved files into {}. Commit/push separately; APK unverified.".format(
        len(changes),root))

if __name__=="__main__":
    try:
        main()
    except (ValueError,KeyError,OSError,json.JSONDecodeError,zipfile.BadZipFile) as exc:
        raise SystemExit("FAIL graphics import: " + str(exc))
