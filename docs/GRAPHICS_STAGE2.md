# Graphics integration stage 2 — approved images and complete faction silhouettes

## Current state

The tactical HUD and shipyard use the approved UI atlas and button graphics from the
checksummed, chunked repository pack in `asset_packs/ui-kit/`. The mobile and desktop
Gradle tasks reconstruct the approved source ZIP and check its SHA-256 and 87-icon
index before bundling those images; cropped status-bar pieces are excluded.
The text controls and their existing input regions remain active.

Both renderer paths now draw the same filled faction-specific top-down ship geometry
when a unique image is absent. The 6 factions and 11 fitting hulls produce **66
distinct silhouette combinations** using the existing `HullLayouts.outline`, not a
generic triangle. The GPU-instanced path batches by faction/class. The software
fallback uses the same triangulation. Legacy unfitted hulls use the corvette
geometry. The authoritative simulation, damage, weapon resolution and engineering
hardpoints are unchanged.

## Real Terran artwork: non-destructive import

The existing `TerranPlaceholderSprites` game renderer loads authored class images
under `assets/ships/terran/`, skipping duplicate geometry for successfully loaded
sprites and using native hull silhouettes for missing class images.

The original approved runtime kit is saved separately as
`Catastrophic_Diplomacy_Graphics_Integration_Kit.zip`
(SHA-256 `ed71aea2d831489ac325397741164c6a703d3321a014a34354459ac46bbbd391`).
Its 57 graphics consist of 10 Terran hull PNGs, 35 beams, 10 themed buttons, one
800×720 icon atlas and its 87-entry TSV registry. It has not yet been pushed to
GitHub as individual sprite binaries.

From the **repository root**, after downloading the kit to the same machine:

```sh
python tools/install_graphics_kit.py --archive /path/to/Catastrophic_Diplomacy_Graphics_Integration_Kit.zip --check
python tools/install_graphics_kit.py --archive /path/to/Catastrophic_Diplomacy_Graphics_Integration_Kit.zip --install
gradle :game:geometrySmoke
gradle :desktop:run
gradle :android:assembleDebug
```

The importer refuses unexpected archives, missing or mismatched per-file hashes,
tampered paths, conflicting edited files, malformed image headers, or wrong approved
asset counts. It is idempotent and only creates missing files. The **battlecruiser,
carrier and dreadnought** have substantial boundary contamination / clipping in the
original source cutouts. The importer quarantines them by default and the game
renders their class-specific native silhouettes instead. Use
`--allow-provisional-large-hulls` only for knowingly provisional visual inspection;
this is not QA approval of those ship sprites.

An install on a local clone does **not** commit the added PNGs back to GitHub:
stage, review and push them separately when a trusted remote upload is available.
The committed UI pack does not include the Terran ship cutouts.

## Acceptance gates

* Java-only test `diplomacy.client.HullSilhouetteTest` checks triangle count and exact
  fill area on all 66 outlines, catching hole/overfill regressions. A mirrored
  standalone JDK17 geometry check passed during development; the repository Gradle
  task and real GPU output remain to be run on a machine with libGDX dependencies.
* Test GPU/GL2 fallback with 200 versus 200, and cycle factions and all eleven hulls.
* Confirm sprites take precedence where present and native silhouettes remain where
  art is absent. Confirm shield, localized damage and firing FX are visually coherent.
* Verify Android/desktop menus with touch, mouse, keyboard, pause/resume, shipyard
  switching, UI scaling, packaging, and actual GPU frame time.
* Do **not** treat alien lineup references as runtime sprites. Crop, reconstruct
  and independently validate every hull before acceptance; never render whole fleet
  sheets as individual vessels.
* There has been no claim of physical Android testing or a newly built signed APK.
