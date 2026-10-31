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
