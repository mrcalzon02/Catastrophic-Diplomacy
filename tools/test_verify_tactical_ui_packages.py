#!/usr/bin/env python3
"""Negative controls for the exact-byte UI package acceptance gate."""
from __future__ import annotations

import json
from pathlib import Path
import struct
import tempfile
import unittest
import warnings
import zipfile
import zlib

from verify_tactical_ui_packages import (
    PackageError, generated_pngs, load_approved, verify_archive,
)


def make_png(width: int = 2, height: int = 3) -> bytes:
    def chunk(tag: bytes, payload: bytes) -> bytes:
        return struct.pack('>I', len(payload)) + tag + payload + \
            struct.pack('>I', zlib.crc32(tag + payload))
    # Valid, fully decoded 8-bit RGB PNG with one red pixel per scanline.
    raw = (b'\x00' + b'\xff\x00\x00' * width) * height
    return (b'\x89PNG\r\n\x1a\n' +
            chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)) +
            chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


class PackageGateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.generated = self.root / 'generated'
        self.generated.mkdir()
        self.assets = {}
        for i in range(24):
            name = f'icon_{i:02d}'
            self.assets[name] = {
                'destination': f'assets/ui/tactical/selected/{name}.png',
                'sheet': 'systems', 'box': [0, 0, 2, 3],
                'sha256': 'a' * 64, 'pixel_sha256': 'b' * 64,
            }
            (self.generated / f'{name}.png').write_bytes(make_png())
        self.manifest = self.root / 'manifest.json'
        self.manifest.write_text(json.dumps({
            'schema': 'catastrophic-diplomacy-selected-ui-v5',
            'approved_only': True, 'assets': self.assets,
        }), encoding='utf-8')
        self.pngs = generated_pngs(load_approved(self.manifest), self.generated)

    def archive(self, android=False, *, missing=None, extra=None, altered=None, duplicate=None):
        path = self.root / ('app.apk' if android else 'desktop.jar')
        prefix = 'assets/ui/tactical/selected/' if android else 'ui/tactical/selected/'
        with zipfile.ZipFile(path, 'w') as out:
            for name, content in self.pngs.items():
                if name != missing:
                    out.writestr(prefix + name + '.png',
                                 make_png(2, 4) if name == altered else content)
            if extra:
                out.writestr(extra, make_png())
            if duplicate:
                with warnings.catch_warnings():
                    warnings.simplefilter('ignore', UserWarning)
                    out.writestr(prefix + duplicate + '.png', make_png())
        return path

    def test_real_zip_membership_contract_desktop_and_android(self):
        for android in (False, True):
            with self.subTest(android=android):
                result = verify_archive(self.archive(android), self.assets,
                                        self.pngs, android=android)
                self.assertEqual(len(result), 24)

    def test_missing_icon_fails(self):
        with self.assertRaisesRegex(PackageError, 'missing='):
            verify_archive(self.archive(missing='icon_00'), self.assets,
                           self.pngs, android=False)

    def test_corrupted_icon_fails(self):
        with self.assertRaisesRegex(PackageError, 'differs'):
            verify_archive(self.archive(altered='icon_01'), self.assets,
                           self.pngs, android=False)

    def test_rejected_atlas_fails(self):
        with self.assertRaisesRegex(PackageError, 'Rejected legacy'):
            verify_archive(self.archive(extra='ui/tactical/icons.png'),
                           self.assets, self.pngs, android=False)

    def test_unapproved_extra_icon_fails(self):
        with self.assertRaisesRegex(PackageError, 'extra='):
            verify_archive(self.archive(True, extra='assets/ui/tactical/selected/unknown.png'),
                           self.assets, self.pngs, android=True)

    def test_duplicate_zip_entry_fails(self):
        with self.assertRaisesRegex(PackageError, 'Duplicate archive'):
            verify_archive(self.archive(duplicate='icon_00'),
                           self.assets, self.pngs, android=False)

    def test_generated_dimensions_fails(self):
        (self.generated / 'icon_00.png').write_bytes(make_png(3, 3))
        with self.assertRaisesRegex(PackageError, 'dimensions differ'):
            generated_pngs(self.assets, self.generated)

    def test_manifest_must_pin_exactly_24(self):
        data = json.loads(self.manifest.read_text())
        del data['assets']['icon_00']
        self.manifest.write_text(json.dumps(data))
        with self.assertRaisesRegex(PackageError, 'exactly 24'):
            load_approved(self.manifest)


if __name__ == '__main__':
    unittest.main()
