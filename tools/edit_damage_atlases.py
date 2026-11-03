#!/usr/bin/env python3
"""Unpack/repack the exact individually editable 24px damage PNG assets.
Only slices and joins existing PNG pixels; does not design any shape."""
import sys
from pathlib import Path
from PIL import Image

FAMILIES=("laser","kinetic","plasma","particle","missile","torpedo","fighter")
SIZE=24
ROOT=Path(__file__).resolve().parents[1]
ATLAS=ROOT/"assets"/"damage_masks"
EDIT=ROOT/"tools"/"editable_damage_masks"

def unpack():
    for family in FAMILIES:
        path=ATLAS/(family+".png")
        with Image.open(path) as source:
            rgba=source.convert("RGBA")
            if rgba.size!=(6*SIZE,SIZE):
                raise ValueError(f"Bad native atlas size: {path}")
            target=EDIT/family
            target.mkdir(parents=True,exist_ok=True)
            for i in range(6):
                rgba.crop((i*SIZE,0,(i+1)*SIZE,SIZE)).save(
                    target/f"v{i+1}.png",optimize=True)
    print(f"Unpacked 42 independent 24x24 masks to {EDIT}")

def pack():
    for family in FAMILIES:
        canvas=Image.new("RGBA",(6*SIZE,SIZE),(0,0,0,255))
        for i in range(6):
            path=EDIT/family/f"v{i+1}.png"
            with Image.open(path) as source:
                if source.size!=(SIZE,SIZE):
                    raise ValueError(f"Wrong 24px frame size: {path}")
                canvas.paste(source.convert("RGBA"),(i*SIZE,0))
        canvas.save(ATLAS/(family+".png"),optimize=True)
    print("Repacked 7 runtime PNG atlases from 42 hand-editable images.")

if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1] not in ("pack","unpack"):
        raise SystemExit("Usage: python3 tools/edit_damage_atlases.py [pack|unpack]")
    (pack if sys.argv[1]=="pack" else unpack)()
