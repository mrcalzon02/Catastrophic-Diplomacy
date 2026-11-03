# Native-resolution armor damage masks (96 x 96)

The seven checked-in `.png` atlases in this directory are the **live
runtime defaults**, not enlarged 24px or 48px images. Each is 576 x 96
RGBA8888 and contains six distinct, individually specified 96 x 96 wounds.
The authoritative source recipes are `tools/generate_damage_masks_96.py`.

| Channel | Meaning |
| --- | --- |
| R | Local soot and blackened plating |
| G | Hot material / residual heat; for laser oxide pixels, hue phase rather than glow strength |
| B | Raised metal or tagged laser oxide |
| A | Surviving original plating, 255 intact / 0 cut through |

The native artwork is centered on an impact anchor identified by the
real directional defense tile. Its lookup uses **36 mask pixels per
armor cell**, so a 96px atlas frame covers exactly the same 2.667-cell
diameter as 24px at 9px/cell and 48px at 18px/cell. Larger images
**never** enlarge the world-space decal, move a physics impact,
change combat balance, or paint outside the original hull alpha.

The current production damage coefficient overlay is 192 x 288 pixels
per damaged vessel (formerly 96 x 144); it is still cached and
repopulated only over dirty physical armor regions. **The original
full-resolution faction hull sprite is never replaced or reduced.**
The 96px source provides more fracture, pockmark, scoring and melt detail,
but the 192x288 overlay and final display zoom still limit what can be
resolved on screen. Evaluate real OpenGL screenshots at tactical and
close zoom; these frames alone do not prove final hull readability.

## Thermal treatment and selective anti-aliasing

The current source masks are an **impact-topology + material** pass. The
generator performs small, localized Gaussian softening only on soot, heat,
oxide and torn-metal response. **Alpha opening topology is never blurred**:
all 42 cutout alpha frames remain bit-for-bit identical to their
pre-material-pass equivalents. The enlarged per-ship mask is just a cached
coefficient texture; no full-hull artwork is replaced.

**Laser annealing is intentionally the widest thermal-affected radius.**
Across all six laser variants, the native-96 oxide material reaches about
**1.18–1.29 armor cells** from the registered impact. Plasma's hot reach
is about **0.97–1.08 cells**; torpedoes and missiles remain smaller.
Laser width describes blue/violet/bronze discoloration of **surviving
plating**, not a larger hole or a colored ellipse over the entire ship.
The generator shapes this field around actual straight/forked/parallel
scores, not a generic radial bowl.

The blue channel uses an exclusive **laser anneal material tag** beginning
at value 164; the renderer preserves that tag through damage severity.
The green channel then selects the oxide phase. Very hot, narrow laser
lips gain a small local incandescent contribution; the distant annealing
band remains primarily discolored metal rather than a bright light source.
Plasma and blast-family molten lips use the hot G channel with the B
channel's exposed metal to produce local gold/amber highlights. Kinetic
has **no thermal G response** and retains cold raised silver fractures.
Shader blending and all masks remain clipped to the original hull alpha.

Run `python3 tools/audit_damage_masks_96_materials.py` to inspect
every authored frame's thermal/oxide reach, alpha cutout footprint,
gradient levels and variant overlap. The report flags style issues as
**nonblocking findings**; gameplay and release workflows are not gated
by aesthetic thresholds.

Family differences:
- Laser: thin straight, forked, parallel and separated scoring, with
  small annealed-oxide zones rather than a broad circular flare.
- Kinetic: asymmetric cold pockmarks and shears, fractured raised-metal
  lips, dark central voids and separate radiating stress lines.
- Plasma: directional channels, drips, hooks, pools and branching melts.
- Particle: slender gouges with one or several precision punctures.
- Missile: multi-pocket blast craters, broken bowls and spalling.
- Torpedo: larger jagged multi-cavity tears, sheared plating and forks.
- Fighter: tiny asymmetric grouped punctures with fine fractures.

The 24px `damage_masks/<weapon>.png` frames and the 48px A/B atlases
remain available as **legacy diagnostic comparisons**, selected using
`CD_ARMOR_MASK_SET=legacy24`, `upscaled48` or `authored48`.
Absent/unknown values use native 96px assets.

Author locally:

```sh
python3 -m pip install Pillow==11.3.0
python3 tools/generate_damage_masks_96.py --preview test-output/armor96/variants.png
```

The optional armor authoring workflow produces and commits checked-in
PNG images and a preview; it never blocks the application release.
The source art and this image mask library remain independent of the
physics, balancing and package-building workflows.
