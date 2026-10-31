# Guided Ordnance Behavior Matrix — design and implementation specification

**Project:** Catastrophic Diplomacy  
**Status:** DESIGN / AUDIT ONLY. Do not mark any proposed feature below implemented.  
**Authority:** reviewed against `core/src/main/java/diplomacy/sim/{Battle,OrdnanceDoctrine,OrdnancePropulsion,ProjectileModel,AmmunitionTable}.java` on 2026-10-30.  
**Scope:** Java 17 deterministic shared combat engine; Android/Fire OS and Windows/Linux share identical mechanics.

## 1. Verified baseline and gaps

The existing seven `OrdnanceDoctrine.Mode` launch patterns are `SINGLE`, `RIPPLE`, `SATURATION`, `MICRO_SWARM`, `HEAVY_STRIKE`, `COLD_LAUNCH`, `DECOY_CLOUD`. The simulator creates physical, interceptable missiles or torpedoes, charges each shot finite ammunition, heat and power, preserves launch warheads and schedules launches by simulation tick. Existing warheads: standard, armor-piercing, fragmentation, high-explosive, EMP and shield-breaker.

Four implemented propulsion modes are `CONSTANT_BURN`, `COLD_START`, `BOOST_COAST_TERMINAL`, `PULSE_BURN`. The engine publishes flight phases for the renderer. However:

1. Missile `vx,vy` are a heading vector, not a persistent world-units/second velocity. Each tick multiplies a constant base speed by a phase modifier. Changing from boost to coast therefore *instantaneously changes speed* without acceleration or conservation of momentum. This is not physically meaningful silent coasting.
2. `COLD_LAUNCH` means a brief ignition delay, not a selectable long unpowered cruise. The flight path still advances at 20% base speed during the delay.
3. PD acquisition and chance use the static `m.spec`. Motor shutdown does not dynamically lower infrared/emission signature or produce a real reacquisition problem. Cross section must remain independently detectable.
4. Swarm weapons are multiple distinct missiles but have no swarm coordination, distribution or cooperative terminal attack.
5. No independent seeker type, waypoint/dogleg, pincer, loiter, terminal retargeting, home-on-jam, submunition bus, interceptor or evasive steering profile.
6. The `OrdnanceDoctrineTest.coldIgnition` assertion expects a static canister despite the current `COLD_EJECT` phase moving at 0.20x base speed. Reconcile expected physics and the test before claiming it passes. `OrdnancePropulsionTest` exists, but no explicit task registration was found in `core/build.gradle`.
7. Existing `OrdnanceDoctrine.Mode` maps one salvo to one motor schedule. This prevents mixing, for example, an eight-body swarm with a cold-coast-sprint flight plan.

These are gaps, not grounds for falsely describing the current system as broken in every respect. Preserve existing real hit detection, physical PD, deterministic seeded replay, ammunition, emitter-origin events and faction/technology modifiers.

## 2. Four independent configuration dimensions

Do **not** define every combination as a new weapon class. A fitted missile or torpedo launcher specifies:

- **Launch pattern:** single; twin; timed ripple; saturation; micro swarm; split-pair/pincer; dispersed decoy screen; synchronized salvo; delayed sequence. Controls real body count, launch interval, spread, launcher cost.
- **Propulsion / flight schedule:** continuous burn; cold eject and ignite; boost/coast/terminal burn; pulse thrusters; long silent ballistic drift; sustained heavy low-thrust; terminal sprint; multistage booster; short-burst maneuvering thrusters. Controls *actual acceleration*, remaining propellant, burn emissions and energy state.
- **Guidance / maneuver doctrine:** pursuit; lead intercept; inertial waypoint; dogleg; evasive weave; spiral/offset; converging pincer; cooperative target allocation; passive sensor track; home-on-jam; loiter then attack; opportunistic reacquisition; terminal hard-over. Controls steering and targeting within the motor's remaining maneuver authority.
- **Payload:** existing six warheads plus later fragmenting bus, cluster/submunitions, anti-fighter fragmentation and anti-missile interceptor payloads. Controls physical damage/electronics/shields and interceptable child bodies.

These axes are **not unrestricted**. Each module/hull/tech tier must gate compatible combinations, ammo requirements, seeker quality, payload mass, thermal and signature consequences. A low-tech micro launcher cannot mount a strategic heavy warhead merely by changing a menu.

## 3. Distinct combat roles and proposed presets

These presets are *design targets*, not claims of existing functionality. Default figures are intentionally unspecified pending balance tests.

| Preset | Body/salvo | Flight and terminal behavior | Tactical identity |
| --- | --- | --- | --- |
| Standard seeker | 1 | Continuous burn, lead intercept | Baseline guided attack |
| Ripple volley | 3+ | Staggered, target-persistent pursuit | Sustainable PD pressure |
| Saturation salvo | 5+ | Dense timed salvo, varied offsets | Overload a local defense battery |
| Micro Swarm | 6–12 | Tiny independent motors; **cooperative target selection** and staggered impact windows | Difficult allocation problem for PD; individually fragile |
| Needle Dart | 1–2 | Small cross section, high terminal agility, modest warhead | Anti-escort precision missile |
| Heavy Payload / Siege Torpedo | 1–2 | Slow, armored, long burn or boost/coast | Threatens capital hulls; very interceptable if spotted early |
| Cold-Ejected Canister | 1 | Unpowered eject, delayed safe-distance ignition | Minimize launch flash; early phase interceptable |
| Silent Ghost | 1–2 | Initial boost, physically inertial long coast, late seeker wakeup and motor restart | Low emissions in midcourse, but nonzero RCS; punishing if initial vector misses |
| Terminal Sprinter | 1–2 | Efficient cruise with saved propellant for final high-acceleration sprint | Compress PD reaction time near impact |
| Pulsed Raider | 1–4 | Short thrust pulses with coast intervals; optional randomized phase | Erratic visibility and controlled maneuver budget |
| Dogleg Ambusher | 1–2 | Stored offset waypoint before final intercept | Circumvents direct PD concentration; costs flight time |
| Pincer Pair | 2+ | Each missile chooses opposite offset corridor then converges | Forces shield-sector and PD coverage tradeoffs |
| Weaving Evasion | 1–4 | Bounded lateral jukes under thrust, no free steering while coasting | Harder aim for kinetic PD, more propellant consumed |
| Decoy Escort | 1 real + decoys | Decoys physically emit and occupy PD attention; real missile has independent route | Trades magazine capacity for defense penetration |
| Lurker | 1 | Unpowered drift/loiter until armed target trigger, then light motor | Area denial without permanent engine glare; lifetime bounded |
| Home-on-Jam | 1–2 | Passive tracking against an active jammer, fallback on inertial estimate | ECM counterplay, not universal perfect homing |
| Cluster Bus / MIRV | 1 parent, bounded children | Booster transports a physical bus; splits once near terminal region | Large payload with finite child-ammunition accounting; all children interceptable |
| Fleet Defense Interceptor | 1–4 | Fast lead intercept against enemy ordnance/fighters | Dedicated anti-missile weapon that competes for magazines and sensors |

**Additional ballistic ammunition patterns** (unguided unless explicitly fitted with guided munitions): high-velocity penetrator/sabot, slow armored siege shell, programmable proximity flak, flechette fan, buckshot canister, fragmentation burst, timed airburst, delayed-fuse shell and armor-piercing kinetic slug. Do not fake a curved gravity arc in open space. Ballistic muzzle velocity, recoil, prefire lead, dispersal, fuses, burst count and physical interception should be independently selectable from impact effects.

## 4. True physical flight contract

Each missile owns: `position`, `velocityWorld`, `maxAcceleration`, `turnRate`, `propellantRemaining`, `motorPhase`, `seekerState`, `lockedTargetId`, `lastKnownTarget`, `waypoints`, `integrity`, `warhead`, `emissionsByPhase`, `physicalCrossSection`, and one immutable validated schematic. Retain event IDs and spawn ticks.

- **Coasting:** velocity persists unchanged in free space except for simulation-defined collisions or gravitational field features; zero propulsion means no unexplained deceleration. Steering with no thruster authority cannot bend trajectory.
- **Powered thrust:** change velocity by bounded acceleration along the motor's controlled thrust direction, with `DT=1/30` and consumed propellant. Cruise/boost/terminal must differ in acceleration and propellant consumption, not merely a new teleporting speed multiplier.
- **Cold launch:** ejection impulse may move the canister slowly; *choose one tested behavior*. No ignition until a safe distance or scheduled tick; the missile remains a physical PD target throughout.
- **Silent flight:** reduced *emission* and flame/smoke effects, not zero radar/optical cross section or invulnerability. Reevaluate sensor acquisition using current phase and seeker emissions, and apply PD fire according to real track continuity and lock quality.
- **Guidance:** support target lead based on observed relative velocity and seeker accuracy; chase-only behavior can remain the cheap baseline. Turning, jinking and retargeting must consume a real maneuver budget.
- **Terminal behavior:** commit to a target sector, then execute saved sprint/retarget/fuse/fragmentation decisions; if source or target disappears, use an explicit and bounded doctrine-specific policy (continue inertially, reacquire permitted hostile, loiter or safe self-destruct).
- **Interception:** physical RCS, emission, size, instantaneous world velocity, integrity and PD tracking matter. Decoys must have zero damage; fragment and bus objects must preserve source attribution and finite ammo/child budgets.
- **Swept collisions:** maintain swept-segment or equivalent continuous collision detection for every high-speed parent and child. No hit from an animation.
- **Randomness:** use only seeded simulation RNG with stable ordering. No `System.nanoTime`, runtime rendering state or unseeded graphical randomness in combat logic.

## 5. Counterplay and UX

A high-signature heavy torpedo should be discovered early but require sustained PD damage; a cold/coasting needle should be harder to acquire during unpowered flight but easier to defeat once acquired. An evasive missile trades speed, range and fuel for survivability. Pincer attacks matter because defensive firing arcs, shield facing and shared PD cooldown are finite. Countermeasures should force actual selection errors, not scripted immunity.

In the **Shipyard**, present compact independent selectors for **Salvo / Flight / Seeker / Warhead**, filtered by fitted module and authorized tech. Describe effective bodies, magazine draw, approximate flight duration, expected damage, burn/coast intervals, cross section and dynamic signature. Show incompatible choices disabled with concise reason. Keep campaign and skirmish/PvP tech authorization distinct but drawing from the same unified nine-level research tree; lower-tech designs remain valid in higher-tech matches.

In **battle and battle screensaver**, renderer reads authoritative phases: ejection, boost flare, dark coast (no engine plume), ignition flash, terminal burn, breakup, decoy and interception. Camera may follow any real missile or fighter without influencing simulation. Existing visuals and effects must stay small and readable at tactical zoom.

## 6. Proposed implementation order and objective tests

### P0 — Correctness and regression (before expanding presets)

1. Reconcile `OrdnanceDoctrineTest.coldIgnition` with actual desired ejection motion; never change the test merely to make it green without naming the desired behavior.
2. Wire `OrdnancePropulsionTest` into the actual core test workflow.
3. Replace phase-specific instantaneous speed multipliers with persistent velocity and acceleration, preserving the 30 Hz deterministic core and existing mission clock.
4. Give engine phases dynamic emission signatures consumed by detection; preserve cross-section independently.
5. Add seeded tests proving: unpowered velocity conservation, no free unpowered turns, finite fuel, phase transitions, cold-launch survivability/detectability and terminal interception.

### P1 — Modular combination

6. Split salvo, motor schedule, guidance and payload into independent validated specs; retain a migration path mapping all seven old modes to exactly their prior semantics where possible.
7. Introduce direct pursuit, lead intercept, dogleg and terminal sprint, with shipyard selection and tech gating.
8. Add end-to-end hardpoint/magazine accounting and per-mounted launcher origins; ensure no orphaned launch queues when a ship dies.

### P2 — Tactics

9. Add micro swarm coordination, pincer, bounded weave, decoy discrimination/home-on-jam and loss-of-lock policies.
10. Add a once-splitting cluster/MIRV bus and anti-ordnance interceptors with hard caps.
11. Expose standing orders for coordinated target selection and friendly-defense missile priority.

### P3 — Performance, diagnostics and finish

12. Profile 200v200 and screensaver continuous rerolls. Bound active ordnance (current global guard: 12,000), spawn work, child counts, targeting cost and sensor search. Never silently drop a missile after paying ammo.
13. Record step traces: motor phase, world speed, acceleration, propellant, signature, seeker lock, PD acquisition/attempts, interception and damage. Make replay diagnostics exportable.
14. Visual verification: captured examples of each motor phase, terminal approach and PD interception at close camera zoom. Cosmetics remain **diagnostic**, not a release-blocking test.

**Required functionality gates:** the Java build launches and runs; ammo/heat/power costs are exact; missile and child collision/interception work; time steps are deterministic and finite; no zero-cost weapons, unbounded spawns, or multiplayer desync; applicable update/release machinery still works. **Not gates:** artistic appearance, effect size, balance judgement and subjective style.

## 7. Acceptance scenarios

- **Cold-to-ghost:** cold eject -> quiet coast for measured distance -> timed terminal light. Confirm invariant velocity when unpowered, lower *observed* signature than burning, still detectability from cross section, real PD kill possible.
- **Micro defense:** eight true bodies; PD may destroy some and let others through; exact ammo/heat; independent IDs and finite collisions; outcome repeatable with same seed.
- **Pincer shields:** two routes are actually spatially distinct and reach separate defenders' sectors; finite steering cost and real tactical coverage effect.
- **Heavy siege:** slower torpedo has demonstrably greater physical durability, is detected sooner when burning, requires several actual PD hits, and never teleports across a target.
- **Decoy screen:** decoys consume ammo and can be acquired/intercepted, but they never inflict warhead damage, even when overlapping the hull.
- **Split bus:** no duplicate damage on parent split; exact bounded children; parents can be killed before deployment; children can be independently killed or miss.
- **Lost target:** an invalid/dead target cannot cause infinite lifetime, illegal friendly retarget or NaN motion.
- **Long-run stress:** repeat identical seeded fleets with every legal mode; equal fingerprints for equal seeds, sane frame/memory cost, no hangs or continually expanding queues.

**Design conclusion:** missile *size* and *warhead* should not determine a missile's entire behavior. Compatible launch, motor, guidance and payload components create distinct tactics without a combinatorial forest of copy-pasted projectile classes.
