# Catastrophic Diplomacy — Hull-first ship engineering & eleven-faction technology

Status: first structural implementation on draft PR #12. The all-race test suite is required before integration; do not treat generated counts as playtest certification.

## Non-negotiable hull/module boundary

A bare playable hull owns **only** its structure, intrinsic armor, limited built-in shields (selected classes only), hull-specific shield refresh, base mobility, stored electrical power, modest internal recharge, sensors, radar cross-section, and the finite locations/mass/volume for outfitting.

An occasional specialist carrier hull has a small intrinsic fighter bay. Every other class starts with **zero** fighter capacity. No hull supplies a laser, kinetic cannon, missile, torpedo, point-defense gun, repair tool, crew berths, ammunition supply, or faction-specific tactical weapon by itself. The majority of actual combat capability and redundancy comes from mounted hardware.

The current legacy `Battle.addShip(team,faction,x,y)` shortcut is an **unfitted simulation fixture** with historically armed defaults. It has no Shipyard.Hull identity. This shortcut must eventually be eliminated from production scenario generation and replaced with explicit `Shipyard.Design` fittings. A real deployed `Battle.spawnWing(...,design,catalog,...)` uses only `Shipyard.fit` capabilities.

`HullBaseline.forHull(faction,hull)` is the deterministic native engineering source. It covers 11 existing hull classes × 11 playable civilizations. `Shipyard.Fitted.baseline` is the immutable snapshot used in real battle; the simulation uses its innate shield recharge, sensor range and cross-section. Unarmed rescue, scout and pure-support loadouts are legal where reactor, engine and radiator readiness is satisfied. Existing prefitted starter templates deliberately mount guns, and are not equivalent to the naked hull.

**Remaining hull-rule decisions:** The current fitting validator still requires at least one reactor, drive and radiator, even though bare hulls have emergency base power/speed. A later explicitly chosen fleet-readiness standard should differentiate *hangar-ready*, *operational*, and *combat-ready*, with power/thermal caps and safe drifting rather than silently changing decades of existing tests. Crew complement is not yet simulated; implementation must be through explicit crew quarters, command or automation modules, not a free hull crew allowance.

## Technology ownership: one rule for every player and AI

The original 6 factions still use their own authored CSV source rosters. The five new ones are **native separately numbered engineering families** with codes:

| Civilization | Tech ID | Parent engineering inspiration (not equipment access) |
| --- | --- | --- |
| Vaerith Prism Dominion | VAE | Draxian |
| Zelthar Luminari Accord | ZEL | Uzipt |
| Aureate Cathedral Synod | AUR | Mandrally |
| Nyx Biolith Enclave | NYX | Carnelian |
| Umbra Foundry Compact | UMB | Zippledrow |

Five new roster families are deterministically materialized from the existing source data, then given their own ID, prerequisite chain, base mechanical tradeoffs in mass, power, heat, output and durability, and carefully bounded per-stage accuracy/output/resilience differences. These are **functional preliminary sidegrades**, not five invented standalone CSV art packs or five fully bespoke balance systems.

Total: **220 base chassis** (11 × 20 subsystem types) with **5,280 independently researchable upgrades** (11 × 20 × 4 exclusive directions × 6 stages). The four current directions (OUTPUT, EFFICIENCY, RESILIENCE, SPECIALIST) are strictly mutually exclusive **per installed socket**. Other modules, including other copies of the same weapon, may choose a different branch. Research costs and fitted stats apply identically to player and enemy. There are no `NPC_ONLY` buffs or hidden stronger AI weapons.

These counts refer to **technology choices**, not 5,280 separately modeled pieces of artwork and not 5,280 individually firing hardpoints. Do not assert unique 3D/sprite variants or independent targeting until they're actually in the asset pipeline and world simulation.

## Expansion to a sustainable thousands-vessel game

*Hull lineage keys*: introduce a stable `parentFaction / subfaction / designFamily / hullClass / mark` identity. A sprite sheet is an art/design family, **not automatically a new race**. Preserve the 19 accepted sheets and assign 8 as subfaction fleets in the 500-level campaign before inventing more races. Hull variants should have controlled changes to intrinsic engineering and slot layouts; no baked weapons, crew or combat hacks.

*Individual hardpoints*: replace the current family-aggregated `Shipyard.Fitted.Weapons` object with a list of actual `WeaponMount` instances, each bound to a source slot, module variant, branch, rank, ammunition, heat, reload, arc, muzzle coordinate and targeting order. Rockets, shells, flak and fighter craft are already physical targets; each shot must inherit its **specific** launching module, not an average across all copies.

*Module variant registry*: each native component can have independent design generations and purposeful sidegrades (short-range high-power beam, long-range precision, low-signature kinetic, PD scan vs intercept-damage, repair nanites vs field-service drone bays). Define costs, power, heat, maintenance, durability, failure modes and visuals per variant, and technology prereqs per faction. Variant identity survives saving/loading and cannot override exclusive research.

*Support & crew*: define module-powered crew quarters, automation and command systems with explicit headcount/capacity constraints, life-support cost, morale/reliability, and no built-in crew capacity. Carriers with native micro-hangars retain module-added fighter hangars. Repair and shield charging should allow multiple specialized and redundant modules without fabricated initial capacity.

*Campaign economy*: store per-faction unlock inventories so a faction's six-rank technologies are selectable by both its own player and NPC instances; 500 missions can unlock or reveal specialized subfaction hulls, modules and designs over time. The UI should distinguish unavailable due to research, incompatible due to mounting, and unaffordable due to operational limits.

*Art*: map every accepted sheet to actual cropped binary sprites and every module family's approved icons and variant visuals. Missing art must remain flagged, not silently counted as complete.

## Acceptance suite / shipping gates

`:core:hullFoundationSmoke`: 121 hull combinations, intrinsic physical numbers, no module-free armament, carrier-only fighter capacity, unarmed support engineering, and symmetric equipment for both battle teams.

`:core:technologySmoke`: validate exactly 220 native base IDs, 5,280 upgrades, and their 11 independent predecessor chains. `:core:moduleResearchSmoke`: six ranks and one exclusive branch per installed component without loss of cost/physics state.

`:core:massBattleCampaign`, `:core:alienFactionSmoke`, fighter and support smokes: check all-race mixed fights, deterministic replay, radar/sensors, moving ordnance, repair/shield exchange, and no headless 200v200 performance regressions.

Integration into `main` **must wait** until foundation checks pass and is distinct from the older Android RC1 signing work. The 50 alien PNG binary assets are separately pending from the art PR; hull engineering success does not constitute sprite completion.
