# Guided launcher firing schematics — 30 Hz physical combat rules

Authoritative sources: `WeaponBehaviorTable.java` (six existing main-gun families, power/heat/aim and PD), `OrdnanceDoctrine.java` (alternate guided-launch scheduling and physical projectile profiles), `ProjectileModel.java` (speed, HP, cross-section and emission signature), `Battle.java` (actual instantiation, movement, tracking, collisions and PD), and `FighterModel.java` (independently armed craft).

This is a new **working game-mechanics subsystem**, not the completion of true per-hardpoint fire controllers: launch doctrine selection currently operates on a ship's aggregated MISSILE or TORPEDO gun family. Each chosen pattern is immutable and ready for future binding to an individual equipment slot, module version or faction-specific technological unlock.

## Guided launcher schematics (for missiles AND torpedoes)

Every launched body consumes **one real round from the fitted magazine and the weapon's power/heat per-shot costs**. All shots retain physical finite HP, an independent radar cross-section, emissions, steering and impact sequence. Without sufficient full-salvo ammunition the launcher does not start; subsequent rounds also abort safely if the parent ship dies, loses crew, loses operable weapons/magazine or power. The normal SINGLE mode remains the default for all existing fleet files and simulation fixtures.

| Mode | Live physical launches | Spacing | Cycle multiplier | Per-real-warhead damage | Flight / PD behavior |
| --- | --- | --- | ---: | ---: | --- |
| SINGLE | 1 guided warhead | immediate | 1.00 | ×1.00 | Existing behavior unchanged |
| RIPPLE | 3 guided warheads | 5 ticks / 0.167 s | 3.20 | ×0.78 each | Separate targetable bodies; spaced pressure |
| SATURATION | 5 guided warheads | 2 ticks / 0.067 s | 5.50 | ×0.60 each | Large barrage, consumes 5 actual ammunition units |
| MICRO_SWARM | 8 micro-warheads | 1 tick / 0.033 s | 4.00 | ×0.23 each | ×1.32 velocity, ×0.42 health, ×0.38 cross-section, ×0.63 emissions |
| HEAVY_STRIKE | 2 armored warheads | 7 ticks / 0.233 s | 3.40 | ×1.45 each | ×0.78 velocity, ×1.85 health, ×1.52 cross-section, ×1.55 emissions |
| COLD_LAUNCH | 1 guided warhead | 15 ticks / 0.5 s ignition delay | 1.22 | ×1.00 | Physical canister exists immediately, stationary and PD-interceptable until propulsion engages; less detectable signature ×0.56 |
| DECOY_CLOUD | 1 real warhead + 4 decoys | 2 ticks / 0.067 s | 2.70 | ×1.00 actual warhead | Four *physical* zero-damage decoys, each consumes a magazine round, with high RCS and emissions to compete for PD attention |

The timing values are **actual combat ticks**, not a looped particle texture or free instant-hit burst. An arriving decoy expires without calling `Battle.hit`; it never creates a legitimate `IMPACT` event or damages shields/hull. Decoys have one quarter-ish ordinary structural integrity and are explicitly susceptible to PD; their increased radar return is intentional. Intercept damage follows the existing `ProjectileModel` rules and decoy interceptions are separately counted. A cold-launch object remains available for defensive acquisition throughout its ignition delay.

A shot's visual event uses the **real launcher and physical shot ID**, including each separately launched micro-round, heavy warhead and decoy. Renderers may use existing approved beam/projectile art, but the absence of a bespoke decoy sprite is an art task, not a reason to invent new gameplay objects.

### Activation

`ship.setLaunchMode(Battle.DamageType.MISSILE, OrdnanceDoctrine.Mode.MICRO_SWARM)` and `battle.setShipLaunchMode(shipId, Battle.DamageType.TORPEDO, OrdnanceDoctrine.Mode.COLD_LAUNCH)` configure the mode. The function rejects non-guided families and null/unknown patterns. **The tactical UI, engineering schematic save codec and per-hardpoint mode selectors are NOT yet wired**; therefore this is currently a simulation API available to authored missions/testing, not a claim of already accessible in-game shipyard controls.

## Wider weapon cadence and reload taxonomy — next integration

Each listed item is a separate operational behavior that future individual `WeaponMount`/magazine engineering must model. A conceptual entry does **not** mean a live firing mode has been implemented.

| Category | Design modes / reload types still needed |
| --- | --- |
| Direct-energy beams (LAS/PLA/PAR) | Charge-to-fire, pulse pairs, staggered alternating emitters, sustained/tracking beam with damage-over-time and power/heat budget, warm-up/spin-up, hard thermal cutout and cool-down, capacitive discharge |
| Kinetic/cannon projectiles | Semi-auto, two-tap, short controlled bursts, rotary/sustained rate, fixed-magazine reload, linked/caseless ammunition, flak shell and proximity fragmentation, slow sabot with dense PD profile |
| Guided missile and torpedo expansions | Remote staging, split-stage MIRV with daughter-round identity, seeker-switch/failsafe self-destruct, flight-profile lofting, blast-radius/falloff warheads, anti-PD jamming missile, ECM-decoy preference vs ECCM resistance, anti-fighter seekers, ship-to-ship torpedo arming distances |
| Point defense | Sensor-linked fire-control networking, automatic magazine management, beam vs kinetic interceptors, specialized anti-micro tracking, flak AOE, selective ignore-decoy priority, per-controller HOLD / ORDNANCE / ANTI_CRAFT / BALANCED modes |
| Fighters and drones | Salvo formation sequencing, independent craft missile/torpedo stock, landing/repair/rearm at carriers, escort ammo priorities and bay refueling |
| Cross-cutting | Per-**physical module slot** muzzle, firing arc, magazine reload, current ammunition, target scoring/priority, cooling source, energy reservation, crew/sensor dependence, independently upgradeable submunition stats, faction and Mk variant doctrine unlocks |

The existing **single aggregated firing/reload timer per weapon family on each ship** is still the main architectural limitation. It must be replaced with real mount-state objects before crediting any of the above conceptual types as complete, especially with thousands of potential ship variants and 500 campaign levels.

## Regression / release gate

`gradle :core:ordnanceDoctrineSmoke` tests all **14** guided-family/pattern combinations with physical shot IDs, finite ammunition/power, boost delays, micro/heavy body signatures, actual zero-damage decoys, point-defense competition, launch interruption, and deterministic replay. Chained into the `massBattleCampaign` suite alongside `weaponBehaviorSmoke`, `projectileModuleSmoke`, `supportSystemsSmoke`, `fighterCraftSmoke`, `moduleResearchSmoke` and the crew and module-version suites.

**Release state:** This is a feature branch, **not** merged into `main` or included in the existing signed Android RC1. The larger alien ship sprite archive is separately missing required binary commits and must not be silently reported complete.
