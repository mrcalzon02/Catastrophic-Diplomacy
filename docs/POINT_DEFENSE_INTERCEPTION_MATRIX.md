# Point-defense versus all interceptable objects

**Status:** first full-family headless benchmark committed; results are **pending
runner execution**. This is an advisory diagnostic, not a new release gate.

## Threat coverage

The existing rocket ladder separately measures all four unguided rocket
sizes, 4/8/16-round salvos, both engine types, target motion, and research
tiers. The new `PointDefenseThreatMatrixDiagnostic` exercises **23 additional
threat configurations**:

- Physical kinetic cannon shells
- Guided missiles: SINGLE, RIPPLE, SATURATION, MICRO_SWARM,
  HEAVY_STRIKE, COLD_LAUNCH, DECOY_CLOUD, CLUSTER_CARRIER, CASCADE_SWARM
- Guided torpedoes: SINGLE, SATURATION, HEAVY_STRIKE, DECOY_CLOUD,
  CLUSTER_CARRIER, CASCADE_SWARM
- Each of the six actual fighter hulls: INTERCEPTOR, ASSAULT_GUNSHIP,
  LASER_FIGHTER, PARTICLE_FIGHTER, MISSILE_FIGHTER, TORPEDO_BOMBER
- Physical defensive drone craft

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
- Position: approximately 390 world units initially, source held stationary,
  defender AI disabled but physical PD, sensors, power, heat, weather,
  armor, shields and damage active
- Control: identical equipped defender with explicit `HOLD_FIRE`, so
  missed unguided rounds and late-arriving craft are not mistaken for PD kills
- Repeats: two deterministic PD-enabled seeds and one PD-off paired seed
  per threat/tier/PD-mount combination
- Additional functional mode exclusions: `ANTI_CRAFT` must not fire at
  isolated missile/kinetic targets; `INTERCEPT_ONLY` must not fire at
  fighter laser craft without incoming physical ordnance
- Objective launcher validity and forbidden-PD firing can be verified.
  **Relative interception success, damage, balance ratios and win rates
  are advisory only**, never release blockers.

## Reported measures

`matrix.csv` records each configuration's actual:
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
  destroying the missile/torpedo/cannon ammunition it fired.
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

## How to run and retrieve

Existing workflow:
` .github/workflows/rocket-pd-balance-diagnostic.yml `
now executes both diagnostics as independent, `continue-on-error` steps:
`gradle --no-daemon :core:rocketPdBalanceDiagnostic` and
`gradle --no-daemon :core:pdThreatMatrixDiagnostic`.

Both run automatically when relevant core source changes on `main`,
or through the workflow's existing manual dispatch. Outputs are uploaded
together (when produced) as
`full-point-defense-interception-matrix`:
`core/test-output/rocket-pd-balance/ladder.csv`,
`saturation-sweep.csv`, and `summary.md`;
`core/test-output/pd-interception-matrix/matrix.csv` and `summary.md`.

Do not claim percentages until the matching runner has actually produced
the report. Keep all diagnostic tuning observational; functional errors,
crashes, invalid shots and invalid stock accounting are separate defects.
