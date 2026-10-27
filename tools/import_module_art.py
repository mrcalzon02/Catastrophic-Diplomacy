#!/usr/bin/env python3
"""Import verified Catastrophic Diplomacy source artwork from the original ZIP.

Examples:
  python tools/import_module_art.py --archive /path/to/library.zip --check
  python tools/import_module_art.py --archive /path/to/library.zip --import
Only --import writes to the checkout. No overwrites of mismatched files.
"""
import argparse
import hashlib
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
LIST = ROOT / "assets/modules/source/manifest.txt"

def sha256(data):
    return hashlib.sha256(data).hexdigest()

def read_manifest():
    rows=[]
    seen=set()
    for line in LIST.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        path, checksum, width, height, status = line.split("|")
        p=pathlib.PurePosixPath(path)
        if p.is_absolute() or ".." in p.parts or len(checksum)!=64 or path in seen:
            raise ValueError("Invalid or duplicate manifest path: " + path)
        seen.add(path)
        rows.append((path, checksum, status))
    if len(rows)!=34:
        raise ValueError(f"Expected 34 source images; found {len(rows)}")
    return rows

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--archive",required=True,type=pathlib.Path)
    mode=p.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check",action="store_true",help="Validate the ZIP and hashes only")
    mode.add_argument("--import",dest="install",action="store_true",help="Copy images, refusing mismatched existing files")
    a=p.parse_args()
    rows=read_manifest()
    with zipfile.ZipFile(a.archive) as z:
        archive_paths=set(z.namelist())
        for path, expected, _ in rows:
            if path not in archive_paths:
                raise ValueError("Archive missing " + path)
            if sha256(z.read(path))!=expected:
                raise ValueError("SHA-256 mismatch: " + path)
        # Verify everything before the first filesystem modification.
        for path, expected, _ in rows:
            dest=ROOT/pathlib.Path(path)
            if dest.exists() and sha256(dest.read_bytes())!=expected:
                raise ValueError("Refusing to overwrite differing file: " + str(dest))
        if a.install:
            for path, _, _ in rows:
                dest=ROOT/pathlib.Path(path)
                if not dest.exists():
                    dest.parent.mkdir(parents=True,exist_ok=True)
                    dest.write_bytes(z.read(path))
    print(f"PASS {len(rows)} original art files validated" + (" and imported" if a.install else "; no files changed"))
    return 0

if __name__=="__main__":
    sys.exit(main())
