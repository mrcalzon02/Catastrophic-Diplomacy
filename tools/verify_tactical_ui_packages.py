#!/usr/bin/env python3
"""Verify real desktop JAR / Android APK carry the 24 source-approved UI PNGs.

The Gradle prepareUiAssets task is the authoritative source-pixel verifier.
This gate checks the *built* distributions, byte-for-byte against its output.
No source art is imported or modified by this verifier.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = Path('assets/ui/tactical/selected_manifest.json')
GENERATED = Path('build/generated/cd-ui/ui/tactical/selected')
PNG_SIGNATURE = b'\x89PNG\r\n\x1a\n'


class PackageError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise PackageError(message)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def png_dimensions(data: bytes) -> tuple[int, int]:
    require(len(data) >= 33 and data.startswith(PNG_SIGNATURE), 'Missing PNG signature/IHDR')
    require(data[12:16] == b'IHDR' and struct.unpack('>I', data[8:12])[0] == 13,
            'Invalid PNG IHDR')
    width, height = struct.unpack('>II', data[16:24])
    require(width > 0 and height > 0, 'Invalid PNG dimensions')
    return width, height


def load_approved(manifest_path: Path) -> dict[str, dict]:
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    require(manifest.get('schema') == 'catastrophic-diplomacy-selected-ui-v5'
            and manifest.get('approved_only') is True, 'Wrong UI approval manifest')
    assets = manifest.get('assets')
    require(isinstance(assets, dict) and len(assets) == 24,
            'Expected exactly 24 approved UI icons')
    for name, item in assets.items():
        require(re.fullmatch(r'[a-z][a-z0-9_]*', name) is not None,
                f'Unsafe icon name: {name}')
        require(isinstance(item, dict) and
                item.get('destination') == f'assets/ui/tactical/selected/{name}.png',
                f'Unexpected icon destination: {name}')
        require(item.get('sheet') in ('systems', 'combat'), f'Unknown source sheet: {name}')
        require(isinstance(item.get('box'), list) and len(item['box']) == 4,
                f'Invalid crop box: {name}')
        x1, y1, x2, y2 = item['box']
        require(all(type(v) is int for v in item['box']) and
                0 <= x1 < x2 <= 1536 and 0 <= y1 < y2 <= 1024,
                f'Out-of-bounds crop: {name}')
        require(all(isinstance(item.get(key), str) and
                    re.fullmatch(r'[0-9a-f]{64}', item[key]) is not None
                    for key in ('sha256', 'pixel_sha256')),
                f'Missing source/pixel digest: {name}')
    return assets


def generated_pngs(assets: dict[str, dict], directory: Path) -> dict[str, bytes]:
    require(directory.is_dir(), f'Generated asset directory missing: {directory}')
    expected = {f'{name}.png' for name in assets}
    found = {p.name for p in directory.iterdir() if p.is_file()}
    require(found == expected, f'Generated PNG inventory differs: missing={sorted(expected-found)}, '
            f'extra={sorted(found-expected)}')
    result = {}
    for name, item in assets.items():
        data = (directory / f'{name}.png').read_bytes()
        x1, y1, x2, y2 = item['box']
        require(png_dimensions(data) == (x2-x1, y2-y1),
                f'Generated PNG crop dimensions differ: {name}')
        result[name] = data
    return result


def verify_archive(path: Path, assets: dict[str, dict], generated: dict[str, bytes],
                   *, android: bool) -> dict[str, str]:
    require(path.is_file(), f'Built distribution missing: {path}')
    prefix = 'assets/ui/tactical/selected/' if android else 'ui/tactical/selected/'
    expected = {prefix + name + '.png' for name in assets}
    with zipfile.ZipFile(path) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        require(len(names) == len(set(names)), f'Duplicate archive entries: {path}')
        # The rejected v1 square atlas must not sneak into any runtime UI folder.
        rejected = [name for name in names if name.lower().endswith('/icons.png')
                    and (name.startswith('assets/ui/') or name.startswith('ui/'))]
        require(not rejected, f'Rejected legacy icon atlas packaged: {rejected}')
        selected = {name for name in names if name.startswith(prefix)}
        require(selected == expected,
                f'Packaged selected-icon inventory differs in {path}: '
                f'missing={sorted(expected-selected)}, extra={sorted(selected-expected)}')
        checksums = {}
        for name, data in generated.items():
            entry = prefix + name + '.png'
            packed = archive.read(entry)
            require(packed == data,
                    f'Packaged PNG differs from source-verified generated PNG: {entry}')
            checksums[name] = digest(packed)
    return checksums


def find_desktop_jar(folder: Path) -> Path:
    jars = sorted(folder.glob('*.jar'))
    require(len(jars) == 1, f'Expected exactly one desktop runtime JAR in {folder}: {jars}')
    return jars[0]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, default=ROOT / MANIFEST)
    parser.add_argument('--generated', type=Path, default=ROOT / GENERATED)
    parser.add_argument('--desktop-dir', type=Path, help='Folder containing the built desktop JAR')
    parser.add_argument('--android-apk', type=Path, help='Actual built Android debug APK')
    args = parser.parse_args(argv)
    if args.desktop_dir is None and args.android_apk is None:
        parser.error('At least one actual packaged distribution must be supplied')
    try:
        assets = load_approved(args.manifest)
        pngs = generated_pngs(assets, args.generated)
        if args.desktop_dir is not None:
            jar = find_desktop_jar(args.desktop_dir)
            checksums = verify_archive(jar, assets, pngs, android=False)
            print(f'PASS: desktop JAR {jar}: {len(checksums)} exact approved PNGs; '
                  'no rejected atlas')
        if args.android_apk is not None:
            checksums = verify_archive(args.android_apk, assets, pngs, android=True)
            print(f'PASS: Android APK {args.android_apk}: {len(checksums)} exact approved PNGs; '
                  'no rejected atlas')
        for name in sorted(pngs):
            print(f'PNG SHA256 {name}.png {digest(pngs[name])}')
        return 0
    except (PackageError, OSError, ValueError, zipfile.BadZipFile) as exc:
        print(f'FAIL: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
