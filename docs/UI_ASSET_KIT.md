# Tactical interface artwork — source-safe v5 implementation (2026-10-25)

## Actual state (do not confuse with an APK certification)

- **24 visually intact original-source icons are now integrated into the build pipeline**, using `assets/ui/tactical/selected_manifest.json` and the existing `:prepareUiAssets` task in `build.gradle`.
- **No broken 87-cell atlas is loaded.** `TacticalUiIcons` now loads individual, aspect-preserved textures from `ui/tactical/selected/<id>.png` and silently retains the original HUD text/hit regions when resources are absent.
- The source image binaries were ALREADY committed under `asset_packs/ui-kit/part-00.bin` through `part-19.bin`. The 20-part pack is retained **only** to recover its two original full-source PNGs; generated v1 atlas/button crops from that pack are rejected and never copied into the runtime.
- `prepareUiAssets` reconstructs and verifies the two full original PNGs with SHA-256, crops the 24 reviewed rectangles, compares the SHA-256 of all **RGBA source pixels**, and writes the exact-source selected PNGs to `build/generated/cd-ui/ui/tactical/selected/`. The existing desktop stage and Android assets.srcDirs both consume that generated directory.
- `tools/import_tactical_ui.py` provides an optional separate explicit `--check / --import / --verify` path for importing only those same 24 pinned PNGs from the audited v5 transfer archive, but builds do **not** depend on Google Drive downloads or manually uploaded PNGs.
- **Not claimed:** a successful whole-project Gradle build, screenshot/device verification, signed APK, user artistic sign-off for all 24 candidates, or approval of the remaining sprites.

## Verified transfer and visual references

- [Preserved and repaired v5 archive](https://drive.google.com/file/d/1sNfo4wI0O30IMsXOKsJ4HObxiYxGWvfy/view?usp=drivesdk): complete original PNG sources; 289 v4 technical-QC-pass crops, 15 newer provisional crops and 22 held older crops/controls.
- [Actual 24 selected sprites contact sheet](https://drive.google.com/file/d/1-T6NUy1TdHiuqW07d_R2zuYQ9t1yhjKS/view?usp=drivesdk) — assembled from the real extracted PNG files. No stock image substitutions.
- [Original approval v4 archive](https://drive.google.com/file/d/194MUEtvKuIR3ieFgJ5UaaI37iElT9_01/view?usp=drivesdk).

The v5 ZIP SHA-256 is `accf38170a34c601273a1cb65f30d066596b3ec8cee75b62f46ce2256f29faf6`.
The source archive already committed to GitHub is SHA-256 `8220b70e36e7133f8bbb5d1d89dacc0a1b14ee12671c481d822454901f80a170`.
The original systems PNG is `3b74777d6c4a6dc8448fa0958f161b1b44967ef5a159a768bd36ca223e36417c` and combat PNG is `7bca86a5ba6cb908f5985e34293157ccad0cb4fb23e4700112afc2f0b2622d26`.

## Build and test instructions

The asset-generation step is dependency-wired to both clients already:

```bash
gradle prepareUiAssets
gradle :desktop:run
gradle :android:assembleDebug
```

Optional verified loose-asset import:

```bash
python tools/import_tactical_ui.py --archive /path/to/Catastrophic_Diplomacy_UI_Repair_v5.zip --check
python tools/import_tactical_ui.py --archive /path/to/Catastrophic_Diplomacy_UI_Repair_v5.zip --import
python tools/import_tactical_ui.py --verify
```

In this environment Java `ImageIO` was independently run on the source ZIP using the exact 24 v5 rectangles: **24/24 RGBA pixel hashes matched and 24/24 PNG readback pixel comparisons passed**. Gradle/Android SDK are unavailable here, so this does not certify the complete launchers or in-game visuals. Tests still required: desktop render screenshot; Android device/emulator; 24 px/32 px readability; UI texture disposal; preservation of touch controls and 200-v-200 combat performance.

## Remaining repair backlog

- **15 recropped icons remain provisional**, because some new tighter cuts still lose frame edges or retain neighboring pixels. Do not call them repaired/approved on the strength of a successful export.
- **20 earlier provisional crops and two visibly damaged controls remain on hold.**
- Only the 24 manifest-pinned, visually intact v4 crops have been selected for the first actual UI build.
- The rest of the 289 v4 technical-QC-pass crops are preserved but have not been configured as active runtime sprites.
- All original images are retained so future polygon masks, manual redraws and informed visual approvals can proceed without irreversible changes.

No new gameplay systems, damage rules, touch actions, or GitHub Actions workflows were introduced by this asset implementation.
