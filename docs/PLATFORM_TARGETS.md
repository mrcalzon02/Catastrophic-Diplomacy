# Deployment targets — one Catastrophic Diplomacy game

**Authoritative code:** `main` in `mrcalzon02/Catastrophic-Diplomacy`. The
simulation, menus, fitted fleets, ships, campaign and saved game rules remain in
`:core` and `:game`, independent of platform. Never create different gameplay
branches or replace approved art just to package another platform.

| Platform | Package target | Status / limitations |
| --- | --- | --- |
| Android phones / tablets | Retained-key signed APK via `:android:assembleRelease` | GitHub Actions signs and checks package identity; emulator and actual-device sign-off required |
| Amazon Fire tablets, Fire OS 7/8/14 | Exact same signed APK, additionally uploaded as a labeled Fire OS sideload artifact | APK compatibility check in CI; actual Fire-device validation and Amazon Appstore submission not certified |
| Amazon Fire OS 16 | Next compatibility target | Requires targetSdk **36+** for Appstore eligibility; currently the Android app targets 35 and must not be claimed Appstore-ready for OS 16 |
| Windows x64 | `:desktop:installDist` then JDK 17 `jpackage --type app-image` | Native EXE launcher with bundled Java runtime and approved resources, zipped in Windows CI |
| Linux x64 | Same source; Linux `jpackage --type app-image` | Native ELF launcher, bundled Java runtime, zipped in Linux CI; desktop GL/device acceptance separate |

## Fire tablet compatibility contract

Fire OS is Android-based; there is **one shared `:android` implementation**,
not an independent fork or a separate game save format. Current
`minSdk 26` supports Fire OS 7 (API 28) and newer compatible tablets, but not
Fire OS 6 (API 25) or older. Current `targetSdk 35` meets the Fire OS
7/8/14 Appstore minima, not the Fire OS 16 Appstore API 36 threshold.
Android's compileSdk remains 36. The Google Play submission API requirement is a
separate release gate; do not represent this source as Play-ready today.

The Android launcher now requests GLES3 only when the device reports it and
retains the game's existing GLES2 path on other devices. Target Fire OS 7,
8 and 14 hardware for separate acceptance including actual touch/menu
navigation, stable shaders/frames, landscape viewport, system Back,
process background/resume, sound, file persistence and installation/upgrade.
A passing generic Android emulator **does not certify** Fire hardware.

No Google Mobile Services are required by the Android game dependency stack;
the Fire APK must not acquire one. Amazon Appstore-installed
(`com.amazon.venezia`) instances must use their store for updates, as must
Google Play-installed instances; sideloaded instances retain the GitHub release
updater with signer/version checks. Appstore submission is not performed by this
repository's CI and would also require an Amazon developer listing and real
Fire hardware testing. The Fire-labeled CI APK is an **identical-byte,
identically-signed sideload candidate** of the Android release; it is **not**
a second Android application ID, alternate certificate, or second update track.

### Fire verification commands (CI)

After a signed release candidate has been built:

```bash
python3 tools/verify_fire_tablet_target.py --apk release/Catastrophic-Diplomacy-0.1.4-rc.1-Android.apk
```

The preflight validates the *actual APK*'s package ID, min/target SDK, ARMv7
and ARM64 native code, Google-free Gradle dependencies, and GLES2 selection.
The signing workflow also validates the original key's signature and then
uploads a separate Fire tablet artifact. Neither static APK checks nor
a renamed APK establish hardware performance or Appstore acceptance.

## Desktop packaging

Use the existing `:desktop` LWJGL3 launcher on both operating systems.
`:desktop:jar` now embeds the exact checked assets and generated UI/music
resources, independent of the shell's working directory.

```bash
gradle :core:gameFlowSmoke :game:menuFlowSmoke :desktop:installDist
python tools/package_desktop.py
```

The Python packager requires JDK 17's `jpackage`, verifies that the app JAR
contains the main class, faction sprite PNGs, approved tactical icons,
weather sprites and game audio, and creates a per-OS app-image ZIP with
an executable launcher, bundled Java runtime and SHA-256 checksum.
CI runs this *separately on Windows and Linux*, uploads the two
native artifacts and verifies executable presence. The CI checks confirm
packaging and headless functionality; actual graphical launch and long-play
GPU acceptance require device testing.

## Acceptance priorities

1. Passing Java17 deterministic game flow and real menu hit-target tests
   on Windows and Linux.
2. Actual distributables for both desktop systems, with bundled Java runtime
   and all accepted assets; unpack and launch on each OS.
3. Signed Android APK and Fire-labeled twin with package, signature and ABI
   checks. Do not rotate the signing identity.
4. Android emulator functional game-flow acceptance and real Fire tablet
   (OS 7/8/14) performance, permissions and save/upgrade acceptance.
5. Explicit Fire OS 16 targetSdk36 migration + acceptance and any future
   Amazon Appstore publication decision.
