# Static Weapon Firing Range — Video Acceptance Plan

**Status: PLAN APPROVED FOR IMPLEMENTATION, NO VIDEO CAPTURE PERFORMED.**
Machine-readable baseline: [`qa/firing-range/batches.json`](../qa/firing-range/batches.json).
This range is a developer-only visual QA tool, not a new standard combat mode or an automated claim that art is good. Do not change existing live combat to satisfy a visual test. Do not add a GitHub Actions workflow without an explicit request.

## Objective

Put **two or three independent weapons** on a stationary test array, fire prescribed solo and barrage sequences toward fixed test targets, record the real libGDX render output and its event trace, review animation and correctness, mark each lane approved or failed, then advance to the next group. Source artwork that contains a physical gun barrel, launcher, model or emitter **inside an FX sprite** is unfit for curated FX even if attractive; the *weapon model* may have a barrel, but emitted FX must not.

### Grounded baseline (October 2026)

- `Battle` actually exposes laser, kinetic, plasma, particle **0.13-second beam records**, missiles, torpedoes, point defense, flak, fighters and drones. `Battle.step()` currently steers ships; it cannot serve as a *static* test without a purpose-built fixture or tested test-mode branch.
- `WeaponBeamSprites` can load five colors × seven transparent PNG frames, using **tick / 3** modulo seven as its global animation clock. At 30 Hz, one frame lasts approximately 0.1 s, but a normal beam lives only about four simulation ticks. A naturally fired beam cannot display seven consecutive frames. **Do not call the full animated-cycle test a production firing PASS.** A separate seven-frame slow inspection is required.
- The laser palette currently depends on the firing *team*, not the six civilizations. The three-faction comparison batches should reveal this gap instead of faking racial visual diversity.
- Projectile/craft art is not yet curated into runtime sprites, and the missing 10 Terran + 35 beam PNG **GitHub binaries** are a blocker for final art playback. The already-committed authored material PNGs are separate. Record `BLOCKED_MISSING_ASSET`, never `PASS`, if fallback geometry is substituted.
- Existing `Battle` firing gates matter: plasma range <245, kinetic <445, laser <530, particle <565, missile 80–690, torpedo 110–840 (world units); select lane targets accordingly.

## Test scene

Use a dedicated `FiringRangeScreen` (developer menu or `--firing-range <batch-id>` desktop launch option), not a real fleet battle, so camera and emitters stay completely still. Three identical mounting plinths/frames are centered vertically at world Y **530, 360, 190**, with weapon sockets at X **165**. Each lane has its own fixed, replaceable diagnostic target at X = 165 + the lane's prescribed distance. Lanes stay separated and labeled with **module ID, faction, sprite/animation ID, frame index, simulation tick, asset fallback status**. Use the installed ship/module geometry when it has been accepted; until then a simple geometric stand is clearly marked **fixture**. Do not embed an emitter or turret into the beam/projectile PNG.

Use a high-contrast dark neutral background with a faint grid and distance line. Offer a second checkerboard/neutral-light background to examine alpha fringes, opaque rectangles and dark halos. Keep camera center, zoom, target locations and weapon facing fixed. The target is an indestructible plate with shield/armor display modes; hit flashes are positioned at the actual collision point, not always at the image center. Defensive batches instead spawn a repeatable stream of incoming missiles. Flight-path and engagement distance must stay inside actual weapon gates.

### Canonical recording sequence

| Phase | Three-weapon duration | What the reviewer learns |
| --- | ---: | --- |
| Still / settle | 0–2 s | Stand, muzzle anchor and palette are correct before firing |
| Solo A / B / C | 2–8 s; 2 s each | Inspect each animation without visual overlap |
| Synchronized barrage | 8–13 s; 5 s | All weapons firing together without missing or swapped sprites |
| Staggered barrage | 13–19 s; 6 s | Alternating 0.25 s lane offsets; track each trail and impact |
| Tail / reset | 19–24 s; 5 s | Impact decay, smoke and trails fully clear; loop can restart cleanly |

Two-weapon groups omit Solo C and naturally use 22 s before any extra projectile tail. For homing ordnance, hold the recording until the **last projectile, impact and cleanup** are finished, bounded by a documented 30-second cap. If the cap is reached with live effects, **FAIL** the cleanup gate. Each run may also generate a *review-only* slow-motion companion, but timing acceptance always uses the **unaltered 1× recording**.

## Capture pipeline and provenance

Capture the **actual final framebuffer** after the production ship, weapon, projectile, impact and material render passes—not a hand-built reconstruction of how the animation ought to look. Use a fixed world `1280×720`; record at **1920×1080, 60 fps** to lossless, numbered PNG frames before offline H.264 MP4 encoding (`-pix_fmt yuv420p`; no watermark or misleading interpolation). Advance deterministic combat at **30 Hz** while rendering at **60 Hz** using explicit interpolation or clearly reported paired frames; never confuse 60 captured frames/s with 60 simulation ticks/s. Avoid real-time `Gdx.graphics.getDeltaTime()` stepping during capture. Render exactly one output frame per prescribed frame number and keep a monotonically increasing simulation tick in its metadata.

The capture writes:

- `capture.mp4`: original-speed recording, labeled with batch ID, commit SHA, weapon names and frame/tick counters
- `frames/frame_000000.png...`: unaltered PNG frames, with no OS pointer, title bar or surprise camera pans
- `events.jsonl`: exact fire/spawn/intercept/impact/despawn events, lane index, mount/source world coords, target coords, asset ID and `fallback_used`
- `contact_sheet.png`: still / first shot / travel / hit / barrage / cleanup representative frames
- `review.json`: capture configuration, Git SHA, asset checksums, source and stage status, per-lane reviewer approvals/rejections and defect notes

A repeat capture of the same commit, asset manifest, seed, configuration and scene code must produce the same **event trace and frame count**. Pixel-level identical MP4 output is not guaranteed across different GPU drivers and codecs; compare key frames and visual features instead. Store video artifacts outside `assets/`; avoid committing raw frame sequences to the game repository by default. Link them from review records.

Example MP4 packaging (requires an available FFmpeg executable; this is **not** an already-run capture):

```sh
ffmpeg -framerate 60 -i frames/frame_%06d.png -c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p capture.mp4
```

## Run order and why

| Group | Mounts (2–3 per pass) | Gate |
| --- | --- | --- |
| **FR-01** | Laser / Kinetic / Particle | Beam alignment, cadence, animated frames and fade |
| **FR-02** | Plasma / Laser / Particle | Close-range thermal glow versus precise beam strokes |
| **FR-03** | Missile / Torpedo / Kinetic reference | Launch, independent projectile, contrail, guidance, impact |
| **FR-04** | Point defense / Flak / Defense drone | Missiles inbound, acquisition, interception and hit flash |
| **FR-05** | Fighter hangar / Drone controller / Missile rack | Launching craft, repeat launch, cleanup and no stranded sprites |
| **FR-06** | Terran / Draxian / Carnelian lasers | Faction differentiation and color consistency |
| **FR-07** | Zippledrow / Uzipt / Mandrally lasers | Remaining three factions, no false tint-only identity |
| **FR-08** | Blue / Green / Violet **visual-only** beam strips | Every frame of full seven-frame animation actually visible |
| **FR-09** | Red / Gold **visual-only** beam strips | Remaining full strips and seam/alpha checks |

Every lane in the machine-readable manifest identifies the actual component ID (`BASE-TER-LAS`, etc.) or a **declared visual-only** art sequence, its target distance and acceptance focus. Faction comparisons must not silently use the same team-based beam coloring as a pretend finished faction skin.

### Required approval checks

**Automated diagnostic checks (not substitutes for seeing the animation):**
- 2–3 lanes, unique IDs, correct class/faction and valid mount, all distances within the real firing gate
- expected shots/launches and impact/despawn events present in trace, no NaN positions or crashes
- fixed test-mount positions, fixed camera, deterministic tick and complete frame numbering
- asset files exist, checksum matches declared provenance, no placeholder/fallback marked as approved art
- enough frames cover each requested phase and slow inspection actually visits each of the seven frames
- no lingering effects beyond the cleanup cap; render scales independent of system FPS

**Human video review of each lane and combined barrage:**
- **Source:** projectile/beam originates at the exact intended muzzle socket; no floating barrel or emitter inside the FX art
- **Animation:** charge, start, sustain/pulse, trail, travel, impact and dissipation make visual sense for the weapon type; no obvious repeated/disconnected fragments
- **Motion:** directional orientation, homing, movement speeds, flight paths and contact flashes agree with world geometry
- **Visual:** no sliced-off ends, stray opaque rectangles, frame-order jumps, unnatural stretch, dark background residue, wrong tint, or unreadable overlap
- **Timing:** no clipped first or final frames; long beams do not accidentally look continuous merely because a single still is stretched; no endless smoke/trails
- **Regression:** previously approved beam and missile visuals remain intact after later changes

Defects get a timestamp and lane reference plus **FAIL** (or **BLOCKED_MISSING_ASSET**) in `review.json`. Preserve the original atlas/failed cutout. Corrections create a new revision and rerun only affected lanes plus a mixed regression barrage. A blocked or failed group can be queued for repair while **unrelated batches advance**; never mark it approved simply to move forward.

## Implementation order (without disrupting combat)

1. **Asset gate:** install the 45 packaged runtime PNG binaries and verify their hashes. Do not import all alien reference palettes as gameplay sprites. If missing, capture fallback diagnostics only and label accordingly.
2. **Fixture and timeline:** introduce a test-only static scene with lane positions, timed solo/synchronous/staggered stages, deterministic incoming ordnance and indestructible target plates. Reuse shared `Battle` ordnance/firing machinery where possible but bypass steering in the test harness. Do not change `Battle.step()` for actual fleets merely to immobilize them.
3. **Renderer parity:** route real `WeaponBeamSprites`, missile/torpedo renderers, damage layers and future trail/impact sprite consumers through the same paths as gameplay. The visual-only full-cycle viewer is a **separate** mode, never passed as an actual combat firing result.
4. **Frame recorder:** GL framebuffer PNG capture on desktop, deterministic fixed-step progress, evidence event log, MP4 conversion via FFmpeg. Android on-device playback/capture is a later independent acceptance gate.
5. **Review UI:** frame stepping, pause/slow playback, A/B compare to previous approved version, timestamps and defect flags. Save approval provenance; do not change CI/release gates merely due to incomplete cosmetic review.
6. **Repeat:** run FR-01→FR-09; repeat faction-specific patterns, weapon upgrades and diverse muzzle-anchor geometries only when the baseline batches pass.

**Exit for initial implementation:** FR-01 scene fires at least three independently positioned, stationary actual components, records a reproducible MP4 and trace with its true source-art status, and permits reviewer PASS/FAIL per lane. It is **not** complete merely because a headless weapon event test succeeds. Subsequent batches are unlocked progressively after the capture/review loop is proven.

## Important safety / status boundaries

This document and the JSON configuration are a **plan**, not recorded evidence or a verified game build. No video capture, desktop/Android visual render acceptance, installation of missing 45 binaries, or automatic visual classification is claimed. Keep essential functional tests release-blocking and debug/visual QA separately flaggable, in accordance with project testing discipline; do not roll back a working art change just because an obsolete cosmetic snapshot test fails.
