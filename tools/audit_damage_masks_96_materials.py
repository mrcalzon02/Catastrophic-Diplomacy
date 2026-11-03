#!/usr/bin/env python3
"""Nonblocking art audit of actual committed native-96 armor RGBA atlases.

Observes thermal reach, family material signatures, cutout diversity and
anti-aliased channel gradients. Aesthetic findings never gate packages.
No artwork synthesis, simulation changes or package build is performed.
"""
import argparse
import itertools
import json
import math
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/"assets"/"damage_masks"/"hires96"
FAMILIES=("laser","kinetic","plasma","particle","missile","torpedo","fighter")
SIZE=96
COUNT=6
PIXELS_PER_ARMOR_CELL=36

def stats(frame,family):
    px=frame.load()
    cut=set()
    thermal=[]
    marked=set()
    phase_values=set()
    hot_values=set()
    for y in range(SIZE):
        for x in range(SIZE):
            r,g,b,a=px[x,y]
            if a<128:cut.add((x,y))
            if r>32 or g>32 or b>32 or a<245:marked.add((x,y))
            is_anneal=b>170 if family=="laser" else g>45
            if is_anneal:
                thermal.append(math.hypot(x-47.5,y-47.5)/
                               PIXELS_PER_ARMOR_CELL)
            if family=="laser" and b>170:phase_values.add(g)
            if family!="laser" and g>45:hot_values.add(g)
    return {
        "cutPixels":len(cut),
        "affectedPixels":len(marked),
        "heatOrAnnealPixels":len(thermal),
        "heatReachArmorCells":round(max(thermal),3) if thermal else 0,
        "oxidePhaseLevels":len(phase_values),
        "thermalGradientLevels":len(hot_values),
    },cut

def audit():
    report={"source":"Committed native-96 RGBA masks, no resampling",
            "metrics":"Widths in units of one 16x16 directional armor tile",
            "framesPerFamily":COUNT,"framePixelSize":SIZE,
            "families":{},"styleWarnings":[]}
    family_reach={}
    for family in FAMILIES:
        path=ART/(family+".png")
        with Image.open(path) as src:
            if src.size!=(SIZE*COUNT,SIZE):
                raise ValueError("Invalid runtime atlas dimensions: "+str(path))
            atlas=src.convert("RGBA")
        frames=[];cutsets=[]
        for v in range(COUNT):
            frame=atlas.crop((v*SIZE,0,(v+1)*SIZE,SIZE))
            one,cut=stats(frame,family)
            one["variant"]=v+1
            frames.append(one)
            cutsets.append(cut)
            if one["heatReachArmorCells"]>1.32:
                report["styleWarnings"].append(
                    family+" variant "+str(v+1)+" material exceeds 1.32 cells")
            if family=="kinetic" and one["heatOrAnnealPixels"]:
                report["styleWarnings"].append("Kinetic mask contains thermal glow")
        overlap=[]
        for a,b in itertools.combinations(range(COUNT),2):
            union=len(cutsets[a]|cutsets[b])
            iou=len(cutsets[a]&cutsets[b])/union if union else 1.
            overlap.append(round(iou,4))
        family_reach[family]=[x["heatReachArmorCells"] for x in frames]
        report["families"][family]={
            "frames":frames,
            "worstCutIoU":max(overlap),
            "meanThermalReach":round(sum(family_reach[family])/COUNT,3),
        }
        if max(overlap)>.65:
            report["styleWarnings"].append(
                family+" masks have similar cut topology (IoU > 0.65)")
        print(f"{family.upper():8s}",
              "reach",family_reach[family],
              "worst cut IoU",max(overlap))
    other_max=max(max(v) for k,v in family_reach.items() if k!="laser")
    report["largestNonLaserThermalReach"]=other_max
    report["laserAnnealExceedsEveryNonLaserVariant"]=all(
        x>other_max for x in family_reach["laser"])
    if not report["laserAnnealExceedsEveryNonLaserVariant"]:
        report["styleWarnings"].append(
            "Laser annealing not wider than every other weapon variant")
    return report

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--report",type=Path,
                        default=Path("test-output/armor-observation/96-material-audit.json"))
    args=parser.parse_args()
    report=audit()
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    for warning in report["styleWarnings"]:
        print("VISUAL ONLY / NOT A RELEASE GATE:",warning)
    print("Saved real native-96 material audit:",args.report)
    return 0

if __name__=="__main__":
    raise SystemExit(main())
