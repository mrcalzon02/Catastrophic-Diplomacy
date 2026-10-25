#!/usr/bin/env python3
"""Emit updater release-manifest.json from an existing, signed sideload APK.

No keystores are accessed. aapt and apksigner must both succeed; this script
refuses unsigned APKs and never publishes or changes GitHub release settings.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

PACKAGE = 'games.catastrophicdiplomacy'
VERSION = re.compile(r'[A-Za-z0-9][A-Za-z0-9._+\-]{0,99}')
APK_NAME = re.compile(r'[A-Za-z0-9][A-Za-z0-9._+\-]{0,179}\.apk$', re.IGNORECASE)
MAX_BYTES = 400 * 1024 * 1024


def describe(apk):
    aapt = shutil.which('aapt')
    apksigner = shutil.which('apksigner')
    if not aapt or not apksigner:
        raise ValueError('aapt and apksigner must be installed (Android SDK Build Tools)')
    result = subprocess.run([aapt, 'dump', 'badging', str(apk)], check=True,
                            text=True, capture_output=True, timeout=90)
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", result.stdout, re.M)
    if not match:
        raise ValueError('Could not independently read APK identity/version via aapt')
    application_id, code, version = match.groups()
    if application_id != PACKAGE or not VERSION.fullmatch(version) or int(code) < 1:
        raise ValueError('Wrong package ID, version, or version code')
    expected = apk.name
    if not APK_NAME.fullmatch(expected):
        raise ValueError('APK filename must be a safe .apk basename')
    size = apk.stat().st_size
    if not 4096 <= size <= MAX_BYTES:
        raise ValueError('APK outside bounded size limits')
    subprocess.run([apksigner, 'verify', '--verbose', str(apk)], check=True,
                   capture_output=True, timeout=90)
    digest_engine = hashlib.sha256()
    with apk.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest_engine.update(block)
    digest = digest_engine.hexdigest()
    return {
        'schema': 1,
        'version': version,
        'versionCode': int(code),
        'applicationId': PACKAGE,
        'minDataSchema': 1,
        'apk': {'name': expected, 'bytes': size, 'sha256': digest},
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('apk', type=Path, help='Already signed release APK')
    ap.add_argument('--output', type=Path, default=Path('release-manifest.json'))
    args = ap.parse_args()
    if not args.apk.is_file():
        ap.error('APK does not exist')
    try:
        release = describe(args.apk)
        args.output.write_text(json.dumps(release, indent=2) + '\n', encoding='utf-8')
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired, ValueError) as error:
        ap.error('Release manifest blocked: ' + str(error))
    print(f"PASS: signed APK metadata for {release['version']} (code {release['versionCode']}) -> {args.output}")


if __name__ == '__main__':
    main()