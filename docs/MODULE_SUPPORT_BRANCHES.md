# Mutually exclusive module doctrines & fleet support machinery

Implementation: Java 17 shared core / libGDX renderer. **Source integration is committed; no Java, GPU, Android or 200v200 runtime pass is claimed until executed.** The module research catalog already includes 4 mutually exclusive tracks, each 6 stages deep, for all 120 base components across six factions. Only ONE branch may be installed on a particular socket; a different socket, even the same base module, may follow another path. The `CD-SHIP-1` save format and non-mixing invariant remain intact.

## Six-step fit-for-purpose module behavior

`ModuleBranchEffects.tune(subsystem, chosenBranch, rank)` is derived from the individual installed module, never a global faction-wide buff and never the sum of multiple branches. These are **incremental** effects at each rank, layered atop existing technology CSV mass, power, heat, cost, accuracy, output and integrity modifiers:

| Installed module | OUTPUT | EFFICIENCY | RESILIENCE | SPECIALIST |
| --- | --- | --- | --- | --- |
| LAS / KIN / PLA / PAR / MSL / TOR | +3.0–4.4% family damage/rank | 2.6% faster cycle/rank, improved power efficiency | slight tracking & cycle benefit, separate physical projectile armor from the research catalog | +4.5% reach/rank and +3% tracking/rank; actual guided missiles turn better |
| PD / FLK | +5% intercept damage/rank | 3% faster firing cycle/rank | small tracking/cycle boost, hardened module | +5–5.9% acquisition reach/rank and +5.2% tracking/rank |
| REP (hull-repair module) | +6% self-repair/rank | reduced power cost for hull/beam repair | +5.5% allied hull repair/rank and +3.3% self-repair/rank | +6.3% remote beam reach/rank and +4.5% allied repair/rank |
| SHD (shield generator) | +5% external shield-transfer rate/rank | improved shield-charge power efficiency | +3.5% transfer rate/rank and hardened installed generator | +5.5% external beam reach/rank, +4.2% transfer rate/rank |
| DRN (drone controller) | +4.5% drone endurance/rank and light offensive improvement | more efficient controller, longer endurance | +6.7% service life/rank, stronger fitted hardware | dedicated physical **repair drones**, +5% repair payload and endurance/rank |
| SEN (sensor array) | modest increased scan reach | research-catalog power economy | better precision | additional targeting precision and scan reach |
| ARM, ECM, ENG, FTR, MAG, RAD, REA, SIF and other non-VFX systems | Existing 6-rank technology CSV branch multipliers still adjust fitted hull, power, heat, integrity or primary subsystem effect. | Existing economics | Existing resilience/hull benefits | Existing catalog modifiers; additional unique behaviors may be developed separately |

These branch rewards use a combination of actual `Shipyard.Part` tech-derived multipliers and new `ModuleBranchEffects.Tuning`, not just shipyard labels. Laser/kinetic/plasma/particle damage, range, cycle time and firing accuracy now feed `Battle.fire`. Missile/torpedo range, cycle and per-round guidance quality do too. PD/FLK range, tracking, per-hit damage and firing cooldown feed live interception logic. The physical projectile health, speed, cross-section and signature from `ProjectileModel` remain independently tracked.

**Current aggregation limitation:** Multiple fitted guns of the same family still use one family-level firing simulation per ship, with averaged family reach/cycle/tracking values. The individual module's physical ammunition spec is averaged by weapon family in `Shipyard.Fitted`. We have **not** yet implemented a unique reload, targeting solution, firing origin, magazine or shot inventory for each individual hardpoint. The modules retain independent research branches; independent hardpoint fire control is the next required mechanical layer.

## Actual self-repair, allied repair, shield-charge and drone mechanics

| System | Activation / target | Rate and source of power | Termination and limits |
| --- | --- | --- | --- |
| **Self hull repair** | Installed REP module; damaged live hull | `selfRepairRate` per second; charge the owner for actual healed points, reactor/heat gates | Cannot heal past own maximum hull; no dead-ship revival |
| **Remote hull-repair beam** | REP module locates most damaged nearby allied ship | `remoteRepairRate` at five updates/sec, scaled by range/research; repairer expends power and heat | Does not heal enemy ships, wrecks or already-full hull; no beam if no real repair |
| **Remote shield-charge beam** | SHD module finds ally with depleted facings | `shieldTransferRate` at five updates/sec; origin pays energy/heat; target restores real directional shield zones in priority order | Cannot exceed any facing's actual capacity or bypass dead-ship constraints; shield impact cooldown still governs normal passive recharge |
| **Repair drones** | *Only* DRN modules committed to SPECIALIST generate repair-drone reserves, if a damaged allied hull is nearby | Spawn an independently moving service craft that approaches an ally, delivers finite onboard repair charge and retargets nearby damaged allies; consumes reactor power at launch | Limited reserves, fuel lifetime, repair charge, range, target health cap and existing global craft cap; can be intercepted |
| **Defense drones** | Non-specialist DRN modules | Existing hostile missile/ballistic interception movement | Separately counted from service drones, with separate reload timers; repair-specialist tech does not convert other DRN modules |

`Battle.SupportLink` is a read-only, time-limited combat effect describing actual hull repair, shield charging or service drone maintenance. `WeaponBeamSprites` renders those with the existing curated **green** (hull and service) and **blue** (shield) beam strips, with a simple line fallback only when the art is missing. No additional art was generated or requested. The renderer cannot award hit points or shield points; those come only from the deterministic simulation. Visual links exist only for successful transfers.

Existing low-level base `damageControl` and normal shield regeneration remain, so non-fitted/demo ships retain previous gameplay. Fitted REP adds active, powered self/remote hull repair; fitted SHD adds charged allied shield support. Five-hertz ally searches avoid every-frame 200v200 scanning. Services retain deterministic order based on deployed ship list and exact simulation tick.

## Test / acceptance

New `:core:supportSystemsSmoke` is chained after `:core:moduleResearchSmoke`; it checks:
- Four mutually exclusive six-rank paths for each selected weapon, PD, flak, repair, shield, drone and sensor family, with measurable per-stage differences.
- Physical fitted REP, SHD, DRN, laser beam range and cooldown changes when its own socket is upgraded.
- Mixed non-specialist and Specialist DRN controllers retain **separate** interceptor and repair drone complements.
- Self hull recovery, friendly repair-beam hull recovery, directional shield charging, actual support telemetry, and no overcharge or hostile repair.
- No support when support modules are absent, finite repair drone capacity and charged flight, and deterministic replay.

Run these as part of the existing smoke chain:
`gradle :core:supportSystemsSmoke`, `gradle :core:moduleResearchSmoke`, `gradle :core:projectileModuleSmoke`, `gradle :core:combatMachinerySmoke`, `gradle :core:massBattleSmoke`, `gradle :desktop:run` and the Android test/build suites. No new GitHub Actions workflow was added. A source commit and static checks do not certify any of these runs.

Next: individual per-hardpoint reload/targeting/ammo/controller identity, drone bay specialization UI and per-faction repair effect curation; instrument support throughput and zoom-aware sound/effects in the 200v200 firing range.
