#!/usr/bin/env python3
"""Source-only, fail-closed audit of the vetted tactical UI implementation.

No Gradle, Android SDK, Pillow or network access required.
Use --source-zip and --selection-zip to check actual binary archives as well.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import zipfile

SOURCE_HASH = "8220b70e36e7133f8bbb5d1d89dacc0a1b14ee12671c481d822454901f80a170"
SELECTION_HASH = "accf38170a34c601273a1cb65f30d066596b3ec8cee75b62f46ce2256f29faf6"
SHEET_HASH = {
    "systems": ("art_source/ui/ui_sheet_01_systems.png",
                "3b74777d6c4a6dc8448fa0958f161b1b44967ef5a159a768bd36ca223e36417c"),
    "combat": ("art_source/ui/ui_sheet_02_combat.png",
               "7bca86a5ba6cb908f5985e34293157ccad0cb4fb23e4700112afc2f0b2622d26"),
}
CLIENT_FILES = [
    "game/src/main/java/diplomacy/client/CatastrophicGame.java",
    "game/src/main/java/diplomacy/client/ShipyardPanel.java",
    "game/src/main/java/diplomacy/client/FleetOrdersPanel.java",
]


def require(ok, detail):
    if not ok:
        raise ValueError(detail)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def png_size(data):
    require(data.startswith(b"\x89PNG\r\n\x1a\n"), "Missing PNG signature")
    require(data[12:16] == b"IHDR", "Malformed PNG IHDR")
    return struct.unpack(">II", data[16:24])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--source-zip", type=Path, default=None)
    parser.add_argument("--selection-zip", type=Path, default=None)
    args = parser.parse_args()
    root = args.repo_root
    manifest = json.loads((root / "assets/ui/tactical/selected_manifest.json").read_text("utf-8"))
    entries = manifest["assets"]
    require(manifest["schema"] == "catastrophic-diplomacy-selected-ui-v5", "Unreviewed icon manifest")
    require(manifest["approved_only"] is True and len(entries) == 24, "Incorrect approved icon count")

    loader = (root / "game/src/main/java/diplomacy/client/TacticalUiIcons.java").read_text("utf-8")
    match = re.search(r'String\[\]\s+REVIEWED\s*=\s*\{(.*?)\};',loader,re.S)
    require(match is not None, "UI loader approved list missing")
    loaded = re.findall(r'"([a-z_]+)"',match.group(1))
    require(len(set(loaded)) == 24 and set(loaded) == set(entries), "Loader/manifest approved ID mismatch")
    for method in ("void drawButton(", "void draw(", "boolean has(", "void dispose("):
        require(method in loader, "Renderer API missing: " + method)
    require("getPackedColor()" in loader and "setPackedColor(prior)" in loader,
            "UI button chrome must restore batch tint without frame allocations")
    require("new Pixmap(1, 1" in loader, "UI button chrome not procedural")

    combined = "\n".join((root / name).read_text("utf-8") for name in CLIENT_FILES)
    require("shipyard.setIcons(uiIcons)" in combined, "Shipyard icons never wired")
    require("fleetOrdersPanel.setIcons(uiIcons)" in combined, "Fleet orders icons never wired")
    require("iconForModule(c.name,selected.mount())" in combined, "Module icons not data-driven")
    require("final String[] menuIcons" in combined, "Tactical menu icons not wired")
    require("ROW_ICONS" in combined, "Fleet-orders row icons not wired")
    for name, item in entries.items():
        require(name in loaded and f'"{name}"' in combined, "Approved icon not used in UI: " + name)
        require(item["sheet"] in SHEET_HASH, "Unknown source sheet: " + name)
        require(item["source"].startswith("approved/"), "Unapproved source path: " + name)
        require(item["destination"] == f"assets/ui/tactical/selected/{name}.png",
                "Unexpected destination: " + name)
        require(re.fullmatch("[0-9a-f]{64}",item["sha256"]) is not None, "Invalid file hash: " + name)
        require(re.fullmatch("[0-9a-f]{64}",item["pixel_sha256"]) is not None, "Invalid pixel hash: " + name)
        x1,y1,x2,y2 = item["box"]
        require(0 <= x1 < x2 <= 1536 and 0 <= y1 < y2 <= 1024,
                "Invalid original sheet crop coordinates: " + name)

    build = (root / "build.gradle").read_text("utf-8")
    require("getSubimage(x, y, w, h)" in build and "pixelDigest" in build, "Unverified Gradle crops")
    require("new java.util.zip.ZipFile(archive)" in build, "Gradle source archive reconstruction missing")
    require("assets/ui/tactical/selected_manifest.json" in build, "Gradle manifest disconnected")
    require("icons.png" not in build, "Rejected old square atlas reintroduced to build")
    for path in ("desktop/build.gradle", "android/build.gradle"):
        text = (root / path).read_text("utf-8")
        require("prepareUiAssets" in text and "generated/cd-ui" in text,
                "Selected art not staged for: " + path)

    if args.source_zip:
        content=args.source_zip.read_bytes()
        require(sha(content) == SOURCE_HASH, "Original source ZIP checksum mismatch")
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            require(archive.testzip() is None, "Original source archive corrupt")
            for name, (path, digest) in SHEET_HASH.items():
                raw=archive.read(path)
                require(sha(raw) == digest and png_size(raw) == (1536,1024),
                        "Original source sheet altered: " + name)
    if args.selection_zip:
        content=args.selection_zip.read_bytes()
        require(sha(content) == SELECTION_HASH, "Selected image pack checksum mismatch")
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            require(archive.testzip() is None, "Selected PNG archive corrupt")
            for name, item in entries.items():
                raw=archive.read(item["source"])
                x1,y1,x2,y2=item["box"]
                require(sha(raw) == item["sha256"], "Selected PNG bytes changed: " + name)
                require(png_size(raw) == (x2-x1,y2-y1),
                        "Selected PNG dimensions don't match crop geometry: " + name)
    print(f"PASS: {len(entries)} vetted icons match loader, battle HUD, tactical menu, "
          "shipyard, fleet orders, and both client packaging routes")


if __name__ == "__main__":
    main()
