# Battlespace timeline testing environment — stage 1

This work establishes deterministic, repeatable **presentation** checks without
changing the authoritative 30 Hz battle integrator, ship fuel, weapons,
multiplayer policy, or the legacy on-screen speed mapping.

## Run

```sh
gradle --no-daemon :game:renderPosesSmoke :game:combatTimelineDiagnostic
```

The standalone diagnostic generates:
- `test-output/combat-timeline/frames.csv`: every rendered sample's speed,
  render FPS, simulation tick, interpolation fraction, interpolated hull and
  camera X, frame-to-frame movement, and wall-time clipping.
- `test-output/combat-timeline/scenarios.csv`: one summary for every
  combination of 0.5, 1, 1.5, 2, 2.5, 3x and 15, 30, 60 FPS.
- A separate 250 ms stalled-frame example explicitly reports the existing
  80 ms wall-delta cap and the 170 ms discrepancy.

These are **scripted, real-coast integration fixtures**, not a GPU/physical
device benchmark or proof of collision accuracy. They use the production
`CombatPace.simulationMultiplier`, `Battle.DT`,
`SublightPropulsion.coast` and `BattleRenderPoses`.

## Regressions captured

1. Paused combat must snap render poses to actual physics so resuming before
   another tick cannot rewind an entity.
2. Standard battle framing must be calculated from the exact same interpolated
   hull poses used by all hull renderers; a raw `BattleView.of(battle)` may
   still be used for headless authoritative framing.
3. Screen-space interpolation must remain continuous in the scripted coast
   fixture across all supported speeds and render rates.
4. Timing samples distinguish wall-time clipping from actual physics ticks.

## Follow-on validation

- Extend the pose buffer to flak, rotary PD and rail penetrators.
- Record camera/pose discontinuities and clamped wall time in *live* ZIP logs.
- Test long-frame recovery and real Android 10/15 presentation with seeded
  16/50/200-ship scenarios; avoid treating emulator FPS as hardware FPS.
- Separate simulation-time event playback and frame-time authored effects.
- Later test physics substeps independently if collisions/guidance need them.

This workflow is an **informational diagnostic** and is not a new visual,
balance or frame-rate release gate.

## Stage 1b — point-defense pose coverage

Flak shells, rotary CIWS bullets and rail penetrators now use the shared
pre-tick pose history and interpolation fraction in their **actual** sprite
rendering paths. Newly spawned rounds start at physical muzzle coordinates.
Physics/collision and projectile IDs remain authoritative and unchanged.

Headless tests cover launch presentation. Additional live pixel-motion capture
at low FPS is still required; the scripted fixture does not certify GPU smoothness.

## Stage 2 — doubled sublight position integration

The live `SublightPropulsion.advance` now performs **two bounded
half-DT velocity/position integrations** inside each existing 30 Hz tactical
step. This is **60 Hz ship thrust/position integration**, not a 60 Hz
`Battle.step()`, sensor, fire-control, fighter, or projectile update.

`WorldScaleAndPropulsionTest.doublePositionAccuracy()` compares one
full integration, two half integrations and a 32-substep reference over
300 authoritative ticks, and requires the 60 Hz trajectory to reduce
integration error while preserving final velocity and physical fuel spent.
The same regression checks fuel-starved inertial coasting.

`gradle --no-daemon :core:worldScaleAndPropulsionSmoke` runs this regression.
This limited numerical improvement should **not** be treated as proof of
smooth real-time display motion: at the existing speed mapping the renderer
still receives only approximately three authoritative snapshots per wall
second at 1x, and guided munitions still integrate within their old
30 Hz world phase. Gameplay-fidelity measurements must precede expansion
to projectile/collision substeps.

## Same-JVM 200v200 position cost A/B

Run `gradle --no-daemon :core:positionSubstepAB` to compare one versus
two propulsion substeps for **400 ships**, 600 simulated ticks per run,
two warmup pairs and five measured pairs with alternating order. The
results are saved to `test-output/position-fidelity/ab-runs.csv`; the
console prints median mean tick time, median p95, and new/old cost ratio.
This is a *ship propulsion-only* CPU measurement. It cannot establish
whole-game FPS, GPU workload, full-battle CPU, frame time stability,
or an acceptable user-facing time-dilation policy by itself.

The diagnostic is **not release-blocking**, regardless of speed ratio.
A production comparison also requires identical seeded 200v200 battles
and a display-side histogram of 50th/95th/99th-percentile frame times,
stutter/pose discontinuities, simulation debt, and actual physics ticks
per real second on target Android and desktop hardware.

## Stage 3 — first-frame flight

At 0.5x/1x, missiles, rockets, kinetic rounds, flak, rotary CIWS and rail
penetrators may be spawned and then advance one entire tick before the
renderer sees them. No previous pose exists in this case, causing a visible
muzzle-to-in-flight teleport.

The renderer now reconstructs the birth-tick launch position from each
live projectile's actual physical origin (not a fabricated weapon effect).
Fighters/drones use their physical pre-advance observations when born.
This is applied only if a real beforeStep snapshot belongs to the
immediately preceding physics tick. Pausing, battle changes, and replay
resets never reuse an old launch position. Live collisions and ship
physics remain untouched.
