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

## Stage 3b — velocity-continuous ships and camera

Presentation snapshots now retain actual velocity as well as position for
ships, fighters, guided ordnance and defensive projectiles. A bounded cubic
Hermite curve avoids abrupt apparent speed changes across 0.5x/1x snapshots;
it never goes beyond the two authoritative endpoints, changes physics or
hides a real large-distance relocation. Facing rotation is eased over the
same physical interval. The ordinary battle camera uses a frame-rate
independent smoothing response, including fleet-bounds changes on ship
deaths. Manual pan/reset is immediate; exhibition mode keeps its existing
separate cinematic director. The scripted CI verification camera is unchanged.

## Stage 4 — host-snapshot ships do not teleport at 10 Hz

The remote battle view previously copied 10 Hz host snapshots directly into
the rendered ship coordinates every frame, bypassing all local interpolation.
`RemoteShipInterpolation` now blends authoritative positions across the
host's actual tick separation, bounded to 250 ms after network gaps.
The display freezes at the last known authoritative pose if packets stop;
it NEVER guesses future movement or authorizes hits, targets, or commands.
Tracks are indexed by **server ship ID**, not array order, preserving identity
when the host reorders ships. Newly observed ships snap to their actual
host-provided origin. Duplicate ticks cannot rewind the presentation; a new
session with a lower tick clears stale state. Facing interpolates across the
shortest angular arc.

The existing `:game:renderPosesSmoke` now tests 10 Hz packet arrivals with
60/30 FPS intermediate frames, reordered IDs, new arrivals, duplicate
snapshots and session restart.

## Stage 5 — high-speed projectiles that vanish within one tick

A real rail, rotary, flak, kinetic, rocket or guided shot can be launched
and physically destroyed between successive display frames. Interpolating
only *live* projectile lists cannot show this flight because the projectile
no longer exists at render time.

`ShortProjectileFlight` pairs actual **FIRE** and **IMPACT/INTERCEPT/TIMEOUT**
events with identical shot ID and authoritative tick, reconstructing only
the recorded start-to-terminal segment for one presentation tick. It uses
the existing kinetic/guided asset atlas, never creates a physical body,
changes collision timing, or invents a target. The event-backed draw is
bounded to 256 transients; more shots drop cosmetic replays only. Terminal
impact art waits until the final part of that visual interval instead of
appearing before the visible projectile arrives.

The shared `:game:renderPosesSmoke` covers a real segment's endpoints,
intermediate position, orphan terminal rejection and tick expiry.

## Stage 6 — reuse hull interpolation within a displayed frame

At low tactical speeds the renderer may draw many frames for each real
physics tick. Camera bounds, ship art, hardpoints, shields and overlays
repeatedly request the same ship's smoothed coordinates during a single
frame. `BattleRenderPoses` now memoizes each hull's rendered pose once
per presentation fraction. Caches invalidate on physics steps, battle
changes, pause/snap and each rendered fraction, and recheck raw physical
coordinates even within one frame so legitimate teleports cannot be
stuck behind a cached visual.

This reduces repeated cubic calculations and per-pass Pose allocation.
It is a rendering optimization only: **do not claim an FPS gain until a
comparable device trace is captured**. The headless smoke suite checks
same-frame identity, fraction invalidation and real midframe relocation.
