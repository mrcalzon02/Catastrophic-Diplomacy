# Point defense: controlled hardware, firing-method and geometry gates

**Status: validated working subset + explicit remaining work (November 2, 2026).**
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
| **FLK** fragmentation | Real installed FLK emitter family; per-socket researched cooldown, physical kinetic-ammo charge, future `burstTick`, predicted point and finite-radius fragmentation that intersects real ordnance/craft bodies on burst. Fitted FLK no longer contributes precision laser range/accuracy/damage or fire rate. | **Working first physical differentiation**. A flak shell is currently a **deferred burst**, not yet a continuously moving/interceptable artillery body. Blast damage uses an inside-radius rule; radial fragmentation falloff and separate magazine controls remain unimplemented. |
| **RCW** rotary kinetic CIWS | Physical small solid rounds, rotational spin-up, point-target lead, finite magazine and heat; strong short-range anti-rocket/drone with a weak answer to armored torpedoes. | **Implemented; 84 paired physical live/HOLD_FIRE trials and renderer compile verified.** |
| **RPD** rail interceptor | A distinct paid precharged capacitor, physical 1210-unit/s solid body, 120-unit blind zone, Doppler radar on actual closing velocity, independent target lock and 3 cartridges per shot; correct two-body swept interception in production. | **Implemented; 36 baseline and 192 geometry pairs plus 192 A/B measurements; headless combat, arena and client compile verified.** |
| **PPD** particle-beam PD | Per-target armor ablation with actual dwell and thermal saturation rather than laser-like instant damage. | **Specified; not implemented.** |
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
**four** hard-kill families are operating: precision laser PD, area FLK,
finite-kinetic-stream RCW and charged sabot RPD. PPD, PLS and IMD are still
planned and must not be counted as installed guns.

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
  not fully balanced production costs. Sustained **200v200** CPU/heap profiling
  with all four installed hard-kill families remains to do; the static
  arena-limit check is not that sustained benchmark.

[Full real-battle railgun balance report](PD_RAIL_INTERCEPTOR_MOVING_BODY_BALANCE_2026-11-02.md).
