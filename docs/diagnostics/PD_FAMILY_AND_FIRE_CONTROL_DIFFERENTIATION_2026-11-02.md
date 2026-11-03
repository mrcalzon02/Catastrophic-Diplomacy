# Physical point-defense family and fire-control differentiation

**Observation date:** November 2, 2026 (Sitka local). **Branch:** `main`.

## Implemented versus planned systems

There are exactly **two implemented hard-kill point-defense gun families** in this test: `PD` (individually mounted precision laser CIWS) and `FLK` (individually mounted ammunition-backed fragmentation flak). Existing captain point-defense orders are `BALANCED`, `INTERCEPT_ONLY`, `ANTI_CRAFT` and `HOLD_FIRE`; standing-order ordnance doctrine is `EARLY_INTERCEPT`, `CLOSE_IN`, `FASTEST_IMPACT`. The proposed `RCW`, `RPD`, `PPD`, `PLS`, and `IMD` are **not** represented as working guns by any test here. ECM/decoys, reactive armor and defensive fighters remain separate domains.

- `PD`: individual physical hardpoints, laser optical slew/lock and thermal/power consumption, researched damage and single-body precision interception. Current installed optical scheduling has four free contacts per mount and bounded acquisition slowdown under overload.
- `FLK`: real purchased weapon slots, independent reload, finite kinetic ammunition, timed flight-to-burst, physical area intersection and separate anti-craft preference. Before this pass, *every* object touched by a fragment burst received full-strength damage at its outer radius, and flak ordnance fire-control always scored by approximate arrival ETA regardless of selected standing doctrine.
- `FLK` now additionally uses the physically overlap-weighted density field `flakFragmentCoverage`. The core 30% of the volume retains full fragment density; energy toward the burst rim falls smoothly, including the projected aircraft radius. The old uniform-density model exists solely as a pre-battle diagnostic override. Flak's actual magazine, cooldown, heat, target detection, gun locations and timed burst remain unchanged.
- `FLK` ordnance targeting now feeds *the very same* `defensiveScore` policy that precision PD honors, so explicit EARLY/CLOSE/FASTEST orders actually affect which physical threat the flak mount chooses. It retains preferential fighter/drone targeting for mounts allowed to engage craft.

### Git commits

- [Physical fragment field introduced for paired laboratory A/B](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/23579b1de1540667bb95720ea6e6200bd72d26e6)
- [Real six-threat-class physical fragment experiments](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/4ddac17fc86a86dbaeff396bb8e597b9206c0ff2)
- [Radial fragmentation enabled in real battles](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/9c96479c35dbdbc3ad2bf5e6e98c94dc977c3db7)
- [Legacy-vs-live control semantics](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/f40913b4c88da6a90268dd79171a88f37fc8f4b0)
- [Flak ordnance fire-control now respects captain doctrine](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/8978d6bcbc18897c16b9d9f0c84b6a2d6165f3f6)
- [Actual fitted doctrine matrix by target class](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/2e0627c96d68cd736a7cf5ef42a69d40bcb68707)

## Verified simulations, not speculative ratios

The [initial flak spatial A/B](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41572711552), the [post-production family regression](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41573067858), and the [full standing-doctrine matrix](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41573494970) **all completed successfully**. In each full suite the four Java 17 headless diagnostics succeeded, including 256 valid load pairs without errors. The [production radial flak fighter-wave run](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41573043801) and [following fire-control code fighter-wave run](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41573416529) each passed 528 paired aircraft conditions, zero experimental errors. No full desktop or Android APK package was built.

Machine-readable workflow artifact `full-point-defense-interception-matrix` holds `pd-family-p0/p0.csv`, `pd-family-p0/flak-radial-ab.csv` and `pd-family-p0/fire-control-doctrine-matrix.csv`, alongside the all-threat, load and rocket diagnostic datasets. The fighter-wave artifact is separate, `pd-flak-simultaneous-fighter-wave-results`; workflow retention is configured to 30 days.

## FLK radial damage: 8 matched physical seeds

The table compares the same fitted flak hardpoints, attack RNG seeds, physical ammunition and timers. Only distance-dependent fragment damage changed:

| Target, FLK tier and mounts | Rockets/shells/craft launched | Uniform burst kills | Radial burst kills | Uniform impacts | Radial impacts |
|---|---:|---:|---:|---:|---:|
| Fighter interceptor, base, 1 | 8 | 8 | 8 | 14 aircraft attack impacts | 14 aircraft attack impacts |
| Fighter interceptor, IX, 4 | 8 | 8 | 8 | 4 | 5 |
| Micro missile swarm, base, 4 | 64 | 39 | 31 | 25 | 30 |
| Cascade missile swarm, IX, 4 | 224 | 90 | 63 | 41 | 48 |
| Standard kinetic, IX, 4 | 24 | 20 | 14 | 4 | 8 |
| Relativistic kinetic, IX, 4 | 8 | 0 | 0 | 7 | 7 |
| Heavy torpedo, IX, 4 | 16 | 0 | 0 | 16 | 16 |
| Torpedo bomber, base, 4 | 8 | 0 | 0 | 24 torpedo impacts | 24 torpedo impacts |
| Torpedo bomber, IX, 4 | 8 | 8 | 8 | 8 | 8 |

These figures validate a physically meaningful division: flak remains especially dangerous to light fighters; area clouds can handle several fragile bodies but lose efficiency at the rim; armored warheads are highly resistant; very fast kinetic rounds are poorly handled even when a few are intercepted by massed high-tier flak. Precision PD remains a more appropriate expensive single-body answer to hard torpedoes and kinetically precise targets. **Not all flak improvements are solved**: tier-0 flak fired 480 rounds against eight torpedo bombers in the matched aggregate without killing any; the larger target's actual armor and cumulative damage need instrumentation before declaring the policy right or wrong.

## Fire-control doctrines: 4 identical seeds per setting

Real defender `ShipStandingOrders` were installed per ship, not faked by changing the target scores in the diagnostic itself. Each entry uses genuine physical shots, actual impacts, 600 simulation ticks per seed and the current radial flak model.

| Actual fitted defense / threat | EARLY_INTERCEPT kills | CLOSE_IN kills | FASTEST_IMPACT kills | Important defender damage outcome |
|---|---:|---:|---:|---|
| IX PD ×4 / 32 micro-missiles | 15 | 20 | 24 | early incurred 57.5; close/fast 41.4 |
| IX PD ×4 / 8 heavy torpedoes | 4 | 7 | 7 | early 482.0; close/fast 120.5 |
| Base FLK ×1 / 32 micro-missiles | 7 | 13 | 10 | all 97.7; intercepted count alone is misleading |
| IX FLK ×4 / 112 cascade bodies | 25 | 31 | 28 | all 14.1; compare carrier splitting and true impacts separately |
| IX FLK ×4 / 8 heavy torpedoes | 0 | 0 | 0 | every doctrine incurred 974.5; weapon fundamentally ill suited |
| Base FLK ×1 / 4 interceptors | 4 | 4 | 4 | 36.8 across all modes; aircraft preference is independent of ordnance scheduling |

The apparent best doctrine **depends on ordnance, fitted weapon, approach geometry and what quantity is scored**. Fastest impact generally helps precision lasers cope with these high-tier swarms, but close-in flak can destroy more small bodies for equal or better ammo expenditure in some conditions. Early interception can select a large number of distant targets that would not be the next damaging collision. That is an observed limitation, not authorization to replace the user's selected doctrine globally.

### Remaining problems and proposed next measurements

1. **Collision threat versus body kills.** The siege report already demonstrated a moving-target boost/coast case where many rockets were intercepted but *zero* net impacts were prevented. Add a current-position/velocity closest-approach and collision-radius forecast as a *rank modifier* for ordnance, preserving manual EARLY/CLOSE/FASTEST choices and conservative uncertainty for accelerating rockets. Use same-seed A/B with individual shot IDs, impacts, protection loss and time to first breach; avoid arbitrary miss rolls.
2. **Flak projectile travel and fragment budget.** Current `FlakRound` is an actual timed pending burst aimed using measured lead and physical ammo, but its world-space projectile path is not individually integrated as a moving shell each 30-Hz step. Measure physical shell-flight velocity and burst-center error, distance-aware fragment density, shot dispersion and multiple-body overlaps. Differentiate proximity-fuze FLK from point-blank rotary gunfire.
3. **Bomber armor and ammo efficiency.** Record hostile craft integrity/armor before and after each actual FLK blast; distinguish hit-but-no-kill from total miss, and measure credits/heat per damage prevented. Avoid solving stage-0 bomber resistance by simply granting flak an automatic kill.
4. **Broader formations and tiers.** Eight-plus seeded battles at research III and VI; simultaneous interleaved torpedoes, cascade children, micro rockets, interceptors and bomber formations; flank and crossing trajectories; 1,2,4 modules, mixed 2+2 laser/flak and four independent defensive ships; near/mid/far launch ranges. Compare equal *paid* costs, not just equal slots.
5. **New hard-kill families.** The separate rotary kinetic `RCW`, rail `RPD`, particle `PPD`, plasma `PLS` and interceptor missile `IMD` are still **planned, not implemented**. Add one real projectile/beam/seeker family at a time, with researched installed hardware, real ammo/energy/heat, distinct physical action and same-seed HOLD_FIRE controls. Keep ECM, defensive fighters, decoys and reactive armor out of this weapon-family count.

**Release policy:** Gameplay functionality can block release; physical balance, art, cosmetics, target-value comparisons and numerical target bands are **advisory only**. Do not run whole game packaging for these incremental physics tests.
