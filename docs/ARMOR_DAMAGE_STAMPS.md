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


### First-use diagnostic observations and review refinements

The first expanded run produced 18 actual framebuffer PNGs. It revealed that
some seed/loadout combinations completely expose local armor and structure
within the first one or two registered hits, leaving no gradual damage states.
This is now reported explicitly as a nonblocking diagnostic warning, not
silently called a successful visual progression.

The follow-up version outfits unarmed targets with the **actual faction
starter shield and defensive systems** (removing only their weapons), so a
shield-contact case cannot masquerade as an unshielded bare-hull hit.
Close-view framing is increased, and `armor-surface-crops.png` /
`ship-region-laser-difference.png` isolate the ship area from HUD labels
for review. Original full OpenGL screenshots remain preserved unchanged.


### Individual, readable screenshot evidence (v2)

The diagnostic produces three original 1280×720 game-client frames per
deterministic scenario: `tactical.png` (combat UI and effects), `close.png`
(combat close-up), and `surface.png` (real ship sprite, armor-mask and
materials rendered normally, but the transient weapon beams/glows and HUD are
not drawn). It keeps the individual original framebuffers, preserves physical
hit and shield/armor telemetry, and uploads a **separate GitHub Actions
artifact per scenario**. Each individual ZIP contains the three images,
`README.txt` and `evidence.json`. There is no enormous contact sheet
that reduces the actual sprites to unreadable thumbnails.

The exposed structural underlay is now lit at 40% of the original composited
color rather than 25%; original hull silhouette, shader inputs, saved physical
armor, hit points, masks, and battlefield balance are unchanged. This is
a small cosmetic refinement subject to actual framebuffer comparison.


### Weapon-light contamination diagnosis and forensic isolation

The same Vaerith Prism corvette has ordinary purple/silver source art when
pristine, but near-full cyan tint after LASER impact, and gold tint after
KINETIC impact. The prior `surface.png` excluded floating/glow FX but
**still fed the real `Battle.visualEvents()` into `ShipLightProbes`**.
That shader adds the weapon color to the albedo across its 108–150-unit
light influence radius, obscuring the vessel's own material.

In an optional forensic-only rendering entrypoint, the game's original
ship surface shader and real directional armor peel remain active, but the
transient weapon probes are disabled and shield/ECM overlays are omitted.
Production five-argument rendering is unchanged. Compare the new
`surface.png` (physical damage) with `close.png` (full combat lighting).
No combat mechanics or source sprites are modified.


### Development-only stationary vessel (no AI)

The deterministic armor gallery registers its **actual fitted Vaerith target**
as a stationary diagnostic target at battle tick zero. It has no autonomous
target acquisition, steering, retreat, mission-command processing, firing,
fighter launches, or other captain actions. Its position and facing remain
fixed across shots, providing comparable pristine and damaged screenshots.
Its physics and defenses remain completely real: shield recharge, armor and
structure depletion, module damage, actual projectile hits, and destruction
are still processed by the normal simulation. The shooting vessel's AI and
real weapon system continue normally.

The opt-in registration exists only on an individual `Battle` instance and
is used by the diagnostics. Normal campaign/skirmish/network battles do not
register stationary test targets. The visual smoke task verifies no drift,
no autonomous target selection and no default opt-in, while keeping visual
acceptance out of the release gates.


### One physical impact, one localized damage anchor

The prior visual algorithm treated **every tile touched by splash damage**
as a separate direct strike and stamped an independent soot/crater mark.
A single laser can physically wear ~10 adjacent tiles, so merely shrinking
each stamp still produced a ship-wide pattern. The rendering now reads an
actual *direct-impact* marker from the center tile selected by the existing
physics impact. Neighbors still lose physical armor and structure normally,
but do **not** invent cosmetic impact centers. The small three-band scorch
and any exposed-metal crater are rendered only at those genuine direct hit
locations, all still clipped to original hull art. This changes no weapon
damage, radius, hit detection, armor energy, shield, or gameplay balance.

A 256-bit (32-byte) impact-location field is appended to the persisted
per-vessel directional-integrity snapshot. Old snapshots without this field
are accepted and infer one local center per connected region of worn armor
instead of producing a stamp per old damaged cell. Repairs clear centers
once the physical tile is restored, and an overhaul clears all markers.
The standalone core test proves that multiple mechanically damaged cells
from one LASER splash produce exactly one visual mark, survives reload,
and respects legacy-save compatibility. Capture tests report impact-center
count against actual weapon hit count.


### Keep native source artwork while using the cheap armor mask

The pre-fix renderer discarded high-resolution ship artwork after the first
hit and drew a 96x144 tinted copy as the *entire hull*. This blurred, discolored
and degraded every untouched surface. A GPU material pass now samples that
same inexpensive per-ship mask only as coefficients:
R=localized scorch, G=heated breach lip, A=remaining armor coverage.
The original full-resolution sprite remains the GL2/ES2 albedo source even
after extensive damage; authored normal/emissive maps remain registered.
Only true armor openings reveal the separate, silhouette-clipped structural
layer. No damage values, weapon radii, armor balance or source sprites changed.
Visual checks are diagnostic, not release gates.


### Weapon-dependent pockmark and thermal surface profiles

Keep the approved original-resolution hull albedo and the same physical
armor-alpha/mask pipeline. Damage mask R=localized dark pit/soot,
G=hot thermal lip, B=bright cool sheared metal and raised crack edge,
A=missing armor. LASER retains prior localized scorch. KINETIC gains a
compact dark circular pit, thin silver sheared rim, raised radial fracture
lines; heavy kinetic impacts add 2 smaller close pockmarks instead of a
large diffuse crushed-metal patch. MISSILE/TORPEDO add a small soot-blast
field with metallic fragments. PLASMA is a compact hot melt, PARTICLE a
fine pinprick cluster, and FIGHTER a small impact dimple.
All are deterministic and bounded to at most 1.3 armor cells from an
actual impact anchor, never one cosmetic stamp per splash-eroded tile.
The damage family is captured and persisted by DirectionalDefense; older
untagged impacts remain on their prior generic scorch style.
Visual inspection is a nonblocking diagnostic.

### Fitted plasma/particle/torpedo/heavy-kinetic forensic captures
Four additional deterministic no-AI firing-lane cases now fire genuine base weapon modules PLA, PAR, TOR and heavy KIN against the fixed target. Every case captures 3 true 1280x720 client frames, typed impact metadata and an individual ZIP. The old rectangle stress test remains distinct and never impersonates a real hit. An unreached milestone remains labeled as such; the artwork quality cannot block releases.


### Corrected invisible weapon-family highlights (alpha-cut overlap)

An objective screenshot comparison found the former kinetic/missile renders
identical before and after the R/G/B profile addition. The profile geometry
was being drawn *under a physically missing armor pixel*: at a fully
penetrated target the physical alpha hole reaches ~0.82 defense cells,
while the kinetic profile stopped at ~0.73 cells. The fragment shader
correctly discarded its entire dark pit and silver rim. MISSILE, TORPEDO,
PLASMA and small particle highlights suffered the same inward placement.
The visual profiles now follow the surviving outer rim of the SAME real
physical crater: dark interior comes from the existing structural underlay,
thin bright/silver lifted edges and short radial fractures sit on intact
armor, and explosive soot/shrapnel or plasma heat occupy their own bounded
outer bands. Maximum affected radius remains 1.3 tile units. The LASER
profile, armor cutout geometry, true physical damage and normal-resolution
source sprites remain unchanged. The separate nonblocking visual smoke test
now asserts each family has nonzero coefficients at a provably intact pixel
immediately outside a severe physical alpha hole.


### Fixed missing impacts on narrow authored faction silhouettes

Actual diagnostic mask reads showed ZERO colored or transparent pixels for
KINETIC and heavy KINETIC even though armor cells lost physical integrity.
The physical 16x16 defense ellipse and a narrow original faction sprite
do not always overlap at the selected hit cell. The previous one-cell
source-alpha search found no opaque sprite pixel in those cases and emitted
a completely transparent/neutral mask. Preserve its existing first-pass
registration for already-visible impacts, especially the approved laser.
When that pass finds no source pixel, a second bounded search (max 3.25
defense-cell units) registers the impact to the NEAREST actual opaque,
surrounded source pixel. Any hit still further from source artwork remains
unmapped rather than jumping to an unrelated distant hull section.
This does not change physical collision points, armor damage, module
targeting, or original sprite transparency. The dirty GPU upload rectangle
expands only as necessary to cover the registered artwork and its 1.3-cell
damage gradient; otherwise the previous small dirty rectangle is retained.
Headless smoke tests validate the bounded projection, lack of teleporting
across a large empty region, correct dirty masking, and no phantom marks.


### Visual pockmark support inside the original silhouette

After restoring actual R/G/B data, a real KINETIC hit still generated only
10 bright-metal mask pixels, all within ONE vertical source-art column at the
edge of the Vaerith corvette. The direct hit was registered to the nearest
opaque border pixel, which has essentially no surrounding plating on which
to draw a circular metallic rim or radial fractures. A deterministic,
once-per-original-sprite secondary registration now picks a candidate no
farther than .95 armor cell from the former nearest-surface point and never
more than 3.25 cells from the real physical impact. It scores the ORIGINAL
source alpha at 16 nearby samples and selects more solid **existing** armor
only when there is meaningfully more room for a drawn crater. Candidate
movement across a fully transparent gap is prohibited. LASER and untyped
historic strikes still use the unchanged original anchors. Normal, high-res
ship artwork and hull transparency remain authoritative. The existing GPU
dirty rectangle also includes the inboard point, without disabling partial
mask uploads. The optional visual workflow logs actual per-family mask
coverage and provides original framebuffer screenshots.


### Diagnostic kinetic target — source artwork constraint

The attempt to shift kinetic damage stamps toward interior painted sections
was reverted: real framebuffer mask coverage for ordinary kinetic changed
from only 10 to 11 metal pixels and severe kinetic regressed from 49 to 35.
The physical hit landed on a *single-pixel narrow Vaerith wing edge*,
which cannot represent a circular raised pockmark without inventing ship
pixels outside the original PNG. Keep actual edge strikes clipped to
the approved source art rather than relocating their contact unnaturally.
The optional KINETIC and heavy KINETIC **demonstration** lanes now fire
the real fitted guns against a broad, originally illustrated TERRAN
frigate/destroyer. Normal gameplay faction, damage, aiming and weapon
physics are entirely unchanged; the approved Vaerith laser captures
remain exactly as before. Only if the genuine new framebuffer and mask
show bright-edged circular damage can this style be considered visually
verified.


### Exact pristine kinetic reference pair

The diagnostic now records two new real, unshot fixed-pose targets:
`kinetic-pristine` is the same TERRAN FRIGATE, seed and orientation as
`kinetic-impact`; `kinetic-heavy-pristine` is the same TERRAN DESTROYER
as `kinetic-heavy`. Each has zero physical hits and intact armor and is
rendered through the genuine desktop OpenGL material renderer. The gallery
produces lossless pixel differences, alongside untouched original 1280x720
source framebuffer PNGs. This prevents claiming a different source hull or
other faction's texture details are kinetic craters. The aesthetic
distinction remains a nonblocking visual diagnostic.

### Cooled laser annealing / interference oxide (second weapon-differentiation pass)

Genuine laser impacts now retain a compact blackened core, plus a narrow,
permanently cooled heat-affected zone on nearby **surviving armor plating**.
The zone uses staggered straw-gold, bronze, purple and sapphire-blue interference
tints resembling temper colors on heated metal. It is neither a bloom nor a
ship-wide tint: full RGB source detail, normal/emissive art, source silhouette,
and the actual armor cutout remain authoritative. It peaks just outside the
physical hole and is zero beyond 1.27 directional grid cells, including on
pristine / fully repaired ships. Non-laser effects keep their existing shapes.

To maintain 200-versus-200 compatibility with no extra per-vessel texture, the
same 96x144 RGBA mask multiplexes B=0..0.56 for ordinary raised silver metal
and B=0.64..1 for laser oxidation; G is the oxide color phase **only** when the
laser tag is present. The GLSL material pass mixes the tint against the actual
source pixel rather than replacing it. All unchanged ships retain their original
full-resolution artwork. Objective path/separation tests remain in the
**nonblocking** ship-damage visual and surface shader diagnostics. Final color
readability on original bright and dark hulls still requires real screenshots.

The pixel-channel forensic gallery now reports the actual tagged
`tempered_laser_pixels` independently from metallic fracture and orange heat
coverage, avoiding false attribution of the laser oxide ring as kinetic silver.
These are diagnostic observations only. Color-treatment assertions are kept in
the separate nonblocking damage test, never the required functional build gate.

### Bright-plating forensic alignment

The original `*-terran-upper` comparison was still striking a narrow,
already-dark left wing during diagonal approach; enlarging metallic scratches
on that region alone would not establish color separation. The real fitted
attacker in upper-panel scenarios now starts on the target's central bow axis,
190 world units forward. Pristine / kinetic / laser / missile controls share
the same shooter alignment and fixed target pose. This changes only the optional
no-AI screenshot fixture, never gameplay targeting or weapon behavior. Whether
the new shot actually contacts brighter paint must be verified from the next
physical OpenGL capture and reported honestly if it misses.

### Fracture-end visibility after real framebuffer comparison

The previous kinetic material-mask refinement changed 1,008 source framebuffer
pixels in the same-hull surface comparison, but the dark round hole remained
visually dominant. Source inspection found a concrete cause: pockmark sampling
exited at `radius + 0.34` even though raised fracture strokes were authored up
to `radius + 0.46`. The remaining crack ends were never emitted. This iteration
removes that premature cut-off, maintains the absolute 1.30-cell bound,
and strengthens only the darker fracture-side relief. The new nonblocking
geometry diagnostic checks an outer radial crack sample on surviving armor.
Actual visual readability awaits the next OpenGL screenshots.
