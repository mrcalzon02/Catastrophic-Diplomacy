# Light-minute battlefield, probabilistic target seeking and physical aim error

**Implementation underway • 2026-11-03 • Stages 0–2 include sublight geometry, light-time history and measured-time signatures. Operational scanning, causal STANDARD targeting, sender-origin SHR/LNK delivery, finite kinetic compute, target-leading pips and first physically intersecting main beams are integrated. Guided seeker prediction, actual beam photon flight-time, persistent scan UI and 200v200 balance remain outstanding.**

## Design intent

Our preordered auto-battler should not grant every cannon knowledge of the
instantaneous position, future acceleration, or identity of a distant hostile.
Detected, identified, tracked, fire-control-grade and *actually struck* are
separate events. An inaccurate solution can still strike a ship when the
actual projectile/ray crosses its silhouette. A correct solution can miss when
the target maneuvers after observation. Weapon impact is physical; no binary
"incorrect track means automatic miss" rule.

Current foundation: 11 factions, 506 source-verified faction/hull variants,
fixed 3,840×2,160 arena, 200 per side, 30Hz; RDR/IRT/XRY/GRA/ACU and stealth
countermeasures; SHR/RES/TRK/LNK/FCM/CAP networks with separately tested
read-only command observations. Preserve all their hardware IDs, researched
variants, socket indices, existing saves and original sprite artwork.

## Implemented in the current production model

`WorldScale` now fixes the existing 4,405.814-unit arena diagonal to
17,987,547.48 km; one simulation second represents **60 physical seconds**.
This is explicit gameplay time compression, not a claim that the 30Hz tick
is physical realtime. The physical speed of light is 73.430 world units
per physical second, or 4,405.814 world units per simulation second.
One-way photons traversing the diagonal need 60 physical seconds,
**30 combat ticks**; a round-trip radar echo requires **60 ticks**.
Future sensor work must use measured-state time history, never instantaneous
world coordinates.

`WorldScale.SHIP_WORLD_SCALE=0.50` is now applied to
`Battle.Ship.visualScale()`; the same factor is already consumed by the
original ship renderer, fitted turret/hardpoint transforms, physical
rocket/ballistic hull overlap, damage/shield surface effects and world
selection/culling. Approved source PNGs and blueprint socket IDs are
unchanged. Physical ship lengths remain **abstract glyphs**, not literal
69,000-km hulls; exact collision/range balance needs retesting.

`SublightPropulsion` replaces instantaneous target-velocity smoothing
with finite physical acceleration, engine fuel spent on delta-V and
inertial coasting on empty fuel. Original ENG hardpoints, count and
existing nine-stage upgrade level select drive profiles:

| Installed machinery | Drive | Maximum physical speed | Max acceleration |
| --- | --- | ---: | ---: |
| Bare hull, no paid ENG | Auxiliary electric | 0.1% c | 0.3 g |
| One standard ENG | Fusion torch | 0.6% c | 3 g |
| Two ENG units or ENG stage IV-VI | Advanced fusion | 1.2% c | 6 g |
| ENG stage VII-IX | High-energy torch | 1.8% c | 9 g |

These are **speculative far-future upper bounds**, not today's
flight-proven propulsion. Actual speed is no higher than both the
engine class cap and the existing purchased/fitted speed rating,
multiplied by physical damage and crew condition. The class defines
a maximum possible cruise speed accumulated over long periods,
*not* the instant speed an engine creates from rest. Search, pursuit,
retreat, waypoints, hold/braking and command maneuvers debit the same
acceleration/fuel pathway; powerless coasting does not receive free
vacuum friction. The battle fingerprint includes propulsion mode and
velocity to detect state mismatch.

`WorldScaleAndPropulsionTest` (run automatically through
`:core:battleArenaSmoke`) asserts physical photon delays, cruise caps,
installed engine count and research tiers, real delta-V/fuel and coast,
the 50% geometry factor and aligned original-art muzzle transforms.

**Stage 2 partial implementation:** each ship now records 128 immutable
position/velocity/alive snapshots. Tactical contacts are derived from a
timestamped physical target sample instead of present-time coordinates.
Photon delay estimation now uses recorded sensor and target positions, not
the target's present hidden world coordinates. Each independently rounded
radar/X-ray leg consumes its own arrival interval.
Passive optical/IR/gravitic observations require one-way propagation;
continuously active radar/X-ray echoes require round-trip transit before
the reflected sample is revealed. Relayed tactical reports incur another
one-way propagation hop. Paid TRK contact memory stores the arrived
measurement, not hidden target truth. The focused
`:core:sensorTacticalPictureSmoke` invokes `:core:sensorLightTimeSmoke`.

**Stage 3 initial operational scan doctrines:** `SensorActiveScan`
is now wired into the authoritative battle tick, sensor-channel range,
read-only tactical display and pulse-mode weapon contact authorization.
`Battle.configureSensorScan(shipId,mode,focusId)` accepts pre-engage
runtime orders. Existing equipment and hardware IDs remain unchanged:

- `STANDARD`: continuous powered radar/X-ray reception, now restricted by
  real round-trip source-emission history before a weapon contact is granted.
- `PASSIVE`: radar and X-ray shut off; optical/IR/gravitic capabilities
  remain independently determined by existing equipment and visibility.
- `BOOSTED_RADAR`: 1.55× RDR range, a paid pulse every 18 combat ticks.
- `FOCUSED_RADAR`: 1.28× RDR range only against a designated target,
  with an individually recorded pulse every 12 combat ticks.
- `XRAY_BURST`: 1.45× XRY range only against a designated target,
  expensive emission every 60 combat ticks; absent XRY cannot emit.

Scan pulses spend real ship power, generate heat, obey damaged sensors,
crew/reactor restrictions and thermal ceilings. A selected active mode
cannot supply a channel until its actual archived emission could return
as a physical echo. The deterministic battle fingerprint includes the
scan doctrine, finite pulse history and heat state. These numerical
multipliers and costs are provisional gameplay balance. A nominal range
bonus is not an instant lock or extra omniscient data.

`:core:sensorNetworkSmoke` now includes `:core:sensorActiveScanSmoke`,
`:core:sensorLightTimeSmoke` and `:core:sensorTacticalPictureSmoke`.
The focused existing headless diagnostic runs these without a full
Windows/Linux/Android package build.

**Stage 4 initial causal targeting and SHR transit:** STANDARD acquisition
and both captain target-priority branches require actual arrived optical
or electronic evidence. Firing geometry receives an immutable historical
target sample rather than relying on current target position alone.
Sender ships maintain `SensorRelayBuffer`, at most 32 contact identities
and 32 immutable transmissions per contact; a receiver can read a packet
only after the originating scout actually acquired and transmitted it and
the additional light-speed relay interval elapsed. Powering sensors on
cannot manufacture earlier outbound radar/X-ray pulses: emitter power is
captured with the original historical sample. Relay queues participate
in the deterministic multiplayer battle fingerprint.

**First physically aimed kinetics:** `SensorTargetingSolution` computes
a finite, deterministic target ray from observation age, measured velocity,
projectile speed/lead, channel resolution and track computation precision.
Real travelling kinetic rounds now launch along this estimated ray, rather
than pointing at the presently hidden target. Existing swept intersection
decides actual contact, so slightly erroneous targeting can still hit, and
even an ostensibly good solution can miss. In one *geometric diagnostic*
of 160 deterministic misaligned rays against a radius-12 world-unit hull,
133 rays intersected and 27 missed; this is not a measured combat kill
percentage or final balance statistic.

**Stage 4 additional integrations:** `SensorLightTimeHistory.Profile` stores
historical radar cross-section, thermal signature, optical cloak, hull mass,
armor and installed sensor capabilities, bounded to 128 per hull. The local
acquisition and relay sender-choice paths check historical photon arrival and
measured signatures instead of the presently maneuvering target. A bounded
backward light-cone search retains echoes already in flight even after abrupt
target movement.

The separately fitted `SensorTrackRelayBuffer` transmits genuine TRK fixes
over LNK, respecting up to 32 identified tracks, 32 per-track buffered sends,
hardware slots, physical sender coordinates and receiver photon travel.
Neither tactical displays nor captain search may reach directly into
another vessel's live TRK map; remote historical packets cannot create
weapon lock permission. Physical LNK buffer state enters replay fingerprints.

`SensorComputeBudget` establishes a finite per-ship, per-combat-tick work
pool using ACU/RES/TRK capacity, command aid, crew, reactor and sensor health.
Distance, age and competing recorded hypotheses raise a quadratic-like work
cost. The real kinetic firing path reserves work before solving a shot; when
insufficient, its uncertainty increases rather than automatically missing.
CAP increases parallel fire-control slots but cannot add processor cycles.
Coefficients are provisional. Dedicated functional tests are included in
`:core:sensorNetworkSmoke`.

**Remaining Stage 3–5 scope:** sensor doctrine UI and saved orders, physical
active scan sector limits and emission detectability, per-channel time-varying
weather, explicit FCM shared-CPU arbitration between allies, richer maneuver
covariance and false-hypothesis competition, physical laser/plasma/particle
beam intersections, guided seeker uncertainty, and 200v200/Android endurance
and balance tuning. Current beam and many guided weapons still consume
hostile world truth after initial contact permission. Power and heat
costs and the precise drive/munition speed envelopes need further tuning.
Visual and balance diagnostics remain nonblocking; objective functional
failures require repair.

## 1. One light-minute *diagonal*, not endless simulation space

Set the arena's maximum corner-to-corner distance to approximately one
light-minute, without changing the boundaries:

- Fixed world: x=-1280..2560 (3,840 units); y=-720..1440 (2,160 units).
- Diagonal: 4,405.814 world units.
- Light-minute: **17,987,547.48 km**, from c=299,792,458 m/s.
- Illustrative physical unit: ~4,082.684 km/world unit.
- Light propagation constant: **c≈73.430 world units / real second**.
- Horizontal one-way light time: 52.29 s; vertical: 29.42 s; diagonal: 60 s.
- Passive optical/IR arrival reports the state at **observation timestamp
  earlier than receipt** by approx `distance/c`; active radar/X-ray echo
  has an approximate **round-trip** delay `2*distance/c`. Inter-ship
  command transmissions also incur propagation, not instant teleportation.
- Keep the max 200v200 arena and finite projectile lifetimes. No distant
  target or round may expand the simulation world.

**Physics consistency blocker to settle before claiming realism:** currently
a basic hull can move tens of world units per simulated second, some legacy
hulls move ~62 units/s, and projectile specs allow up to 1,500 units/s. This
is comparable to, or exceeds, **c=73.43 units/s** under a literal physical
mapping. Even the base drawn 34-unit-high ship would be ~138,811 km long
at 1× scale, and ~69,406 km after halving—still a *stylized tactical glyph*,
not a literal ship diameter. The project MUST NOT claim both literal light
delay and existing engine speed values are SI-consistent.

Adopt a two-layer contract rather than quietly changing all gameplay:
`WorldDistanceScale` documents the one-light-minute sensor range mapping;
`SimulationMotionScale` records the *explicit* combat speed/time compression
choice. Stage velocity normalization and projectile time-of-flight in a
separate, test-covered migration. No superluminal ordinary shells or hulls
should be described as physically realistic. If physical fidelity is chosen,
retune movement/projectile speeds and use battle fast-forward rather than
pretend the current units are already correct.

## 2. Halve ship size coherently (50% linear diameter)

Primary request: reduce ship hull rendering and its apparent physical extent
to **0.50×** the currently deployed game scale. Apply a shared dimensionless
multiplier; preserve relative hull-family differences, paid tech inventory,
faction source PNGs, exact approved image-space socket coordinates and
special-variant identity.

`Battle.Ship.visualScale()` currently controls ship sprite height (34×),
ballistic/rocket collision radius (12×), turret placement, hit effect dimensions
and `WeaponMuzzles` fitted-socket transforms. Changing only the renderer
creates invisible large targets and disconnected floating guns. Changing
`visualScale()` changes **real hit odds** and must be called a gameplay
rebalance rather than an innocent cosmetic fix.

Implementation choice: a single `SHIP_WORLD_SCALE=0.5` contract applied
consistently to the existing rendered hull, transformed hardpoints,
collision radii, damage overlays, shields, selectable hitboxes and physical
turret locations. Audit hard-coded other ship radii and hull-relative
measurements first. Keep editor/shipyard preview controls separately
legible; do not halve Android button sizes or touch target accessibility.

We should eventually distinguish `renderMagnification` from
`physicalCollisionRadius` and use an explicitly stylized visual glyph.
At light-minute physical scale even half-sized hull artwork is literally
tens of thousands of kilometres long, so the sprite size is a readability
choice and **not** a physical ship-length measurement.

Acceptance: same ship ordinal, modules, art alpha, local socket map and
campaign save; projectile muzzle on actual downsized hull; real swept-hit
radii on the same outline; stable click selection and no unclickably tiny
Android touch areas. Visual screenshots diagnostic only, objective hit/
muzzle/selection mismatches functional blockers.

## 3. Observation data and uncertainty

Use separate physical stages: **SEARCH → DETECTION → CLASSIFICATION →
TRACK → SOLUTION → RELEASE → IN-FLIGHT CORRECTION → IMPACT/EXPIRY**.
A contact may fail, be wrong, or persist as an uncertain last-known fix
without an authorized fire-control solution.

A source observation has: target hypothesis ID; source ship and channel(s);
sensor emission and object-measurement timestamps; receipt timestamp;
measured x/y and vx/vy (not instantaneous hidden target truth); estimate of
angular/range/velocity error; false-contact likelihood; confidence; and
power/heat/crew/detection provenance. Track records are bounded by physical
TRK memory and by LNK sender/receiver link capacity. CAP bounds simultaneous
target solutions. Never expose target truth to a solver merely because the
simulation owns those coordinates.

Represent each candidate target as **one Gaussian-like 2D uncertainty
ellipse and optionally a small bounded set of competing hypotheses**
(real ship, ECM ghost, decoy). No unbounded particle swarm or per-pixel
Monte-Carlo cloud. Axis-aligned or motion-aligned variances are adequate.

Provisional uncertainty law for each axis (units, not tuned constants):

`sigma^2 = sigma_measurement^2
         + (age_seconds * sigma_velocity)^2
         + (0.5 * age_seconds^2 * sigma_acceleration)^2
         + sigma_ECM^2`

Here `age_seconds = (currentTick - measuredTick)/30 + projectileETA`.
The measured tick already includes passive or active sensor propagation
history; do not double-count. Angular uncertainty alone grows into a larger
positional error proportional to distance. Maneuver unpredictability grows
quadratically with the prediction horizon. Higher range, smaller hull,
weaker signal-to-noise, exhausted power, weather, interference, ghosts and
unresolved tracks all worsen the solution.

A cheap bounded prediction uses **measured** `p+v*age` and optionally
measured acceleration from recent valid fixes. Under no circumstances
consult the actual target velocity at fire tick unless a fresh physical
observation has arrived. Use a compact deterministic solver, not a
high-iteration orbital optimizer.

### Errors that may still hit

At fire time select **one deterministic aim-point error offset** from the
solution's ellipse, using the existing shot seed/identity and sensor error
quality. A kinetic, rail or rocket body travels that physical angle. A
near-but-wrong aim point can intersect the downsized hull and cause the
**normal actual impact**. An apparent correct center aim can miss because
the target later accelerates. A decoy-aimed round can accidentally intersect
a real ship on its path; it does not receive a fake "lucky hit" buff.

Do not add a separate binary `if(targetingDataWrong) miss` gate. The current
`Battle.fire` laser/plasma/particle energy families use immediate
Bernoulli hit rolls; migrate carefully to an aim ray/short finite travel
event or a geometry-overlap test before removing those rolls. In the interim
do NOT add a second arbitrary hit roll on top of the existing probability.

### Latency and physically meaningful information

For a source-target separation `d`:
- Passive receipt age ≈`d/c`.
- Active monostatic illumination and echo age ≈`2d/c` between initial
  probe and return (target state at reflection is midway through that trip).
- Routed allied signal adds another sender-receiver propagation and finite
  throughput. A shared track may be intrinsically older than a local fix.
- Projectile interception horizon adds `distance/projectileSpeed`, plus
  acceleration and coasting phases where appropriate.
- Missile terminal seeker can update a new solution based on actual onboard
  measurements without giving launch ship omniscient target access.

A gameplay mode may compress the resulting time scales, but it must declare
which clock is physical and which is compressed. Until the speed model is
consistent, treat these as an explicit staged design contract, not already
implemented relativistic physics.

## 4. New search/acquisition *modes*, using existing equipment

**Do not create a duplicate sensor tree.** Extend modes/upgrades of existing
RDR, IRT, XRY, GRA, ACU, SHR, RES, TRK, LNK, FCM and CAP modules, respecting
normal power, heat, crew condition, tech tier and fitted sockets.

| Mode | Existing systems | Benefit | Non-negotiable drawback |
| --- | --- | --- | --- |
| Wide-area boosted radar sweep | RDR, RES | Long range, many low-grade potential contacts per scan | Broad low-precision returns, power/heat, long echo delay, detectable active emission |
| Narrow-beam fire-control radar | RDS/RDA, RES, TRK | Long dwell and precise range/velocity on one sector | Narrow field, sweep blind spots, expensive compute and exposure |
| Targeted high-output X-ray probe | XRY/XRS/XRA, RES | Hard-to-fake contrasting return useful for close-to-mid-range identification | Extremely expensive energy, heat, cooldown, physically weak distant return; speculative future hardware, not a magic armor-vision ray |
| Passive IR telescope / spectroscopy | IRT, RES | Detect hot engines and thrust changes without illuminating target | Thermal masking, low angular resolution, distant aging |
| Multi-baseline optical interferometry | FCM + LNK + optical | Cross-correlate genuine spatially separated observations | Needs several live sensors, timing and bandwidth; cannot eliminate light-delay |
| Gravitic mass corroboration | GRA, RES | Independent, weaker clue against some heat/radar ghosts | Slow/low-resolution, only when dedicated hardware fitted |
| Track reconstruction & maneuver forecast | ACU + TRK + FCM | Lowers velocity/acceleration uncertainty from historical fixes | Consumes bounded computation; high target maneuverability remains unpredictable |
| Terminal seeker cue & reacquisition | Existing missile/torpedo guidance | Corrects accumulated launch error near target | Onboard seeker range, ECM, power, guidance and fuel remain physical limits |

Active pulses should reveal the scanner with a propagation-aware emission
signature to enemy passive receivers. Scan modes are selected before pressing
`ENGAGE` through ship orders/doctrine (e.g. PASSIVE, SEARCH, FOCUSED,
EMERGENCY PING), not realtime tactical micromanagement.

## 5. Bounded computation / target solutions

Each surviving ship receives a **per-second/tick compute budget** derived
from paid ACU/RES/TRK and crew/power, not a free global fleet solver.
FCM contributes a small finite shared pool to a bounded number of allies
within range. CAP defines **parallel solutions**/engagement capacity and
must never substitute for actual CPU, sensor observations or weapon mounts.

Proposed normalized planning law (not committed balance):

`cost = base * (1 + alpha*normalizedRange)^2
             * (1 + beta*observationAge/60)
             * (1 + gamma*competingHypotheses)`

Fast close-range locks are cheap; intermittent long-range targets demand
more tracking updates, more confidence work and longer dwell. If resources
cannot fund a solution, a ship may make a lower-confidence shot if doctrine
permits, keep searching, allocate another pulse, or decline to fire. False
hypotheses also consume bounded tracking slots and compute; this makes ECM
deception economically meaningful.

Performance contract: do not recompute every ship vs every other ship
against several hypothesis samples every combat tick. Sensor sweeps use a
scheduled staggered cadence, a finite max candidates per ship/sector, cached
tracks and bounded memory. Use a sampled ring buffer or timestamped event
queue for delayed observations; choose whichever achieves actual light-delay
in acceptable Android memory. A 10Hz, 120s, 400-object, four-float history
is roughly **7.7 MB** before index/metadata, so a compact history is
plausible; a naive per-source/target/per-tick event log is not.

## 6. Gameplay readability and incentives

Tactical HUD should show the **selected** contact's uncertainty ellipse,
last observation age, confidence and source/channel, plus whether the
ship is SEARCHING, TRACKING, SOLVING or LOCKED. A very stale ghost can be
visible as a grey expanding last-fix ring rather than an actual hull.
Limit full ellipses to selected targets or a capped few important tracks
so the 200v200 screen is still legible. Keep Android text/touch targets
responsive, not desktop UI scaled down.

Different enemy tactics now have genuine counterplay:
- Keep moving → old trajectories become unreliable; unguided rockets lose
  more than terminal-guided missiles.
- Change direction → acceleration uncertainty grows; powerful early radar
  cannot perfectly foresee later maneuvers.
- Manage emissions → passive visibility versus active scanning confidence.
- Deploy ECM/ghosts → waste enemy track and computation capacity; independent
  corroboration may reject false candidates without direct access to truth.
- Buy RES/TRK/ACU/FCM/CAP → real quality, compute, track retention, fusion and
  capacity advantages, not a universal `+10% accuracy` buff.
- Fight close → dramatically reduce observation age, projected uncertainty,
  solution cost and projectile response time.

## 7. Safe integration sequence

**Stage 0 — scale audit and 0.50× geometry:** audit every
`visualScale()`, hard-coded hit radius, hull selection, muzzle transform
and visual emitter. Make one shared multiplier and adjust physical overlap
tests as a functional change. Keep snapshot/screenshot comparison as
diagnostic. Do not alter source sprites or save IDs.

**Stage 1 — define actual world/time contract:** commit world light-minute
calibration and explicit simulated velocity/time interpretation. Baseline
existing speeds, ammo and missions; decide real-speed versus time-compressed
mode before making light-delay a combat blocker. Preserve fixed arena and
lifetime caps.

**Stage 2 — provenance and delayed observations:** extend existing
`Battle.TacticalContact`/TRK tracks; introduce a capped history or event
scheduler. Require that a viewer's delayed position derives from an
old measured state, not current hidden truth. Add passive and active
roundtrip timing fixtures.

**Stage 3 — active search modes:** implement boosted/wide radar,
narrow radar and targeted X-ray bursts with explicit power/heat/exposure,
cone, scan cadence and TTL. Validate that an unfitted hull cannot do them.

**Stage 4 — uncertainty and computation:** one bounded per-target ellipse,
deterministic error growth, finite compute credits per track, capacity and
cooldown, false hypotheses and power/crew degradation. Wire real ACU, RES,
TRK, FCM and CAP without inventing free sensors.

**Stage 5 — physically resolved aiming:** replace binary Bernoulli for
appropriate weapon families with a chosen estimated aim point and real
path intersection. Ballistics/rockets first (already swept bodies), then
missile terminal updates, then lasers/plasma/particle once the travel-time
and geometry contract is settled. Preserve ordinary hull damage and
munition inventory; never double count a hit.

**Stage 6 — headless and device acceptance:** 1v1/16v16/200v200 paired
seed runs at close/mid/far range; static versus maneuvering/accelerating;
real versus decoy; sensor blackout; active pulse with no returned echo;
same-hull moving target with just-off-center aim; weapon arrivals after
old data; FCM contention/capacity; the 0.5× hitboxes and changed hit odds;
frame time and heap on Android plus desktop visual inspection.

Keep existing focused CI; **functional failure gates only** (bad launch,
wrong targeting permissions, corrupted saves, unresolved terminal battle,
missiles crossing nonexistent hitboxes). Confidence tuning, graphics,
visual overlays and balance tables are diagnostics.

## Decisions before implementing the light-delay physics

1. **Physical consistency:** Is a one-light-minute world literal with
   lower physical ship/munition speeds and fast-forward for gameplay, or a
   gameplay-compressed representation with explicitly abstract speeds?
   Do not silently mix those incompatible meanings.
2. **0.50× scope:** The user asked for *overall ship* scale reduction.
   Preferred is shared visual/physical footprint now, with touch UI kept
   usable; later separate sprite magnification and authentic hit radius
   once physical ship dimensions exist.
3. **Futuristic active X-ray:** treat a targeted X-ray burst as high-cost
   speculative sensor hardware, not a real-world radar-quality long-range
   reflected X-ray imager.
4. **Distant guesses:** doctrines may permit low-confidence blind fire,
   but every hit must derive from a real path through a target; stale
   contacts alone still must not silently grant automatic lock authorization.

**Implementation status:** Geometry, bounded historical photon arrivals,
temporal signatures, scan doctrines, causal STANDARD contacts, real SHR/LNK
packets and finite kinetics compute are committed and functional-tested.
`SensorLeadProjection` now exposes read-only target intercept positions,
contact-age uncertainty and line-of-flight predictions from measured
position/velocity. The existing tactical sensor overlay shows up to eight
lead pips and three unguided trajectories, including stale TRK/LNK fixes;
beam-only viewers still get a positional prediction, not a fictional
unguided flight line. These are estimates, NOT enemy future truth or
guaranteed hit points.

Main-ship LAS/PLA/PAR now emit rays toward deterministic historical
targeting solutions and damage the first truly intersected enemy hull
using finite physical collision silhouettes; near misses cannot damage
and slightly incorrect rays can strike a hull edge. First live tests
passed 6/6 laser, 4/4 plasma and 3/3 particle ray/impact parity on
high-acuity stationary-target fixtures (verified again in run 41708998332);
these are not ordinary combat hit-rate figures. Main beam delivery remains
instant in the current damage engine: photon time-of-flight is NOT yet queued.
Point-defense and fighter beam timing remain separate. Dumbfire rocket
salvos now freeze their aim from a delayed track and a finite
pre-salvo computation allocation rather than the present target state;
their launch-time and finite dual-rack reservation tests now wait for
photon arrival. Focused run 41708998332 verified `:core:beamRaySmoke`,
`:core:sensorLeadProjectionSmoke`, `:core:rocketBarrageSmoke`,
`:core:sensorNetworkSmoke`, the physical muzzle/PD diagnostics, and
`:game:compileJava` with actual Gradle `BUILD SUCCESSFUL`.
This proves Java simulation and client compilation, NOT on-device HUD rendering,
Android package signing, or final combat balance.

**Rocket flight-time prediction integrated:** `RocketBarrage.nextSpeed`
is now the shared fixed-step motor law for real unguided rocket bodies and
`RocketTrajectoryPrediction`. Boost/coast, sustained burn, fitted size,
research-stage thrust and the actual 7.5-second body lifetime drive a
bounded numerical intercept estimate. A historical contact's age/velocity,
not current enemy coordinates, determines the proposed rocket bearing;
the real salvo retains finite compute dispersion and never changes direction
after trigger. The HUD's rocket-mode pips use the same motor-specific
prediction, and unreachable targets receive a caution-colored pip with
no fictitious successful trajectory line. A dedicated smoke trial covers
16 size/motor/research combinations plus real distance/ETA parity and
no-intercept behavior.

**Workflow verification policy:** the existing no-package PD/flak workflow
now separates required Java functional tests and game client compilation
from optional PD fighter-wave balance reports. Actual Gradle failures in
`weaponMuzzleSmoke`, `sensorNetworkSmoke`, `rocketBarrageSmoke`
and `:game:compileJava` are release-significant objective failures,
not advisory successes. PD/flak balance remains diagnostic only.
The new focused Java check first ran on commit `c4fdb8f` (Actions
41710225241); verify the actual task output and full job conclusion
rather than assuming a green balance wrapper implies functional success.

**Guided ordnance observation and launch causality (2026-11-03):**
`OrdnanceSeeker.passiveReturn` now reads the target's historical position,
velocity, RCS, ECM strength and hologram-emitter status with one-way photon
delay. Live missile guidance and terminal propulsion states use delayed
measurements and bounded velocity extrapolation, not target present-time
coordinates. Physical collisions, payload detonation and parent-canister
release still test real current hull proximity. Fitted and legacy missile/
torpedo launch queues store one actual received targeting fix and retain
that fix for the remainder of their finite launched salvo, including
CAP-selected rack targets. The pending-launch snapshot participates in the
battle fingerprint; no queued round receives a free current-target update.

The historical seeker, ECM, lost-lock, decoy, cold-coast PD, actual cluster/
cascade deployment and deterministic replay smoke passed in Actions
41717830198. Eight separate guided rounds retaining their original
observed bearing while the target moved were verified in Actions
41718280159, with the complete required core and client Java smoke
also returning `BUILD SUCCESSFUL`.

**Important first-pass limitations:** the on-body passive receive solver
uses the missile's current own pose, rather than a recorded historical
moving-receiver trajectory; it is not yet an exact relativistic light-cone
solution for fast moving receivers. Hologram seeker spoof selection and ghost coordinates now consume the
same historical light-delayed emitter profile and measured pose as the seeker
return; live emitter movement or shutdown cannot edit already travelling
ghost evidence. Visual phantom rendering remains current-time for spectators;
a timestamped fleet-wide SHR false-contact stream is now implemented, but
independent multi-hypothesis weapons arbitration is not yet supported. Guided missile steering now uses a bounded deterministic per-seeker estimate
instead of consuming exact historical coordinates: historical ECM and
cross-section affect finite position/velocity uncertainty, measurement age
and range widen it, and fitted seeker quality narrows it. The sample in
SensorLightTimeHistory remains immutable ground truth for sensor modeling;
real missile guidance does not directly steer toward its exact coordinates.
Onboard multiple competing hypotheses, stochastic belief filters and
moving-receiver light-cone history are not yet implemented.
These limitations should not be reported as solved.

**Guided trajectory forecast integration:** `GuidedTrajectoryPrediction`
now numerically advances the actual friendly missile's current velocity
through the same `OrdnancePropulsion` motor phases, steering laws, finite
remaining delta-V and lifetime as its flight body, but only toward its last
photon-delayed and noisy target hypothesis. A read-only closest-approach ETA,
uncertainty envelope and at most 13 route points feed a maximum of three
launching-ship guided trajectories on the battle HUD. These are hypothetical
no-future-update paths, NOT enemy future truth, guaranteed hits or remote
sensor permissions. Coasting/empty-fuel bodies receive no fictitious turns.
Focused `guidedTrajectoryPredictionSmoke` is a required part of
`sensorNetworkSmoke`; visual layout/balance remains advisory.

**Fleet false-contact packet integration (2026-11-03):**
Fitted SHR scouts now relay bounded phantom hypotheses with separate
observation, transmission and light-limited arrival timestamps. Every phantom
competes with genuine contacts for the SAME limited SHR transmission slots,
rather than receiving a free infinite broadcast. Inactive historical emitter
returns produce delayed shutdown tombstones; old photons remain visible
until the newer information arrives. A 32-hypothesis, 32-report per-key
ring bounds memory; packet expiration prevents an abandoned phantom from
persisting indefinitely. `tacticalFalseContacts` is independent of true
`TacticalContact` and weapon authorization, and the battle HUD's false
contact markers now derive from genuinely received historical packets instead
of present-time enemy ghost geometry. This stage adds an actual, finite
fleet-wide false-contact picture and regressions, but NOT multi-hypothesis
weapon fire-control or target-ID substitution. Visual fidelity is diagnostic.

**Main mounted LAS/PLA/PAR photon travel (2026-11-03):**
Actual main-ship beam shots now retain immutable historical aimed bearings and
advance their physical photon fronts one finite light-speed step per combat
tick. Damage resolves only where the wavefront intersects a real living hull
at the later arrival time; moving ships can dodge and a mistaken bearing can
still graze a ship. Existing power, heat, selected laser subtype, module
muzzle, projectile ID and directional armor remain intact. Wavefronts are
replay-fingerprinted, visible beam endpoints progress instead of teleporting,
and unresolved-battle accounting includes pending energy. Headless temporal
collision and deterministic replay checks are required through
`beamRaySmoke`. Point-defense/fighter energy remains separate and has NOT
yet been migrated to this photon model. Finite 30-Hz segment collision is
not a complete relativistic moving-body light-cone calculation.

**Historical lock-quality consistency:** All ship-mounted firing families
now derive their lock-quality ECM and hologram penalties from the received
emitter profile rather than the enemy's present electronics. The lock-range
estimate uses only channels reported as actually received and the historical
radar/optical/thermal/armor signatures. This closes a second current-state
causality leak following the timestamped false-contact and finite photon-beam
changes. Lock scoring remains a balance law rather than a full probabilistic
hypothesis fusion solver.

**Observed false hypotheses now impose physical compute cost:** The existing
finite target-solution processor receives additional competition from
historically emitted holographic alternatives, not the opponent's present
hologram state. Main-beam, kinetic and unguided rocket solutions all pay
for extra observed alternatives and lose precision when overloaded.
Shutdown information only removes that burden after its photons arrive.
This stage does not claim probability-weighted hypothesis fusion or
remote false-report deduplication.

**Scale measurement plan:** A new optional real fitted
`FleetSensorPhotonScaleDiagnostic` runs independently repeated 16v16 and
200v200 engagements with real SHR/RES/TRK/LNK/FCM/CAP, RDR, LAS/MSL and HLP
installations. It measures direct/relayed/stale/false historical observations,
in-flight beams, guided ordnance, finite compute allocations, genuine combat
events, heap and wall clock. Seeded replay fingerprints and physical-state
sanity are checked. No absolute CPU speed, hit rate, device frame rate,
visual quality or memory budget blocks builds. The existing focused
PD/flak workflow performs this heavy test only on explicit
`[sensor-scale]` integration commits or manual dispatch; outputs are
CSV/Markdown artifacts, not packaged applications.

**Moving missile receiver timing (2026-11-03):** Each flying missile now
retains its immediately preceding own-ship receiver pose and timestamp
(three bounded scalar fields). Photon-arrived onboard seeker returns must
cross the expanding historical emitter wavefront between the receiver's
previous and current sample; already-passed pulses cannot be reacquired
by repositioning the missile. Launch-tick fallback uses the new body's
actual launch pose. The receiver's own history participates in deterministic
battle fingerprints. The short worldline segment is physically sampled at
30 Hz and does not pretend to solve continuously moving multi-light-minute
relativistic photon trajectories or give the missile future coordinates.

**Finite FCM compute donation (2026-11-03):** A paid powered fleet command
module owns one dedicated per-tick command-compute reserve, independent of
every individual ship's RES/TRK/ACU local processor pool. FCM assistance
no longer multiplies local capacity on all linked hulls. Instead, a finite
command reserve is consumed in deterministic recipient order, after the
recipient observation is at least two physical light-speed command hops old.
Real fitted command capacity and operational radius bound recipients.
Main beams, kinetics and rocket fire all consume the same actual local
and shared work pools; multiple recipients cannot duplicate shared credits.
The donor's spent credits, assigned donor IDs and recipient allocations
participate in replay fingerprints. This is a first finite shared-compute
model, not a full distributed Bayesian filter or peer-to-peer multi-FCM
allocation optimizer.

**Kinetic spoof causality:** Unguided kinetic rounds now derive false
holographic aim bearings from the same historically received emitter
profile and measured position as their real fire-control solution, not
the enemy's current fake-ship geometry.

**Post-FCM fleet-scale evidence:** The existing opt-in 16v16/200v200
sensor-photon diagnostic additionally records donor FCM requests and
exhausted shared-credit attempts separately from intrinsic ship compute
allocations. This permits testing whether real-world command coverage
and received-track age actually trigger sharing in the larger battle;
a lack of requests is reported as evidence, not fabricated into success.
The CSV and summary remain optional and never impose runtime speed or
target kill-ratio release gates.

**Finite fighter flare/chaff countermeasures (2026-11-03):** Six fighter
hull families now have model-specific flare/chaff magazines and research-scaled
upgrade quantities/cooldowns from actual fitted FTR modules; existing saved
fighter IDs and ship designs remain unchanged. An emitted countermeasure is a
bounded, individually moving and decaying, replay-fingerprinted world-space
object with exclusive infrared (FLARE) or radar (CHAFF) signature.
Working radar-controlled flak and rotary CIWS may aim physical shots at
adjacent fighter-owned chaff emissions, expending real ammunition/power while
the fighter continues to exist and is physically vulnerable to nearby blasts.
Actual flak and rotary diversions are counted only after a shot fires.
Optical laser/particle PD does not become blind because of radar-only chaff.
Flares are physically deployable from a finite per-craft magazine via the
battle command API, but IR autodeployment and guided missile diversion
require fighter-targeting infrared missile seekers that do not yet exist.
No fictional anti-missile flare effectiveness or renderer artwork is claimed.
A focused real-world flak test and deterministic replay are included in
the objective sensor-network smoke tests; visual quality and balance remain
advisory.

**Still pending:** multi-hypothesis weapon fire-control arbitration,
higher-resolution sub-tick receiver paths, probabilistic multi-hypothesis
seeker filtering and compute arbitration, higher-fidelity guided trajectory
envelopes with future onboard scan updates, complete
200v200 balance/performance, maneuver covariance, more elaborate multi-FCM shared-compute
arbitration, point-defense/fighter beam travel delay and persisted scan
doctrine controls. The rocket prediction is a constant-velocity TARGET hypothesis
over the real rocket thrust schedule, not a guarantee against maneuver.
Visual presentation and balance tuning remain diagnostic.
