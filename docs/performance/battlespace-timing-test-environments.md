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
