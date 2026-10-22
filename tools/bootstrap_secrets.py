#!/usr/bin/env python3
"""One-shot, local-only GitHub Actions secret bootstrap for Catastrophic Diplomacy.

Run once on a trusted machine after `gh auth login` and installation of JDK/keytool:
    python tools/bootstrap_secrets.py

DO NOT push .local-secrets. Keystore and passwords are durable release identity;
make an offline protected backup. The script never prints secret contents.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

REPO = 'mrcalzon02/Catastrophic-Diplomacy'
ALIAS = 'catastrophic-diplomacy'
ROOT = Path(__file__).resolve().parent.parent
PRIVATE = ROOT / '.local-secrets'
STORE = PRIVATE / 'android-release.p12'
PROPERTIES = PRIVATE / 'release.properties'
NAMES = ('CD_ANDROID_KEYSTORE_BASE64', 'CD_ANDROID_KEYSTORE_PASSWORD',
         'CD_ANDROID_KEY_ALIAS', 'CD_ANDROID_KEY_PASSWORD')


def invoke(command: list[str], *, input_text: str | None = None,
           env: dict[str, str] | None = None) -> str:
    completed = subprocess.run(command, input=input_text, text=True, capture_output=True,
                               env=env, check=False)
    if completed.returncode:
        # Commands operate on secrets; avoid dumping stdout/stderr containing anything sensitive.
        raise RuntimeError(f'{command[0]} failed (exit {completed.returncode}). '
                           'Check authentication, permissions or local tools.')
    return completed.stdout.strip()


def write_secret_file(path: Path, text: str) -> None:
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    fd = os.open(str(path), flags, 0o600)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as handle:
            handle.write(text)
            handle.flush()
            os.fsync(handle.fileno())
    except BaseException:
        path.unlink(missing_ok=True)
        raise


def parse_properties(text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in text.splitlines():
        if not line or line.lstrip().startswith('#'):
            continue
        key, sep, value = line.partition('=')
        if not sep:
            raise RuntimeError('Local signing properties are malformed.')
        result[key.strip()] = value.strip()
    return result


def credentials() -> dict[str, str]:
    has_key, has_properties = STORE.exists(), PROPERTIES.exists()
    if has_key != has_properties:
        raise RuntimeError('Incomplete existing signing identity in .local-secrets. '
                           'Restore BOTH files from backup; refusing to rotate.')
    PRIVATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    try:
        PRIVATE.chmod(0o700)
    except OSError:
        pass  # Windows ACL control is administered by the host.
    if has_key:
        saved = parse_properties(PROPERTIES.read_text(encoding='utf-8'))
        if saved.get('storeFile') != '.local-secrets/android-release.p12':
            raise RuntimeError('Unexpected keystore path; inspect the local signing properties.')
        if saved.get('keyAlias') != ALIAS:
            raise RuntimeError('Unexpected key alias; refusing to replace the signing identity.')
        password = saved.get('storePassword', '')
        key_password = saved.get('keyPassword', '')
        if not password or not key_password:
            raise RuntimeError('Incomplete signing passwords in local properties.')
        print('Reusing the existing local signing identity; no key rotation.')
    else:
        tool = shutil.which('keytool')
        if not tool:
            raise RuntimeError('JDK keytool not found on PATH. Install a JDK 17+ first.')
        password = secrets.token_urlsafe(36)
        key_password = password  # Required for widest PKCS12 Android signing compatibility.
        env = os.environ.copy()
        env['CD_BOOTSTRAP_STOREPASS'] = password
        print('Generating new local Android upload key with keytool (no material printed).')
        try:
            invoke([tool, '-genkeypair', '-noprompt', '-storetype', 'PKCS12',
                    '-keystore', str(STORE), '-alias', ALIAS,
                    '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                    '-dname', 'CN=Catastrophic Diplomacy, OU=Local Release, O=Independent Games, C=US',
                    '-storepass:env', 'CD_BOOTSTRAP_STOREPASS',
                    '-keypass:env', 'CD_BOOTSTRAP_STOREPASS'], env=env)
            write_secret_file(PROPERTIES, '# Locally generated signing identity; never commit.\n'
                              'storeFile=.local-secrets/android-release.p12\n'
                              'keyAlias=' + ALIAS + '\n'
                              'storePassword=' + password + '\n'
                              'keyPassword=' + key_password + '\n')
        except BaseException:
            # If keystore creation succeeded but metadata failed, do not silently mint a
            # second incompatible identity on retry; require deliberate recovery.
            raise
        try:
            STORE.chmod(0o600)
        except OSError:
            pass
    return {'CD_ANDROID_KEYSTORE_BASE64': base64.b64encode(STORE.read_bytes()).decode('ascii'),
            'CD_ANDROID_KEYSTORE_PASSWORD': password,
            'CD_ANDROID_KEY_ALIAS': ALIAS,
            'CD_ANDROID_KEY_PASSWORD': key_password}


def main() -> int:
    parser = argparse.ArgumentParser(description='Create and upload private Android signing secrets once.')
    parser.add_argument('--check-only', action='store_true',
                        help='Check prerequisites without generating keys or uploading secrets')
    args = parser.parse_args()
    cli = shutil.which('gh')
    if not cli:
        print('Missing GitHub CLI `gh`. Install it, then run gh auth login.', file=sys.stderr)
        return 2
    try:
        try:
            invoke([cli, 'auth', 'status'])
        except RuntimeError:
            print('GitHub CLI is not authenticated. Opening one-time browser authorization.')
            login = subprocess.run([cli, 'auth', 'login', '--hostname', 'github.com',
                                    '--web', '--scopes', 'repo,workflow'], check=False)
            if login.returncode != 0:
                raise RuntimeError('GitHub browser authentication was not completed.')
            invoke([cli, 'auth', 'status'])
        resolved = invoke([cli, 'repo', 'view', REPO, '--json', 'nameWithOwner',
                           '--jq', '.nameWithOwner'])
        if resolved.lower() != REPO.lower():
            raise RuntimeError('GitHub repository identity did not match expected repo.')
        if args.check_only:
            print('PASS: gh is authenticated and repository is reachable. No keys changed.')
            return 0
        existing_listing = invoke([cli, 'secret', 'list', '--repo', REPO,
                                   '--app', 'actions', '--json', 'name'])
        existing = {entry['name'] for entry in json.loads(existing_listing)}
        if (set(NAMES) & existing) and not (STORE.exists() and PROPERTIES.exists()):
            raise RuntimeError('This repository already has Android signing secrets, but the '
                               'local signing identity is absent. Restore .local-secrets '
                               'from backup; refusing to rotate an existing release key.')
        values = credentials()
        for name in NAMES:
            invoke([cli, 'secret', 'set', name, '--repo', REPO, '--app', 'actions'],
                   input_text=values[name])
            print('Uploaded: ' + name)
        listing = invoke([cli, 'secret', 'list', '--repo', REPO, '--app', 'actions',
                          '--json', 'name'])
        found = {row['name'] for row in json.loads(listing)}
        missing = set(NAMES) - found
        if missing:
            raise RuntimeError('Secret readback missing: ' + ', '.join(sorted(missing)))
        print('PASS: all 4 GitHub Actions secret names verified in ' + REPO + '.')
        print('IMPORTANT: Back up .local-secrets securely OFFLINE. Never commit it.')
        return 0
    except (RuntimeError, OSError, ValueError, json.JSONDecodeError) as error:
        print('FAILED: ' + str(error), file=sys.stderr)
        print('Signing files, if generated, remain local for a safe retry.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
