#!/usr/bin/env python3
"""Extract existing approved atlas rows into modular FIRING and IMPACT PNG frames.

Requires Pillow. No image generation or procedural VFX. Strict SHA256 and crop QA.
Usage: python tools/extract_modular_weapon_fx.py <atlas_dir> <repo_root>
Supply the 35 pre-curated payload beams from Catastrophic_Diplomacy_Runtime_Art_Stage1.zip
separately; this script does not overwrite them.
"""
from __future__ import annotations
from pathlib import Path
import hashlib
import io
import sys
from PIL import Image, ImageChops

SOURCES = {
    "laser": ("blue_sci_fi_energy_vfx_sprite_sheet.png",
              "ea3626d30fa898733b08b3f0438703f1375d8f249f09d7ad0e7146a6578d2685", 8),
    "kinetic": ("golden_sci_fi_energy_vfx_sprite_sheet.png",
                "798f67c9dfa168a7cfb3de8380a16fab1f2328cdfe9a2b859ca11853c0fa1aab", 8),
    "plasma": ("crimson_energy_slash_vfx_atlas.png",
               "bac6eb74dcee442dd5b2b733f0b54a717afee726f675a0a186df051b8a54a190", 7),
    "particle": ("purple_energy_vfx_sprite_sheet.png",
                 "2d643bdf599d51460cfd8cb0cbd23a123085cd584295ff4872ec418e10471cbc", 8),
}

def sha(payload:bytes)->str:
    return hashlib.sha256(payload).hexdigest()

def edge_brightness(cell:Image.Image)->float:
    w,h=cell.size; px=cell.load(); hot=total=0
    for y in range(h):
        for x in range(w):
            if 8<=x<w-8 and 8<=y<h-8:continue
            r,g,b,a=px[x,y]
            hot+=a>160 and max(r,g,b)>160
            total+=1
    return hot/total

def prepare(source_dir:Path):
    output={}
    for weapon,(filename,digest,fire_count) in SOURCES.items():
        raw=(source_dir/filename).read_bytes()
        if sha(raw)!=digest:raise ValueError("Wrong or altered atlas: "+filename)
        atlas=Image.open(io.BytesIO(raw)).convert("RGBA")
        if atlas.size!=(1774,887):raise ValueError("Unexpected source sheet dimensions")
        for stage,row,limit in [("firing",0,fire_count),("impact",2,8)]:
            for i in range(limit):
                box=(round(i*atlas.width/8),round(row*atlas.height/3),
                     round((i+1)*atlas.width/8),round((row+1)*atlas.height/3))
                cell=atlas.crop(box)
                if edge_brightness(cell)>0.025:
                    raise ValueError(f"Possible clipped art: {weapon}/{stage}/{i+1}")
                red,green,blue,alpha=cell.split()
                # Alpha-only cleanup of near-black tinted AI atlas backgrounds.
                bright=ImageChops.lighter(ImageChops.lighter(red,green),blue)
                fade=bright.point(lambda value:max(0,min(255,(value-16)*255//64)))
                cell.putalpha(ImageChops.multiply(alpha,fade))
                buffer=io.BytesIO();cell.save(buffer,format="PNG",optimize=True)
                output[f"assets/effects/weapon_fx/{weapon}/{stage}_{i+1:02d}.png"]=buffer.getvalue()
    if len(output)!=63:raise ValueError("Incomplete stage extraction")
    return output

def main():
    if len(sys.argv)!=3:
        raise SystemExit("Usage: extract_modular_weapon_fx.py <atlas_dir> <repo_root>")
    source=Path(sys.argv[1]);root=Path(sys.argv[2])
    files=prepare(source)
    # Refuse to overwrite any previously curated frame that differs.
    for rel,contents in files.items():
        target=root/rel
        if target.exists() and sha(target.read_bytes())!=sha(contents):
            raise ValueError("Refusing to replace edited art: "+str(target))
    for rel,contents in files.items():
        target=root/rel
        if target.exists():continue
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(contents)
    print("PASS: installed 63 real, checksum-verified FIRING/IMPACT frames.")
    print("PAYLOAD: install the existing 35 beam strips from the prior runtime ZIP.")

if __name__=="__main__":
    main()
