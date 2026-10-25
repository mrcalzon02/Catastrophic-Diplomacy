# Artwork integration — staged runtime build 1 (2026-10-24)

## Asset inventory and actual remote state

- **34 source images** The full original module-art archive contains **34 source images**: 22 technology-module sheets, ten Terran ship cutouts, and two battle/damage reference illustrations. It is backed up in the ChatGPT project Library as `catastrophic_diplomacy_art_source_library_2026-10-24.zip` (SHA-256 `dfae7ce118b654012901ddd080378f8326eb3c2d9dc87bbbcd00d0ff06674a66`). The raw module PNGs themselves are not all committed to GitHub.
- **7 weapon-effect sheets**, including the approved continuous-beam panel, live in the backed-up `catastrophic_diplomacy_weapon_fx_source_2026-10-24.zip` (SHA-256 `07def3cb7ad47206aef4331c3a7f595b4504d749f85704a288de52487f72efb0`). The raw effect-sheet PNGs are not all committed to GitHub.
- **3 real material PNG binaries ARE committed to `main`** at `assets/materials/shield_energy_blue.png`, `structure_damaged.png`, and `structure_destroyed.png`; Git tree blob hashes were verified independently from source bytes. No source manifest has been misrepresented as a PNG upload.
- **10 Terran PNG cutouts + 35 curated beam PNGs** are packaged in the project Library transfer file `Catastrophic_Diplomacy_Runtime_Art_Stage1.zip`, along with the three material PNGs and a SHA-256 manifest. These **45 image binaries are not yet committed to GitHub**. The pack is self-contained and extracts to the runtime's `assets/` directory.
- **Alien fleet lineup PNGs** already present under `assets/ships/alien/lineups/` are reference palettes. They are not equivalent to ten transparent individual ship cutouts per faction. Their assignment/segmentation and ship-specific hit-box/UV anchors require review; do not automatically render a whole fleet palette as a vessel.

## Actual runtime code now present

1. `TerranPlaceholderSprites` resolves every fitted hull to its appropriate cutout. The legacy generic Cruiser uses `light_cruiser.png`. A ship without its exact sprite still gets the existing generic geometry fallback. The instanced GPU path and software fallback skip geometry for vessels that already have approved sprites, avoiding double-rendered ships.
2. `ShipyardPanel` offers an explicit **SHOW ART / SHOW MAP** view toggle. Art preview shows the intact Terran ship cutout but never pretends the provisional hardpoint schematic coordinates are verified pixel-space or 3D mounts. Module editing remains in SHOW MAP mode; changing faction or hull resets preview safely.
3. `WeaponBeamSprites` loads 35 transparent isolated beam frames at `assets/effects/beams/{blue,red,green,violet,gold}_01..07.png`. Laser, kinetic, plasma and particle beam simulation records drive the animation and stretched draw geometry; no physical emitter art is accepted. Missing effect PNGs fall back to existing straight colored beam lines.
4. The existing localized hull damage transparency compositor can now load the authored damaged and destroyed metal textures through an alpha-masked inner-hull material, and applies the shield material as a faint masked overlay. With missing materials, the former procedural structure and no shield overlay are retained. No damage physics were rewritten.
5. `desktop/build.gradle` resolves `Gdx.files.internal` from `assets/`, matching Android's existing assets root.

## Importing authentic source archives

Keep both authoritative zip archives in a single folder, optionally accompanied by the original `shield_energy_blue.png.b64.txt`, `structure_damaged.png.b64.txt`, and `structure_destroyed.png.b64.txt`.

Run from a trusted clone:

```sh
python tools/import_game_art.py --source-dir /path/to/archives --check
python -m pip install Pillow
python tools/import_game_art.py --source-dir /path/to/archives --import
```

`--check` uses only stdlib and verifies **all** original binary hashes before touching the checkout. `--import` writes a deterministic manifest, all ten Terran images, 35 emitter-free beam frames, and (when provided) three original material images. The raw module/effect art and reference sheets are staged under `art_source/` **outside** `assets/`, so Android does not accidentally bundle dozens of MB of unapproved source imagery. The importer refuses any modified destination, rather than overwriting good artwork to satisfy an obsolete test. Review `art_source/IMPORT_MANIFEST.json` and git status before committing images.

Alternatively, extract `Catastrophic_Diplomacy_Runtime_Art_Stage1.zip` at the repository root to install the runtime-ready subset only. Verify `runtime-art-manifest.json` first and commit original PNG bytes rather than screenshot proxies.

## Static firing-range video QA (planned)

The repeatable two/three-weapon static range and nine review batches are specified in
[Weapon Firing Range](WEAPON_FIRING_RANGE.md) and the machine-readable
[`qa/firing-range/batches.json`](../qa/firing-range/batches.json).
This includes a 24-second three-lane fixed-camera barrage, deterministic 30 Hz combat
sampling with 60 fps video, impact and cleanup tests, and **explicit human video approval**.
This is a *plan*, not an already-rendered MP4, and the missing 45 runtime PNG Git binaries
remain an asset installation gate. The current four-tick beams cannot be represented
as a completed seven-frame animation; production-timed and slow frame-inspection
captures have separate acceptance statuses.

## Boundaries and remaining acceptance work

- No libGDX desktop or Android display/build verification was run during this integration. The user must not be told this is a verified playable APK.
- No alien individual sprites were arbitrarily harvested from fleet comparison sheets.
- No physical emitter attached to a projectile is accepted as a particle FX.
- Module sheets are preserved, **not** automatically mounted as curated module icons.
- Physical turret muzzle coordinates, per-module visual damage, exact sprite-space slot anchors, transparency edge QA, particle effects beyond these beam strips, and GL30 performance are pending.
- A full remote Git binary import is still pending for the 10 Terran and 35 beam cutouts. Code readiness and a Library transfer ZIP are not proof of GitHub binary availability.

## Smoke checks

```sh
python tools/import_game_art.py --source-dir /path/to/archives --check
python tools/import_game_art.py --source-dir /path/to/archives --import
gradle :core:shipyardSmoke
gradle :desktop:run
gradle :android:assembleDebug
```

Visually verify each of the six factions, all 11 hull selections, the Terran SHOW ART toggle, sprite fallback and non-overlap, beams by damage type, damage material transitions, native touch selection, and APK asset paths. Do not use a text manifest or a headless test as a substitute for observing the rendered frames.
