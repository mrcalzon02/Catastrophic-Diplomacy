# Precision CIWS independent-mount validation and next tuning gate

**Recorded:** November 2, 2026 (Alaska local time).
**Code commit:** [fae117855705734da13144501061369633f70f2a](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/fae117855705734da13144501061369633f70f2a).
**Status:** functional runner checks completed; observed balance still advisory.

## Source of truth

- New actual four/eight aircraft formation run [41562569048](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41562569048): 528 PD-on/off pairs, 0 invalid, independent socket timer/inventory checks, physical muzzle ownership, and `pd_distinct_sockets_fired` in the trial CSV.
- New rocket, threat, load, and flak run [41562569060](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41562569060): all four Java headless Gradle tasks successful; 768 rocket configurations, 736 additional physical threat configurations, 256 timed-load paired scenarios with zero diagnostic failures, plus the focused PD-vs-FLK test.
- Previous comparable physical fighter-wave run [41561038432](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41561038432).
- Previous comparable rocket run [41560226971](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41560226971).
- All data above are from completed runner logs and uploaded test artifacts, not guessed from design specifications. No Android/desktop package was produced by these optional checks.

## Causal change isolated

A fitted PD weapon now has one `Ship.PrecisionMount` for each purchased socket, with its own researched range, precision-hit profile, shot origin and reload. Each mount can independently engage an actual physical projectile or aircraft; it pays real energy and heat. An unfitted/legacy manually constructed ship retains the old shared `pdReload` fixture controller. FLK's existing separate ammunition-backed airburst behavior was not changed.

This is meaningful physical differentiation, not a new component name or an interception-probability adjustment. **It is not yet the complete Gate A:** the live scan still traverses candidate bodies for every emitter, and there is no independent angular slew/reacquisition lock or projected-damage deconfliction.

## Matched three-seed wave readback: 8 bombers, 220 world units, base PD rank

All rows below use the same eight-aircraft scenario per seed, 24 real aircraft over three seeds, 72 possible torpedo impacts. `HOLD_FIRE` records 72/72 impacts. Entries are actual physical torpedo impacts with defense enabled.

| Genuine installed battery | Before 41561038432 | After 41562569048 |
|---|---:|---:|
| PD x1 | 65/72 | 65/72 |
| PD x2 | 64/72 | 54/72 |
| PD x4 | 53/72 | 37/72 |
| FLK x1 | 72/72 | 72/72 |
| PD x2 + FLK x2 | 62/72 | 51/72 |

Single precision mounts and unchanged flak make good controls. Extra purchased laser emitters now give tangible additional ordnance defense while remaining poor aircraft hull killers. Effects are *scenario-specific* and should not be presented as general interception rates or cost parity.

## Rocket surge: danger of late-game overdefense

For genuine rank-IX siege rocket configurations, 4 attacking racks versus 4 defending PD mounts, 16 rockets per rack, 3 seeds, stationary target:

| Actual physical outcome | Before 41560226971 | After 41562569060 |
|---|---:|---:|
| Boost/coast rockets launched | 192 | 192 |
| Boost/coast rockets intercepted (eventually) | 138 | 187 |
| Boost/coast rockets escaping interception | 54 | 5 |
| Boost/coast actual hull impacts | 54 | 5 |
| Boost/coast terminal-zone entries | 71.9% | 42.7% |
| Sustained-burn rockets intercepted | 171/192 | 192/192 |

The early motivating target of roughly one-third penetration applies to *large concentrated rocket barrages only*. It is a tuning hypothesis, NEVER a release block or forced 33% miss. New independent fire control is mechanically successful but the stage-IX configuration can now nearly erase a siege wave. A hypothetical suppression percentage alone is not a valid fix.

The rocket diagnostic as a whole reports 12,096 fired bodies, 10,906 physical interceptions, and 940 real impacts (previous: 10,329 interceptions and 1,408 impacts). These aggregate totals mix a variety of ranks, motor states and target motion, not one balanced matchup.

## Relevant load and geometry evidence

With 128 live boost/coast rockets in a SURGE load at specialist IX, four guns on one hull intercepted 126/128, while four independent hulls intercepted 102/128. Placement, power concentration and independent firing/coverage must be distinguished before declaring a slot-count balance rule. Eventual intercepted count also hides late defensive failure: the one-hull result had 60 terminal entries even when 126 rockets were finally killed.

With sustained 256-rocket boost/coast attacks and a damaged reactor, the same four-mount defender intercepted 211/256 rather than nominal 252/256, reaching zero observed reactor fraction and around 1.59 peak heat fraction. The power and heat systems are materially involved under stress. These are deliberately harsh test conditions, not normal shipyard performance.

## Next controlled implementation and test work

1. Cache bounded hostile projectile/craft tracks **once per defending hull per simulation tick**, reusing immutable contact geometry across its separate mounts. Preserve independent per-mount detection/range/research evaluations, priority modes, `coversThreat` and deterministic iteration. Benchmark the actual body-scanning cost in large fleets.
2. Add independently modeled turret slew, target reacquisition and persistent target tracking. A target switch must spend *time and power/heat while aiming*, not trigger arbitrary RNG misses. Avoid a design that causes perpetual retarget starvation during saturation.
3. Deconflict simultaneous shots by reserving expected remaining target integrity and time-to-impact, without preventing necessary concentrated fire on genuinely hardened torpedoes. Compare mounted laser shots per actual unique projectile and early-versus-late kills.
4. Rerun fixed-seed 1/2/4 mounts, 4 independent hulls, stage 0/IX, 4/8/16 rockets of both propulsion types, isolated craft and mixed swarms. Report inner-ring breaches, real impacts and damage prevented, not just eventual kills. Treat all target bands as advisory.
5. Complete the already distinct FLK mechanism: true shell position/velocity, radial fragment density falloff, and safe arcs. Only then add RCW, RPD, PPD, PLS and IMD one functional physical interception method at a time. Preserve original save and technology IDs.

**Unchanged boundaries:** ECM, decoys, defensive fighters and reactive armor are separate equipment systems. No fake EMP defense category. Numerical/visual balance diagnostics cannot block an otherwise functional release.
