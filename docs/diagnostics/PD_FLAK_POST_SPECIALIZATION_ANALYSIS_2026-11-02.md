# Eight-seed physical laser-PD versus flak diagnostic review

**Analysis date:** November 2, 2026. **No combat balance changes in this commit.**

## Source and scope

Primary verified measurement: [optional headless GitHub test 41554352157](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41554352157), artifact `full-point-defense-interception-matrix`, `pd-family-p0/p0.csv`. All four diagnostic steps passed.

The comparison contains **88 measured aggregate configurations**, with **eight deterministic seeds per configuration**: fitted Terran **PD** or **FLK**, one or four actual weapon mounts, stage 0 or IX. Projectiles launch from a **base-stage legacy physical fixture**, NOT a stage-IX researched attacking ship. Projectile engagements begin at 390 world units; fighter engagements are measured at 95, 220 and 390 world units. A matched HOLD_FIRE control accompanies every test.

Earlier runs provide before/after provenance:
[shared laser baseline 41551408398](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41551408398);
[physical paid finite-flight flak 41552438947](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41552438947);
[tracking and hardness 41552938867](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41552938867);
[eight-seed pass 41553313274](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41553313274);
[laser aircraft dwell 41553816859](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41553816859).

## Measured interception, 4 mounts, 390-unit starting separation

| Threat | PD base | FLK base | PD IX | FLK IX |
|---|---:|---:|---:|---:|
| Standard kinetic | 24/24 (100%) | 4/24 (17%) | 24/24 (100%) | 20/24 (83%) |
| Relativistic kinetic | 0/8 | 0/8 | 3/8 (38%) | 0/8 |
| Micro missiles | 45/64 (70%) | 39/64 (61%) | 64/64 (100%) | 33/64 (52%) |
| Cascade missiles | 29/68 (43%) | 27/224 (12%) | 32/32 (100%) | 90/224 (40%) |
| Heavy torpedoes | 8/16 (50%) | 0/16 | 8/16 (50%) | 0/16 |
| Interceptor fighters | 0/8 | 8/8 | 0/8 | 8/8 |
| Torpedo bombers | 0/8 | 0/8 | 0/8 | 8/8 |

**Critical caveats**:
- Fighter kills are **eventual aircraft losses**, not strikes prevented. A fighter can fire repeatedly before it is killed.
- All eight aircraft in a row are from **eight sequential one-fighter trials**, **not** one coordinated eight-aircraft attack. The observed 100% single-fighter FLK kill rate should NOT yet prompt a global flak nerf.
- Cascade child-body denominator varies when an upstream carrier missile is shot down before spawning its children: `32/32` and `90/224` are **not directly comparable**. Require parent-lineage, submunition release and damage-prevention comparisons.
- A projectile can be intercepted *after* crossing the 90-unit terminal zone. Stage-IX four-flak versus standard kinetic scored 20/24 eventual kills but **17 terminal entries**. Raw kill rates alone are misleading.
- Research rank IX on the defender against the base launcher is NOT an IX/IX matchup.
- Eight physical projectiles or fighters per configuration are insufficient for precise 5% balance decisions.

## Important consequences, not just kills

**Four baseline mounts at 390 world units:**

- Standard kinetic: PD prevents all 11 observed impacts (defender loss 0 vs 271.0 hold-fire); flak records 12 versus 11 hold-fire impacts and 299.1 vs 271.0 loss. The slight negative protection is **scenario/RNG variance**, not a change in the attacker weapon's damage.
- Heavy torpedoes: PD stops 8/16, resulting in 964.1 actual damage versus 1948.9 hold-fire (**approximately 50.5% prevention**); flak stops 0/16, preventing none.
- Micro swarm: PD prevents about **53.1%** of hold-fire damage and flak about **24.8%**; flak fragments often fail to prevent inner-zone impacts.
- Interceptor fighters (220-unit starting distance): PD kills 0/8, allows 176 fighter-fired impacts versus 186 without defense; FLK kills 8/8 but still allows six fighter-fired impacts versus 186. That is roughly **96.8% fewer impacts with flak** and about 97.9% less actual damage.
- Torpedo bombers (220 units): PD kills **zero aircraft** yet intercepts their outgoing torpedoes, reducing actual impacts from 24 to 0. FLK base kills **zero aircraft** and allows 24 impacts; the bombers can fire from beyond its reach.

**Bomber engagement range, single baseline FLK mount:**

| Start distance | Aircraft eventually killed | Physical torpedo impacts / 24 hold-fire impacts |
|---|---:|---:|
| 95 | 8/8 | 8/24 |
| 220 | 0/8 | 24/24 |
| 390 | 0/8 | 24/24 |

This sharp range dependence is plausible bomber counterplay, not proof of an intermittent flak malfunction.

## Interpretation

**Promising**: Real fitted FLK now uses paid ammunition and timed finite-flight physical fragmentation; its specialist role against incoming fighters is evident, while laser PD counters light kinetic/projectile threats. Both fail against certain threats; no universal 100% point-defense mechanism remains across all types.

**Potential issue**: FLK might suppress isolated close-range interceptors too reliably. But real simultaneous multi-craft and mixed-weapon attacks, time to first fighter firing, and firing-volume stress are still **not measured** in this FLK-versus-PD test. Do NOT tune single-fighter kill rate to a desired percentage before saturation trials.

**Architecture issue**: Shared `Shipyard.fit` PD/FLK tracking, output and cycle aggregation still needs review in **mixed** two-PD/two-FLK loadouts. It can distort allocation if we tune on a pooled statistic.

## Next focused diagnostics — before changing balance

1. Actual **four and eight simultaneous launched fighters**, not four/eight independent one-fighter seeds, against one and four PD/FLK mounts. Test both interceptors and standoff torpedo bombers at 95, 220 and 390 units, with HOLD_FIRE controls. Record aircraft kills **and** aircraft shots/torpedo impacts and launch timing.
2. Mixed simultaneous aircraft, micro missiles, rocket barrages and armored torpedoes to expose engagement priority, tracking overload and finite-ammunition limitations.
3. Parent/carrier versus independent child missile accounting, pre-terminal interception, 90-unit breaches, real impacts and matched damage prevention.
4. Mixed fitted **two-PD plus two-FLK** against four-PD and four-FLK, normalized by hardware and ammunition cost.
5. The full four cross-tier tests with a **genuine researched attacking weapon fit**: 0/0, IX/IX, IX/0, 0/IX.
6. Independent heat, reactor, ammunition and 200v200 performance studies, not per-tuning rebuilds.

**Likely later tuning knobs**: FLK burst radius, predictor uncertainty, aircraft damage per real fragment, flak reload and ammunition; PD air-target dwell and light-projectile tracking. Preserve heavy-torpedo and fast-kinetic specialization. Tune against **actual damage prevention and terminal leakage**, not merely total eventual kills.

**Release policy**: Balance, visuals and style remain advisory diagnostics. Only objective functional failures may block builds or releases.
