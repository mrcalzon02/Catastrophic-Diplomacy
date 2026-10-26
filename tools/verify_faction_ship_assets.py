#!/usr/bin/env python3
"""Release gate: refuse a 'new alien sprites' claim without ALL 50 PNGs.
No Pillow dependency; checks PNG header, RGBA, dimensions, and per-file SHA256.
"""
import hashlib
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parent.parent
ASSETS=ROOT/"assets/ships/factions"
PATHS={"vaerith_prism","zelthar_luminari","aureate_synod","nyx_biolith","umbra_foundry"}
HULLS={"interceptor","corvette","missile_corvette","frigate","destroyer",
       "light_cruiser","cruiser","heavy_cruiser","battlecruiser","carrier"}
def fail(reason): raise SystemExit("ALIEN SPRITE GATE FAILED: "+reason)
def main():
    manifest=ASSETS/"approved-sprites.json"
    if not manifest.exists():
        fail("50 approved PNGs and their per-file checksum index have not been committed")
    data=json.loads(manifest.read_text())
    if len(data.get("factions",[]))!=5:fail("expected five source lineups")
    complete=set()
    for faction in data["factions"]:
        key=faction["id"]
        if key not in PATHS:fail("unknown faction "+key)
        for entry in faction["ships"]:
            hull=entry["hull"]
            if hull not in HULLS or (key,hull) in complete:fail("unknown/repeated hull")
            complete.add((key,hull))
            path=ASSETS/key/(hull+".png")
            if not path.is_file():fail("missing "+str(path.relative_to(ROOT)))
            content=path.read_bytes()
            if hashlib.sha256(content).hexdigest()!=entry["sha256"]:
                fail("unapproved or damaged sprite "+str(path.relative_to(ROOT)))
            if not content.startswith(b"\\x89PNG\\r\\n\\x1a\\n") or content[25]!=6:
                fail("invalid RGBA PNG "+str(path.relative_to(ROOT)))
            width=int.from_bytes(content[16:20],"big")
            height=int.from_bytes(content[20:24],"big")
            if width<80 or height<130:fail("unexpected crop dimensions")
    if complete!={(x,y) for x in PATHS for y in HULLS}:fail("not all 50 hulls committed")
    print("PASS: all 50 approved individual RGBA hulls are actually in Git with matching SHA256")
if __name__=="__main__":main()
