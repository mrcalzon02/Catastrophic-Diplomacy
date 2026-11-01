# Original-art ship hardpoints — technical audit

## Root cause / correction

Previously `HullLayouts.slots(faction,hull)` projected class/faction templates onto a **schematic polygon**. The shipyard's real-art preview separately scanned transparent PNGs and snapped its on-screen marks onto alpha. `WeaponMuzzles` and `CapitalTurretSprites` continued to use the unmodified schematic location, so a weapon could appear detached from its ship, particularly on narrow or eccentric faction silhouettes.

The graphics client now registers an original-art projection with `HullLayouts` **before deploying ships**. All consumers ask for the same immutable `Shipyard.Slot` list:

- `Shipyard.Design.placements()` (module editor and actual socket hit targets)
- `WeaponMuzzles.equipped()` (live projectile and beam source positions)
- `CapitalTurretSprites.render()` (drawn gun hardware)

The alpha scanner only accepts deeply opaque sprite-interior points, requiring solidity in four directions. It excludes top and bottom caption/exhaust margins and reserves body-relative clearance for mounted hardware. All positions resolve lazily and deterministically from the original source image and are cached per unique PNG. Each specialist variant gets a different deterministic ideal-point offset, but retains the real alpha interior. No new placeholder hulls, fake turrets or randomly moving sockets are introduced.

## Coverage

All 11 playable factions and 46 append-only chassis/variants (506 combinations). Four examples of the *existing* class differentiation:

| Chassis | Systems | Weapons | Defenses | Supports | Habitats | Total |
|---|---:|---:|---:|---:|---:|---:|
| Interceptor | 4 | 2 | 1 | 0 | 2 | 9 |
| Interceptor Flak | 4 | 3 | 2 | 0 | 2 | 11 |
| Carrier | 9 | 4 | 6 | 8 | 2 | 29 |
| Carrier Command | 11 | 5 | 6 | 8 | 2 | 32 |

Counts are chassis properties, NOT image properties. Specialist variants already have different module numbers. **Do not casually reduce the interceptor to four total sockets**: current fitting rules require a reactor, engine, radiator, two independently equipped weapons, shield, crew quarters and life support. Replacing those requirements with inherited hull machinery would require an explicit, save-compatible feature migration. Current inventory, ship IDs, slot IDs, and existing saves are unchanged.

## Verification and remaining limitations

- `core:hullLayoutsSmoke` asserts all chassis/faction slot counts, legal base ship builds, no duplicated logical sockets, stable ordinals, distinct variant counts, and projectable world muzzle origins that exactly match shipyard socket identities.
- Artwork/alpha quality is **diagnostic only**, not a release-blocking aesthetic gate. The 11-faction runtime PNGs remain authoritative; the schematic fallback only applies when approved image data cannot be sampled. A specific sprite failing to supply valid core-alpha candidates must be reported rather than disguised as a complete pixel-level audit.
- Full desktop/Android rendered screenshot review across every faction and variant is separate from headless functional checks. Do not call screenshots visually verified unless their actual runtime captures have been inspected.
- Since alpha scanning is client-side, anyone implementing deterministic **headless** network arbitration should next serialize/bake these positions into a shared resource, so a process without graphics access cannot disagree on exact weapon origin geometry.

The authoritative source art is left unmodified.
