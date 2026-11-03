#!/usr/bin/env python3
"""Compare two ACTUAL framebuffer captures from the same real Battle.step() lanes.
No synthetic ship artwork or painted damage. The fixed crop shows source game
pixels at 1:1; a comparison image is composed only after both real screenshots
exist and their battle/provenance metadata match.
"""
import csv
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageChops

ROOT=Path("test-output/armor-ab")
MODES=("upscaled48","authored48")
SCENARIOS=(
    "laser-terran-upper",
    "kinetic-terran-upper",
    "missile-terran-upper",
    "plasma-terran-upper",
    "particle-terran-upper",
    "torpedo-terran-upper",
)
VIEWS=("tactical","close","surface")
OUT=ROOT/"comparisons"
OUT.mkdir(parents=True,exist_ok=True)

rows={}
for mode in MODES:
    p=ROOT/mode/"framebuffer"/"frames.csv"
    with p.open(newline="",encoding="utf-8") as stream:
        values=list(csv.DictReader(stream))
    rows[mode]={(row["scenario"],row["view"]):row for row in values}

manifest={
  "method":"real libGDX/OpenGL capture from unmodified deterministic Battle.step() scenarios",
  "sourceArt":"original game hull PNGs",
  "maskChoice":"CD_ARMOR_MASK_SET selects fixed real 48x48 RGBA authored asset",
  "upscaled":"nearest-neighbor 2x of current 24x24 static mask assets",
  "authored":"separately authored native 48x48 masks",
  "capturePixelSize":[1280,720],
  "shipCrop":[440,120,840,650],
  "scenarios":[]
}
for name in SCENARIOS:
    record={"scenario":name,"files":{}}
    for view in VIEWS:
        first=rows[MODES[0]][(name,view)]
        second=rows[MODES[1]][(name,view)]
        # Confirm game seed, damage, shot counts and timestep were identical.
        telemetry=("seed","tick","hits","armor_exposure","internal_exposure",
                   "worn_cells","exposed_cells","milestone","synthetic_stress")
        mismatch={k:[first[k],second[k]] for k in telemetry if first[k]!=second[k]}
        if mismatch:
            raise RuntimeError("The A/B captures are not paired: "+name+" "+view+" "+str(mismatch))
        src=[]
        for mode,row in zip(MODES,(first,second)):
            p=ROOT/mode/"framebuffer"/row["file"]
            with Image.open(p) as im:
                if im.size!=(1280,720):
                    raise RuntimeError("Unexpected native OpenGL resolution: "+str(p))
                src.append(im.convert("RGB").copy())
        crop=(440,120,840,650) if view=="surface" else (390,80,890,665)
        left,right=(im.crop(crop) for im in src)
        cw,ch=left.size
        canvas=Image.new("RGB",(cw*2,ch+28),"#111727")
        canvas.paste(left,(0,28));canvas.paste(right,(cw,28))
        dr=ImageDraw.Draw(canvas)
        dr.text((8,8),"A: UPSCALED 24 -> 48",fill="white")
        dr.text((cw+8,8),"B: AUTHORED NATIVE 48",fill="white")
        filename=name+"-"+view+"-AB-real-game.png"
        canvas.save(OUT/filename,optimize=True)
        record["files"][view]=filename
        if view=="surface":
            delta=ImageChops.difference(left,right)
            # Differences also include dynamic ship FX; do not mislabel as
            # damage pixels only. This is a visual diagnostic.
            record["surfaceChangedPixels"]=sum(1 for px in delta.getdata() if max(px)>7)
            record["identicalGameplay"]={k:first[k] for k in telemetry}
    manifest["scenarios"].append(record)
(OUT/"report.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf8")
print("PASS: 6 matching real-ship scenarios x 3 camera views from two separate 48px mask render passes")
print("Saved 18 side-by-side original-game-frame comparisons:",OUT)
