# 500-level campaign threat and fleet progression

## Contract

The campaign is ten sectors of fifty missions. Each page shows ten connected
systems, each with a real operation, opponent, enemy engineering ceiling, fleet
cap and reinforcement count. A page represents a single front against one
opposing faction; the next page may transition to a different faction. System
50, 100, ..., 500 is a sector-capital engagement.

**Player technology and enemy technology are not the same entitlement.** The
player must purchase the nine campaign engineering licenses and individually
fund compatible technology modules, as before. The mission generator only
uses the same validated hulls, sockets, physical module catalogs and nine
engineering ranks; it cannot grant the player free unlocks or equip
unmanufacturable bonus statistics. Skirmish/PvP authorization is unchanged.

| Campaign levels | Enemy ceiling | Fleet composition |
|---|---|---|
| 1–50 | Tech 0 | Interceptors/corvettes, baseline patrol and screening weapons |
| 51–100 | Tech I | Missile corvettes, frigates, destroyers; torpedoes and first upgrades |
| 101–150 | Tech II | Light through heavy cruisers and battlecruisers begin appearing |
| 151–200 | Tech III | All eleven base hulls including carriers and dreadnoughts |
| 201–250 | Tech IV | First strike/screen specialist variants and stronger modules |
| 251–300 | Tech V | Additional electronic warfare and command variants |
| 301–350 | Tech VI | Fleet-support/repair logistics variants become available |
| 351–400 | Tech VII | Veteran multi-role formations and advanced installed parts |
| 401–450 | Tech VIII | Experimental engineering across mixed fleet generations |
| 451–500 | Tech IX | Apex fitted fleets, upgraded escorts and capital commands |

A tier ceiling is NOT a required minimum for every enemy hull. Ships within a
wave can be two tiers older and may use different base chassis, physical weapons
and upgrade tracks. Sizing follows **HullProgression**, so every base hull is
available by tier III; higher ranks primarily improve equipment and give access
to variants, not ever-larger raw hull sizes.

## Ten-operation circuit

The opening ten-system front follows this sequence. Subsequent fronts rotate
its starting operation deterministically by front and sector, so they retain
tactical coverage without repeating the exact same order:

1. Patrol — light reconnaissance vessels and general-purpose guns
2. Interdiction — missile pickets and pursuit hulls
3. Escort screen — destroyer/frigate point-defense oriented groups
4. Convoy battle — mixed corvettes, frigates and cruiser escorts
5. Torpedo raid — high-alpha missile and torpedo attackers
6. Strike wing — launch platforms and missile strike escorts
7. Siege line — heavier particle/plasma support once available
8. Breakthrough — armored heavy chargers with close-range support
9. Command fleet — carriers and command cruisers when licensed
10. Fleet action — combined arms with broad hull distribution

The final system of the fifth front (level 50n) is **Sector capital**,
replacing the scheduled operation with a heavier flagship-oriented mission.
Where early tiers cannot support a named advanced hull, choose a valid earlier
combat hull rather than bypass its technology authorization.

The physical enemy count varies by operation: fewer but better-equipped ships
on raids, carriers, command missions and sieges; larger screens, fleet actions
and sector-capital fleets. Counts always respect the current mission's 1–200
fleet cap. Reinforcements arrive in the same live Battle without resetting
friendly hull damage, ammunition, crew or surviving craft.

## Engineering and replay constraints

- Ship fittings are generated per vessel, per wave, from the level seed,
  wave number and enemy faction. Replaying the same mission and wave produces
  the same hardware, modulo intentional changes to the engineering corpus.
- Weapons (laser, kinetic, missiles, torpedoes, plasma, particle) and their
  upgrades occupy real mounts. Later capital ships can install additional
  batteries only when spare physical reactor, cooling, weapon and defensive
  sockets support those fittings. Every fitting passes Shipyard.inspect before
  reaching the battle. Module research stages and manufactured marks remain
  at or below that ship's actual tier.
- Specialist vessels are relatively uncommon. Empty/unpowered experimental
  choices fall back to buildable native hulls rather than receiving invisible
  bonuses. Aircraft launch platforms must install actual launch equipment.
- Enemy captains use operation-specific standing targeting/movement doctrine;
  those temporary AI orders do not overwrite the player's saved per-vessel orders.
- Faction/surface art, physics, weapon damage, service drones, retreat and
  campaign ownership all continue through the same Battle simulation.

## Validation

Run `:core:campaignOppositionSmoke` (also chained to
`:core:gameFlowSmoke`), `:core:gameFlowSmoke`,
`:core:shipyardSmoke`, and the existing campaign deployment/retreat smoke
checks. The campaign-opposition regression covers every map node, all ten
sector tech boundaries, deterministic equipment, physical fit legality,
actual deployment and second-wave continuity. CI green is required before
any release claim.
