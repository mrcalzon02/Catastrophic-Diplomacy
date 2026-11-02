#!/usr/bin/env python3
"""Build one inspectable evidence package per real-rendered armor scenario.

Original game OpenGL PNGs are unchanged. No giant contact-sheet output.
The clean 'surface' view uses the normal ship material/armor shader; HUD
and short-lived impacts are excluded by the client capture mode.
Only missing/corrupt evidence is fatal; visual quality remains diagnostic.
"""
import csv
import json
import os
import shutil
from pathlib import Path
from PIL import Image, ImageStat, ImageChops

root = Path(os.environ.get("CD_ARMOR_CAPTURE_DIR", "test-output/armor-observation/framebuffer"))
out = root.parent
rows = list(csv.DictReader((root/"frames.csv").open(newline="", encoding="utf-8")))
assert rows, "No actual framebuffer capture metadata"
keys = list(dict.fromkeys(row["scenario"] for row in rows))
views = ("tactical","close","surface")
assert all(sum(row["scenario"]==key and row["view"]==view for row in rows)==1
           for key in keys for view in views), "A scenario lacks a genuine OpenGL framebuffer view"
warnings=[]
for row in rows:
    png=root/row["file"]
    with Image.open(png) as image: image.verify()
    with Image.open(png) as image:
        assert image.size==(1280,720), (png,image.size)
        assert max(ImageStat.Stat(image.convert("RGB")).stddev)>=3, f"Blank framebuffer: {png}"
    if row["view"]=="surface":
        hits=int(row["hits"])
        armor=float(row["armor_exposure"])
        structure=float(row["internal_exposure"])
        if row["milestone"]=="false":
            warnings.append(f"{row['scenario']}: requested combat milestone was not reached (tick {row['tick']})")
        if row["scenario"] not in ("pristine","rectangle-stress"):
            if hits<=2 and armor>=.99:
                warnings.append(f"{row['scenario']}: local armor fully exposed after just {hits} hit(s)")
            if hits<=2 and structure>=.99:
                warnings.append(f"{row['scenario']}: structure fully exposed after just {hits} hit(s)")
            if row["scenario"]=="shield-contact" and armor>.001:
                warnings.append("Shield contact also depleted physical armor")
        if armor>.01 and int(row["mask_uploads"])==0:
            warnings.append(f"{row['scenario']}: worn armor without any actual GPU mask upload")
        if hits==0 and row["scenario"] not in ("pristine","rectangle-stress"):
            warnings.append(f"{row['scenario']}: no actual weapon hits registered")
    # Scenario-local, individually downloadable artifact directory.
    folder=out/"individual"/row["scenario"]
    folder.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(png,folder/(row["view"]+".png"))

for key in keys:
    folder=out/"individual"/key
    sample=[row for row in rows if row["scenario"]==key]
    (folder/"evidence.json").write_text(json.dumps({
        "schema":"cd-armor-scene-v2",
        "scenario":key,
        "render":"Actual libGDX/OpenGL game framebuffer with original sprite artwork",
        "surface_view":"Transient effects and HUD excluded only for forensic inspection",
        "execution":"recorded",
        "visual_approval":"NOT_ASSESSED",
        "metadata":sample,
    },indent=2)+"\n",encoding="utf8")
    (folder/"README.txt").write_text(
        "CATASTROPHIC DIPLOMACY — ORIGINAL FRAMEBUFFER EVIDENCE\n\n"
        f"Scenario: {key}\n"
        f"Weapon: {sample[0]['weapon']}  Hull: {sample[0]['hull']}\n"
        f"Seed: {sample[0]['seed']} Tick: {sample[0]['tick']} Hits: {sample[0]['hits']}\n"
        f"Armor exposure (local max): {sample[0]['armor_exposure']}\n"
        f"Structural exposure (local max): {sample[0]['internal_exposure']}\n"
        "tactical.png — normal battle with effects and HUD\n"
        "close.png — normal close-up with effects and HUD\n"
        "surface.png — same real ship renderer, without transient beam/HUD overlays\n"
        "These are NOT concept images or generated fake hull damage.\n",
        encoding="utf8")
# A lossless per-hull difference image, without changing/overwriting the sources.
try:
    a=next(row for row in rows if row["scenario"]=="pristine" and row["view"]=="surface")
    b=next(row for row in rows if row["scenario"]=="laser-scoring" and row["view"]=="surface")
    if a["hull"]==b["hull"] and a["seed"]==b["seed"]:
        with Image.open(root/a["file"]) as before, Image.open(root/b["file"]) as after:
            delta=ImageChops.difference(before.convert("RGB"),after.convert("RGB"))
            delta.save(out/"pristine-vs-laser-surface-difference.png")
except StopIteration:
    warnings.append("No comparable same-hull pristine and laser surface captures")

report={
    "schema":"cd-armor-review-v2",
    "original_framebuffers":len(rows),
    "scenarios":keys,
    "views":list(views),
    "original_pngs":"framebuffer/",
    "individual_downloads":"individual/<scenario>/",
    "execution":"captured",
    "visual_approval":"NOT_ASSESSED",
    "warnings":warnings,
    "observations":rows
}
(out/"armor-review.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf8")
(out/"armor-review-warnings.txt").write_text(
    ("\n".join(warnings) if warnings else "No automated diagnostics warnings.")+"\n",encoding="utf8")
(out/"README.txt").write_text(
    "Open individual/<scenario>/surface.png first.\n"
    "Each subdirectory is a standalone downloadable artifact with three full-resolution images.\n"
    "The original framebuffers are preserved in framebuffer/.\n"
    "Visual warnings are diagnostic ONLY, never a release quality gate.\n",
    encoding="utf8")
print(f"PASS captured {len(rows)} actual game PNG frames, {len(keys)} independently browsable scenarios")
for line in warnings:print("VISUAL DIAGNOSTIC (nonblocking):",line)
