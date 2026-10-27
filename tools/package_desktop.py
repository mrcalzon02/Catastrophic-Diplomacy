#!/usr/bin/env python3
"""Produce native Windows/Linux app-images containing Java 17 and validated game assets.

Run after Gradle :desktop:installDist. jpackage packages the current OS only;
GitHub Actions runs the same script on both windows-latest and ubuntu-latest.
No Android signing keys, hosted downloaders, or alternate game logic involved.
"""
from __future__ import annotations

import hashlib
import os
import pathlib
import platform
import shutil
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
LIB = ROOT / "desktop" / "build" / "install" / "desktop" / "lib"
OUT = ROOT / "desktop" / "build" / "platform-packages"
NAME = "CatastrophicDiplomacy"
ASSETS = (
    "audio/music/final_surge.mp3",
    "audio/music/cold_unresolved_drone.mp3",
    "audio/sfx/laser_zap.wav",
    "effects/weather/asteroid_rock_00.png",
    "ui/tactical/selected/shipyard_ship.png",
)


def main() -> int:
    system = platform.system()
    if system not in ("Windows", "Linux"):
        raise SystemExit("Only Windows and Linux app-image targets are currently configured")
    jars = sorted(LIB.glob("desktop-*.jar"))
    if len(jars) != 1:
        raise SystemExit(f"Expected precisely one built desktop app JAR in {LIB}: {jars}")
    with zipfile.ZipFile(jars[0]) as jar:
        names = set(jar.namelist())
        missing = [name for name in ASSETS if name not in names]
        if missing:
            raise SystemExit(f"Desktop archive is missing real runtime assets: {missing}")
        if "diplomacy/desktop/DesktopLauncher.class" not in names:
            raise SystemExit("Desktop main class missing from packaged JAR")
        if not any(p.startswith("ships/factions/") and p.endswith(".png") for p in names):
            raise SystemExit("Faction ship art missing from desktop JAR")
    if shutil.which("jpackage") is None:
        raise SystemExit("JDK 17 jpackage is required for bundled desktop runtime")
    OUT.mkdir(parents=True, exist_ok=True)
    image = OUT / NAME
    if image.exists():
        shutil.rmtree(image)
    cmd = [
        "jpackage", "--type", "app-image", "--name", NAME,
        "--input", str(LIB), "--main-jar", jars[0].name,
        "--main-class", "diplomacy.desktop.DesktopLauncher",
        "--dest", str(OUT),
        "--java-options", "-Dfile.encoding=UTF-8",
    ]
    subprocess.run(cmd, check=True)
    expected_launcher = (image / (NAME + ".exe")) if system == "Windows" else (image / "bin" / NAME)
    if not expected_launcher.is_file():
        raise SystemExit(f"Missing platform executable: {expected_launcher}")
    runtime_root = image / "runtime" if system == "Windows" else image / "lib" / "runtime"
    runtime_java = runtime_root / "bin" / ("java.exe" if system == "Windows" else "java")
    if not (runtime_root / "release").is_file() or not (runtime_root / "lib" / "modules").is_file():
        raise SystemExit("Portable package does not contain its own linked Java runtime")
    target = OUT / f"Catastrophic-Diplomacy-{system}-x64.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for source in sorted(image.rglob("*")):
            if source.is_file():
                archive.write(source, pathlib.Path(NAME) / source.relative_to(image))
    if not zipfile.is_zipfile(target):
        raise SystemExit("Invalid desktop distributable ZIP")
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (OUT / (target.name + ".sha256")).write_text(f"{digest}  {target.name}\n", encoding="ascii")
    print(f"PASS {system} self-contained launcher, bundled Java runtime, actual art/audio, {target.name}, sha256={digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
