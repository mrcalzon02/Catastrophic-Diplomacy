# Rotary CIWS: installed hardware, physical firing, sensors and measured effectiveness

**Research and combat verification:** November 2, 2026, Sitka local. **Branch:** `main`.
**Primary evidence:** [Focused rotary CIWS run 41578652780](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41578652780).
The run completed **successfully**: both `:core:rotaryCiwsDiagnostic` and
`:game:compileJava` passed, and the original machine-readable CSV and Markdown
report were uploaded in `rotary-ciws-physical-results`. This is a compile
and genuine headless simulated-fire verification, **not** an Android APK build
or a claim that every numeric balance target is met.

## Status of the seven physically separate hard-kill families

| Weapon | Operational status | Real modeled shot physics | Sensor / range behavior |
|---|---|---|---|
| PD pulse laser | Existing and in use | Independent fitted optical locks, short instant single-body pulse, researched shot damage and thermal reload | Individual researched socket range, target cross-section, weather, emitter condition and shared four-track fire control |
| FLK timed fragmentation | Existing and in use | Per-shell kinetic ammo, finite predicted flight delay, actual spatial burst and measured radial fragment falloff | Individual researched detection/servo profile, sensor condition, motor heat signature, craft-first when enabled |
| **RCW rotary kinetic CIWS** | **New, in use and core + client compiled** | Individually spooled mechanical barrel; moving inertial bullets with swept physical collisions; per-shot ammo/recoil, independent researched tracking | **Separate fitted close-in range**, short radar/signature gate; target leads based on live velocity, reactor and sensor condition |
| RPD rail interceptor | Planned, **not installed** | Requires charged high-speed solid penetrator, long between-shot interval, physical lead and narrow intersection | Proposed long-range (120–335), point-blank blind spot, strong hard-target profile |
| PPD particle-beam PD | Planned, **not installed** | Requires continuous/long dwell and paid energy/thermal ablation, not a pulse laser reskin | Proposed 90–275, precision hardened-target scan |
| PLS plasma burst | Planned, **not installed** | Requires short expanding energy/volume, physical radial/falloff and significant heat | Proposed 20–115, broad close-range area |
| IMD interceptor missile | Planned, **not installed** | Requires actual launched guided counter-missile, finite expensive magazines, flight/arming radius and seeker response | Proposed 180–480 with ~70-unit minimum arming range |

Do **not** treat this table as seven working weapons: three are active; four remain future work. ECM, decoys/chaff, defensive drones/fighters and reactive armor are separate systems.

## RCW native research and fitted installation

- `TechnologyCatalog` appends genuine `BASE-<faction>-RCW` components and four independent `OUTPUT`, `EFFICIENCY`, `RESILIENCE`, `SPECIALIST` research chains (levels I–IX, with VII–IX generated on demand) for the eleven supported native factions. The original base technology files and existing component IDs remain intact. Research branch tuning now recognizes `RCW`; it does **not** share a laser's module count or receive a phantom flak emitter.
- `Shipyard` only allows the purchased new weapon on real weapon hardpoints, computes a separate `RCW` research-aware profile, independent firing cycle and nominal close-in range; `ShipDamageTargeting` locates its physical point-defense subsystem for damage/repair.
- `WeaponMuzzles` maps RCW to `ROTARY_CIWS` and the exact hull-art socket. Live `Battle.Ship.rotaryMounts` contains independent mount slot IDs, rank, reload, barrel spin, servo orientation, tracking/acquisition counters and research-generated profile. The pure-RCW fitted ship has **zero** phantom PD laser or FLK modules.
- A bare hull still has its intrinsic engine, reactor, cooling and base finite stores; RCW draws actual kinetic ammunition from those stores. One barrel shot spends one cartridge plus .44 reactor power and .52 heat, with a small additional electrical/thermal servo draw. Magazine hits can destroy additional stored ammunition, which the report counts as store depletion **separately from rounds fired**.

### Actual measured implementation constants

| Property | Physical implementation |
|---|---|
| RCW nominal fitted sensor range | `min(155, 35 + fittedEffect * 2.7 * fittedSpecialistReach)` world units; base Terran is approximately 100, increasing with purchased specialist research toward 155 |
| Minimum permitted RCW engagement distance | 16 world units (does not fire its barrel into point-blank sensor contacts) |
| Sensor acquisition | `ProjectileModel.acquisitionRange` from **this RCW mount** using radar signature/cross-section, current thrust emission, equipped tracking/detection multiplier, sensor condition, weather and the observer's actually fitted radar/acuity; unpowered sensor array produces no active tracks |
| Targeting doctrine | Honors BALANCED / INTERCEPT_ONLY / ANTI_CRAFT / HOLD_FIRE and captain EARLY_INTERCEPT / CLOSE_IN / FASTEST_IMPACT, with actual protected-ship scope |
| Rotary spin-up | Active contact increases spin by `2.7/s`; first shot requires at least 0.40 spool; spin falls while idle |
| Mechanical slew | Real heading with bounded servo rotation proportional to this mount's researched tracking, fire requires <=0.085-radian lead error |
| Projectile speed | **705 world units/s**, constant heading and velocity after discharge, life limited by the real fitted socket reach |
| Turret shooting cycle | `max(.054, .118 * fittedCycle / pointDefenseCondition)` seconds, independent per mount |
| Flight and impact | Every round in `Battle.rotaryRounds()` has its real world position, velocity, source, mount ID, life and damage. It moves each 30 Hz simulation frame and uses the **earliest swept segment collision** with an enemy missile, rocket, ballistic shell or craft. It disappears after that single hit or upon physical range expiration |
| Damage | Small kinetic penetration, decreasing continuously with warhead hardening; drone/fighter hulls use real `hitCraft` armor absorption. No randomized per-target free kill and no multi-target flak explosion |
| Bounded active projectile population | 2048 globally; additional guns must wait rather than invent a shot when this cap is reached |
| Live visual implementation | Separate authored kinetic turret cell, individual socket bearing, small firing/impact atlas effects, actual moving kinetic tracer rendered at simulated coordinates, not an instant line to a target |

The sensor calculation is the same established physical environmental/signature
engine used by PD, **but its range, mount state, ammunition, projectile motion,
effectiveness and research are separate**. The exact common weather and signal
constants are not claims of a separate radar mode or new global sensor subsystem.

## Measured 60 paired physical trials: three attack seeds each

Four-defense comparisons equip four **real RCW modules** at the stated research
tier, versus the same seeded attack and an equivalent fitted HOLD_FIRE ship.
This is a focused close-range intercept fixture; the missile/torpedo cases
launch three physical guided bodies, kinetics six, and 216 combined physical
rockets per rocket case. All numbers below are counts for the stated trials;
they are **not final economic balance parity estimates**.

| Attacking weapon | RCW tier and mounts | Launched | Physical RCW shots | Real intercepted | Attacks hitting protected hull | HOLD_FIRE hits |
|---|---|---:|---:|---:|---:|---:|
| Missile | base, 1 | 3 | 6 | 3 | 0 | 3 |
| Missile | base, 4 | 3 | 12 | 3 | 0 | 3 |
| Hardened torpedo | base, 1 | 3 | 15 | 0 | 3 | 3 |
| Hardened torpedo | base, 4 | 3 | 24 | 3 | 0 | 3 |
| Hardened torpedo | IX, 1 | 3 | 15 | 3 | 0 | 3 |
| Kinetic round | base, 1 | 6 | 3 | 2 | 2 | 4 |
| Kinetic round | IX, 4 | 6 | 24 | 6 | 0 | 5 |
| Boost/coast rockets | base, 4 | 216 | 64 | 43 | 161 | 195 |
| Sustained-burn rockets | base, 4 | 216 | 113 | 88 | 111 | 195 |
| Boost/coast rockets | IX, 4 | 216 | 100 | 45 | 158 | 195 |
| Sustained-burn rockets | IX, 4 | 216 | 181 | 109 | 95 | 195 |

**Interpretation, not a target value:** RCW provides a fast paid kinetic stream,
much better at close standard guided threats and rockets than at a single
very hardened torpedo with only one base turret. Under the test's physical
approach geometry, sustained-burn rockets are exposed to the close-range
kinetic stream for longer than boost/coast rockets. Research IX can make an
expensive single RCW capable of repeatedly damaging a heavy body, but multiple
mounts still burn ammunition to do so. Fighter/drone roles and full 200v200
CPU pressure **have not** been measured in this new focused diagnostic yet.

### Mechanical validation checks

- All eleven faction base RCW components exist; research stage I/III/VI/IX resolves for each.
- One and four mounted RCW sockets exist in battle exactly as installed.
- Pure RCW loads do not obtain hidden PD beams or FLK.
- Stage IX increases separate RCW tracking, range remains bounded and sensor loss/depleted power reduce active detection.
- Target type hardening reduces a rotary bullet's direct damage without hard-coded immunity.
- Every shot originates at a purchased RCW world-space muzzle. In-flight rounds are sampled every frame, or a same-frame physical collision is recorded. One cartridge is deducted per shot; collateral ammunition-magazine damage is accounted separately.
- `HOLD_FIRE` discharges no RCW ammunition and has no RCW shot events.
- Java 17 core combat and client visual source **both compile** under CI; no full APK, distribution, or asset regeneration is required.

### Next tests / remaining family implementation gates

1. Add RCW's intended **defensive drones and aircraft** to the same real-lifecycle A/B test (carrier/drone spawn, relative speed/lead, armor, shots and damage prevented). Include simultaneous mixed threats and protected allied ships.
2. Test `RCW` across III/VI and other factions, power-starved emitters, sensor damage, heat saturation, magazine depletion, three distances, flanking/crossing arrival, and mixed `PD + FLK + RCW` fitted batteries. Stress allocation/sensor work at 200v200, and compare CPU and heap against the previous missile/rocket baselines.
3. Add `RPD` **next** as an entirely separate railgun charged-solid-penetrator controller (minimum bore distance, paid large power, physical fast narrow round, and long charging reload). Do not borrow rotary spin-up/ammo cadence.
4. Add `PPD` as sustained single-target particle-beam armor ablation with expensive power/thermal dwell; `PLS` as expanding near-field plasma burst with density falloff and heat; `IMD` as real seeker-guided, stock-paid counter-missile with actual minimum arming distance and decoy vulnerability. Only call each operational once it has a fitted source, live shot, renderer and passing physical/visual compile tests.
5. Preserve existing PD and FLK functional behavior and true tracking profiles; never relabel them as the above families. Continue equal-stage, IX/base, base/IX and measured incoming impact/production-cost normalization after each distinct weapon is verified.

**Release policy:** Objective build/launch/run, save/load, required network/update
functionality can block releases. Cosmetic validation, performance-tuning
targets, suppression ratios, visual taste and target kill-rate bands remain
advisory. All physics work here is headless; the compiled renderer check did
not package or distribute an APK.
