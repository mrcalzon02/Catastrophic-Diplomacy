#!/usr/bin/env python3
"""Audit the committed, editable 24x24 damage-mask PNG frames.

No mask is generated or altered here. This reads the EXACT seven runtime
atlases, reports spatial diversity, and optionally exports the 42 original
24x24 slices verbatim. Cosmetics are findings, never release gates.
"""
import argparse
import itertools
import json
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/"assets"/"damage_masks"
FAMILIES=("fighter","kinetic","laser","missile","particle","plasma","torpedo")
SIZE=24
COUNT=6

def read(family):
    with Image.open(ART/(family+".png")) as src:
        if src.size!=(SIZE*COUNT,SIZE):
            raise ValueError("Invalid native mask atlas dimensions: "+family)
        return src.convert("RGBA")

def mask_stats(image):
    pixels=image.load()
    cut=set()
    marked=set()
    for y in range(SIZE):
        for x in range(SIZE):
            r,g,b,a=pixels[x,y]
            if a<128: cut.add((x,y))
            if a<250 or max(r,g,b)>36: marked.add((x,y))
    return cut,marked

def overlap(a,b):
    union=len(a|b)
    return round(len(a&b)/union,4) if union else 1.0

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--report",default="test-output/armor-observation/mask-library-audit.json")
    p.add_argument("--export-frames",default=None,
                   help="Optional directory for 42 exact 24px PNG files, no resampling")
    args=p.parse_args()
    report={"source":"runtime checked-in authored RGBA mask PNGs","family":{}}
    for family in FAMILIES:
        atlas=read(family)
        frames=[atlas.crop((i*SIZE,0,(i+1)*SIZE,SIZE)) for i in range(COUNT)]
        cutsets,marksets=[],[]
        result=[]
        for i,frame in enumerate(frames):
            cut,marked=mask_stats(frame)
            cutsets.append(cut)
            marksets.append(marked)
            result.append({"variant":i+1,"cutPixels":len(cut),
                           "coloredOrCutPixels":len(marked),
                           "dimensions":[SIZE,SIZE]})
            if args.export_frames:
                target=Path(args.export_frames)/family
                target.mkdir(parents=True,exist_ok=True)
                frame.save(target/("v"+str(i+1)+".png"),optimize=True)
        pairs=[]
        for i,j in itertools.combinations(range(COUNT),2):
            pairs.append({"a":i+1,"b":j+1,"cutIoU":overlap(cutsets[i],cutsets[j]),
                          "allChannelsIoU":overlap(marksets[i],marksets[j])})
        worst=max(pairs,key=lambda q:q["cutIoU"])
        report["family"][family]={"frames":result,"maxCutOverlap":worst,
                                  "pairwise":pairs}
        print("AUTHORED MASK",family,"24x24 x6",
              "holes:",*[x["cutPixels"] for x in result],
              "max overlap",worst["cutIoU"],"variants",worst["a"],worst["b"])
        if worst["cutIoU"]>=.65:
            print("VISUAL-ONLY WARNING: near-duplicate armor silhouette:",family)
    path=Path(args.report)
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(report,indent=2)+"\n")
    print("Reported real PNG diversity:",path)
    return 0

if __name__=="__main__":
    raise SystemExit(main())
