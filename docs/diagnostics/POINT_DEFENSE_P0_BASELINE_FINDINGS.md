# Point defense P0: initial measured PD versus flak comparison

**Date:** November 2, 2026
**Source:** [successful advisory GitHub diagnostic run #41551408398](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41551408398)
**Structured results:** [56-row baseline CSV](pd_family_p0_baseline_run_41551408398.csv)
**Full artifact:** full-point-defense-interception-matrix (see run's Artifacts)

## Test conditions and validity

- Genuine fitted Terran laser PD (PD) and flak (FLK) compared using independent 
  single/4-mount shipyard installations and stages 0/IX.
- Seven priority physical threat types; two deterministic seeds per fitting.
  Every active run has a matched HOLD_FIRE control.
- Launcher is a **legacy base-stage physical weapon fixture**, not stage-IX
  attacking armament. These tests do **NOT** yet establish IX/IX parity.
- No measured hard-kill physical difference is currently modeled between PD 
  and FLK: both mount families enter the same laser-beam interception loop.
  Different coefficients/cost/research are not a proper counter mechanic.
- Each reported percentage is the actual intercepted body count divided by
  launched body count for that branch; 2-seed results may be very noisy.
  Cascades can spawn different numbers of children depending on whether a
  carrier is intercepted early. Therefore **cascade percentages do NOT
  compare a matched fixed cohort** and must not be used to tune directly.

## Selected baseline numbers

| Threat | Base PD x1 | Base FLK x1 | Base PD x4 | Base FLK x4 | Problem |
|---|---:|---:|---:|---:|---|
| Standard kinetic | 3/6 | 3/6 | 6/6 | 5/6 | Similar mechanics |
| Relativistic kinetic | 0/2 | 0/2 | 0/2 | 0/2 | Extremely small sample |
| Micro swarm | 1/16 | 1/16 | 8/16 | 11/16 | Flak only changes coefficient, not AOE behavior |
| Cascade swarm | 0/56 | 1/56 | 8/14 | 8/8 | Child generation makes the denominator variable |
| Heavy torpedo | 4/4 | 4/4 | 4/4 | 4/4 | Unacceptable universal defense in tiny test |
| Interceptor fighters | 2/2 | 2/2 | 2/2 | 2/2 | Laser equals flak against aircraft |
| Torpedo bombers | 0/2 | 0/2 | 2/2 | 2/2 | Need larger attack cohorts and damage metrics |

## Physical combat corrections to build and retest

1. Give each installed PD/FLK emitter an independent reload and actual
   weapon-family identity. A four-mount fitted hull has four paid mounting
   locations; it must not become one magically accelerated shared hitscan.
2. Keep PD as a precise, physically narrow, single-body laser attack with
   meaningful per-target tracking, power, switching latency, and low
   armor-penetration damage. Its good matchups are light, fast projectiles.
3. Give FLK real, finite-flight timed/proximity fragmentation bursts with
   physical radius, misses, limited reloads and ammunition. Allow a burst
   to threaten multiple *actual* nearby craft or missile bodies. It must
   struggle with ultra-fast kinetics and isolated tiny ordnance.
4. Make tougher torpedoes require multiple PD hits or a heavier dedicated
   anti-torpedo system. **Do not** directly reduce every torpedo's health
   or give either PD class a blanket magic damage cap.
5. Keep in-flight survival, outer-90-unit terminal breaches, actual impacts,
   loss of hull/shield/armor, and missile/craft life cycles separate.
   An eventual interception inside the damage ring is not a successful
   early defense.
6. Compare all four attack/defense tier pairings using real installed and
   equally licensed stage-IX offensive and defensive hardware. Use more
   seeds and 1/4/8/16 salvo volumes before setting final balance.
7. Verify real mounted flak effects under live mixed-screen 200v200 battles;
   do not rebuild desktop/Android packages for optional tuning diagnostics.

**Balance target is advisory, never release-blocking.** Release blockers
remain objective errors such as failure to build, boot, enter combat, update
or use required multiplayer. This document changes no live balance.
