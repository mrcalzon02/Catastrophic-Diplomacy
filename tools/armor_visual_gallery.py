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
                ("missile-terran-upper-pristine","missile-terran-upper"),
                ("plasma-terran-upper-pristine","plasma-terran-upper"),
                ("particle-terran-upper-pristine","particle-terran-upper"),
                ("torpedo-terran-upper-pristine","torpedo-terran-upper"))
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

# Exact real coefficient pixels uploaded to the material shader, separate
# from any apparent difference caused by dark plating or engine glow.
# These are cropped GPU mask observations; not synthetic hit marks.
channel_coverage=[]
for key in keys:
    path=root/(key+"-mask-coefficients.png")
    if not path.exists():continue
    with Image.open(path) as original:
        pixels=original.convert("RGBA")
        dark=hot=metal=anneal=opening=0
        for red,green,blue,alpha in pixels.getdata():
            dark+=(red>12)
            # Laser's B>0.64 tags cooled heat-oxide. Never count it as
            # raised silver metal or interpret its G hue as orange glow.
            is_anneal=blue>158
            anneal+=is_anneal
            hot+=(green>12 and not is_anneal)
            metal+=(blue>12 and not is_anneal)
            opening+=(alpha<243)
        item={"scenario":key,"dirty_mask_width":pixels.width,
              "dirty_mask_height":pixels.height,
              "dark_pixels":dark,"heated_edge_pixels":hot,
              "bright_metal_pixels":metal,"tempered_laser_pixels":anneal,
              "real_opening_pixels":opening}
        channel_coverage.append(item)
        print(f"ARMOR-MATERIAL {key} dark={dark} heat={hot} "
              f"metal={metal} annealed={anneal} opening={opening}")
(out/"damage-channels-report.json").write_text(
    json.dumps({"schema":"cd-armor-channels-v1",
                "note":"Real R/G/B/A GPU mask dirty-region coverage; cosmetic evidence only",
                "nonblocking":True,"observations":channel_coverage},
               indent=2)+"\n",encoding="utf8")

# Compare measured upload-mask geometry between equal original Terran
# frigates. This is an objective observation on REAL renderer mask bytes,
# not a subjective grade, synthetic damage, or a release gate.
by_scenario={item["scenario"]:item for item in channel_coverage}
pair_names=("particle-terran-upper","torpedo-terran-upper","kinetic-terran-upper")
opening_observations={
    name:{"opening_pixels":by_scenario[name]["real_opening_pixels"],
          "heated_edge_pixels":by_scenario[name]["heated_edge_pixels"],
          "bright_metal_pixels":by_scenario[name]["bright_metal_pixels"],
          "mask_size":(by_scenario[name]["dirty_mask_width"],
                       by_scenario[name]["dirty_mask_height"])}
    for name in pair_names if name in by_scenario
}
if len(opening_observations)==3:
    needle=opening_observations["particle-terran-upper"]["opening_pixels"]
    rupture=opening_observations["torpedo-terran-upper"]["opening_pixels"]
    warmth=opening_observations["kinetic-terran-upper"]["heated_edge_pixels"]
    print(f"ARMOR-WOUND-COMPARISON particle_cut={needle} "
          f"torpedo_cut={rupture} kinetic_hot={warmth}")
    if rupture<=needle:
        warnings.append("Torpedo hole is not larger than particle hole in actual "
                        "same-hull uploaded mask; inspect source silhouette clipping")
    if warmth==0:
        warnings.append("Kinetic mask has no hot-edge samples after physical strike")
else:
    warnings.append("Incomplete same-hull material-mask geometry comparison")
(out/"damage-wound-comparison.json").write_text(
    json.dumps({"schema":"cd-armor-wound-comparison-v1",
                "source":"Actual uploaded RGBA armor mask, not projected effect pixels",
                "no_release_gate":True,
                "observations":opening_observations},indent=2)+"\n",
    encoding="utf8")

# A labeled review sheet built exclusively from genuine original game
# framebuffers. Unlike a gallery of unrelated hulls, all six use the same
# Terran frigate and each actual-hit scene has its own pristine seed match.
# If a capture is missing, report the problem rather than fabricate art.
try:
    from PIL import ImageDraw, ImageFont
    font_path="/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    try:
        title_font=ImageFont.truetype(font_path,20)
        detail_font=ImageFont.truetype(font_path,15)
    except OSError:
        title_font=detail_font=ImageFont.load_default()
    families=("laser","kinetic","missile","torpedo","plasma","particle")
    panel_w,panel_h=560,380
    sheet=Image.new("RGB",(panel_w*2,panel_h*3),(16,19,25))
    painter=ImageDraw.Draw(sheet)
    complete=0
    for index,weapon in enumerate(families):
        live=next((row for row in rows if row["scenario"]==f"{weapon}-terran-upper"
                   and row["view"]=="surface"),None)
        pristine=next((row for row in rows if row["scenario"]==f"{weapon}-terran-upper-pristine"
                       and row["view"]=="surface"),None)
        if live is None or pristine is None or live["hull"]!=pristine["hull"] or live["seed"]!=pristine["seed"]:
            warnings.append(f"{weapon}: real same-hull pristine / damaged evidence is missing")
            continue
        x=(index%2)*panel_w
        y=(index//2)*panel_h
        painter.text((x+12,y+6),f"{weapon.upper()}  |  genuine hits: {live['hits']}",
                     fill=(236,240,244),font=title_font)
        for j,scenario in enumerate((pristine,live)):
            with Image.open(root/scenario["file"]) as original:
                cut=original.convert("RGB").crop((390,90,890,680))
                cut.thumbnail((260,312),Image.Resampling.LANCZOS)
            left=x+10+j*275
            sheet.paste(cut,(left,y+35))
            painter.text((left+6,y+354),"PRISTINE" if j==0 else "REAL IMPACT",
                         font=detail_font,fill=(185,195,211))
        complete+=1
    if complete==len(families):
        folder=out/"examples"
        folder.mkdir(exist_ok=True)
        sheet.save(folder/"six-weapon-matched-hull-examples.png")
        (folder/"README.txt").write_text(
            "Six matched damage examples from REAL libGDX/OpenGL screenshots, "
            "not generated concept art or synthetic marks. All six original "
            "Terran frigates share the same pose and authored source art. "
            "Each weapon's pristine image also uses its damage scene's exact "
            "seed. Hits shown are cumulative and correctly labeled. "
            "Screenshot quality is a nonblocking diagnostic.\\n",
            encoding="utf8")
        print("ARMOR-EXAMPLES six genuine matched-hull weapon comparisons")

        # Inspection crops for the three newly differentiated materials.
        # Find the most changed ORIGINAL surface region, then show the
        # EXACT same source crop before/after, with nearest-pixel zoom.
        # No painted holes, simulated fracturing, or AI illustrations.
        from PIL import ImageFilter, ImageDraw
        detail_types=("particle","torpedo","kinetic")
        summary=Image.new("RGB",(1024,460*3),(16,19,25))
        summary_draw=ImageDraw.Draw(summary)
        for n,weapon in enumerate(detail_types):
            live=next((row for row in rows if row["scenario"]==f"{weapon}-terran-upper"
                       and row["view"]=="surface"),None)
            virgin=next((row for row in rows if row["scenario"]==f"{weapon}-terran-upper-pristine"
                         and row["view"]=="surface"),None)
            with Image.open(root/live["file"]) as src,Image.open(root/virgin["file"]) as base:
                a=src.convert("RGB")
                b=base.convert("RGB")
            # This bounding region contains the Terran hull, not HUD or
            # remote sprites. Difference maxima are a review aid, never a
            # physical-impact claim: multiple real hits may remain visible.
            delta=ImageChops.difference(a,b).convert("L")
            scan=(415,110,860,630)
            blurred=delta.filter(ImageFilter.BoxBlur(24)).crop(scan)
            peak=max(blurred.getdata())
            if peak<7:
                warnings.append(f"{weapon}: no visible surface difference for detail crop")
                continue
            pts=list(blurred.getdata())
            peak_i=pts.index(peak)
            cx=scan[0]+peak_i%blurred.width
            cy=scan[1]+peak_i//blurred.width
            # 136x136 square around the detected genuine changed surface.
            left=max(0,min(1280-136,cx-68))
            top=max(0,min(720-136,cy-68))
            bounds=(left,top,left+136,top+136)
            y=n*460
            summary_draw.text((14,y+8),
                f"{weapon.upper()} | paired real OpenGL material close-up",
                fill=(236,240,244),font=title_font)
            for j,original in enumerate((b,a)):
                crop=original.crop(bounds).resize((408,408),Image.Resampling.NEAREST)
                x=10+j*510
                summary.paste(crop,(x,y+42))
                summary_draw.text((x+6,y+445),
                    "PRISTINE" if j==0 else f"REAL HITS: {live['hits']}",
                    fill=(185,195,211),font=detail_font)
        summary.save(folder/"particle-torpedo-kinetic-impact-closeups.png")
        # Exact torpedo alpha edge cutout at native 96x144 GPU-mask
        # resolution, enlarged with nearest-pixel sampling. This is NOT
        # a procedural illustration: every gray value is uploaded alpha
        # from the same real in-game damaged Terran frigate.
        torpedo_mask=root/"torpedo-terran-upper-mask-coefficients.png"
        if torpedo_mask.exists():
            with Image.open(torpedo_mask) as original_mask:
                rgba=original_mask.convert("RGBA")
                alpha=rgba.getchannel("A")
            cuts=[(ix%alpha.width,ix//alpha.width)
                  for ix,value in enumerate(alpha.getdata()) if value<243]
            if cuts:
                x0=max(0,min(x for x,y in cuts)-7)
                x1=min(alpha.width,max(x for x,y in cuts)+8)
                y0=max(0,min(y for x,y in cuts)-7)
                y1=min(alpha.height,max(y for x,y in cuts)+8)
                # Gray is actual missing-hull alpha amount.
                authentic_cut=Image.eval(alpha.crop((x0,y0,x1,y1)),
                                         lambda a:255-a).convert("RGB")
                authentic_cut.thumbnail((360,360))
                authentic_cut=authentic_cut.resize(
                    (authentic_cut.width*12,authentic_cut.height*12),
                    Image.Resampling.NEAREST)
                authentic_cut.thumbnail((430,430),Image.Resampling.NEAREST)
                edge_sheet=Image.new("RGB",(1024,510),(16,19,25))
                edge_pen=ImageDraw.Draw(edge_sheet)
                edge_pen.text((15,10),
                    "TORPEDO | real original-hull impact / uploaded armor alpha",
                    font=title_font,fill=(240,243,247))
                # Reuse the already-rendered full-size torpedo screenshot
                # crop, never redraw the ship or invent material pixels.
                with Image.open(root/"torpedo-terran-upper-surface.png") as shot:
                    shot_crop=shot.convert("RGB").crop((505,285,760,540))
                shot_crop=shot_crop.resize((440,440),Image.Resampling.NEAREST)
                edge_sheet.paste(shot_crop,(10,50))
                edge_sheet.paste(authentic_cut,
                                 (535+(440-authentic_cut.width)//2,
                                  50+(440-authentic_cut.height)//2))
                edge_pen.text((15,490),"REAL OpenGL ship framebuffer",
                              font=detail_font,fill=(185,195,211))
                edge_pen.text((535,490),
                    "REAL GPU hole alpha; white = removed armor",
                    font=detail_font,fill=(185,195,211))
                edge_sheet.save(folder/"torpedo-actual-rupture-edge-review.png")
                print(f"ARMOR-EDGE original alpha cutout pixels={len(cuts)} "
                      f"bbox={(x0,y0,x1,y1)}")
            else:
                warnings.append("Torpedo alpha edge review has no physical cutout")
        else:
            warnings.append("Torpedo GPU alpha mask missing: no edge review produced")
        print("ARMOR-DETAIL actual particle / torpedo / kinetic source-pixel close-ups")

    else:
        warnings.append(f"Same-hull weapon example sheet incomplete: {complete}/6")
except Exception as error:
    warnings.append(f"Nonblocking example presentation could not render: {error!s}")

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
    "contrast_pairs":contrast_metrics,
    "material_mask_channels":channel_coverage,
    "same_hull_example_sheet":"examples/six-weapon-matched-hull-examples.png"
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
