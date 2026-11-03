# Fixed battle envelope and projectile lifetime policy

**Mode:** Catastrophic Diplomacy is an auto-battler. Fleet orders are chosen
before play; ships move, fire, retreat and deploy craft autonomously. Fog and
camera framing are presentation layers; neither may resize the simulation.

## One arena for every fleet size

- World-space bounds: **X = -1280 to +2560**, **Y = -720 to +1440**.
- Fixed world dimensions: **3840 × 2160**; center **(640, 360)**.
- Initial 1280 × 720 tactical deployment remains centered inside this world.
- Maximum ships: **200 per team / 400 total**, whether spawned as homogeneous
  wings, mixed fitted fleets or separate ship additions.
- Every entry point preflights capacity. A request exceeding the limit fails
  before partially placing a wing/fleet. Initial ships must be inside the world.
- Ships remain in-bounds under AI search, pursuit, queued movement, drifting
  inertia and HOLD orders. Outward velocity is stopped at a world boundary
  without adding momentum, power or fuel. Craft coordinates are also clamped.
- Existing rear-edge retreat extraction remains at X=0 for team 0 and
  X=1280 for team 1; the larger world provides maneuvering room, not a change
  to campaign extraction eligibility.
- No camera zoom, formation size, sensor range or projectile trajectory can
  expand the arena beyond these fixed limits.

## All physical projectiles have finite lives

| Family | Maximum lifetime | Additional cleanup |
| --- | --- | --- |
| Guided missiles and torpedoes (including decoys) | 20 seconds absolute; existing fuel may expire earlier | Despawn immediately on leaving world bounds or losing their target |
| Unguided rockets | 10 seconds absolute; normal life is 7.5 seconds | Despawn on leaving world bounds |
| Kinetic shells | 40 seconds absolute; normal flight is range/speed (usually much shorter) | Despawn on leaving world bounds |
| Rotary CIWS physical bullets | 3 seconds absolute; mechanical range/speed expires first | Despawn on leaving world bounds |
| Rail-point-defense penetrators | 3 seconds absolute; mechanical range/speed expires first | Despawn on leaving world bounds |
| Flak timed bursts | Existing scheduled finite burst tick | Never detonate beyond the world border |
| Instant energy beams, repair and support visuals | Existing short effect lifetimes | No long-lived travelling body |

The 40-second kinetic upper bound is intentional: slow physical rounds must
not silently lose their legitimate researched range just because a short
arbitrary lifetime was introduced. Most real kinetic shots expire in a few
seconds. No projectile survives indefinitely, and an outgoing miss disappears
as soon as it crosses the border rather than running forever or enlarging
any spatial indexing / camera / game-world extent.

Swept collision checks run **before** the world-edge expiry of the projectile
within that tick: a fast physical round cannot lose a valid in-arena impact
simply because its end-of-tick coordinate falls beyond the border.

## Functional verification

`gradle --no-daemon :core:battleArenaSmoke` runs the dedicated Java 17
headless `BattleArenaLimitsTest`, covering full 200v200 deployment,
oversize rejection and atomicity, ship inertial confinement, hard age expiry
for five physical projectile types, and outward edge crossing.

The arena check is chained into existing `gameFlowSmoke` and the existing
headless `rotaryCiwsDiagnostic` workflow. No new workflow, GPU check,
desktop package or Android APK build is required for this focused test.
