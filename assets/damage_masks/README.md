# Authored, image-based armor damage masks (v2)

These seven **real PNG image atlases** are the only approved *runtime*
source of per-hit damage shape. Each 144x24 RGBA atlas is precisely six
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
