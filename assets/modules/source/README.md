# Source art holding library — Catastrophic Diplomacy

**Originals are preserved for selective alteration, not automatically accepted as mechanically correct.** The initial generated art source bundle holds 22 technology-module sheets (four baseline, five exploratory including one unsuitable multi-atlas composite, ten revised runs, three follow-up sheets), ten individual Terran ship concept cutouts, and two battle/armor reference sheets.

The baseline artwork defines our visual direction: crisp three-quarter isometric modules; coherent metallic chassis, visible bolt/edge construction, functional mounts and restrained light accents; faction-specific industrial/alien differences. **A launcher launches ordnance; missiles do not grow out of missiles.** Cannons have a breech, receiver, realistic mount and defined barrels. Repeated barrels, impossible missiles, fused adjacent icons, ambiguous silhouette, and style drift are quality failures requiring editing before use.

* `manifest.txt` registers all 34 source images, the intended repository paths and full SHA-256 checksums.
* A persistent copy of the full ZIP is also saved in the ChatGPT Library at `/Catastrophic diplomacy/catastrophic_diplomacy_art_source_library_2026-10-24.zip` (55,390,649 bytes); this is a user-held archive backup, not a GitHub binary commit.
* The original ZIP is `catastrophic_diplomacy_art_source_library_2026-10-24.zip` (SHA-256 `dfae7ce118b654012901ddd080378f8326eb3c2d9dc87bbbcd00d0ff06674a66`). Obtain from the project conversation's delivered download; it is **not yet hosted in GitHub**.
* `python tools/import_module_art.py --archive path/to/archive.zip --check` checks all source image hashes without modifications.
* `python tools/import_module_art.py --archive path/to/archive.zip --import` imports all original images into their registered repository folders, refusing to overwrite a modified copy.
* Existing Terran art may have a separate manifest and can remain in that scheme. Future curated module icons belong in `assets/modules/curated/` under stable catalog identifiers; never overwrite original source sheets.
* After importing, explicitly add and commit binary images from a normal authorized local Git environment. **Manifest presence alone is not evidence that the PNG binaries are in the remote repository.**

Each approved cropped/reworked icon must record source sheet ID, cell number, revision, mount scale, subsystem/technology ID, mechanical QA verdict, and alpha-edge inspection. Never automatically map a source tile to a gameplay module based solely on how it looks. Keep both original and edited versions for revisions.
