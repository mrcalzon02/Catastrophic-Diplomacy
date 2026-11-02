# Rocket barrage versus concentrated point defense — calibration contract

**Goal:** at the *largest physical firing program* (16 dumbfire rockets per
fitted rack), a massed barrage should get **roughly one-third of its launched
rockets into the inner defense zone of a concentrated, comparably
researched PD formation**. Track ordinary non-interception separately so a
wide miss cannot count as a successful penetration. This is a
combat-balance goal, **not a release-blocking functional requirement**.

## Measurement definition

- **Interception:** a real rocket body destroyed by an actual live PD weapon
  or defensive craft. Intercept events and counts are authoritative.
- **Escaped PD:** launched minus intercepted, measured **only after all
  bodies from the first physical barrage have either struck a target or
  timed out**. A rocket that misses an evasive target can still have escaped PD.
- **Terminal-zone penetration:** the unique rocket body reaches within
  **90 world units** of the defended ship while still in flight (or scores a
  real hull impact). It is counted once using immutable physical projectile IDs.
  This excludes harmless distant misses from the success measurement.
- **Impact:** a rocket that physically collides with a damageable enemy hull
  and applies real shield/armor/structure effects. It is *not* the same as
  escaping PD, particularly for dumbfire weapons.
- **Desired range:** approximately 30–37% terminal-zone penetration in the matched heavy
  saturation reference scenario, across at least three stable random seeds.
  This is a tuning band, never a mandatory pass/fail check.
- **Reference:** tier-IX SIEGE rocket racks, 16 per rack, four installed
  launchers on one physical dreadnought versus four equally researched
  real PD modules on a similar defender, both boost/coast and sustained-burn,
  stationary and laterally evasive target profiles.
- **Controls:** 1:1 and 2:2 matched installations and four/eight-round
  barrages, with PD-on and physically matched PD-off targets; verify that
  PD still dominates isolated rockets and that evasion works independently.
- **Repeated supply:** every round must be paid from finite kinetic stock
  and each physical rack magazine, with real reload/power/heat; do not
  increase available magazines to manufacture an empirical success rate.

## Findings before instrumentation correction

The successful old measured ladder at
https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41514786136
recorded 768 configurations, 12,096 actual launches, 10,264 interceptions
and 1,398 collisions. Endgame 4:4 siege rows varied sharply between motors.
However the old seven-second observation could leave newly released bodies
still airborne. Destroyed targets also stopped defending, corrupting later
samples. **Those rows cannot by themselves certify a 33% end-state escape.**

## Current diagnostic change

`RocketPdBalanceDiagnostic` now runs ten simulated seconds (long enough to
finish the initial salvo given its 7.5-second body lifetime and 2-tick
release spacing), refuses to report unresolved airborne rockets, and
keeps a genuinely damageable target's hull high enough to avoid prematurely
turning off its sensors/PD through destruction. Original shielding, armor,
reactor, heat and wear still operate in the actual `Battle` implementation.

The report separates physical PD interceptions, *escaped* rocket bodies,
**unique 90-unit terminal-zone entries**, impact counts, protected-damage
totals, and a matched no-PD baseline. All counted rockets must complete
flight, so late in-flight rockets cannot inflate escape percentages.

The additional `saturation-sweep.csv` varies **experimental** tracking
pressure (0.00/0.15/0.30/0.50/0.75) against one and four real PD racks,
both propulsion motors and stationary/moving defenders, with three seeds
per configuration. The candidate formula uses the real number of acquired
incoming rocket tracks and real fitted PD emitters: up to four tracks per
emitter are unaffected; excess concurrent rocket tracks reduce shot quality
deterministically. No fake hits or interceptions are created.

**The new model is currently DISABLED for ordinary gameplay.** Only
explicit laboratory battles can activate a test coefficient at tick zero.
Changing missile/torpedo/ballistic/fighter tracking is outside this tuning
scope. Do not enable or select a live coefficient until measured data
supports a choice.

## Acceptance after trial data arrives

Inspect `ladder.csv`, `saturation-sweep.csv` and `summary.md` from the
already existing optional rocket/PD diagnostic workflow:

1. Confirm all launched first-salvo bodies resolve; verify the real PD
   defender survives long enough to service the entire engagement.
2. Compare **terminal-zone penetration** to the 30–37% tuning band for
   4:4 full volleys, **separately for each motor and target-motion state**;
   show
   both per-seed variance and aggregate, not just an average.
3. Report both raw non-interception (launched minus intercepted) and actual
   terminal-zone entries. Check collisions under offline-PD control to
   identify dumbfire misses and target evasion independent of interception
   effectiveness.
4. Verify energy and heat remain real limitations; fitted PD and incoming
   rocket counts are not substituted by abstract firepower ratings.
5. Evaluate the 1:1 and 2:2 controls. A massed 16-round barrage should
   overwhelm tracking bandwidth more than isolated four-round attacks;
   concentrated PD must retain credible defensive value.
6. Once a candidate works, enable **only that tested mechanic** for real
   rocket defense with a separate functional regression; then rerun the
   same 768-scenario comparison and actual full-combat seeds.
7. Track balance as non-blocking diagnostic telemetry. Actual crashes,
   invalid stock creation, missing effects of player orders, broken
   save/load, and simulation hangs remain objective functional defects.

Other ongoing CI failures (including two 200v200 seeds that reached the
90,000-tick nonterminal limit) are separate and must not be misdiagnosed
as a rocket/PD balance issue.
