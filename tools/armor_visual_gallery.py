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
        if row["scenario"]!="pristine" and not row["scenario"].endswith("-pristine") and row["scenario"]!="rectangle-stress":
            if hits<=2 and armor>=.99:
                warnings.append(f"{row['scenario']}: local armor fully exposed after just {hits} hit(s)")
            if hits<=2 and structure>=.99:
                warnings.append(f"{row['scenario']}: structure fully exposed after just {hits} hit(s)")
            if row["scenario"]=="shield-contact" and armor>.001:
                warnings.append("Shield contact also depleted physical armor")
        if armor>.01 and int(row["mask_uploads"])==0:
            warnings.append(f"{row['scenario']}: worn armor without any actual GPU mask upload")
        if hits==0 and row["scenario"]!="pristine" and not row["scenario"].endswith("-pristine") and row["scenario"]!="rectangle-stress":
            warnings.append(f"{row['scenario']}: no actual weapon hits registered")
    # Scenario-local, individually downloadable artifact directory.
    folder=out/"individual"/row["scenario"]
    folder.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(png,folder/(row["view"]+".png"))

for key in keys:
    folder=out/"individual"/key
    source_mask=root/(key+"-mask-coefficients.png")
    if source_mask.exists():
        # Actual GPU-upload coefficient image, never a synthesized ship view.
        shutil.copyfile(source_mask,folder/"mask-coefficients.png")
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
        "mask-coefficients.png (when present) — actual 96x144 GPU mask data, not ship art\n"
        "These are NOT concept images or generated fake hull damage.\n",
        encoding="utf8")
# Audit a genuinely comparable BEFORE/AFTER pair: identical faction, hull,
# pose and seed; the only state difference is real Battle.step() weapon damage.
# These are lossless image DIFFERENCES, never invented gameplay screenshots.
contrast_pairs=(("kinetic-pristine","kinetic-impact"),
                ("kinetic-heavy-pristine","kinetic-heavy"),
                ("kinetic-aureate-pristine","kinetic-aureate"),
                ("kinetic-zelthar-pristine","kinetic-zelthar"),
                ("kinetic-aureate-upper-pristine","kinetic-aureate-upper"),
                ("kinetic-terran-upper-pristine","kinetic-terran-upper"),
                ("laser-terran-upper-pristine","laser-terran-upper"),
                ("missile-terran-upper-pristine","missile-terran-upper"))
contrast_metrics=[]
for pristine,damaged in contrast_pairs:
    first=next((x for x in rows if x["scenario"]==pristine and x["view"]=="surface"),None)
    second=next((x for x in rows if x["scenario"]==damaged and x["view"]=="surface"),None)
    if first is None or second is None:
        warnings.append(f"{damaged}: corresponding genuine pristine reference missing")
        continue
    if first["hull"]!=second["hull"] or first["seed"]!=second["seed"]:
        warnings.append(f"{damaged}: baseline hull or seed mismatch")
        continue
    with Image.open(root/first["file"]) as baseline,Image.open(root/second["file"]) as after:
        delta=ImageChops.difference(baseline.convert("RGB"),after.convert("RGB"))
        filename=f"{pristine}-vs-{damaged}-surface-difference.png"
        delta.save(out/filename)
        # Paired original source pixels: quantify whether the base plating
        # starts too dark for a black compression crater to read. These
        # measures are image observations, not physical armor-hit metrics.
        changes=bright=dark=low=mid=high=0
        source_luma_sum=after_luma_sum=0.0
        for source_rgb,damage_rgb in zip(baseline.convert("RGB").getdata(),
                                         after.convert("RGB").getdata()):
            if max(abs(damage_rgb[i]-source_rgb[i]) for i in range(3))<=20:
                continue
            changes+=1
            old=.2126*source_rgb[0]+.7152*source_rgb[1]+.0722*source_rgb[2]
            new=.2126*damage_rgb[0]+.7152*damage_rgb[1]+.0722*damage_rgb[2]
            source_luma_sum+=old
            after_luma_sum+=new
            if old<70:low+=1
            elif old<160:mid+=1
            else:high+=1
            if new-old>18:bright+=1
            elif old-new>18:dark+=1
        metric={"scenario":damaged,"pristine":pristine,"changed_pixels":changes,
                "source_mean_luma_0_255":round(source_luma_sum/max(1,changes),2),
                "damaged_mean_luma_0_255":round(after_luma_sum/max(1,changes),2),
                "source_dark_changed_pixels_below_70":low,
                "source_mid_changed_pixels_70_to_159":mid,
                "source_bright_changed_pixels_160_plus":high,
                "brightened_pixels_over_18":bright,"darkened_pixels_over_18":dark,
                "interpretation":"Paired actual OpenGL framebuffer differences, not a crater-quality grade"}
        contrast_metrics.append(metric)
        print(f"ARMOR-CONTRAST {damaged} changed={changes} "
              f"source_luma={metric['source_mean_luma_0_255']} "
              f"bright={bright} dark={dark} baseline_dark={low}")
        if changes==0:warnings.append(f"{damaged}: ZERO rendered pixels differ from pristine")
(out/"damage-contrast-report.json").write_text(
    json.dumps({"schema":"cd-armor-contrast-v1",
                "measure":"Paired actual OpenGL surface RGB changes versus pristine",
                "nonblocking":True,"pairs":contrast_metrics},indent=2)+"\n",
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
    "observations":rows,
    "contrast_pairs":contrast_metrics
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
