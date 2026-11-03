# Point defense: controlled hardware, firing-method and geometry gates

**Status: validated working subset + explicit remaining work (updated November 3, 2026).**
This is an implementation ledger, not a new all-defense redesign. It covers
**hard-kill point-defense gun modules only**; ECM, decoys, defensive fighters and
reactive armor are independent equipment categories.

## References and actual completed tests

- Original design roster:
  [seven hard-kill family plan](../POINT_DEFENSE_EXPANDED_ROSTER_AND_BALANCE_PLAN.md).
- Intended full 7-defense/46-threat stage-0/IX tests:
  [planned cross-tier CSV](pd_intended_cross_tier_test_plan.csv).
- Before specialization: [run 41551408398](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41551408398).
- Initial finite-flight FLK and P0 remeasurement:
  [run 41554352157](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41554352157).
- Four/eight simultaneous real-fighter baseline:
  [run 41559351484](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41559351484),
  360 valid ON/OFF pairs.
- Mixed batteries with separate fitted PD/FLK identities and cycles:
  [run 41560487545](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41560487545),
  432 valid ON/OFF pairs. Permanent scenario IDs restore same RNG across
  repeated battery configurations. This is essential to valid A/B conclusions.
- Compact 10-unit vs dispersed 46-unit carrier lateral spacing with **identical
  initial RNG seeds**:
  [run 41561038432](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41561038432).
  528 paired ON/OFF conditions, zero validation errors; the
  `weaponMuzzleSmoke` and wave task both passed. **All 144 compact
  aggregate results reproduce the previous fixed-seed compact baseline.**
- [Machine-readable 176-row geometry comparison](pd_flak_geometry_run_41561038432.csv),
  compact 144 rows + targeted dispersed 32 rows, 3 actual seeds per row.
  Full raw individual-trial CSV is in the workflow artifact, retained 30 days.

## Distinct defensive methods: actual completion audit

| Type | Current physical combat method | Status |
|---|---|---|
| **PD** precision laser | Real installed PD emitter family, immediate single-target beam hit attempt; projectile-specific kinetic/armor ablation; weak air-target tracking/dwell. PD-only stats are now isolated from flak modules, with genuine PD-only muzzle selection. | **Independent socket controller committed; diagnostic runner pending.** Per-socket research range, hit profile, dwell and reload replace the pooled fitted laser controller; legacy unfitted fixtures keep `pdReload`. Shared track caching and throughput measurement remain open. |
| **FLK** fragmentation | Independent mounted 390-unit/s inertial shell, actual world-space muzzle-to-fuze flight, finite world/lifetime bounds, paid physical ammunition, and radial fragment-density damage against current hostile ordnance/craft at true arrival. Fitted FLK does not contribute precision-laser range, damage, or firing rate. | **Travel and burst physics verified November 3.** In-flight shells have independent positions and authored kinetic sprite rendering. Enemy interception of flying flak shells and distinct on-mount magazines remain open. |
| **RCW** rotary kinetic CIWS | Physical small solid rounds, rotational spin-up, point-target lead, finite magazine and heat; strong short-range anti-rocket/drone with a weak answer to armored torpedoes. | **Implemented; 84 paired physical live/HOLD_FIRE trials and renderer compile verified.** |
| **RPD** rail interceptor | A distinct paid precharged capacitor, physical 1210-unit/s solid body, 120-unit blind zone, Doppler radar on actual closing velocity, independent target lock and 3 cartridges per shot; correct two-body swept interception in production. | **Implemented; 36 baseline and 192 geometry pairs plus 192 A/B measurements; headless combat, arena and client compile verified.** |
| **PPD** particle-beam PD | Real, separately purchased optical mount with 90–275-unit sensor envelope, servo and continuous target lock, frame-by-frame energy-backed armor ablation, per-hull power/heat and mount-local thermal quenching. Target loss resets dwell; physical missiles, torpedoes, rockets, shells and aircraft can be damaged. | **Implemented and tested November 3.** Base plus four researched branches through stage IX in all eleven factions. Same-seed HOLD_FIRE, direct launcher fixtures, equal-tier mounted attackers and moving geometry verified; authored particle muzzle and independent turret render paths. |
| **PLS** plasma burst | Short-range physically expanding/decaying thermal volume; strongly range-limited and susceptible to fast objects. | **Specified; not implemented.** |
| **IMD** guided counter-missile | Costed ammunition, finite-launch weapon and genuine seeker/arming/propulsion/flight stages; heavy incoming guided bodies are its intended focus. | **Approved and specified; not implemented.** |

No new equipment may be described as shipped or tested merely because a roster
code is present in this specification.

## Isolation and reproducibility invariants now tested

1. A 2-PD/2-FLK ship has the **same calculated precision laser range,
   penetration, tracking, sensor multiplier and firing-cycle bonus** as an
   identically researched 2-PD-only ship. Adding flak does not boost lasers.
2. FLK-only mounted ships do **not** receive ghost precision lasers.
3. PD and FLK fire events originate from their **own real sockets**.
   `WeaponMuzzles.atSlot()` rejects using an FLK slot as a PD laser muzzle.
4. Flak pays one real ammunition unit per physical burst, not one unit per
   body hit. HOLD_FIRE emits neither PD nor FLK fire.
5. Four or eight **real FTR aircraft** launch in the same Battle simulation,
   rather than eight separate one-fighter trials.
6. Aircraft destruction, incoming aircraft weapon shots, impacts, torpedo
   launches, terminal passages, defender damage and flak rounds remain separate
   telemetry. Eventual kills are not automatically damage prevention.
7. Five original battery identities use fixed numeric seed codes. Adding
   experimental variants cannot change old tests' random seeds. Geometry
   A/B intentionally uses the same starting seed, changing only position.
8. Numerical balance targets and visual quality are **advisory**. Objective
   build/launch/runtime/update/multiplayer failures are functional gates.

## Verified geometry sensitivity; base FLK x1, eight bomber wave at 95 units

Across 3 matched seeds, 24 real bomber aircraft and 72 possible torpedo
impacts:

| Measure | Compact | Dispersed |
|---|---:|---:|
| Fighters destroyed | 24/24 | 18/24 |
| Physical torpedo impacts | 24/72 | 42/72 |
| Actual flak rounds paid | 16 | 82 |

Dispersal severely increases flak ammunition expenditure and terminal leaks,
yet compact groups remain vulnerable to area fragmentation. Preserve that
counter relationship; **do not globally nerf flak solely to reduce eventual
single-aircraft kills**.

## Next implementation gates — do not skip

**Gate A — precision CIWS mount identity and real throughput**

- Move fitted PD beam reload/target state from ship-wide `pdReload` to
  independently installed PD mounts, while retaining a **legacy simulation
  fixture path** for manually constructed unfitted old tests.
- Cache the bounded hostile threat tracks **once per hull per update**, not
  once for every emitter. Deconflict expected target durability and
  imminent strike timings to prevent four mounts gratuitously shooting the
  same already-suppressed body.
- Each mount pays its own actual power/heat/reload charge; per-mount
  stage/track and physical slot determine its parameters.
- Prove 1-v-2-v-4 PD scaling under single/surge/sustained loads. Ensure
  zero-module hulls fire no imaginary defense; keep 200v200 performance
  diagnostics separate.

**Gate B — finish flak physics without losing counter role**

- Track finite shell flight position/velocity/airburst and physical arrival
  rather than a teleporting scheduled `aimX/aimY` explosion.
- Model fragment density/damage declining with radial distance and target
  exposed area, retaining blast effectiveness against grouped aircraft.
- Bound target reservation, ammo consumption, hazard/arc safety and multiple
  flak-mount saturation. Never combine flak reload with laser PD tech.
- Re-measure compact/dispersed and multi-axis craft waves, escorted bomber
  attacks and finite stock. Avoid a design where flak hits all projectiles
  regardless of hardness or high closing speed.

**Gate C — implement other hard-kill technologies one real mechanism at a time**

- RCW then RPD (real separate rounds); PPD then PLS (distinct beam dwell and
  physical thermal volume); IMD (guided counter-missile with paid launch stocks
  and min-arming separation). Add purchasable parts **after** each mechanism
  can be tested and rendered. Preserve original save IDs.

**Gate D — stage parity and fleet validation**

- All four attack/defense research matchups: 0/0, IX/IX, IX/0 and 0/IX,
  with **genuinely researched attacking launchers**.
- Full 46-threat × fitted defense family matrix, 1/4 mounts, mixed screens,
  near/mid/far engagement, real salvos, finite ammo/power/heat.
- Report raw physical launched/cohort identities, preterminal kills,
  90-unit boundary breaches, eventual kills, impacts, actual shield/armor/hull
  damage avoided, ammunition spent, thermal stress, reactor limits and
  defense credits per credits protected.
- Isolate 200v200 integration/performance from the quick no-packaging
  point-defense diagnostics.

**Release discipline:** Never run a full APK/desktop package build just to
adjust balance. No balance percentage or art test may block publication.

## Current precision-emitter change: runner-verified subset

Fitted precision CIWS now has independently ticking, socket-bound cooldowns, researched
profiles and weapon muzzle locations for both physical ordnance and hostile craft.
The four-mount fit no longer inherits a single accelerated ship-wide controller;
legacy manually constructed simulation fixtures retain their prior behavior. The
fighter-wave CSV adds `pd_distinct_sockets_fired` and asserts that each observed
laser event originates at a purchased PD socket. Installed socket inventories and
independent timer progression have functional assertions at stages 0 and IX.

**Still incomplete:** bounded hull-shared hostile-contact acquisition, deconflicted
per-target predicted damage, true saturation throughput measurement against
kinetic/rocket/missile salvos, and optional 200v200 load profiling. The existing
wave diagnostic remains advisory and does not build an APK. Do not assert new
interception percentages or stage parity until GitHub runner artifacts are read.

**Runner readback:** [wave 41562569048](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41562569048) completed 528 valid pairs and zero errors; [comprehensive 41562569060](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41562569060) completed 768 rocket configurations, 736 additional physical-threat configurations and 256 timed-load valid pairs. The same-stage late-game 4v4 barrage is now nearly erased, so physical tracking/slew and early terminal-ring leakage need study **before numerical tuning**. See [the measured precision-mount review](PD_PRECISION_MOUNT_REVIEW_2026-11-02.md). Keep all balance advisory.

## Gate C update: researched RCW now a verified third hard-kill family

**This section supersedes the earlier historical "RCW next" status.**
The previous Gate A separate PD mount identities, optical tracking/slew,
four-channel scheduling and expected-damage deconfliction are operating.
The hull-wide cached hostile sensor scan is **still pending**. Gate B's
real radial fragment-density model is active, while flak's continuous
individual shell flight remains unfinished.

- **RCW installed and firing:** all eleven native factions have genuine
  base `RCW` weapon modules and exclusive nine-stage OUTPUT/EFFICIENCY/
  RESILIENCE/SPECIALIST research, without rewriting old IDs. Shipyard
  sockets and Battle contain separate `ROTARY_CIWS` emitters, not laser or
  FLK aliases. An installed RCW has its own nominal close-in sensor range,
  radar/signature/weather/condition gate, tracking profile, spin-up, servo
  bearing, discrete ammo feed and short firing cycle.
- **RCW actual physics:** each 705-units/s bullet is an inertial projectile
  drawn at its world-space position. Swept 30-Hz collision chooses its first
  actual hostile rocket, guided missile, physical shell or craft intersect.
  One spent cartridge, power and heat are charged per firing. Armored
  torpedoes absorb more small-kinetic damage; aircraft armor goes through the
  real `hitCraft` path. Neither a synthetic laser beam nor an instant
  fragmentation pulse can count as an RCW interception.
- **Visual integration:** using authored turret/kinetic atlas art, the fitted
  turret pivots on its own servo bearing and the projectile is rendered at
  its live position. No placeholder or reconstructed hit tween is added.
- **Runner evidence:** [focused RCW 41578652780](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41578652780)
  completed with BOTH `:core:rotaryCiwsDiagnostic` (60 live/HOLD_FIRE
  trial pairs) and `:game:compileJava` passing. The machine-report artifact
  contains true physical launches, paid shots, interceptions, incoming
  impacts, and ammunition-store depletion. The optional workflow now
  correctly fails on a functional compile or gun-test error; numerical
  balancing remains advisory. No APK or desktop release packaging.
- **RPD milestone completed**: real separate charged railgun, three paid
  cartridges, mechanical lead, physical projectile and meaningful pulse-
  Doppler detection are verified. Keep the same target-class/fleet-scale
  scrutiny for the next **PPD** sustained particle ablation method.
  PLS/IMD remain unimplemented.

[Complete RCW engineering and measured target report](PD_ROTARY_CIWS_PHYSICAL_IMPLEMENTATION_AND_TARGET_EFFECTIVENESS_2026-11-02.md).

## Gate C verification update: physically correct moving-body rail interception

The historical statements above describe intermediate states. As of the
[latest production RPD validation](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41604234413),
**four** hard-kill families were operating at that historical checkpoint:
precision laser PD, area FLK, finite-kinetic-stream RCW and charged sabot RPD.
**Subsequent November 3 validation adds PPD as the fifth implemented family.**
PLS and IMD are still planned and must not be counted as installed guns.

- RPD has its own paid fitted technology and high-speed 1210-unit/s narrow
  projectile; independent rank-aware long-range sensor, pulse-Doppler gate
  from **actual radial approach**, capacitor/target lock, servo traverse,
  minimum 120-unit engage distance, 3 paid kinetic rounds per launch, heat
  and lengthy reload.
- Real penetrators persist until impact, world edge or timeout; predicted
  target damage is reserved until that actual projectile exits, rather than
  simultaneously wasting four charged sabots on one light target.
- Incoming missiles, rockets and shells now record physical start-of-tick
  coordinates; production RPD compares both true world-space movement
  segments. Laboratory-only legacy end-tick intersection remains an
  explicit A/B control. This resolves a reproducible **0/4 to 4/4**
  research-VI head-on missile interception defect without any damage,
  chance-to-hit, ammunition, range or sensor bonus.
- Verified [initial 36 paired physical RPD trials](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41600726201),
  [192 mid-tier range/off-axis pairs](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41603400426),
  [192 same-seed moving-vs-endpoint A/B pairs](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41603953046),
  [production RPD plus fixed-arena and visual compilation](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41604234413).
- These tests confirm physical role fidelity and measured contact geometry,
  not fully balanced production costs. A subsequent 200v200 focused load
  diagnostic now exists; its 20-second trial is documented under Gate D below.
  The static arena-limit check alone was not a sustained benchmark.

[Full real-battle railgun balance report](PD_RAIL_INTERCEPTOR_MOVING_BODY_BALANCE_2026-11-02.md).


## Gate D verification update: genuine four-family 200v200 differentiated load

**Verified:** [full Java17 rail + 200v200 A/B + renderer CI run 41607457278](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41607457278);
[earlier source-attribution run 41606914482](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41606914482);
[RCW physical-mechanics correction 41607196653](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41607196653).
Headless measurements only; no APK built, no subjective threshold promoted to a release gate.

The optional `:core:pdFleetScaleDiagnostic` was added to the **existing**
physical rail CI job as a **nonblocking** performance/balance step. Its
real combat setup has 200 attacker hulls (50 each of fitted MSL, TOR, KIN,
siege rocket) and 200 defender hulls (50 fitted PD laser, FLK, RCW, RPD).
Each same-seed ACTIVE/HOLD_FIRE pair runs 600 ticks at 30 Hz. Two
controlled conditions are reported because a bare dreadnought includes
intrinsic fighter capacity: **MIXED** retains the default baseline
fighter wings; **ORDNANCE_ONLY** zeroes just fighter/drone reserves before
combat on both teams, leaving the real gun sockets, ammunition, sensors,
orders, and attacking launchers intact.

| Condition | Real intercepted ordnance | Real craft destroyed | Defender hulls alive ACTIVE / HOLD | Net defense protection loss ACTIVE / HOLD |
|---|---:|---:|---:|---:|
| MIXED | 388 | 372 | 114 / 77 | 163217.51 / 241839.26 |
| ORDNANCE_ONLY | 324 | 0 | 111 / 104 | 161915.55 / 182166.16 |

The isolated ordnance-only kill attribution is **per physically removed
hostile body**, not per shot or successful hit roll:

| Real hard-kill source | Missiles | Torpedoes | Rockets | Kinetic shells | Sum |
|---|---:|---:|---:|---:|---:|
| PD laser | 8 | 12 | 0 | 1 | 21 |
| FLK | 6 | 3 | 1 | 1 | 11 |
| RCW | 1 | 20 | 179 | 78 | 278 |
| RPD | 0 | 6 | 5 | 3 | 14 |
| **Total** | **15** | **41** | **185** | **83** | **324** |

These figures support **role differentiation in the measured scenario**:
FLK is decisively the anti-craft volume weapon in MIXED (363 physical
craft kills credited to FLK), rotary is the principal close-in
rocket/shell screen in ORDNANCE_ONLY (179 rockets and 78 kinetic shells),
and the slow charged railgun produces a small number of individual
physical hardened-body intercepts. This does **not** yet establish an
equal-cost dominance ranking, balancing of research rank I-IX, or
statistically robust performance across formation geometry.

The diagnostic fixes a misleading ammunition distinction: in the
ordnance-only ACTIVE trial, **2756** kinetic cartridges were paid for
actual FLK/RCW/RPD firing, while **3647** net kinetic inventory was
depleted; the residual **891** reflects other stock effects, including
combat magazine losses. The corresponding HOLD_FIRE had **zero**
defensive shot ammunition yet lost **999** stock. Per-gun firing and
kill attribution obey objective accounting invariants. The production
`breachStores` process destroys inventory after real penetration,
so total store depletion cannot be called 'PD ammo fired'.

The two ordnance-only conditions retained a peak of 881-891 active
physical projectile bodies, with no craft artificially reintroduced.
The headless ACTIVE simulation took 2.47s wall time versus 2.10s in
HOLD_FIRE on that runner, but these are **not** isolated PD CPU timings:
combat outcomes and world-body counts change between runs. Runner heap
and timing do not demonstrate Android frame rate or a complete 200v200
campaign battle. A future sustained full-battle time profile and
research/cost-parity geometry matrix are still necessary.

A recent RCW test incorrectly required a spinning kinetic CIWS to fire
in **every** tiny, fast KINETIC encounter even where physical sensor
contacts were too fleeting for mechanical slew/spin-up. The test now
reports zero-fire contacts as advisory, still verifies weather/sensor
damage, real mount legitimacy, paid cartridges and actual projectile
flight, and requires at least one real shot across the full threat
matrix. This preserves the documented intention that RCW is weaker
against fast tiny solid shells without concealing actual failures.

The next unimplemented hard-kill families are **PLS** short-range thermal
area denial and **IMD** paid guided counter-missiles; PPD's separate
powered sustained-beam path is implemented and documented below. Flak shell **flight** is now implemented;
hostile interception of flying flak bodies, per-mount magazines and
off-axis lead analysis remain separate open work. Fleet sensor
sharing/fusion/capacity is an independent module concern, not automatically
part of PD.


## Gate B follow-up: live physical flak shell flight (November 3, 2026)

**Production implementation commits:** [world-space flight](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/082c870f096e9a9f22f49bae122d854643e6d14e),
[in-flight authored-kinetic rendering](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/fcdf3a4e3a28e5574d88a7655ecba3b780db34dc),
[wave trajectory assertions](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/43b7ee0d8e39978c3a2e9ba955807411fd2ede17),
[arena lifetime checks](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/78cbb9cf2abf8537425d595ac6baca046c282794).

A purchased FLK socket now launches **one physically moving unguided
390-world-unit/s shell** from `WeaponMuzzles.atSlot` to a fixed
predicted airburst/fuze point. Its `x/y/vx/vy`, launch tick, original
muzzle, target aim, remaining travel and finite lifetime persist in the
Battle simulation. The shell changes world position each 30-Hz tick,
cannot expand the fixed 3840×2160 battle arena, and is removed if it
leaves the world or exceeds the 3-second PD lifetime. **Only physically
reaching the original fuze point produces fragmentation**, using the
already implemented radial fragment-density/armor-exposure model.
The projectile is now visible using the original kinetic atlas; it is
not a decorative fire-to-target tween. Prior static scheduled `burstTick`
explosions no longer cause damage.

**Verified focused Java17 runner evidence (no APK packaging):**

- [528 measured simultaneous-fighter ON/HOLD_FIRE pairs; zero validation
  errors](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41612688509).
  Asserts physical travel velocity, paid muzzle identity, finite flight,
  no duplicated or premature bursts, plus all historical real-aircraft
  attacks. This tests compact and dispersed formations, four/eight
  simultaneous craft, both fighter classes, one/four/mixed batteries,
  two defense ranks and three seeds per condition.
- [Rotary/arena/renderer CI passed](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41612745201),
  including actual flak lifetime/edge removal among six live projectile
  families and compiled live flight renderer.
- [Charged rail, arena and 200v200 load validation passed](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41612745217),
  including the new FLK shell physics with the existing PD/RCW/RPD families.

**Measured flak-vs-fighter-wave behavior**, comparing matching aggregate
rows from the last validated pre-flight 528-pair run
[41604234379](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41604234379)
against the first post-flight run [41612688509](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41612688509):

| Aggregated compact scenario | Fighters killed before → after | FLK shots before → after | Incoming attack hits before → after |
|---|---:|---:|---:|
| INTERCEPTOR, FLK ×1 | 216 → 216 | 204 → 210 | 347 → 357 |
| INTERCEPTOR, FLK ×4 | 216 → 216 | 452 → 492 | 168 → 183 |
| TORPEDO_BOMBER, FLK ×1 | 144 → 144 | 338 → 338 | 360 → 360 |
| TORPEDO_BOMBER, FLK ×4 | 144 → 144 | 1216 → 1216 | 360 → 360 |

Rows aggregate multiple 3-seed wave/range/tier cases, not a single
216-aircraft simultaneous engagement. The grouped figures are not
cross-target effectiveness percentages. They show that moving FLK shells
retain the intended anti-craft role while small differences in arrival
timing influence ammunition efficiency and earlier attack impacts.
**Do not automatically tune away these differences.**

**One maximum-fleet ordnance-only check** from run 41612745217 recorded
343 physically destroyed ordnance with active defense versus zero
with HOLD_FIRE, and 117 defender ships remaining versus 104
with HOLD_FIRE. The same-seed test measured 1,861 FLK shots and
2,849 paid total defensive cartridges; ship magazine damage remains
tracked separately. This is 20 simulated seconds on the GitHub runner,
not a sustained mobile-framerate guarantee or a complete campaign match.

**Remaining FLK work:** independent shell interception by incoming
hostile point-defense hardware, mount-specific magazine reserves, 
friendly/neutral blast exclusion if the rules require it, moving-ship
relative aim and more equal-cost off-axis wave geometry. Preserve the
existing real fragment-density physics. Numeric balance, visual finish
and economic goals remain advisory; only real functionality may block.

### Corrected live-flak population accounting

[Focused rail + corrected fleet telemetry 41613927278](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41613927278)
now includes actual **in-flight flak shells** in peak projectile population;
older fleet reports counted the other five ordnance/interceptor families
but omitted moving FLK shells. This corrects the diagnostic, not the
production battle size or a weapon balance parameter.

| 200v200 mode | Peak ALL moving bodies (including FLK) | Peak concurrent FLK shells | Physical fuze bursts | Flak timeouts | FLK shells launched |
|---|---:|---:|---:|---:|---:|
| MIXED ACTIVE | 1082 | 49 | 1675 | 0 | 1704 |
| MIXED HOLD_FIRE | 1056 | 0 | 0 | 0 | 0 |
| ORDNANCE_ONLY ACTIVE | 910 | 43 | 1836 | 0 | 1861 |
| ORDNANCE_ONLY HOLD_FIRE | 881 | 0 | 0 | 0 | 0 |

The launch-minus-resolved differences (29 mixed and 25 ordnance-only)
are permitted in-flight shells still present when the 600-tick sampling
window ends, not phantom hits or a lifetime leak. All six projectile
families still obey the fixed arena and timeouts; the linked headless
suite passed mechanics, world smoke, load measurement and game-client
renderer compilation. No full release/APK build was triggered for this
point-defense change. Timing and memory differences remain diagnostic,
not release gates.


## Gate C follow-up: independently purchased particle ablation (Nov 3, 2026)

**PPD is the fifth implemented defensive gun family, not the existing
offensive `PAR` weapon and not a rebranded pulse laser.**

- Eleven independently purchasable `BASE-<faction>-PPD` modules,
  each with OUTPUT / EFFICIENCY / RESILIENCE / SPECIALIST research
  through stage IX (upgrades VII–IX remain lazily synthesized).
  The old installed module IDs are preserved.
- `Shipyard.defenseAt/pointDefenseRangeAt/pointDefenseCycleAt` recognize
  PPD independently without adding any pulse-laser PD range, rate or damage.
  Each fitted PPD owns its exact physical `PARTICLE_PD` hardpoint and its
  own observed target identity, traverse bearing, acquisition lock,
  uninterrupted dwell and local thermal reservoir.
- A contact must remain optically acquired across successive real ticks.
  Switching to another incoming physical body, losing sight or losing power
  removes accumulated target exposure. No percentage-based forced miss or
  instant-kill event is substituted for physical elapsed beam time.
- While emitting, **each turret spends 9 power units/sec and produces 11
  hull-heat units/sec** as well as its independent local heat. Hardened
  target casings, accumulated dwell and mount temperature determine actual
  per-second ablation. The mount's heat trip now has hysteresis:
  it must physically cool before firing again, rather than oscillating
  on/off every tick at the overheat threshold.
- True missiles, torpedoes, rockets, kinetic rounds and aircraft lose
  their persisted integrity only for actually powered beam frames.
  Destruction increments the appropriate real-body interception counter
  once. `HOLD_FIRE` produces no beam, expenditure or defensive kills.
  Kinetic ammunition is not borrowed from RCW/RPD/FLK magazines.
- `Battle.Beam` carries the genuine particle type and muzzle/target
  endpoints. The original particle projector turret art rotates with
  its own live bearing; the render path uses compact authored particle
  muzzle artwork and the existing violet particle beam. Cosmetic
  brightness and balance observations are never release gates.

**Mechanical and UI compilation verification:**

- [Initial 24 A/B physical trials and Java17 graphical compilation
  succeeded](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41680272989).
- [Expanded 24 initial + 48 rank-matched approach A/B pairs passed](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41680995537),
  including a physical radiator cooldown lockout, real stage-matched fitted
  attacking launcher families and moving 72-unit flank geometry.
  A/B means a deterministic ACTIVE and `HOLD_FIRE` pair.
- [Dedicated focused workflow](https://github.com/mrcalzon02/Catastrophic-Diplomacy/blob/main/.github/workflows/particle-pd-diagnostic.yml)
  uses only `:core:particlePdDiagnostic`, `:game:compileJava`,
  machine-readable CSV and advisory numerical reports. No full APK or
  desktop package is part of the PPD regression.

**Initial baseline (not research parity):**

| Incoming | PPD stage 0, one mount | PPD stage IX, one mount |
|---|---:|---:|
| Stock missiles removed | 10 / 10 | 10 / 10 |
| Stock torpedoes removed | 4 / 6 | 6 / 6 |
| Stock kinetic shells removed | 0 / 38 | 29 / 38 |

These baseline numbers are aggregates over two deterministic seeds per
condition and deliberately contrast IX defense against **stock**
projectiles, not IX vs IX. Such results do not imply universal PPD
dominance or justify a blanket nerf. The separate rank-matched moving
scenario CSV is needed before counterbalance tuning. PPD remains an
optical single-target beam rather than area fragmentation or guided
interceptor ammunition; saturation, side angles, power exhaustion,
fitted cooling/electronics costs and equal-tier target hardness remain
the principal next balance evaluations. Fleet sensor-network modules
continue to be a separate system, not free implicit PPD upgrades.

**Remaining hard-kill families:** PLS expanding/decaying plasma volume,
then IMD guided, physically launched and properly costed counter-missiles.
