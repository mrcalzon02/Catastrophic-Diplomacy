# Shipyard: faction-specific hull hardpoints

## Current integrated slice

The Java 17 shared shipyard now provides **11 selectable fitting classes** (ten art-roster classes plus the existing generic Cruiser, retained for save compatibility) across **six playable factions**. This is an implemented **schematic UI and equipment fitting model**, not a claim that every concept-art ship has been cropped into a final runtime sprite.

The shipyard's side panels choose left/right fleets, class and faction; the central drawing is an interactive top-down hull silhouette with individually selectable colored hardpoints. System, weapon, defense and support are mechanically distinct mount categories, and only compatible faction components can be installed. Empty mounts show muted markers; fitted mounts are colored; selected mounts are highlighted. The list and schematic select the same stable slot number.

| Class | System | Weapon | Defense | Support | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| Interceptor | 4 | 2 | 1 | 0 | 7 |
| Corvette | 3 | 2 | 1 | 0 | 6 |
| Missile Corvette | 4 | 4 | 1 | 1 | 10 |
| Frigate | 4 | 3 | 2 | 1 | 10 |
| Destroyer | 5 | 5 | 3 | 1 | 14 |
| Light Cruiser | 6 | 5 | 3 | 2 | 16 |
| Cruiser (legacy/general) | 6 | 5 | 3 | 2 | 16 |
| Heavy Cruiser | 7 | 6 | 4 | 3 | 20 |
| Battlecruiser | 8 | 8 | 4 | 3 | 23 |
| Carrier | 9 | 4 | 6 | 8 | 27 |
| Dreadnought | 9 | 9 | 5 | 4 | 27 |

These are first-pass engineering capacities awaiting fleet-balance testing, not claims of established canon. The six factions share per-class mounting *counts* in this slice, while their positions, outlines and fitting technologies differ. Future distinct racial capacity rules must be reconciled with save compatibility and combat balance.

## Racial layout philosophies

- **Terran Confederacy:** axial protected systems, paired conventional broadside mounts and aft support.
- **Draxian Empire:** extended wing/crescent weapon distribution, forward protected core and shifted defensive coverage.
- **Carnelian Furlongs:** massed inward batteries, heavy wide peripheral armor/defense mounts and deeply set machinery.
- **Zippledrow:** narrow fast prow weapon groups, forward machinery and lean aft bays.
- **Uzipt:** forked/lateral engagement arcs, offset systems and rear defensive screen.
- **Mandrally:** distributed internal pods, broad weapons and clustered service/support facilities.

Coordinates are **normalized hull-space**, not texture crop pixels. `HullLayouts.slots(faction,hull)` computes a deterministic layout for each pair, projects hardpoints into its actual schematic hull outline, enforces non-overlap and caches immutable locations. The same coordinates drive desktop clicking and Android touch hit tests; the forward axis is +Y. The placement algorithm is intentionally in `:core`, not duplicated per client.

## Persistence and compatibility

The authoritative slot index and mounting family are maintained in `Shipyard.Hull.slots`; faction-specific position data is derived in `HullLayouts` from the saved faction and class. Existing `CD-SHIP-1` and `CD-FLEETS-1` encodings remain unchanged; legacy Corvette, Frigate, Cruiser and Dreadnought enum ordinals and slot counts remain intact. New class blueprints use the same validated schema. Local standalone saves are now keyed by fleet side, faction **and hull class**; the loader tries the original side/faction filename as a legacy fallback but refuses to load a different selected hull class.

Changing a hull or faction starts its legal factory fitting, **not** a silent carryover of potentially incompatible installed equipment. Applying to battle continues to require full mass, volume, reactor, heat and armament validation. Original battle deployment is preserved.

## Testing and acceptance

`gradle :core:hullLayoutsSmoke` runs `HullLayoutsTest`: all 66 faction/class combinations, exact class slot counts, stable indices, physical containment, ten-unit marker separation, deterministic layouts, click/touch hit testing, six-faction differentiation, legal starter designs and portable blueprint round trips. The test is also chained after the existing `:core:shipyardSmoke` Gradle task.

**Not yet verified in this interaction:** actual Java/Gradle build execution or rendering on desktop/Android hardware. The schematic layouts have been exercised by independent integer-geometry inspection, but must still pass the Java smoke tests and be checked on real screens.

**Later integration:** map these normalized mounts onto each approved final hull sprite or 3D hull with artist-specified UV/anchor coordinates and physically located muzzle/impact/armor events. Combat currently consumes fitted module *aggregates*, not per-hardpoint firing origins or independently damageable internal module compartments. Do not claim those are implemented. Weapon effect sheets are separate visual resources; their physical emitter/housing graphics are not projectile effects.
