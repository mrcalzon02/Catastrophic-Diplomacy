#!/usr/bin/env python3
"""Import only individually selected, hash-pinned UI sprites from the reviewed v5 ZIP.

No artwork is fetched remotely, no new GitHub workflow is added, and the old
87-cell atlas is never accepted. Existing differing artwork is never replaced.

  python tools/import_tactical_ui.py --archive /path/to/Catastrophic_Diplomacy_UI_Repair_v5.zip --check
  python tools/import_tactical_ui.py --archive /path/to/Catastrophic_Diplomacy_UI_Repair_v5.zip --import
  python tools/import_tactical_ui.py --verify
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import zipfile

ARCHIVE_SHA256 = "accf38170a34c601273a1cb65f30d066596b3ec8cee75b62f46ce2256f29faf6"
SCHEMA = "catastrophic-diplomacy-selected-ui-v5"
PNG = b"\x89PNG\r\n\x1a\n"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def safe_path(name, prefix):
    path = Path(name)
    return (
        not path.is_absolute() and "\\" not in name
        and ".." not in path.parts and str(path).startswith(prefix)
        and not any(part in ("", ".") for part in path.parts)
    )


def load_manifest(root):
    manifest = json.loads((root / "assets/ui/tactical/selected_manifest.json").read_text("utf-8"))
    assets = manifest.get("assets", {})
    if manifest.get("schema") != SCHEMA or manifest.get("approved_only") is not True:
        raise ValueError("Not a reviewed/approved-only v5 manifest")
    if len(assets) != 24:
        raise ValueError("Expected exactly 24 reviewed runtime selections")
    seen = set()
    for icon_id, item in assets.items():
        if not icon_id.replace("_", "").isalnum() or not icon_id.islower():
            raise ValueError("Invalid icon id")
        if not safe_path(item["source"], "approved/") or not item["source"].endswith(".png"):
            raise ValueError("Unsafe approved source")
        target = f"assets/ui/tactical/selected/{icon_id}.png"
        if item["destination"] != target or not safe_path(target, "assets/ui/tactical/selected/"):
            raise ValueError("Invalid runtime target")
        if len(item["sha256"]) != 64 or any(c not in "0123456789abcdef" for c in item["sha256"]):
            raise ValueError("Invalid checksum")
        if target in seen:
            raise ValueError("Duplicate destination")
        seen.add(target)
    return assets


def verified_sources(archive_path, selections):
    blob = archive_path.read_bytes()
    if sha256(blob) != ARCHIVE_SHA256:
        raise ValueError("Source archive hash mismatch: do not import unknown/repacked art")
    with zipfile.ZipFile(archive_path) as pack:
        if pack.testzip() is not None:
            raise ValueError("Corrupt ZIP payload")
        names = pack.namelist()
        if len(set(names)) != len(names):
            raise ValueError("Duplicate ZIP entries")
        result = {}
        for icon_id, item in selections.items():
            content = pack.read(item["source"])
            if sha256(content) != item["sha256"] or not content.startswith(PNG):
                raise ValueError(f"Approved sprite failed integrity test: {icon_id}")
            result[icon_id] = content
    return result


def installed(root, selections):
    missing, changed = [], []
    for name, item in selections.items():
        path = root / item["destination"]
        if not path.is_file():
            missing.append(name)
        elif sha256(path.read_bytes()) != item["sha256"]:
            changed.append(name)
    return missing, changed


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--archive", type=Path, help="Exact v5 archive downloaded from project Drive")
    ap.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parents[1])
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--import", dest="do_import", action="store_true")
    mode.add_argument("--verify", action="store_true")
    args = ap.parse_args()
    root = args.repo_root.resolve()
    selections = load_manifest(root)
    missing, changed = installed(root, selections)
    if changed:
        ap.error("Refusing to overwrite modified existing sprites: " + ", ".join(changed))
    if args.verify:
        if missing:
            ap.error("Runtime sprites not yet imported: " + ", ".join(missing))
        print(f"PASS: all {len(selections)} installed sprites match pinned hashes")
        return
    if args.archive is None:
        ap.error("--archive is required for --check and --import")
    blobs = verified_sources(args.archive, selections)
    if args.check:
        print(f"PASS: {len(blobs)} verified source sprites; {len(missing)} missing; {len(blobs)-len(missing)} already installed")
        return
    for name, blob in blobs.items():
        destination = root / selections[name]["destination"]
        if destination.exists():
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        fd, temp = tempfile.mkstemp(prefix=".approved-ui-", suffix=".png", dir=destination.parent)
        try:
            with os.fdopen(fd, "wb") as handle:
                handle.write(blob)
            os.replace(temp, destination)
        finally:
            if os.path.exists(temp):
                os.unlink(temp)
    missing, changed = installed(root, selections)
    if missing or changed:
        raise RuntimeError("Post-import verification failed")
    print(f"IMPORTED AND VERIFIED: {len(blobs)} reviewed sprites")


if __name__ == "__main__":
    main()
