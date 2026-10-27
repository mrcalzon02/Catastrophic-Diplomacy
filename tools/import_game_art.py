#!/usr/bin/env python3
"""Safe, repeatable Catastrophic Diplomacy art importer. Python 3.10+.

python tools/import_game_art.py --source-dir /path/to/archives --check
python tools/import_game_art.py --source-dir /path/to/archives --import

--check uses only stdlib. --import needs Pillow for approved beams:
    python -m pip install Pillow

Never substitutes an arbitrary old sprite for a missing or unapproved asset.
Unreviewed concept sheets are staged OUTSIDE assets/ so Android builds do
not accidentally package tens of MB of unsuitable raw artwork.
"""
import argparse
import base64
import hashlib
import io
import json
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ART = "catastrophic_diplomacy_art_source_library_2026-10-24.zip"
FX = "catastrophic_diplomacy_weapon_fx_source_2026-10-24.zip"
EXPECTED = {
    ART: "dfae7ce118b654012901ddd080378f8326eb3c2d9dc87bbbcd00d0ff06674a66",
    FX: "07def3cb7ad47206aef4331c3a7f595b4504d749f85704a288de52487f72efb0",
}
TERRAN = ("interceptor", "corvette", "missile_corvette", "frigate", "destroyer",
          "light_cruiser", "heavy_cruiser", "battlecruiser", "carrier", "dreadnought")
PALETTE = ("blue", "red", "green", "violet", "gold")
MATERIALS = ("shield_energy_blue", "structure_damaged", "structure_destroyed")


def digest(payload):
    return hashlib.sha256(payload).hexdigest()


def manifest(text, count):
    rows = []
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        fields = line.split("|")
        if len(fields) != 5 or len(fields[1]) != 64:
            raise ValueError("Malformed source manifest: " + line[:100])
        path = pathlib.PurePosixPath(fields[0])
        if path.is_absolute() or ".." in path.parts:
            raise ValueError("Unsafe source path")
        rows.append((str(path), fields[1]))
    if len(rows) != count or len(set(p for p, _ in rows)) != count:
        raise ValueError("Unexpected source manifest count or duplicate paths")
    return rows


def checked_archive(path, declared_sha, records):
    if not path.is_file() or digest(path.read_bytes()) != declared_sha:
        raise ValueError("Missing, modified or incorrect source archive: " + str(path))
    result = {}
    with zipfile.ZipFile(path) as archive:
        for member, sha in records:
            data = archive.read(member)
            if digest(data) != sha:
                raise ValueError("Source PNG checksum mismatch: " + member)
            if not data.startswith(b"\x89PNG\r\n\x1a\n"):
                raise ValueError("Source file is not PNG: " + member)
            result[member] = data
    return result


def curated_beams(png):
    try:
        from PIL import Image
    except ImportError as error:
        raise RuntimeError("Install Pillow to curate beams (python -m pip install Pillow)") from error
    image = Image.open(io.BytesIO(png)).convert("RGBA")
    if image.size != (1536, 1024):
        raise ValueError("Approved beam sheet dimensions changed")
    result = {}
    for column, color in enumerate(PALETTE):
        for row in range(7):
            x = 12 + column * 307
            y = 754 + row * 39
            cut = image.crop((x, y - 13, x + 280, y + 14))
            pixels = bytearray(cut.tobytes())
            # Remove diffuse tinted panel haze, leaving bright beam + glow.
            for i in range(3, len(pixels), 4):
                alpha = pixels[i]
                pixels[i] = 0 if alpha < 20 else (alpha - 20) * 255 // 235
            frame = Image.frombytes("RGBA", cut.size, bytes(pixels))
            dest = io.BytesIO()
            frame.save(dest, format="PNG", optimize=True)
            result[f"assets/effects/beams/{color}_{row+1:02d}.png"] = dest.getvalue()
    return result


def checked_materials(folder):
    found = {}
    for material in MATERIALS:
        source = folder / (material + ".png.b64.txt")
        if not source.exists():
            continue
        raw = base64.b64decode(source.read_text("ascii").strip(), validate=True)
        if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError("Not a PNG: " + str(source))
        found[f"assets/materials/{material}.png"] = raw
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=pathlib.Path, required=True)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--check", action="store_true")
    modes.add_argument("--import", dest="install", action="store_true")
    args = parser.parse_args()
    folder = args.source_dir
    art_records = manifest((ROOT / "assets/modules/source/manifest.txt").read_text(), 34)
    with zipfile.ZipFile(folder / FX) as z:
        fx_records = manifest(z.read("projectile_fx/source/manifest.txt").decode("utf-8"), 7)
    art = checked_archive(folder / ART, EXPECTED[ART], art_records)
    effects = checked_archive(folder / FX, EXPECTED[FX], fx_records)
    materials = checked_materials(folder)
    if not args.install:
        print("PASS 34 original module/ship/reference images + 7 projectile sheets validated;"
              " " + str(len(materials)) + " optional material textures; no files changed")
        return 0

    entries = {}
    for path, data in art.items():
        if path.startswith("assets/ships/terran/"):
            # All ten approved starter ship cutouts are runtime-ready placeholders.
            entries[path] = data
        else:
            # Never ship raw concept sheets in desktop/Android packaged assets.
            entries["art_source/" + path] = data
    for path, data in effects.items():
        entries["art_source/effects/" + path] = data
    entries.update(materials)
    entries.update(curated_beams(effects["projectile_fx/source/07_continuous_beam_fx.png"]))

    provenance = {
        "schema": "cd-art-install-1",
        "sourceArchives": EXPECTED,
        "sourceCount": {"concept_and_ships": 34, "weapon_effect_sheets": 7},
        "curatedBeams": 35,
        "installedTerran": list(TERRAN),
        "materials": [path for path in sorted(materials)],
        "curation": "Only clean continuous beam strips; no physical emitters or unreviewed missile cutouts",
        "installedPaths": {k: digest(v) for k, v in sorted(entries.items())},
    }
    entries["art_source/IMPORT_MANIFEST.json"] = (
        json.dumps(provenance, indent=2, sort_keys=True) + "\n").encode("utf-8")
    # Complete conflict pass BEFORE writing anything; no old art ever overwritten.
    for path, data in entries.items():
        target = ROOT / path
        if target.exists() and digest(target.read_bytes()) != digest(data):
            raise ValueError("Refusing to overwrite modified file: " + str(target))
    written = 0
    for path, data in entries.items():
        target = ROOT / path
        if target.exists():
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_suffix(target.suffix + ".tmp")
        temporary.write_bytes(data)
        temporary.replace(target)
        written += 1
    print(f"PASS installed {written} new files; {len(entries)} total registered."
          " Do not claim GitHub images are committed until git push + remote readback.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, RuntimeError, zipfile.BadZipFile) as error:
        print("FAIL art import: " + str(error), file=sys.stderr)
        sys.exit(1)
