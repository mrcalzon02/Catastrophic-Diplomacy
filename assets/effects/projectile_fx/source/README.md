# Catastrophic Diplomacy — weapon VFX source archive registry

Status: RAW_SOURCE_ARCHIVED; PNG_BINARIES_NOT_YET_COMMITTED
Source Library backup: /Catastrophic diplomacy/catastrophic_diplomacy_weapon_fx_source_2026-10-24.zip
Archive bytes: 17089477
Archive SHA-256: 07def3cb7ad47206aef4331c3a7f595b4504d749f85704a288de52487f72efb0

The seven original, uncut PNG sprite sheets are preserved in the archive. Paths below are archive paths relative to assets/effects/ when the ZIP is extracted into a repository checkout. Registering them here does not mean the binary PNGs were committed to GitHub. Do not mark the ingestion completed until the actual PNG blobs are in Git and read back from the remote.

# Source contents (archive path|sha256|size bytes|dimensions|selection status)
projectile_fx/source/01_initial_projectile_arsenal.png|a92f2c88b76c0e95f476af2df07476898b3609070fa9fc541a74367ef406649c|1788108|1254x1254|candidate_unreviewed
projectile_fx/source/02_neon_projectile_variants.png|85872fc18d10b33e0271cea882c66f0b4d4ba9f7546a953cd68eaa0b21172428|2417817|1254x1254|candidate_unreviewed
projectile_fx/source/03_ballistic_missile_arsenal.png|069edc48f9d04d1eb2f14c1b8eb7fbf2e729660d2464c11bb3b31d3391926264|2345137|1254x1254|candidate_unreviewed
projectile_fx/source/04_missile_volley_explosions.png|29050aa2bb4dc0da8091e466f9ca810eae61e847adb809c2c961b4503a87ede3|2807254|1254x1254|candidate_unreviewed
projectile_fx/source/05_alien_energy_projectiles.png|6226a820dfa828cbca8a844796879c830144216d6041195f45de90c3f169c5da|2539258|1254x1254|candidate_unreviewed
projectile_fx/source/06_neon_weapon_effects.png|97bd675f5806096c4dac53e95c84c2e62ba07b42b65be5f7ca1c024d52e18d85|2885978|1254x1254|candidate_unreviewed
projectile_fx/source/07_continuous_beam_fx.png|5baab71d918953fd950ad06ddef3807a4cf50a6f0fe620246ff7b9fb2f490abe|2791612|1536x1024|user_approved_effects_source

## Curation policy
- Never discard originals merely because individual sprites are unsuitable.
- Any projectile/beam FX with a visible barrel, muzzle, launcher housing, physical emitter, or attached mechanical assembly is rejected from the usable FX catalog. Those parts belong on the ship/weapon model, not its projectile effect.
- Reject malformed physical constructions, e.g. missiles growing out of missiles, contradictory directionality, disconnected detail and nonsensical merged missiles.
- Prioritize continuous beam strips, charge sequences, discharge pulses, impact flares, shockwaves and coherent projectile animation groups.
- Continuous-beam atlas is approved as an EFFECTS SOURCE, not yet as sliced/transparent runtime sprites. Extract individual frames, remove panel backgrounds, clean edges, standardize dimensions/origins, and QA animation continuity before runtime use.
- Final curated sprites go in assets/effects/projectile_fx/curated/ with stable IDs and provenance to original sheet.

## Completing Git import
1. Obtain the original ZIP from the preserved Library backup or the project conversation.
2. Verify the archive SHA-256 shown above.
3. Extract into the repository's assets/effects/ directory, keeping the internal projectile_fx/source/ paths.
4. Verify each PNG's SHA-256 against this registry.
5. Add/commit the seven PNGs and verify they are retrievable from the remote; update RAW_SOURCE_ARCHIVED only after this verification.
