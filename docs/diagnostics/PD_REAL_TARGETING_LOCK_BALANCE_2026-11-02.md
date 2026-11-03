# Physical precision CIWS: target coordination, optical dwell and research parity

**Observed November 2, 2026 (Sitka local).** This is a live-code and CI-evidence
review, not a replacement for the existing seven-family weapon plan.

## Verified changes and scope

- [fae1178](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/fae117855705734da13144501061369633f70f2a): genuine independent fitted precision-laser socket state, researched profiles, muzzle ownership and cooldowns.
- [4c55b0a](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/4c55b0a865571e7fd03447dfd624d0e589c14896): each fitted turret independently traverses toward a physical target, retains the lock on the same visible body, and incurs actual elapsed reacquisition delay, servo power and heat. The legacy unfitted test-fixture path remains.
- [d5162fa](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/d5162fac25d8116895e32c4084268701b1f23e1e): opt-in laboratory lock-duration sweep and per-socket acquisition/rotation/wait telemetry.
- [aa9b0e1](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/aa9b0e17c7ba97ceccafbcf462c3b8814763759d): normal gameplay uses bounded, per-hull *expected physical laser damage reservations* during target acquisition so multiple emitters do not needlessly chase one fragile rocket. Hardened bodies can still attract multiple lasers. Same-seed laboratory A/B can disable this coordination without altering damage, ammo, power or accuracy.
- [3fed497](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/3fed4971a09bf47c87504e9b81b384986b9e7d2f): extended coordinated lock-duration sweep.
- [ceec139](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/ceec13900356293593216b02f908a42f56ae67ab): independently installed attacker/defender research parity, testing 0/0, IX/IX, IX/0, 0/IX.
- [40d9dd1](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/40d9dd17c6b1376fcfcbe7ebdc53751bab32637a): reused bounded per-hull damage-reservation map instead of a new HashMap every 30-Hz tick. **Its own run must finish before functional acceptance is claimed.**

No physical FLK implementation, ECM countermeasure, armor, fighter or rocket-motor statistics were modified in these steps. The five other hard-kill proposals (RCW, RPD, PPD, PLS, IMD) remain unimplemented.

## Exact diagnostic provenance

- Real installed 4/8-fighter-wave run [41565973315](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41565973315): 528 valid paired cases, zero errors after mechanical turret traverse.
- Real optics-duration sensitivity [41566424714](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41566424714): headless Gradle execution succeeded, including 256 operating-load pairs with zero failed scenarios.
- Same-seed optical reservation A/B [41566992536](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41566992536): all tasks passed; 256 valid operating-load paired cases, zero diagnostic errors. Controlled A/B varied only coordinated target allocation.
- Extended-duration scan [41567245520](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41567245520): all headless steps passed; additional physical lock values through 2.0.
- Genuine rocket research parity [41567586907](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41567586907): all diagnostics successful; the cross-tier output is `core/test-output/rocket-pd-balance/real-cross-tier-rockets.csv`.
- Raw CSVs from these runs are uploaded as the `full-point-defense-interception-matrix` workflow artifact. The separate mixed-flak fighter-wave CSV remains in `pd-flak-simultaneous-fighter-wave-results`. Artifacts are retained for 30 days by current workflows.

## Physical acquisition and target allocation: paired outcomes

The *siege* test uses 4 real IX rocket hardpoints at 16 rounds each versus 4 real
IX laser PD hardpoints, 3 seeds (192 launched bodies), starting separation 440
world units, with actual power and heat. The defender's hull is kept alive
for a legitimate complete test but retains real shield, armor and hull damage.

| Rocket / target | Optical lock multiplier | Target coordination | Intercepted / 192 | Escaped PD | Entered inner 90-zone | Real impacts |
|---|---:|---|---:|---:|---:|---:|
| Boost/coast, stationary | 1.00 | off | 72 | 62.5% | 83.9% | 72 |
| Boost/coast, stationary | 1.00 | on | 181 | 5.7% | 46.9% | 11 |
| Boost/coast, stationary | 1.60 | on | 132 | 31.3% | 62.5% | 48 |
| Boost/coast, moving | 1.60 | on | 140 | 27.1% | 56.8% | 45 |
| Sustained-burn, stationary | 1.60 | on | 192 | 0.0% | 32.3% | 0 |
| Sustained-burn, moving | 1.60 | on | 191 | 0.5% | 2.1% | 0 |

Coordination at normal lock timing cut tracked-body acquisitions from 441 to
285 and laser lock-wait ticks from 1,063 to 756 in the stationary boost/coast
A/B, *without changing physical shot accuracy*. Some apparent pre-coordination
weakness was self-inflicted tracking duplication rather than healthy saturation.

**Do not equate escaped interception with successful hits.** A projectile may
enter the terminal ring before being shot down; moving hulls can also evade
unguided rockets entirely. Impacts, PD-off impacts, and protected damage are
distinct. Three seeds are meaningful smoke/trend evidence, not a narrow
confidence interval for a 5%-point production tuning decision.

## Why a global 1.60 lock slowdown is not yet approved

The same genuinely researched 4v4 cross-tier program yielded:

| Actual attack / defense | Motor / target | Lock scale 1.00 escape | Lock scale 1.60 escape |
|---|---|---:|---:|
| Base / base | Boost/coast, stationary (48 rockets) | 77.1% | 77.1% |
| Base / base | Sustained, stationary (48) | 43.8% | 62.5% |
| IX / base | Boost/coast, stationary (192) | 88.0% | 95.8% |
| Base / IX | Boost/coast, stationary (48) | 0.0% | 0.0% |
| IX / IX | Boost/coast, stationary (192) | 5.7% | 31.3% |
| IX / IX | Sustained, stationary (192) | 0.0% | 0.0% |

The initial base defense already struggles against concentrated base boost
rockets. Slowing all turrets equally would worsen weaker early-game counters
and would not solve the sustained-burn motor's nearly complete vulnerability
to IX defenses. **Therefore 1.60 remains a laboratory hypothesis, not a
production tune.** Normal battles retain the original lock duration (1.00)
plus actual optical servo/coordination.

Also, several neighboring values produce identical outcomes at the fixed
30-Hz tick interval: e.g. coordinated boost/coast at 1.30 and 1.45 both
escaped 7.8%, while 1.60 escaped 31.3%. A bare global timer causes discrete
threshold cliffs and would be difficult to calibrate equitably.

## Priority next development / validation

1. **Performance first:** cache the physical hostile contact geometry once
   per hull per tick and share it across installed emitters. Keep per-socket
   research and detection quality independent; never allocate per-threat
   heaps or redo `coversThreat` for each mount without profiling. Verify
   object allocations, worst-case scanning cost, and full 200v200 performance.
   The small reservation HashMap reuse is **not** this larger sensor cache.
2. **Physical track quality:** optical acquisition time should respond to
   actual cross-section, sensor quality, transverse angular velocity,
   range, thermal signature and tracking-damage state, rather than a
   constant per-body threshold. Record acquisition distance/time for every
   shot and verify that heavy slow targets do not become identical to
   tiny high-speed threats.
3. **Tactical engagement:** test near/mid/far rocket launches, multi-axis
   approach, mixed armored torpedoes + micro swarms and genuine fighter
   attacks. Ensure hardened missiles can receive necessary combined
   fire while fragile bodies do not receive four identical useless locks.
4. **Cross-tier fairness:** repeat 0/0, IX/IX, IX/0, 0/IX with 8+ seeds and
   rank III/VI interpolations after improvements. Model actual defender
   damage prevented and consumed credits, not just eventual kills.
5. **Motor viability:** separately test boost/coast and sustained burn,
   launch envelope, terminal speed, geometry and moving targets. Do not
   'fix' the latter merely by changing arbitrary PD accuracy.
6. **Flak and the other five families:** keep physical finite-flight FLK
   and ammunition mechanics intact while finalizing shell trajectories
   and radial fragment falloff. Proceed to RCW then RPD, PPD, PLS and IMD
   only as fully physical, fitted and tested weapons.

All physics/cost/balance/art results are *advisory*; only actual functional
failure to build, launch, play, update or use required network systems may
block release. No APK or desktop full package should be rebuilt for routine
PD coefficient studies.
