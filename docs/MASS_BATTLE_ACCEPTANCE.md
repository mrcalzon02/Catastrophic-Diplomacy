# Massed battle escalation and visibility acceptance — 2026-10-21

This report distinguishes **headless simulation verification** from **unperformed native rendering/device tests**.
Source branch before test: `main` at `c61064d730f16619aa328424b626749b9de50037`.
Re-run the test suite at the eventual committed SHA before interpreting results as release proof.

## Implemented gates / observed results (Java 21 runtime, `javac --release 17`)

- Exact escalation: 1v1, 2v2, ... **200v200**, including 200 independent counts. Variable-duration simulation at each step, with state checks for finite positions, velocities, hull, heat, power, ammunition, missiles and craft.
- Full 6 civilizations × 4 hull classes × 6 first weapon types × 6 second weapon types = **864 assembled pairings**; **860 accepted** and exercised in combat; **4 rejected** by legitimate engineering constraints. The six weapon families are LAS, KIN, PLA, PAR, MSL, TOR.
- Engine stages: all four parallel upgrade tracks × stages 1..6 × six civilizations × four hulls, **568 fitted cases accepted** (others rejected under real engineering budget constraints).
- Weapon stages: all four parallel tracks × stages 1..6 × six weapon families × six civilizations × four hulls, **3,404 fitted cases accepted**.
- Directed effect payloads observed for LASER, KINETIC, PLASMA, PARTICLE; launchable missiles and heavier torpedoes both observed. Ship propulsion values must remain positive and finite.
- Ten deterministic replay sizes: 1, 2, 4, 8, 16, 32, 64, 100, 150, 200 per side, repeating an identically seeded encounter.
- **200v200** six-weapon combined-arms stress repeated twice for **480 ticks**; **3,419 hits, 75 destructions, peak 264 missiles, peak 150 craft**, deterministic fingerprint `aac7a36a84db3f9f` in both runs. Same result within the same Java/runtime setup; not a cross-device reproducibility certificate.
- Initial viewport projection: every hull starts within drawable 1280×720 bounds at each scale. During a separate 750-tick 200v200 engagement, camera projection retains all surviving hulls inside the battle viewport, away from fixed HUD overlays.
- Independent Java2D raster test: every one of **400 triangles** has projected screen geometry inside the battlefield and the result contains visible colored ship pixels. This is a geometry diagnostic, **not** a real libGDX/OpenGL screenshot.

## In-game controls and actual visual primitives

- Fitted corvettes, frigates, cruisers and dreadnoughts have different visual scale based on hull integrity rating. Current hull depiction is original colored arrowhead geometry, *not* high-fidelity models or sprites.
- A lightweight engine plume line is drawn behind each surviving ship on both instanced and fallback paths. Each directed weapon has a color-coded beam (laser blue/coral, kinetic gold, plasma orange, particle violet). Missiles, larger orange torpedoes, fighters and drones use independently rendered circles.
- On-screen fleet size − / + and keyboard `[ / ]` select 1, 2, 4, 8, 16, 32, 64, 100, 150, or 200 per side. Both sides use the same count for this skirmish UI.
- Battle camera dynamically follows all surviving hulls. HUD stays in screen coordinates. GPU path verifies instance count against living ships and falls back if inconsistent.

## Mandatory acceptance checks still open

1. Install Gradle/JDK17 and run desktop compilation and `:desktop:run` in persistent checkout. Confirm both GPU-instanced and forced-fallback geometry are on-screen (count ships in screenshot, check shader compile errors), verify all four directed-weapon colors, separate torpedoes/missiles, fighters/drones and exhaust.
2. Run 1v1, 16v16, 100v100 and **200v200** on desktop; capture frame times, draw calls, peak memory and an actual screenshot; exercise shipyard, pause, speed, fleet-size controls and dynamic camera.
3. Run Android SDK 35 builds and install on emulator and physical device. Verify same hull visibility, correct touchscreen hit-targets, speed/faction/fleet controls, steady performance, pause/resume, and save/restore.
4. Perform unsigned debug APK and then **signed release packaging** using the *existing* local signing identity only. Never regenerate, upload to Git, log or expose key material. Backup `.local-secrets/` offline first.
5. Profile large fleets over sustained engagements; headless wall-time measurements are not GPU FPS or proof of good device performance. Optimization should follow actual frame-time traces, not fabricated FPS claims.

No GitHub Actions workflows were introduced for this campaign.
