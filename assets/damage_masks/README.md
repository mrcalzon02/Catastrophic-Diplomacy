# Authored armor damage mask library

**Runtime default since the native 96px revision:** the seven
`hires96/<weapon>.png` atlases, six distinct **96x96** frames each,
authored by `tools/generate_damage_masks_96.py`. See
[`hires96/README.md`](hires96/README.md) for current pixel channels,
world-space footprint, localized material signatures, and resolution limits.

## Legacy 24px comparison (preserved, not default)

These seven **legacy PNG image atlases** are available only when
`CD_ARMOR_MASK_SET=legacy24`. Each 144x24 RGBA atlas is precisely six
independently authored 24x24 images in order from left to right. These
are different wound topologies (separate punctures, tears, shorn plates,
branches, scrape trajectories), not rotations or randomized variants of
one crater. No dynamic stamp geometry is calculated at runtime.

| Asset | Six authored wounds |
| --- | --- |
| torpedo.png | jagged burst, disconnected cavities, long rip, branching rupture, vertical breach, side tear |
| particle.png | straight puncture gouge, reverse diagonal, twin punctures, angular trench, forked track, ricochet |
| fighter.png | unequal star crack, paired pinholes, triangular punch, off-axis cross, scratch-and-pin, triple cluster |
| kinetic.png | faceted plug, wedge shear, petaled tear, stepped rupture, split plates, small radiating pock |
| missile.png | crooked crater, twin craters, collapsed rim, concentrated detonation, oblique gouge, compact spall |
| plasma.png | drip melt, pooled wash, forking rivulets, oblique smear, clustered drips, hooked melt |
| laser.png | fine diagonal, bent score, branch, two separate burns, branching spider mark, zig-zag scar |

**Pixels / channels:** width 144, height 24, contiguous frames of width 24.
R = locally darkened armor, G = thermal intensity, B = bright metal / oxide,
A = surviving exterior (255 means intact, 0 means full cut-through).
The renderer applies one 24px frame centered at the registered impact tile;
one image pixel is 1/9 of an armor tile. At maximum, each local stamp covers
no more than 2.67 of the ship's 16x16 defense cells. They are **never scaled
to fill the hull**. The GPU's existing 96x144 ship overlay is only a
compositing surface for potentially hundreds of small local wounds.

**Editing:** use `python3 tools/edit_damage_atlases.py unpack` to
obtain 42 individual 24x24 PNGs. Edit one directly in any pixel editor.
Then `python3 tools/edit_damage_atlases.py pack` to update these seven
checked-in runtime atlases from those exact images. No blurring, no
random shape generator, no substitute geometry. A source-audit ZIP with
all 42 separate 24px originals accompanies the initial implementation.

**Visual diagnostics are nonblocking.** No full package rebuild is
necessary when reviewing mask artwork.

## Silhouette refinement and audit (v3)

Five families were **repainted as fixed source PNG frames**, not procedural
stamps: fighter, kinetic, laser, missile and plasma. The original six torpedo
and six particle frames remain untouched. Every variant is a distinct pixel
image in its six-frame atlas, locally sampled against the registered impact
tile. Frames are still 24x24 RGBA and contain no mipmaps or super-resolution
textures. The existing 96x144 per-ship composite has not grown.

Visible distinct silhouettes now deliberately include:
- **Fighter**: single asymmetric star, separated double punch, triangular
  triple burst, offset fractures, graze-terminal puncture, four-hole group.
- **Kinetic**: compact faceted plug, long wedge shear, separated petaled
  breaches, elongated diagonal gouge, two separated angular hull holes,
  tiny cold-metal pock with long raised silver stress lines.
- **Laser**: straight score, fork, zigzag, separate parallel burns, five
  pinholes, hooked heat scar. The physical cut-throughs are **tiny**;
  the thermal metal response is wider but still locally bounded. No
  broad thermal orange/brown band on kinetic.
- **Missile**: broken round blast bowl, oblong ejecta blast, collapsed
  half crater, two discrete craters, narrow ragged shrapnel split,
  small offset clustered detonations.
- **Plasma**: vertical melt drip, lateral pool, branching molten Y,
  disconnected splash droplets, sharp L-shaped flow, curved hook melt.

`python3 tools/audit_damage_mask_assets.py` reads the literal committed
PNG frames and reports each 24px cutout's area plus 15 pairwise silhouette
overlaps per weapon. For direct inspection of ALL 42 original PNG files:
`python3 tools/audit_damage_mask_assets.py --export-frames /tmp/armor-frames`.
Exporting is pixel slicing only—no resampling, no interpolation and no new
shape synthesis. It does not replace the editable single-frame workflow:
`python3 tools/edit_damage_atlases.py unpack` and `pack`.

A pairwise intersection-over-union above .65 is a **cosmetic warning**
(requires visual review), never a reason to block functionality, packages or
release CI. This applies to style, legibility, balance and art checks.

The actual in-game appearance still requires original source-sprite
OpenGL captures at real camera zoom, hit clusters, mixed impacts and edge
hits; the bare frames are not proof of final pixel readability at 12-30 px.
