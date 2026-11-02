#!/usr/bin/env python3
"""Postprocess true client framebuffer screenshots into reviewable evidence.
Only scales/crops genuine PNGs; never paints or synthesizes ship artwork.
Warnings are cosmetic diagnostics, not release quality gates.
"""
import csv
import json
import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageStat, ImageChops

root = Path(os.environ.get("CD_ARMOR_CAPTURE_DIR", "test-output/armor-observation/framebuffer"))
out = root.parent
rows = list(csv.DictReader((root / "frames.csv").open(newline="", encoding="utf-8")))
assert rows, "No actual framebuffer metadata produced"
keys = list(dict.fromkeys(r["scenario"] for r in rows))
views = ["tactical", "close"]
assert all(sum(r["scenario"] == key and r["view"] == view for r in rows) == 1
           for key in keys for view in views), "Each scenario must have both original views"
warnings = []
for row in rows:
    path = root / row["file"]
    with Image.open(path) as im:
        im.verify()
    with Image.open(path) as im:
        assert im.size == (1280,720), f"Wrong real framebuffer dimensions: {path}"
        rgb=im.convert("RGB")
        s=ImageStat.Stat(rgb).stddev
        if max(s)<3:
            warnings.append(f"Nearly blank or uniform frame: {path.name}")
    if row["milestone"]=="false":
        warnings.append(f"Combat milestone not reached: {row['scenario']} at tick {row['tick']}")
    if row["weapon"]!="KINETIC_GRID_STRESS" and int(row["hits"])==0 and row["scenario"]!="pristine":
        warnings.append(f"No hits observed for {row['scenario']}")
    if row["view"]=="close" and float(row["armor_exposure"])>0.01 and int(row["mask_uploads"])==0:
        warnings.append(f"Armor was damaged but no GPU mask update recorded: {row['scenario']}")
font=ImageFont.load_default()
CELL_W,CELL_H=620,390
gallery=Image.new("RGB",(CELL_W*2,CELL_H*len(keys)),(18,22,30))
draw=ImageDraw.Draw(gallery)
for i,key in enumerate(keys):
    for j,view in enumerate(views):
        row=next(r for r in rows if r["scenario"]==key and r["view"]==view)
        x,y=j*CELL_W,i*CELL_H
        image=Image.open(root/row["file"]).convert("RGB")
        # This is only a thumbnail of an unchanged real-frame screenshot.
        image.thumbnail((600,337))
        gallery.paste(image,(x+10,y+34))
        draw.text((x+10,y+9),f"{key} / {view} - {row['weapon']}",font=font,fill=(239,242,248))
        draw.text((x+12,y+374),
                  f"tick {row['tick']} hits {row['hits']}  armor {float(row['armor_exposure']):.2f}  "
                  f"internal {float(row['internal_exposure']):.2f}  milestone {row['milestone']}",
                  font=font,fill=(210,217,228))
gallery.save(out/"armor-review-contact-sheet.png")
# Compare only two repeatable views from the same fitted hull and scene seed.
try:
    clean=next(r for r in rows if r["scenario"]=="pristine" and r["view"]=="close")
    scored=next(r for r in rows if r["scenario"]=="laser-scoring" and r["view"]=="close")
    a=Image.open(root/clean["file"]).convert("RGB")
    b=Image.open(root/scored["file"]).convert("RGB")
    diff=ImageChops.difference(a,b)
    diff.save(out/"pristine-vs-laser-difference.png")
    if max(ImageStat.Stat(diff).mean)<0.5:
        warnings.append("Laser scoring visually indistinguishable from pristine at screenshot resolution")
except StopIteration:
    warnings.append("No same-hull laser reference available")
report={
    "schema":"cd-armor-review-v1",
    "original_rendered_framebuffers":len(rows),
    "scenarios":keys,
    "capture_source":"CatastrophicGame real OpenGL framebuffer; actual fitted Battle.step() impacts unless stress-labeled",
    "reference_originals":"Original screenshot PNGs are available individually in framebuffer/",
    "execution_status":"captured",
    "visual_approval":"NOT_ASSESSED",
    "warnings":warnings,
    "observations":rows
}
(out/"armor-review.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
(out/"armor-review-warnings.txt").write_text(
    ("\n".join(warnings) if warnings else "No automated diagnostic warnings.")+"\n",encoding="utf-8")
print("Captured",len(rows),"actual frames across",len(keys),"scenarios")
print("Generated review contact sheet and comparison difference")
for warning in warnings: print("VISUAL DIAGNOSTIC (nonblocking):",warning)
