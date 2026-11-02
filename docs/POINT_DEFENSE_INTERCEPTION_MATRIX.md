# Point-defense versus all interceptable objects

**Status:** first full-family headless benchmark committed; results are **pending
runner execution**. This is an advisory diagnostic, not a new release gate.

## Threat coverage

The existing rocket ladder separately measures all four unguided rocket
sizes, 4/8/16-round salvos, both engine types, target motion, and research
tiers. The new `PointDefenseThreatMatrixDiagnostic` exercises **38 additional
threat configurations**:

- All five physical kinetic shells: standard, armor-piercing, fragmentation,
  high explosive, and relativistic high-velocity rounds
- Guided missiles: five additional physical warhead profiles
  (armor-piercing, fragmentation, high explosive, EMP, shield breaker),
  plus SINGLE, RIPPLE, SATURATION, MICRO_SWARM,
  HEAVY_STRIKE, COLD_LAUNCH, DECOY_CLOUD, CLUSTER_CARRIER, CASCADE_SWARM
- Guided torpedoes: those same five alternate physical warheads,
  plus SINGLE, SATURATION, HEAVY_STRIKE, DECOY_CLOUD,
  CLUSTER_CARRIER, CASCADE_SWARM
- Each of the six actual fighter hulls: INTERCEPTOR, ASSAULT_GUNSHIP,
  LASER_FIGHTER, PARTICLE_FIGHTER, MISSILE_FIGHTER, TORPEDO_BOMBER
- Physical defensive drone craft and a repair drone repairing a real damaged ally

Missile/torpedo dispensers spawn **real interceptable micro or ultra-micro
submunitions**, which are included individually in launched/intercepted totals;
their carrier splits and child counts also have separate telemetry. Decoy
hardware is a physical interceptor target with no warhead damage and a
separate fired/killed count. Fighter-fired kinetic and guided ordnance is
identified independently of the fighter craft killed. Instant beam fire
(LAS, PLA, PAR) is **not** an interceptable projectile, so it does not
belong in PD interception denominators.

## Controlled physical experiments

- Defender: real installed `BASE-TER-PD` modules, one vs four weapon
  sockets on a dreadnought, baseline rank 0 vs specialist rank IX
- Offensive projectiles: genuine `Battle` entities, finite magazines and
  launcher reload/energy; a legacy source fixture for reliable selection
  of all guided salvo modes, not yet a rank-matched attacker research module
- Craft: actual fitted cruiser flight deck, with the real fighter variant;
  physical stock consumption, fuel and fighter-borne weapons
- Position: approximately **390 world units** for ordnance, plus **220
  and 390 world-unit** engagements for each fighter and drone variant,
  to distinguish inner-PD exposure from long-range standoff. Source held
  stationary, defender AI disabled but real PD, sensors, power, heat,
  weather, armor, shields and damage active
- Control: identical equipped defender with explicit `HOLD_FIRE`, so
  missed unguided rounds and late-arriving craft are not mistaken for PD kills
- Repeats: two deterministic PD-enabled seeds, each with a **same-seed
  paired PD-off control**, per threat/distance/tier/PD-mount combination;
  online and offline impact counts therefore share an equal denominator
- Additional functional mode exclusions: `ANTI_CRAFT` must not fire at
  isolated missile/kinetic targets; `INTERCEPT_ONLY` must not fire at
  fighter laser craft without incoming physical ordnance
- Objective launcher validity and forbidden-PD firing can be verified.
  **Relative interception success, damage, balance ratios and win rates
  are advisory only**, never release blockers.

## Reported measures

`matrix.csv` records each configuration's launch distance and actual:
`primary_launched`, `primary_intercepted`, `terminal_entries`,
`primary_hits`, `decoys_launched`, `decoys_intercepted`,
`children_launched`, `carriers_split`,
`secondary_launched`, `secondary_intercepted`, `secondary_hits`,
`craft_lost`, `fighter_shots`, `paid_units`, `pd_shots`,
protection damage, net power difference, peak heat, fitted tracking and
physical PD reach. The `summary.md` includes per-threat and per-PD-grade
interception ratios and matched PD-off impacts.

Definitions:
- **Intercepted**: an actual projectile removed by a live defensive system,
  not simply a projectile that missed the enemy or ran out of fuel.
- **Craft lost**: actual fighter/drone entity loss, distinguished from
  destroying the missile/torpedo/cannon ammunition it fired. Repair craft
  target a damaged friendly ally: that tests whether the existing PD
  target-scope filter recognizes a support craft as hostile without
  assuming it does.
- **Terminal entry**: real object or its real impact entered the 90-world-unit
  threat zone; distinct from a direct hull impact.
- **Impact**: actual physical hit or a fighter weapon's recorded damage event.
  Compare to PD-off to distinguish projectile accuracy/evasion.
- **PD workload**: physical PD shots per incoming body; resource consumption
  is still governed by the same existing reactor and thermal limits.

**Important limitations and next calibration:** This first matrix tests
rank-IX PD *against legacy baseline projectiles*, not an attacker equipped
with an equal-rank offensive research module. Use these results to establish
PD's behavior envelope, detect target-type failures, and identify saturation
outliers. Before finalizing cross-tier competitive balance, add matched
attacker hardware research for KIN/MSL/TOR, larger multi-ship mixed volleys,
per-body impact outcomes for fighter-launched ordnance, PD tracking
distribution over time, and paired trials for other factions. Do not assume
one-third penetration applies to missiles or fighters: the user's stated
~33% goal applies to **large, concentrated rocket barrages**. Guided missile,
torpedo, kinetic and fighter targets require their own empirical comparison.

## Operating-load benchmarks: incidental, surge, sustained

The separate `PointDefenseLoadDiagnostic` extends the per-object matrix
with actual combat time and overlapping physical firing commitments:

| Load | Physical schedule | What to compare |
|---|---|---|
| INCIDENTAL | One attacker (four staggered sources for mixed threats), short firing opportunities spaced 150 simulation ticks; no restocking | Isolated interception, first-alert timing, wasting PD energy on one small threat |
| SURGE | Four simultaneous launch platforms; one opening salvo/short firing window; then projectile flight | PD acquisition/target prioritization under overload, full-size 16-round siege rocket penetration, leakage despite multiple PD modules |
| SUSTAINED | Four attackers authorized to fire for 1,800 ticks (60 seconds), long enough for multiple physical siege reloads, under finite supplies; 300 ticks (10 seconds) to resolve flight | Early/middle/late interception throughput, reactor and heat headroom, physical reload fatigue, stock depletion, terminal survival |

Each condition includes six initially configured ship-launched threats:
Kinetic, guided missile, guided torpedo, 16-round siege rockets with both
boost/coast and sustained-burn motors, and a mixed 4-lane battery of two
guided/one kinetic/one rocket. (INCIDENTAL uses the real 4-round rack setting.)
The load matrix additionally runs real fitted **interceptor** and **torpedo
bomber** squadrons; their craft destruction, secondary ordnance, range and
limited onboard fuel must be read separately.

PD layout is an intentional variable:
**FOUR_MOUNTS_ONE_HULL** versus **FOUR_INDEPENDENT_HULLS**. The engine currently
aggregates all mounts on a single ship under one `pdReload` controller:
four separately mounted PD-capable ships can therefore deliver more
independent shot opportunities. These configurations have exactly four
installed PD mounts, but different fire-control parallelism. The benchmark
must reveal this rather than falsely asserting equivalence.

Defenders use real fitted PD ranks 0 and IX, and every online seed receives
a same-seed `HOLD_FIRE` control. All defensive ships have physical shields,
armor, heat/power budgets, sensors and PD tracking/reload. High durability
is used only to avoid premature enemy death invalidating the long test.
Dumbfire rockets still follow their frozen launch bearing; no test modifies
live interception balance coefficients. The laboratory provisions a finite
paid-equivalent initial shared kinetic store for its real rocket rack and
records remaining stock. It NEVER adds free ammunition mid-engagement.

`core/test-output/pd-load-profiles/profiles.csv` reports each simulation's
actual projectile/craft launches, real kills, terminal-zone entries,
actual hull impacts, physical flight timeouts, remaining unresolved bodies,
shot activity and kills during the 0–600, 600–1,200, 1,200–1,800 and
1,800–2,100-tick windows, observed minimum reactor fraction and peak heat,
actual PD craft kills separately from fuel-expired craft, hull loss and
kinetic/missile/torpedo stores physically consumed. These time buckets
measure **event-time throughput**, NOT cohort-specific kill fractions; total
run totals are the appropriate denominators for overall interception ratios.
Incidental and surge cases stop after their shorter firing windows and a
300-tick physical flight drain instead of simulating empty minutes.
`summary.md` provides side-by-side comparisons and reports any diagnostic
errors without converting balance targets into release blockers.

The expected ~33% inner-defense penetration goal applies only to **large
massed 16-round rockets**, not to single missile contacts or armed fighters.
No fixed kill-percentage expectation may gate the release.

## First completed operating-load result and diagnostic repairs (2026-11-02)

The first completed run
[41527843801](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41527843801)
compiled and ran all three optional programs. Its load test reported 153
successful PD-on/off pairs and **39 failed fighter-related samples**. The
workflow itself was green because the diagnostic deliberately returned zero
despite missing samples. **Do not interpret those 39 as 0% interceptions.**

Selected real physical surge/sustained 16-round siege rocket results with
four specialist-rank IX PD mounts on **one hull**:

| Loading | Boost/coast intercepted | Sustained-burn intercepted |
|---|---:|---:|
| SURGE, 128 launched | 94 (73.4%) | 114 (89.1%) |
| SUSTAINED, 256 launched | 186 (72.7%) | 230 (89.8%) |

The escaped-interception fraction is *not* the same as reaching the inner
90-unit defended zone. The metrics show an apparent **motor-type imbalance**
in favor of the boost/coast missiles. These results are advisory, not a
permission to modify combat balance without a verified correction.

The failing fighter cases were caused in part by **FIGHTER_CANNON /
FIGHTER_MISSILE / FIGHTER_TORPEDO launch labels not matching subsequent
KINETIC / MISSILE / TORPEDO interception events**. The revised test joins
all physical shot IDs; starts fighter carriers close enough for real target
acquisition; closes the fighter launch bay only after a real sortie;
separates actual PD craft kills from natural fuel expiration; and marks the
optional diagnostic step failed if any scenario fails after preserving
its results as artifacts.

The completed nominal load test also found a **94–99% minimum reactor
charge and roughly 1% peak heat**. These measurements primarily reflect
tracking/cooldown limits, not power exhaustion. Additional **SUSTAINED /
DAMAGED_REACTOR** scenarios use half initial reactor charge, 8% normal
power regeneration and 12% cooling (with actual engineering/power/heat
rules unmodified), as a separate stress test rather than contaminating
nominal balance measurements.

The newer complete rerun must be read before accepting fighter, stage-by-
stage or damaged-reactor performance numbers. Never report those new
figures as verified merely because code was committed.

## How to run and retrieve

Existing workflow:
` .github/workflows/rocket-pd-balance-diagnostic.yml `
now executes all three diagnostics as independent, `continue-on-error` steps:
`gradle --no-daemon :core:rocketPdBalanceDiagnostic` and
`gradle --no-daemon :core:pdThreatMatrixDiagnostic`, and
`gradle --no-daemon :core:pdLoadProfileDiagnostic`.

All three run automatically when relevant core source changes on `main`,
or through the workflow's existing manual dispatch. Outputs are uploaded
together (when produced) as
`full-point-defense-interception-matrix`:
`core/test-output/rocket-pd-balance/ladder.csv`,
`saturation-sweep.csv`, and `summary.md`;
`core/test-output/pd-interception-matrix/matrix.csv` and `summary.md`;
`core/test-output/pd-load-profiles/profiles.csv` and `summary.md`.

Do not claim percentages until the matching runner has actually produced
the report. Keep all diagnostic tuning observational; functional errors,
crashes, invalid shots and invalid stock accounting are separate defects.
