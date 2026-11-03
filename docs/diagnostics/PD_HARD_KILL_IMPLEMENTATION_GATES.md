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
| **PD** precision laser | Real installed PD emitter family, immediate single-target beam hit attempt; projectile-specific kinetic/armor ablation; weak air-target tracking/dwell. PD-only stats are now isolated from flak modules, with genuine PD-only muzzle selection. | **Works**, but laser firing still uses **one ship-wide** `pdReload` instead of truly independent per-socket firing state. |
| **FLK** fragmentation | Real installed FLK emitter family; per-socket researched cooldown, physical kinetic-ammo charge, future `burstTick`, predicted point and finite-radius fragmentation that intersects real ordnance/craft bodies on burst. Fitted FLK no longer contributes precision laser range/accuracy/damage or fire rate. | **Working first physical differentiation**. A flak shell is currently a **deferred burst**, not yet a continuously moving/interceptable artillery body. Blast damage uses an inside-radius rule; radial fragmentation falloff and separate magazine controls remain unimplemented. |
| **RCW** rotary kinetic CIWS | Physical small solid rounds, rotational spin-up, point-target lead, finite magazine and heat; strong short-range anti-rocket/drone with a weak answer to armored torpedoes. | **Specified; not implemented.** |
| **RPD** rail interceptor | Charged high-velocity solid body, low volume of fire, projectile lead, high energy/thermal cost, armor penetration. | **Specified; not implemented.** |
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
