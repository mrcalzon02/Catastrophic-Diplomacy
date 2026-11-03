# Actual 48x48 armor mask A/B

Two lossless PNG image atlases are kept as concrete assets:
- `upscaled48.png`: every approved 24x24 v3 mask upscaled with nearest-neighbor
  to 48x48, maintaining its existing colors and physical silhouette.
- `authored48.png`: 42 separate 48x48 masks with new fine-line fractures,
  cutout geometry and material response, created before the comparison.

Each atlas is 288x336 RGBA, seven horizontal 48px rows in exact
`Battle.DamageType` order LASER, KINETIC, PLASMA, PARTICLE, MISSILE, TORPEDO,
FIGHTER; each family row has six distinct 48x48 frames.

The runtime defaults to the approved 24px originals.
`CD_ARMOR_MASK_SET=upscaled48` and `CD_ARMOR_MASK_SET=authored48` select
the fixed A/B images in separate test processes. Both map exactly 18 source
pixels per logical armor cell instead of 9, so the diameter on the ship remains
**2.667 logical armor cells in BOTH cases**. The existing 96x144 ship composite
and native original ship artwork stay unchanged. No random decals or shapes.

The optional `.github/workflows/armor-mask-48-ab.yml` executes two actual
libGDX/OpenGL desktop test launches with identical Battle.step() seed,
weapon, target, damage-state and view, then writes paired
`*-surface-AB-real-game.png` and other camera comparisons, and retains raw
screenshots. `tools/armor_48_ab_compare.py` asserts deterministic scenario
metrics are the same before making a side-by-side from real framebuffer crops.
No generated ship art is used. Aesthetic/visual diagnostics are nonblocking to
release builds; no Android APK or desktop installer is packaged.
