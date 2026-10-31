# Curated original Catastrophic Diplomacy projectile bodies

The user's original source archive is
`catastrophic_diplomacy_weapon_fx_source_2026-10-24.zip`,
**sha256 07def3cb7ad47206aef4331c3a7f595b4504d749f85704a288de52487f72efb0**.
It was materialized from the user's Library and verified byte-for-byte
against the archive registry in `assets/effects/projectile_fx/source/README.md`.

The original full-resolution sheets are untouched in the Library.
These runtime atlases are separate manually inspected cuts, optimized for
small, readable world-space sprites and preserving transparent backgrounds.
The reduced-color PNG export changes only the separate runtime cuts, never the
original source PNG. Neither atlas is a screenshot of actual in-game rendering.

**`missile_bodies_6.png`**: 192×80, 3 columns×2 rows, 64×40 regions.
Source: `03_ballistic_missile_arsenal.png`
sha256 `069edc48f9d04d1eb2f14c1b8eb7fbf2e729660d2464c11bb3b31d3391926264`.
Cells: 0 red guided missile (standard), 1 black heavy assault (heavy
strikes), 2 red penetrator (AP), 3 blue seeker dart (micro/decoy/EMP/shield),
4 olive armored torpedo (torpedoes), 5 red needle (HE/fragmentation).
Drive staging: https://drive.google.com/file/d/1MfA8ApaMGZ4CevBE_DenEGo5pFeIDzvb/view?usp=drivesdk

**`kinetic_bodies_5.png`**: 320×40, 5 columns×1 row, 64×40 regions.
Source: `01_initial_projectile_arsenal.png`
sha256 `a92f2c88b76c0e95f476af2df07476898b3609070fa9fc541a74367ef406649c`.
Cells: 0 standard gray shell, 1 armor-piercer bronze slug,
2 fragmentation gold round, 3 high-explosive heavy gold round,
4 relativistic blue tracer.
Drive staging: https://drive.google.com/file/d/14c2MffjLCC6mLXvg-3eUTWTWLKpo8zFa/view?usp=drivesdk

`ModularWeaponFxRenderer` caches atlas TextureRegions ONCE and draws them
from the real missile/ballistic's simulation position and velocity.
`GuidedPayloadVisual` and `KineticPayloadVisual` select the actual
ammo/payload configuration rather than presenting one universal missile.
A missing atlas still falls back to the existing approved missile/torpedo
payload textures or kinetic/shape renderer as appropriate.
All rendering still uses the game's one-third weapon-FX footprint multiplier.
No physical hitboxes, module behavior, ammunition consumption, damage,
research or save data were altered.

This visual mapping has independent diagnostics only. Art approval requires
REAL desktop/Android game screenshot inspection and does not block a
functionally working release.


## Original 64-frame weapon muzzle and impact atlas

`weapon_fire_impact_64.png`: 1024×1024 RGBA, eight columns by eight rows,
128×128 original source-art frame cuts. This is sourced from the existing
`Catastrophic_Diplomacy_Modular_FX_Existing_Art.zip` Library archive
(6,503,142 bytes) which already contained independently sliced transparent
frames for laser, kinetic, plasma and particle effects. No new weapon artwork
was synthesized and no source frames were overwritten. The source archive
still remains unchanged in the Library.

Row 0: laser FIRING, row 1: laser IMPACT.
Row 2: kinetic FIRING, row 3: kinetic IMPACT.
Row 4: plasma FIRING (7 original frames, eighth cell empty),
row 5: plasma IMPACT.
Row 6: particle FIRING, row 7: particle IMPACT.

Each row has eight ticks/frames (plasma fire seven) matching the existing
`WeaponFxProfile` clip timing and actual FIRE/IMPACT visual events. The
renderer only creates the atlas's 64 `TextureRegion` objects once, shares
them among active effects and falls back to independently packaged source
frames when necessary.

SHA-256 curated atlas:
`0b05f708d61f2c04488139fb3f2ab87806c3155b789b447cda8d462c98d5a61a`
Drive staging copy:
https://drive.google.com/file/d/182Ap_Ebifq8yS9IOs4WdR80yL5OXxKgE/view?usp=drivesdk

This change also leaves guided projectile launch/explosion arts for further
specific curation; it does not misrepresent a laser or kinetic impact as
a missile impact. Cosmetic checks are isolated from release gates.
