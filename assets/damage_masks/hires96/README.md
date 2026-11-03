# Native-resolution armor damage masks (96 x 96)

The seven checked-in `.png` atlases in this directory are the **live
runtime defaults**, not enlarged 24px or 48px images. Each is 576 x 96
RGBA8888 and contains six distinct, individually specified 96 x 96 wounds.
The authoritative source recipes are `tools/generate_damage_masks_96.py`.

| Channel | Meaning |
| --- | --- |
| R | Local soot and blackened plating |
| G | Hot material and residual heat |
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
