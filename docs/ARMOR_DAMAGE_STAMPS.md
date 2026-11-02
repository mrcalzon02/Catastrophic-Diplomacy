# Localized punched-through armor stamps

**Source of truth:** The 16×16 real per-vessel directional armor and structure
grid, not random visual explosions. Each damaged occupied grid tile cuts one
stable irregular crater centered on its physical position in the approved ship
sprite. Sprite rotations and sizes reuse the existing renderer.

- Surface wear darkens the actual source PNG without alpha changes.
- Deeply eroded armor opens a visible, fully transparent crater core.
- The revealed layer is already a silhouette-clipped structural material under
  the source sprite; no outline or alpha is added outside the original art.
- Repeated strikes and armor bypass can open or widen real breaches. Saved
  integrity and paid repairs continue to govern the visual damage.
- Masks retain the current cached 96×144 texture per damaged vessel and rebuild
  only when real armor/structure integrity changes. Source PNG is never edited.
- The four-ship Android damage showcase already has actual legal fitted
  dreadnoughts with authentic simulated impact states. Global hit points,
  weapon damage, armor balance, and normal gameplay are NOT increased or changed.

Run `gradle :game:shipDamageVisualSmoke` as a standalone **nonblocking**
visual diagnostic. Review real Android game-frame screenshots before
claiming display-level acceptance.


## Incremental rendering refinement

- The `96×144` per-vessel exterior mask now uploads only the dirty
  rectangle around changed protection tiles, including edge/scorch padding.
  Dispersed hits still use a full update if their bounding region spans it.
- Real armor integrity is quantized consistently with the existing change
  fingerprint. No per-frame or per-ship texture regeneration is needed when
  integrity is unchanged; independent shield regeneration does not dirty armor.
- Repaired ships, destroyed ships and ships removed from a battle release
  obsolete GPU overlay masks. Off-camera damaged ships keep theirs, avoiding
  pan-induced allocations and image upload spikes.
- The incremental rectangular update is covered by headless visual diagnostics
  for single-point hits, structural bypass, repair, edge and distant impacts.


## Source-art damage registration

An impact near a thin or irregular hull edge may be inside the physical
16×16 armor grid but over original PNG transparency. On first damage use
per unique approved sprite, we sample its **actual alpha** into 96×144
coordinates and cache 256 nearest-opaque visual crater anchors.
Only an off-hull crater whose center is **within one original armor cell** of
solid authored artwork can shift its visual center, by at most that distance.
Already-opaque centers remain unchanged; source alpha clipping is still
absolute. This does not move the physical projectile strike, damage tile,
armor loss, module hit or saved battle state. Low-alpha labels/thruster fringes
are excluded from anchor candidates.


## Combat observability diagnostic

Run `gradle :core:armorBreachTelemetry` for **all 11 actual, legally
fitted same-size hull duels**, 450 real simulation ticks each.
The diagnostic reports each vessel's maximum hull, armor, first tick with
90%+ armor exposure **while still alive**, first death tick, and maximum
breached armor-cell count. It prints the number of ships that perish before
ever showing an armor hole. An optional CI artifact saves those measurements.

This measures the original concern ("ships die before their armor holes can be
seen") rather than falsely assuming that a perfect cutout algorithm guarantees
visibility. It is completely independent of gameplay balancing and is never a
release gate; device screenshots remain necessary.


## Bounded Android source-art cache

Previously, each unique faction/hull's full decoded original PNG remained
resident in CPU heap after the first shield/damage lookup, despite all three
consumers reading only 96×144 sampled coordinates. The CPU cache now retains
**one 96×144 RGBA Pixmap (55,296 bytes)** per unique ship texture, retaining
the exact integer source-pixel mapping previously used by the mask builder,
shield sector stencil, and armor anchor registration. The full-resolution
approved original texture remains uploaded on the GPU and is never modified.
Temporary full-resolution decoding is disposed immediately. This bounds the
persistent source-pixel cache independently of the original fleet art size.


### Runtime evidence for 200-vs-200

The existing in-game `CD-DEMO-VERIFY` log now includes
`damageMaskUploads` (actual full/subrect texture updates),
`damageMaskPixels` (total pixels uploaded, rather than an assumed gain),
and `sourceCpuBytes` (the retained sampled CPU art cache). They are
observations only, never release pass/fail thresholds. A real Android or
desktop battle should be used to validate these values under load.


## Dark exposed cavities and hot transparency-edge rims

The real material pass now reduces the damaged/destroyed structural underlayer
composite RGB to **25%** of its old brightness. It retains the authored source
alpha, not a generic geometric oval, and uses a shadowed source-pixel fallback
if its damage material has a transparent sample. Transparent original ship
pixels remain completely empty.

The real 96×144 alpha-peeling mask samples the local directional armor-crater
gradient only for partially opened pixels, producing a tightly bounded
orange-hot rim in the remaining plating. Full hole centers remain transparent,
shallow scuffs have no rim, and no glow is painted outside source alpha or
when no underlying pixel exists. This is not a fake shield bloom. The
gradients are calculated on dirty-region rebuilds, not every frame, and
functional hull HP, impact locations, repair and shielding are unchanged.

The existing optional Android four-ship comparison must be re-captured for
visual inspection; headless mask tests only validate structural invariants.


## Repeatable live-fire framebuffer review (optional)

The independent `armor-damage-visual-diagnostic.yml` now runs the actual desktop
game under Mesa/Xvfb. `ShipSurfaceDamageCapture` creates deterministic fitted
firing lanes (laser, kinetic, missile) and records `Battle.step()` hits and
physical 16×16 armor state. These are NOT fake damage bitmaps. The original
rectangular 6×6 tile fixture is intentionally retained under
`rectangle-stress` only, to expose grid artifacts without mistaking it for
ordinary battle damage.

Artifacts include 1280×720 original PNG framebuffers at tactical and close
zoom, `frames.csv` with seed/weapon/tick/hits/exposure/mask uploads,
`armor-review-contact-sheet.png`, a pristine/laser difference image,
`armor-review.json` and textual diagnostics. Cases that fail to reach a
requested damage milestone are labeled as such, never silently fabricated.
The contact sheet scales actual screenshots without generating replacement
concept art.

Only objective screenshot production and real simulation execution may fail
the independent diagnostic. Pixel-art appearance, rectangularity, legibility,
and balance are human-review observations and never dependencies of releases.
