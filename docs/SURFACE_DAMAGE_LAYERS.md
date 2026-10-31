# Ship surface damage: shield -> armor -> exposed compartments

Implementation note for the actual 0.1.x client. This document describes
implemented mechanics, not concept art or a replacement combat system.

## Authoritative damage progression

1. A weapon strike arrives from a world-space direction. The appropriate
   directional shield facing absorbs damage first. Shield recharge/delay remain
   governed by `DirectionalDefense`; a still-powered unrelated facing does not
   paint the depleted facing blue.
2. A penetrating strike selects an occupied local surface point. Guided and
   ballistic physical collisions use their actual contact coordinates. Beam
   weapons now aim at mounted shipyard modules rather than the hull center.
3. The remaining energy is spread across occupied armor cells using
   `DirectionalDefense.impactSplash`. Weight decreases with radial distance;
   the energy budget is normalized and conserved. Existing per-weapon
   shield/armor/structural modifiers and bounded armor bypass remain active.
4. The armor grid is authoritative for localized damage and campaign persistence.
   The exact 16x16 armor and structural integrity is serialized, restored,
   fingerprinted and repaired by existing service/overhaul rules.
5. A penetrating hit can damage the installed subsystem local to its impact
   point: real reactor, engine, mounted weapons, point defense, hangar,
   sensors, cooling, magazine, electronic warfare or command.
6. A small procedural pixel mask interpolates neighboring armor cell exposure
   and introduces deterministic irregular scatter. It reduces original
   exterior opacity locally; the underlying original-silhouette-clipped
   structural material shows through. This is not a uniform whole-hull fade.
   Mask images are cached per vessel and updated only when quantized armor
   exposure changes. They preserve the original artist-approved sprite.
7. Four shield-material masks are computed once per source hull PNG, from
   `materials/shield_energy_blue.png`, clipped to the original sprite's
   alpha. Individual shield-facing opacity follows that facing's actual charge.

## Weapon damage footprints (radius in 16x16 armor cells)

| Weapon | Radius |
| --- | ---: |
| Laser / particle | 1.15 |
| Kinetic / fighter | 1.65 |
| Plasma | 2.15 |
| Missile | 2.50 |
| Torpedo | 3.15 |

These are *area shapes*, not extra damage multipliers. Each strike's remaining
energy is divided among the affected cells. The core simulation is independent
of OpenGL or texture availability.

## Coordinate contracts

- Simulation: bow local +X, port local +Y.
- Shipyard `HullLayouts`: bow +Y, lateral +X.
- Installed module positions are converted and clamped to legal local hull
  coordinates; bounded per-hit scatter is seeded deterministically.
- Sprite front is image top. The visual 16x16 armor mask is sampled in the
  same front-to-back row order as `DirectionalDefense`.
- The protected source RGBA alpha determines the visible hull silhouette;
  neither damage nor shield overlays may paint outside it.
- Runtime battlefield shots carry visual shot IDs; hit events preserve the
  actual off-center point when a beam succeeds.
- Ship texture dimensions never affect combat hit points or armor strength.

## Verification

`ShipSurfaceDamageTest` proves shield-first gating, different impact radii,
module lookup and subsystem identity, unchanged distant armor, exact save
roundtrip and deterministic replay. `WeaponFxPipelineTest` additionally
checks that a registered laser hit occurs at the same noncentral location
specified by its original FIRE event. Real GUI acceptance must use an actual
desktop or Android runtime screenshot plus log/telemetry, not a concept render.

## Remaining work / boundaries

- The physics grid still has approximate elliptical occupancy rather than
  the original sprite's exact pixel collision mesh. Refine local hitboxes
  without tying the headless simulation to libGDX texture loading.
- Missiles and torpedoes still track the target hull for guidance; their
  physical surface collision, not module-specific guidance, determines the
  armor point. Full selectable module targeting for guided bodies is separate.
- Armor depletion under each panel is physically represented; individual
  outward-facing module mesh panels and detached debris remain future art.
- Confirm pixel-level legibility at normal and extreme tactical zoom, worst-case
  masked-texture memory usage, and shield/damage material contrast on physical
  low-memory devices. These are diagnostics unless they break actual operation.

**Release policy:** Only failed executable function, launch, essential gameplay,
or required network correctness can block publication. Art and aesthetic
diagnostics must not independently block a working build.

## Superficial armor wear versus structural breach

The simulation retains four shield facings, 16x16 occupied armor cells, saved
localized structure and global hull health. The client now distinguishes:

- Shield-only impacts: shield contact feedback, no permanent hull mark.
- Unpenetrated armor wear: original plate pixels become scorched while remaining
  fully opaque; low-level wear is not incorrectly portrayed as an open hole.
- Near-exhausted armor: irregular exterior alpha reduction exposes the shared
  structural-material layer below; fully exhausted armor is transparent locally.
- Actual structural penetration via ammunition bypass can reveal a smaller
  internal patch even if some exterior armor remains.
- Hull HP loss: a bounded heated edge and short gray smoke puff; penetration
  takes priority when armor, hull and shield drop during the same tick.
- Repairs and saves: alpha masks derive from authoritative armor and structural
  integrity; the cache signature includes both. Paid overhaul restores both.

`ShipSurfaceDamageTest` now tests repeated kinetic hits until penetration,
armor bypass, untouched neighboring cells, persistence and overhaul.
`ShipSurfaceDamageVisualTest` is a headless pixel diagnostic covering original
source alpha, scorch-only damage, gradual openings, structural bypass and full
exposure. It is invoked by the separate hull diagnostics workflow, not added to
release gates. Visual quality still requires actual desktop/Android screenshots,
which these headless checks do not replace.
