# Original faction fleet artwork restored from Google Drive

**Diagnosis:** The five original non-Terran civilizations (Draxian, Uzipt, Mandrally, Carnelian and Zippledrow) had no runtime art paths, so the OpenGL instanced renderer drew blue or faction-tinted polygon silhouettes in the battle screensaver. The five newer alien factions did have working standalone PNG files. The user's original Ships-bulk Drive directory already contains ten individually class-labeled fleet sheets, and identical binary sources were committed to GitHub under `assets/ships/alien/lineups/series-01/`.

The canonical, checksum-pinned source-to-faction binding is `assets/ships/factions/legacy-sheets.json`. The `prepareLegacyFactionArt` Gradle task extracts **five × ten** transparent ship images from **five independent original sources**, removing the matte and title labels. It produces `build/generated/cd-legacy-factions/ships/legacy_factions/<faction>/<role>.png`. The generated output is included in both Android assets and the desktop runtime JAR via their standard packaging tasks.

Original source sheets are **not replaced or recolored**, and new alien factions retain their own separate 50 PNGs. This is a curated assignment of existing original sheet art to the five older faction identities. The legacy class roster has its own dreadnought sprite; its additional `CRUISER` hull variant shares `light_cruiser.png`, since the source sheet has exactly ten class images.

To verify the actual source-to-runtime flow locally: `gradle prepareLegacyFactionArt :desktop:stageDesktopAssets` and inspect `desktop/build/runtime-assets/ships/legacy_factions/`. For APKs, `gradle :android:assembleDebug` must package the same generated textures. Confirm device screenshots for Urzipt and the remaining original civilizations; source assembly and registry tests alone are **not** proof of observed on-device rendering. Art acceptance is diagnostic, while only functional failures block published releases.

The five assignments are documented in the machine-readable registry, not an unconsumed prose table.
