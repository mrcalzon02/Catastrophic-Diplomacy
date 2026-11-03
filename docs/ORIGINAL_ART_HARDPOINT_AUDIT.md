# Original-art ship hardpoints — technical audit

## Root cause / correction

Previously `HullLayouts.slots(faction,hull)` projected class/faction templates onto a **schematic polygon**. The shipyard's real-art preview separately scanned transparent PNGs and snapped its on-screen marks onto alpha. `WeaponMuzzles` and `CapitalTurretSprites` continued to use the unmodified schematic location, so a weapon could appear detached from its ship, particularly on narrow or eccentric faction silhouettes.

**Pass A implementation (2026-11-02):** `tools/ApprovedHardpointBake.java` now
reproduces the approved PNG-alpha projector during ordinary Gradle asset
preparation. `bakeApprovedHardpoints` processes the 110 original source PNGs
and generates one versioned, SHA-256-signed `hardpoints/approved-v1.txt`
classpath resource for all **506** faction/chassis variants. The
`ApprovedHardpointResource` core loader validates the complete inventory,
socket ordinals, mount types, source-art SHA metadata and artifact checksum
without any libGDX, Java AWT or rendering dependency.

`HullLayouts.slots()` consumes the same baked map in core and in graphics
clients. The actual desktop/Android renderer no longer registers its
texture-dependent dynamic projector. The headless network server and
graphics client both **fail closed** if the baked resource is absent,
instead of silently switching between schematic and sprite layouts.

All consumers ask for the same immutable `Shipyard.Slot` list:

- `Shipyard.Design.placements()` (module editor and actual socket hit targets)
- `WeaponMuzzles.equipped()` (live projectile and beam source positions)
- `CapitalTurretSprites.render()` (drawn gun hardware)

The **build-time baker** uses the same deeply opaque PNG-interior sample grid
and four-direction footprint test as the previously graphics-only projector.
The original image-derived positions are generated once from the approved
pixels, then cached as an immutable runtime resource—not rescanned during
combat or on Android. Each specialist variant retains its distinct
deterministic ideal-point offset and unchanged socket identity.
No new placeholder hulls, fake turrets or randomly moving sockets are introduced.

## Coverage

All 11 playable factions and 46 append-only chassis/variants (506 combinations). Four examples of the *existing* class differentiation:

| Chassis | Systems | Weapons | Defenses | Supports | Habitats | Total |
|---|---:|---:|---:|---:|---:|---:|
| Interceptor | 4 | 2 | 1 | 0 | 2 | 9 |
| Interceptor Flak | 4 | 3 | 2 | 0 | 2 | 11 |
| Carrier | 9 | 4 | 6 | 8 | 2 | 29 |
| Carrier Command | 11 | 5 | 6 | 8 | 2 | 32 |

Counts are chassis properties, NOT image properties. Specialist variants already have different module numbers. **Do not casually reduce the interceptor to four total sockets**: current fitting rules require a reactor, engine, radiator, two independently equipped weapons, shield, crew quarters and life support. Replacing those requirements with inherited hull machinery would require an explicit, save-compatible feature migration. Current inventory, ship IDs, slot IDs, and existing saves are unchanged.

## Packaged-source alpha audit results

Audited the real packaged source-art PNGs from successful release workflow
[41248768356](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41248768356),
not concept art or schematic polygons. The original approved PNG checksums match
the repository's approved-faction art manifest.

- **11 factions × 46 chassis = 506** combinations.
- **110 distinct authentic PNGs** supplying those combinations.
- **9,515 physical socket positions**, of which **2,244 are weapon sockets**.
- **0 weapon sockets** needed an unspaced fallback.
- **0 sockets** landed on transparent/insufficient-alpha pixels.
- **2 dense internal sockets** required relaxed separation on exceptionally thin
  source silhouettes; neither was a mounted weapon.
- Every variant using the same sprite identity received distinct deterministic
  socket placements. Art sharing does *not* force shared module counts.

This is a pixel-level geometry audit against the original assets, **not**
a claim of manually reviewed device screenshots for all 506 combinations.
The detailed socket-level CSV was generated as a conversation artifact.
The source art and existing campaign inventories were not modified.

## Verification and remaining limitations

- `core:hullLayoutsSmoke` asserts all chassis/faction slot counts, legal base ship builds, no duplicated logical sockets, stable ordinals, distinct variant counts, and projectable world muzzle origins that exactly match shipyard socket identities.
- `core:approvedHardpointsSmoke` verifies all 506 configurations, 9,515
  physical sockets, immutable save IDs/mount types and actual fitted muzzle
  coordinates against the baked, checksum-verified core map. A standalone
  unbundled pure-javac test may still use schematic test geometry, but real
  client/server startup **requires** the baked map.
- Artwork/alpha appearance and original-art marker spacing are **diagnostic
  only**, not release-blocking style gates. Duplicate socket positions,
  broken blueprint IDs, mismatched physical muzzle origins and missing
  required production resource are objective functional defects.
- Full desktop/Android rendered screenshot review across every faction and variant is separate from headless functional checks. Do not call screenshots visually verified unless their actual runtime captures have been inspected.
- The former headless-versus-graphics geometry mismatch is now addressed
  in source. Complete device acceptance still requires an actual Android
  launch/screenshot pass and deterministic server/client combat replay
  on a packaged build. Headless CI cannot certify mobile rendering.

The authoritative source art is left unmodified.
