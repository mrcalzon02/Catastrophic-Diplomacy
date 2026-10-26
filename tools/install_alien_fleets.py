#!/usr/bin/env python3
"""Install exactly the approved 50 PNGs from the exported artwork pack.

Usage:
  python3 tools/install_alien_fleets.py <Catastrophic_Diplomacy_50_Alien_Ship_Sprites_20261025.zip> [repo_root]

Does not generate, recolor, resize, or recompress the approved art. The source
archives and their independent 50 checksums remain inside the user's ZIP.
The importer rejects unexpected filenames, duplicates, corrupt content,
non-transparent assets and collisions with edited repository artwork.
"""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

EXPECTED={
    "vaerith_prism":"be885ee6688741cd7b0b346b8629107ee6c34139289f3f2e2e7009e4d9d4a7d4",
    "zelthar_luminari":"418ca4039c1b0c67c0738bbba6b1866752bedae090477513a3a72ad8c1b517e0",
    "aureate_synod":"2268ef2e202797657b30f14cc4f208144222aa1058d96410407ffbbc7f3043cb",
    "nyx_biolith":"5202acfe9e87a9e7fad41b2ea7b82fc2b87b657189cc062c66b5908c81ea1d45",
    "umbra_foundry":"9e6e5e22a1cccea114bed972b1ece6dc09d29f8dab26f1bc8926aecf05b30def",
}
HULLS=("interceptor","corvette","missile_corvette","frigate","destroyer",
       "light_cruiser","cruiser","heavy_cruiser","battlecruiser","carrier")
def sha(data): return hashlib.sha256(data).hexdigest()
def main():
    if len(sys.argv) not in (2,3):
        raise SystemExit("Usage: install_alien_fleets.py <zip> [repository_root]")
    archive=Path(sys.argv[1]);root=Path(sys.argv[2]) if len(sys.argv)==3 else Path(__file__).resolve().parent.parent
    with zipfile.ZipFile(archive) as z:
        info=z.infolist()
        if len(info)!=len({x.filename for x in info}):raise ValueError("duplicate archive file paths")
        manifest=json.loads(z.read("faction_registry.json"))
        factions=manifest.get("factions",[])
        if {x["id"] for x in factions}!=set(EXPECTED) or len(factions)!=5:
            raise ValueError("unexpected faction catalog")
        content={}
        for f in factions:
            name=f["id"]; ships=f["ships"]
            if f["source_sha256"]!=EXPECTED[name]:raise ValueError("source sheet checksum changed: "+name)
            if {x["hull"] for x in ships}!=set(HULLS) or len(ships)!=10:
                raise ValueError("missing or repeated ship class: "+name)
            for ship in ships:
                path="assets/"+ship["sprite"]
                expected=f"assets/ships/factions/{name}/{ship['hull']}.png"
                if path!=expected:raise ValueError("invalid sprite path")
                data=z.read(path)
                if not data.startswith(b"\x89PNG\r\n\x1a\n"):
                    raise ValueError("not a PNG: "+path)
                if sha(data)!=ship["sha256"]:raise ValueError("hash mismatch "+path)
                # PNG color type 6 denotes RGBA. Source sheet extraction must retain it.
                if len(data)<33 or data[25]!=6:raise ValueError("transparent RGBA expected "+path)
                content[path]=data
        if len(content)!=50:raise ValueError("must install 50 unique hull sprites")
        for rel,data in content.items():
            target=root/rel
            if target.exists() and sha(target.read_bytes())!=sha(data):
                raise ValueError("refusing overwrite of hand-edited ship sprite "+str(target))
        for rel,data in content.items():
            target=root/rel;target.parent.mkdir(parents=True,exist_ok=True)
            if not target.exists():target.write_bytes(data)
        # Commit this exact per-image SHA-256 contract beside the binary artwork.
        index=root/"assets/ships/factions/approved-sprites.json"
        serialized=json.dumps(manifest,indent=2,sort_keys=True)+"\n"
        if index.exists() and index.read_text()!=serialized:
            raise ValueError("Existing art approval index differs; review before replacing")
        index.write_text(serialized)
    print("PASS 50 unmodified RGBA sprites verified and staged at assets/ships/factions/")
    print("Commit the 50 binary assets with Git (they are NOT present until imported).")
if __name__=="__main__":main()
