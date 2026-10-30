#!/usr/bin/env python3
"""Build a REAL Windows Installer (.msi) or portable Linux app ZIP.

Never disguise an application folder as a Windows installation package.
Run only after Gradle :desktop:installDist; native packages embed Java 17.
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
MSI_UPGRADE_UUID = "cfc2e09f-fbaa-4b95-9f20-d59ac1148057"
MSI_APP_VERSION = "0.1.5"  # Windows Installer numeric major.minor.patch
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
        raise SystemExit("Only Windows and Linux application targets are configured")
    jars = sorted(LIB.glob("desktop-*.jar"))
    if len(jars) != 1:
        raise SystemExit(f"Expected one installed desktop JAR: {jars}")
    with zipfile.ZipFile(jars[0]) as jar:
        names = set(jar.namelist())
        missing = [name for name in ASSETS if name not in names]
        if missing:
            raise SystemExit(f"Missing real runtime assets: {missing}")
        if "diplomacy/desktop/DesktopLauncher.class" not in names:
            raise SystemExit("Desktop main class missing")
        if not any(x.startswith("ships/factions/") and x.endswith(".png") for x in names):
            raise SystemExit("Faction ship art missing")
    if shutil.which("jpackage") is None:
        raise SystemExit("JDK 17 jpackage is required")
    OUT.mkdir(parents=True, exist_ok=True)
    cmd = [
        "jpackage", "--name", NAME,
        "--input", str(LIB), "--main-jar", jars[0].name,
        "--main-class", "diplomacy.desktop.DesktopLauncher",
        "--dest", str(OUT), "--java-options", "-Dfile.encoding=UTF-8",
    ]
    if system == "Windows":
        # The hosted Windows 2025 image provides WiX 3.14 for JDK 17 jpackage.
        env = os.environ.copy()
        for version in ("3.14", "3.11"):
            root = pathlib.Path(os.environ.get("ProgramFiles(x86)", "C:/Program Files (x86)")) / (
                "WiX Toolset v" + version) / "bin"
            if (root / "candle.exe").is_file() and (root / "light.exe").is_file():
                env["PATH"] = str(root) + os.pathsep + env["PATH"]
                break
        if shutil.which("candle.exe", path=env["PATH"]) is None or \
           shutil.which("light.exe", path=env["PATH"]) is None:
            raise SystemExit("WiX 3 is required. No ZIP fallback for Windows.")
        old = set(OUT.glob("*.msi"))
        subprocess.run(cmd + [
            "--type", "msi", "--app-version", MSI_APP_VERSION,
            "--vendor", "Catastrophic Diplomacy",
            "--win-upgrade-uuid", MSI_UPGRADE_UUID,
            "--win-per-user-install", "--win-dir-chooser",
            "--win-menu", "--win-shortcut",
        ], check=True, env=env)
        made = list(set(OUT.glob("*.msi")) - old)
        if len(made) != 1:
            raise SystemExit(f"Expected exactly one new MSI: {made}")
        target = OUT / "Catastrophic-Diplomacy-Windows-x64.msi"
        if target.exists():
            target.unlink()
        made[0].rename(target)
        with target.open("rb") as installer:
            magic = installer.read(8)
        if target.stat().st_size < 1_000_000 or magic != bytes.fromhex("d0cf11e0a1b11ae1"):
            raise SystemExit("Output is not an MSI structured-storage installer")
        if zipfile.is_zipfile(target):
            raise SystemExit("Windows installer must not be a ZIP")
    else:
        image = OUT / NAME
        if image.exists():
            shutil.rmtree(image)
        subprocess.run(cmd + ["--type", "app-image"], check=True)
        runtime = image / "lib" / "runtime"
        if not (image / "bin" / NAME).is_file() or not (runtime / "release").is_file() or \
           not (runtime / "lib" / "modules").is_file():
            raise SystemExit("Linux launcher or bundled runtime missing")
        target = OUT / "Catastrophic-Diplomacy-Linux-x64.zip"
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for source in sorted(image.rglob("*")):
                if source.is_file():
                    archive.write(source, pathlib.Path(NAME) / source.relative_to(image))
        if not zipfile.is_zipfile(target):
            raise SystemExit("Invalid Linux distributable")
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (OUT / (target.name + ".sha256")).write_text(
        f"{digest}  {target.name}\n", encoding="ascii")
    print(f"PASS {system} {target.name} ({target.stat().st_size} bytes), sha256={digest}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
