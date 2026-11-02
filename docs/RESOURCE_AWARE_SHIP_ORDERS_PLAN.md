# Resource-aware ship orders: firing, targeting, propellant, resupply and damage

**Status:** implementation in progress. P0 audit and rocket-only tender correction
are committed. The first P1/P2 functional slice is committed: 29 captain settings (23
original plus six resource controls), CD-STANDING-3 legacy-safe persistence, live
ammunition reserves/energy-only/hold-offense/continuous ration/spacing and
whole-salvo commitments, with desktop/mobile editor page 3 and functional regression
tests. **CI validation for the new head is pending**: do not call this release-verified.
P3 precision tracking, per-weapon target selection, P4 fuel/reactor/repair coordination,
and further per-family tuning remain specified below, not implemented.

## 1. Verified current implementation, boundaries, and gaps

| Concern | Existing authority | Observed gap / planned integration |
|---|---|---|
| Persistent captain policy | `ShipStandingOrders`, `ShipTacticalDirectives`; `ShipStandingOrdersCodec` (`CD-STANDING-1/2`) | 23 simultaneous controls but no rate-of-fire or ammunition allocation |
| Fleets and inheritance | `BattleOrders`, `BattleOrdersCodec` (`CD-ORDERS-1`), `FleetDoctrineGroups` | Team defaults must never wipe individual overrides; group copy preserves personal defend/escort IDs |
| Individual runtime | `Battle.setShipStandingOrders`, `Battle.setShipOrders`, `Battle.chooseTarget`, `Battle.orderedSteer` | One selected enemy target per ship, category-aware but not weapon-specific target profiles |
| Weapon authority | `Battle.fire`, `WeaponBehaviorTable`, `LaserFamilies`, `AmmunitionTable` | All eligible weapon families fire at readiness; no shot-value gate or ammo reserve |
| Missile/torpedo salvo | `OrdnanceDoctrine`, `Battle.queueRackSalvos`, `Battle.updateQueuedLaunches` | A queued volley must count against a budget, but consume stock only on real FIRE |
| Dumbfire rocket racks | `RocketBarrage`, `Battle.queueRocketBarrage`, `Battle.updateRocketLaunches` | 4/8/16 independent programs; kinetic stock and physical per-rack rounds must BOTH be available |
| Sensors and accuracy | `Battle.hasTargetContact`, `Battle.fire` lock calculation, `DeceptionProjection` | Lock quality is calculated at firing time, not persistent track age; don't promise higher resolution merely by waiting |
| Energy, cooling, damage | `Battle.replenish`, shield facing regeneration, hull repairs, module conditions, weather | Lasers/plasma/particle are **ammunition-free**, never power/heat-free; firing competes with shield and repair output |
| Propellant and retreat | `Battle.steerToward`, `Battle.consumeEngineFuel`, `Battle.steerRetreat` | Engine speed cap exists (25–100%); simply slowing down may NOT materially reduce fuel per distance under current consumption law |
| Logistics | `Battle.updateTenderSupport`, `TenderDoctrine`, `SupportDoctrine`, `CampaignFleet.Stores` | Finite compatible physical transfers; no stock creation, no remote aura; rocket-only KTS eligibility separately repaired |
| UI | `CatastrophicGame`, `GameMenuPanel`, `FleetOrdersPanel` | Current standing-order editor has two compact pages; new fields need responsive mobile pages and descriptive state feedback |

**Existing default:** preserve the current fire-at-ready behavior (including the current
simulation RNG sequence) when no resource policy is selected. Visual art and aesthetics
are diagnostics only, never release blockers.

## 2. Separate the decisions instead of one confusing "aggression" slider

The new **ShipResourceOrders** (proposed immutable value object) has six independent
policy dimensions. Defaults preserve existing combat:

1. **Firing intent:** `IMMEDIATE` (current), `CONFIRMED`, `PRECISION`,
   `OPPORTUNISTIC`, `HOLD_OFFENSE`. Aiming applies to each eligible weapon
   without turning off point defense.
2. **Ammunition expenditure:** `EXPEND`, `AUTOMATIC_RATION`, `SPACE_VOLLEYS`,
   `HOLD_RESERVE`, `ENERGY_FIRST`, `ENERGY_ONLY`, `EMERGENCY_RELEASE`.
   These are primary presets; a captain can separately tune reserve and cadence.
3. **Volleys and rate:** independent minimum launch separation by family,
   `SINGLE` / `SPACED` / `FULL_SALVO` burst intent; do not confuse this with
   researched missile/rocket hardware's mandatory physical pattern (4/8/16, etc.).
4. **Aim requirement:** per-family target confidence threshold (0–100), bounded
   maximum wait before reassessment, target tracking/stability requirement, and
   a priority to conserve ammunition against low-probability shots.
5. **Target preference:** `STANDARD`, `HIGH_THREAT`, `EXPOSED_HULL`,
   `DISABLE_WEAPONS`, `DISABLE_ENGINES`, `DISABLE_LOGISTICS`,
   `FINISH_WOUNDED`, `ANTI_FIGHTER_SCREEN`, `COORDINATED_VOLLEY`.
   These are scoring preferences, **not** unearned perfect knowledge or guarantees
   of subsystem hits.
6. **Fuel/energy reserves:** `NORMAL`, `EFFICIENT_TRANSIT`,
   `HOLD_AND_COAST`, `WITHDRAWAL_RESERVE`, `EMERGENCY_THRUST`; a propellant
   minimum, safe reactor headroom for shields/PD/repair, and a conditional
   permission to use disposable ordnance if the reactor is unable to support
   energy weapons.

An optional per-weapon-family override (LAS/KIN/PLA/PAR/MSL/TOR/ROCKET) inherits the
captain default until explicitly changed. Real physical rocket/guided launchers may
later override again by **hardpoint ID**; priority: socket > family > captain > fleet >
legacy DEFAULT. No setting may grant a module or ammo absent from the fitting.

### Explicit meanings of commonly requested orders

| Order | Expected behavior | Must never do |
|---|---|---|
| **Fire at will / empty the magazines** | Fire any physically ready authorized family as target enters valid range | Bypass power/heat, magazine health, range, ammo or weapon condition |
| **Wait for good resolution** | Hold discretionary shots until measured/confident solution improves or timeout invokes a declared fallback | Grant magical aim, ignore ECM/decoys, or wait forever without feedback |
| **Save the last bullet** | Preserve a HARD minimum of 1 (or N) real rounds **per physical resource** and account for already queued salvos | Fire that last round as a stray queue step, or block explicitly allowed defensive PD |
| **Ration at 50%** | Begin a *continuous* reduction of discretionary shot frequency as normalized stock falls below 50% | Abruptly skip every second wall-clock tick; deplete a reserve through burst scheduling |
| **Space volleys** | Keep whole researched salvos intact but delay starting the next salvo; coordinate allied waves by deterministic tick offsets | Forge fractional rocket barrages or ignore physical cooldown |
| **Full salvo** | Commit entire stock-supported prescribed salvo when aim/target conditions permit; count **every body**, including physical decoys | Promise 16 rockets when only 12 on-rack rounds or insufficient shared kinetic units exist |
| **Energy first / energy only** | Prefer LAS/PLA/PAR where fitted and power/heat can sustain them; conserve physical ammo | Claim lasers are free, starve PD/shield/repair, or allow an unarmed ship to stall forever |
| **Fire on an exposed target** | Prefer enemies with actually observed shield/armor damage, shot geometry and valid contact | Read hidden enemy stores, choose phantom contacts knowingly, or guarantee breaches |
| **Reserve fuel for withdrawal** | Estimate return-to-friendly-border fuel and avoid optional pursuit that jeopardizes it | Teleport on retreat or ignore depleted engines, thrusters, crew, weather and damage |

## 3. One authoritative eligibility and scheduling sequence

Do **not** bolt independent multipliers into the existing six `fire()` branches.
Instead create a small, pure deterministic `ShipFireControl` policy calculator
returning a `Permit` for a proposed action:

`evaluate(captainPolicy, shipSnapshot, weaponSnapshot, targetSnapshot, tick,
           committedSalvoCost, sensorSnapshot) -> Permit(allow, reason,
           expectedQuality, nextEligibleTick, reservedAfter)`

Only **the actual Battle firing functions** may mutate ammunition, power, heat,
reload or visual shot events. The policy calculator is purely advisory/authorizing;
it must never fire, spawn or consume a projectile. Every path (LAS/KIN/PLA/PAR,
MSL/TOR family, individual guided rack, each ROCKET hardpoint) must pass through
the same policy contract immediately before initiating a shot or burst.

Order of gates:

1. Ship alive, not escaped/withdrawing, legal live target contact; crew, module,
   magazine and energy health; fitted physical range and weather.
2. Retreat/mission/manual `HOLD_OFFENSE`, explicit family disable and authorized
   emergency exceptions. Withdrawal overrides attack and burst initiation.
3. Defending ship **point defense runs independently** under its current PD mode.
   No energy-only / last-bullet policy disables PD; PD still pays reactor energy
   and obeys its genuine reach, hit/timing and damage.
4. Validate **whole-salvo** available supply: 1 unit per KIN shot; one or more
   MSL/TOR rounds including decoys; rocket count times per-size cheap KIN units,
   and real `Rack.rounds`; subtract in-progress reserved commitments.
5. Apply target priority, sensor quality, minimum wait, cooldown/cadence and
   resource conservation. Do not require a precise hit chance for already
   guided bodies; use conservative contact/tracking and guidance estimates.
6. Honor reactor/cooling safety budgets and fuel autonomy policy. If a shot
   becomes ineligible, leave current cooldown and physical stores unchanged.
7. On real FIRE, deduct actual resources exactly once, record reason/shot ID,
   then emit existing FX/audio. Pending salvos recheck *hard safety* at each
   body release and are canceled if their source withdraws/dies/loses hardware;
   decision-mode changes only affect **new** salvos.

The no-policy branch should bypass all new stochastic calculations and preserve
the old deterministic RNG stream. Do not add random-number draws during
permission checks; use fixed arithmetic, existing calculated sensor state, and
stable ship/weapon IDs.

## 4. Ammunition inventory, reserves, and continuous rationing

Normalize each commodity separately. KIN reserve includes the cost of bullets
**and all four rocket sizes**. MSL/TOR are separate; rocket per-rack magazine
capacity is an additional hard limit. The `effectiveAvailable` for a new salvo is:

`max(0, sharedStock - queuedCommittedCost - hardReserve) `,
then `min` against actual rack stock and any separately reserved magazine cost.

Pending-launch commitments are **reservations, not consumption**. The physical
ammo count must decrease only on actual FIRE (current rule). Reservation totals
must be released when a queued volley finishes or is canceled; otherwise
multiple rack types can oversubscribe the same KIN/MSL/TOR stock. For a legacy
family queue and modern socket queues, count each queue's *remaining* bodies,
not already launched ones. Never pre-bill the player for a canceled volley.

**Recommended default discretionary rate curve**, continuous at threshold T:

- `r = clamp((stock - committed) / max(1,capacity),0,1)`
- `T = 0.50`, user adjustable 0.10–0.90 in 5% increments
- `rateFraction = r >= T ? 1.0 : max(0.20, r/T)`
- `minimumCadence = fittedReload / rateFraction`, subject to custom
  inter-volley interval. At 50% it fires normally, at 25% roughly half-rate,
  at 10% one-fifth rate. **No 50% discontinuous cliff.**
- `hardReserve = ceil(capacity * reservePercent)` or an exact minimum count;
  an explicit `SAVE_LAST_ROUND` reserves at least one.
- Use e.g. 5 percentage-point hysteresis when switching *categorical* policies
  (energy-first/mandatory resupply), not for this smooth numeric rate curve.
  Reevaluate after physical tender resupply, but use next-eligible tick to stop
  jitter or "catch-up" instantaneous burst spam.

Ammunition below hard reserve must block new discretionary salvos. The
`EMERGENCY_RELEASE` policy may explicitly spend that reserve (e.g. a lethal
threat or mission objective), but the player must opt in to exceptions. Do not
silently expend the final torpedo because the target is nearly destroyed.

Burst delay is governed by **real simulation ticks** (30 Hz), never frame rate.
Prefer stable integer `nextEligibleTick` and physical cooldown counters,
with user option 0 / 1 / 2 / 4 / 8 / 15 seconds for inter-volley spacing.
For coordinated fleets, stable stagger `(shipId,slotId) mod waveWindow`
groups launches without costly cross-fleet scheduling.

## 5. Target resolution and shot-value calculation

Separate *acquisition* (there is a real contact) from *track quality*
(we have a sufficiently good fire solution) and from *expected damage*.
Source quality must come from existing sensor range, acuity, radar/infrared,
X-ray, gravitic observation, crew readiness, target stealth/hologram, ECM,
weather, velocity and damaged sensors. Shared spotting permits acquisition,
but does not grant perfect individual fire solution.

Proposed bounded per-target `TrackState`:
`{targetId, firstSeenTick, lastSeenTick, quality, stableTicks}`.
Reassess at most every 5 simulation ticks (or on contact loss, ship damage
and major target maneuver) for fixed-time O(1) per currently engaged target.
Quality may rise as the contact remains stable; cap at **what the current
sensor conditions can actually resolve**. A jink, target switch, ECM/decoy
or lost contact reduces/resets confidence. Waiting for better resolution
therefore has an actual opportunity cost and does not manufacture detection.

For LAS/KIN/PLA/PAR, calculate the estimated `Gun.aim(...)` result using the
**same** ship stats, weather factor, tracking and measured lock that determine
the real roll. Kinetic projectiles additionally discount aim for time of
flight and lateral target velocity. Rockets calculate a conservative fixed
bearing intercept quality using immutable launch solution and expected PD risk.
Missiles/torpedoes do not get an invented hitscan probability: use measured
contact, supported seeker/guidance, time-to-impact, physical PD exposure,
magazine cost and target threat as an **estimated** shot-value score.
The confidence display must label estimates; actual simulated hit rolls and
physical collision remain authoritative.

`PRECISION`: wait for threshold e.g. 75/100 for ordinary guns and 65/100 for
unguided rockets, timeout e.g. 6s, then `HOLD` or `FIRE_IF_THREATENED` as an
explicit configurable fallback. `CONFIRMED`: baseline 55/100 and 3s.
`IMMEDIATE`: no extra gate; existing physical constraints still apply.
All thresholds and timeouts are proposed tuning values, not balance facts.
After 6 seconds at unresolved ECM/stealth, either act per fallback or report
"LOCK UNRESOLVED"—never wait invisibly forever.

Target scoring order: obey the player's class priority / exclusions, faction
and visibility; then apply intended role and current fire-control intent:
danger to escorted friendlies, hostile weapon output, exposed facing/armor,
time to destroy, armor/shield effectiveness of fitted ammunition, estimated
interception, active repair/tender value, overkill already in flight, cost
to kill, and relative range. Keep present NEAREST/COOPERATE/VULTURE/RETALIATE/
PROTECT compatible as primary doctrine; new profiles **modify** rather than
replace those selection constraints.

Multi-weapon target assignment is a second phase: begin with the existing
one-target-per-ship acquisition and independent **fire permission** per
family. Later allow each weapon family/hardpoint to select from a cached
bounded top-K hostile candidate list, with limited firing arcs and actual
muzzle/target geometry. This avoids multiplying the existing 200v200
ship-pair search by seven weapon types or rereading the same contact list
on every cooldown tick.

## 6. Energy-only is an engineering doctrine, not infinite free fire

LAS/PLA/PAR do not decrement stored ballistic rounds; they **do** take
power and add heat through existing physical tables. Reactor condition,
cooling condition, crew, weather, shield-facing recharge, self repair, remote
repair, support drones, PD and fuel pumps remain authoritative.

- `ENERGY_FIRST`: if an eligible energy shot has acceptable solution and
  reactor/thermal headroom, prioritize it and retain scarce KIN/MSL/TOR
  stores; allow kinetic ordnance when energy output is insufficient or when
  a designated emergency/high-value target warrants its cost.
- `ENERGY_ONLY`: *never* start a nondefensive KIN/MSL/TOR/ROCKET attack.
  If the ship has no live energy guns, it must navigate, screen or withdraw
  according to other orders, with visible "NO ENERGY WEAPON" feedback.
  Aircraft weapon loadouts are separate and should not be silently disabled
  unless a specific fighter ammo order is added.
- `REACTOR_RESERVE`: reserve a percentage of effective power headroom for
  life/defense, PD, shields and supported repair. Thermal cap must remain
  physical; do not gate an unavoidable real PD intercept behind arbitrary
  offensive weapon preference.
- `SUSTAINABLE_FIRE`: estimate recharge minus shield/repair/PD demand and
  the weapon's average power/heat per second; gate or space optional energy
  attacks before sustained fire collapses shielding. Damage to generators,
  coolant or power conduits invalidates the estimate immediately.

Suggested priority under scarcity: keep the ship functional, honor committed
withdrawal, react to imminent defensive interceptions under existing PD
orders, preserve essential repairs and shield survival, then offensive fire,
then optional nonurgent support transfers. Respect player `HOLD_FIRE` on PD
rather than inventing a forced PD expenditure. All allocation is bounded,
does not generate extra power, and leaves current physical capacity unchanged.

## 7. Propellant conservation and logistics behavior

Fuel doctrine is NOT simply a new speed-limit slider. Current engine fuel
consumption is proportional to velocity * duration; slower travel to the
same distance need not save much. First implement real **behavioral**
conservation: fewer unnecessary closing/orbiting moves, coasting/holding,
maintaining standoff with viable energy range, allowing formation
leaders to maneuver while followers stay within their escort radius.

Modes:

- **NORMAL:** existing pursuit and movement.
- **HOLD_AND_COAST:** cease powered thrust when inside viable weapon range,
  damp unnecessary path corrections, permit natural coast only if collision
  avoidance allows; do not turn holding into perpetual artificial braking.
- **EFFICIENT_TRANSIT:** throttle acceleration/burn scheduling and avoid
  repeated overshoot; measure fuel per unit distance and time-to-arrival.
- **WITHDRAWAL_RESERVE:** estimate fuel from current x/y, route, health and
  engine efficiency to the actual rear extraction border plus a modest safety
  margin; refuse discretionary pursuit that breaches it.
- **EMERGENCY_THRUST:** can consume previously reserved fuel, never free fuel;
  activated explicitly or by a defined threat rule.

Keep 25–100% existing speed cap as separate maximum velocity. Damage and
crew loss lower feasible acceleration and effective escape range; a zero-fuel
ship must not report a withdrawal is guaranteed. If retreat requires more fuel
than available, offer "INSUFFICIENT FUEL" feedback, call a real nearby tanker
if fitted/possible, and let the hull coast/attempt what it can rather than
teleporting.

Tenders preserve equal/opposite transfers of real FUEL, KIN, MSL and TOR
stores, proximity, matching warheads where required, fixed pulse rates,
reactor cost and hull capacity. A rocket-only ship consumes shared KIN stock
and needs to be a valid KTS recipient; a completely spent physical rocket
rack does NOT magically receive new on-rack rounds without an explicit
rearm mechanism. Future `REQUEST_REARM` and `REQUEST_FUEL` flags adjust
tender target priority **within physical reach and compatibility**, but must
never add an uninstalled transfer module or conjure ammunition.

## 8. Damage, repair, escape, hazards and mission-state precedence

Precedence for every simulated tick:

1. Physical ship death, engine/reactor/magazine damage and actual max
   capacity; no order overrides broken systems or finite stock.
2. Committed withdrawal (irreversible until physical border escape),
   mission-level scripted restrictions; suppress offensive new fire/salvos.
3. Existing explicit PD mode and valid defensive service actions.
4. Player explicit `HOLD_OFFENSE` / `ENERGY_ONLY` / hard reserve and
   mission-authored exceptions, with visible reasons when they conflict.
5. Real sensor contact and fire-control quality.
6. Target class priorities, target-profile scoring, salvo cadence and
   remaining commodity budgets.
7. Ship steering, energy/heat service priority, tender/repair drone requests.

Already-launched ordnance remains in the world with physical flight rules;
new policies must not delete rockets in flight or redirect existing
dumbfire bearings. Queued salvos stop when a source withdraws, dies, loses
its launch module, loses power/heat control, or runs out of paid supply. The
rest of the volley is never back-charged if interrupted. Damage to a fuel tank
or magazine changes budget immediately; if it causes a lower stock level,
future salvos must re-evaluate before any new commitment.

Repairs may restore engine/weapon/sensor effectiveness, but must not cancel
a committed retreat. Recovery beacons, escape pods, casualty accounting,
surviving paid inventory and campaign resupply stay with the existing
`CampaignFleet.Stores` pipeline. A protected reserve is a **choice not to
fire**, not hidden free reloadable ammunition.

## 9. Persistence, control UI and player diagnostics

Proposed `ShipResourceOrders` immutable records:
`FireIntent`, `AmmoPolicy`, `SalvoIntent`, `TargetPreference`,
`FuelPolicy`, `PowerReserve` plus bounded `FamilyPolicy` values.
Keep a single `DEFAULT` that exactly models today's behavior. Attach to
`ShipStandingOrders` as a new component with an overload for older
constructors; do not graft transient tracking, cooldown or magazine counts
into saved policies.

Use `CD-STANDING-3` for the extended per-captain settings; decode existing
`CD-STANDING-1/2` with `ShipResourceOrders.DEFAULT`. Preserve checksummed
atomic write, backup, and 200-individual-key semantics (commissioned IDs or
deployment slots). Save bounded numeric settings and enum names; reject
duplicate, unknown, out-of-range or malformed records. Do not alter
`CD-SHIP-1..6` blueprint formats or `CD-ORDERS-1` unnecessarily. Save
`nextEligibleTick`, active target track and queued commitments only if
an actual mid-battle save/restore system requires it; otherwise replay state
is deterministic simulation data, not a persistent captain preference.

**UI:** extend the actual prebattle standing-orders editor to compact
scrollable pages rather than a giant 1280x720 scaled Android menu. Suggested
sections: **TACTICS** (existing), **ESCORT / SUPPORT** (existing),
**FIRE CONTROL**, **AMMO / VOLLEYS**, **POWER / FUEL**. Each exposed order
shows an effective summary and inherited/overridden status; values change
with large touch targets and Windows mouse/keys. Keep a per-ship select
marker, group-apply without copying individualized escorts/defend references,
and provide explicit **DEFAULT / RESET FAMILY** affordances.
Do not bury settings only inside a developer panel.

Live tactical HUD: selected ship displays **firing mode**, **lock quality**,
**ammo % by family**, **hard minimum**, **seconds to next authorized shot**,
**reactor/heat reserve**, **fuel and estimated return margin**, **queued
rocket/missile bodies**, **resupply request** and top suppression reason.
Use a compact inspect/expand layout, not every value over every ship.
Log actual exceptions ("HELD FOR ACCURACY", "SAVED LAST ROUND",
"WAITING FOR TENDER", "DEFENSIVE POWER PRIORITY",
"ROCKET MAGAZINE EMPTY") without log flood: change-of-reason or throttled
events only.

Suggested captain presets (do not make one mandatory):
`ASSAULT` (immediate/full burst), `MARKSMAN` (precision/confirmed),
`CONSERVATION` (50% auto ration, energy first, fuel reserve),
`LAST_STAND` (emergency release, no discretionary withdrawal),
`ESCORT_GUARD` (target threats to defended IDs, preserve PD power).
These presets only populate editable values; changing aggression doesn't
overwrite an explicit ammunition order.

## 10. Metrics, diagnostics and objective acceptance tests

Record per ship/weapon family, bounded counters:
`shotsEligible`, `shotsFired`, `heldForAim`, `heldForReserve`,
`heldForPower`, `heldForCadence`, `heldForMagazine`,
`salvosQueued`, `salvosCanceled`, `bodiesActuallyLaunched`,
`stockConsumed`, `stockTransferred`, `shotHit`,
`expectedHitQuality`, `friendlyShieldLost`, `selfHullLost`,
`selfRepairDone`, `powerAndHeatSpent`, `fuelDistance`,
`fuelReturned`, `escaped`, `overkill` and `timeToFirstShot`.
Do not claim a cosmetic visual effect is an actual hit. CSV row IDs
should include seed, faction, hull, fitting checksum, order checksum,
target conditions, time ticks and wall-clock performance.

Mandatory functional tests (integrate with **existing** test tasks; no new
GitHub Actions workflow without explicit authorization):

1. **Backward compatibility:** all old order and ship blueprints decode,
   current default 200v200 replay fingerprint unchanged, deterministic RNG
   unchanged for an unmodified captain, fleet and individual overrides obey
   precedence.
2. **Ration sweep:** 100/75/50/40/25/10/1/0% ammo, with precisely bounded
   rates, no 50% cliff and no stock below reserve. Retender above threshold
   correctly restores activity without unbounded catch-up bursts.
3. **Last round:** 1/2/many remaining KIN/MSL/TOR bodies; two rocket racks
   sharing KIN, separate rocket magazine, guided decoys, multiple queues;
   never double-reserve or pre-charge canceled bodies.
4. **Aim quality:** stationary/moving/high-jink/cloaked/holo/ECM/weather/
   sensor-damaged targets; confirmed hold, timeout, fallbacks, no phantom
   confidence; verify actual hits improve only where observation warrants.
5. **Energy:** energy-first and energy-only under adequate and damaged
   reactors, PD saturation, shield recharge, repair drone demand, thermal
   throttling and no energy guns.
6. **Propellant:** stationary/coast/escort/rapid pursuit, damaged engines,
   no fuel, tanker transfer, retreat border at both teams; report consumed
   fuel and escape success, not just policy labels.
7. **Target roles:** protect, retaliate, focus fire, logistic interdiction,
   crippled enemy and protected ship; no unseen-target cheats or illegal
   faction targeting.
8. **Persistence/UI:** codec v1/v2->v3 migration and corrupted backups,
   200 independent captain settings, group apply preserves individual
   defend/escort references, Android responsive hitboxes/large fonts.
9. **Stress:** six or seven seeded 200v200 fully completed fights with
   mixed energy/kinetic/rocket/guided/PD/fighter/tender/repair hulls;
   capture memory allocation, p50/p95/p99 tick CPU, active projectile cap,
   target scan counts, performance regressions and resulting inventories.

**Release-blocking:** failure to build/launch/run, crash/hang, deterministic
corruption, illegal resource creation, missing required multiplayer/updater
paths, or mandatory user control not working. **Advisory only:** tuning,
expected hit/damage efficiency, relative fleet win rate, art, animation,
balance or performance measurements absent a genuine functional hang.

## 10a. First implemented slice and exact boundaries

As of the initial resource-orders implementation:
- `ShipResourceOrders` stores the validated immutable `IMMEDIATE/HOLD_OFFENSE`
  and `UNRESTRICTED/AUTO_RATION/HOLD_RESERVE/SPACE_VOLLEYS/ENERGY_ONLY`
  policies, with a 10–90% ration threshold, percent/minimum-round reserve
  and 0–900-tick minimum spacing. These are the first exposed policies,
  not all of the proposed targeting/fuel/energy presets above.
- `ShipStandingOrders` retains the original 23 settings and adds six rows,
  with a third page of enlarged touch targets. New rows persist through
  `ShipStandingOrdersCodec` CD-STANDING-3; V1/V2 entries load unrestricted.
- `ShipFireControl` computes a pure authorization decision and a continuous
  rate fraction. `Battle.fire`, `queueRackSalvos` and
  `queueRocketBarrage` consult it at initiation. The shared kinetic
  budget includes outstanding rocket salvo bodies. Guided pending launch
  bodies also count against missile/torpedo budgets. Only real firing
  debits stock; existing physical reload/power/heat checks remain mandatory.
- A newly selected `ENERGY_ONLY` policy blocks new kinetic, missile,
  torpedo and rocket attacks, not existing already-in-flight/projectile
  bodies. The separate fighter mission remains controlled by the existing
  fighter orders. `HOLD_OFFENSE` currently gates the ship's offensive
  main-battery firing path, not separate craft and defensive PD.
- Per-family override selectors, precision lock accumulation, energy-first
  scheduling, hard reactor reserves, fuel autonomy, target-profile
  scoring and on-screen hold-reason telemetry still await P3/P4/P5
  work. They must not be described as shipped.
- Regression coverage extends existing standing orders, fleet group,
  battle orders, rocket barrage and menu flow smoke tests. The current
  cross-platform CI results are tracked by GitHub Actions and are not
  presumed successful merely because source was committed.

## 11. Staged delivery with reviewable commits

**P0 — audit + supply corrections.** This spec; identify existing fire gates,
standing order codecs and KTS rocket-only eligibility; add physically
conservative tender regression. No new player fire semantics yet.

**P1 — immutable policy + codec migration.** `ShipResourceOrders`, validation,
legacy default, `CD-STANDING-3`, per-family overrides, pure setting and group
inheritance tests. UI initially may read-only reflect the new values.

**P2 — deterministic ammunition authorization.** Implement hard reserves,
automatic 50% curve, cadence and full-salvo commitments in a pure
`ShipFireControl` evaluator; integrate KIN, MSL, TOR and ROCKET queue initiation
and actual-shot safety gates. Verify zero free stocks and legacy fingerprints.

**P3 — sensor quality and target selection.** Bounded `TrackState`, real
lock/confidence and wait/fallback, per-family target weights and optional
cached top-K candidates. Do not split to true per-hardpoint enemy targets
until shared-family tracking and scaling pass tests.

**P4 — reactor, repair and fuel coordination.** Energy-first/energy-only,
reactor/shield/PD/repair reserve; purposeful coast, escape fuel autonomy,
resupply requests and target tender prioritization. No fictitious fuel
economy or support equipment.

**P5 — accessible interaction.** Ship standing-orders pages, family override
selector, contextual tooltips, effective orders summary, emergency button
and live reason codes, responsive Android layouts; existing fleet presets and
per-ship group application.

**P6 — full mixed-fleet acceptance and tuning.** Extensive six-plus 200v200
runs and Android campaign/Windows/Linux tests; side-by-side seed-matched
metrics, CPU budgets, known outliers, screenshots as optional diagnostics.

**Dependency rule:** preserve existing base functionality before broadening
the UI. Every phase produces runnable code and tests; commit on main with
remote readback, do not retag releases or introduce a new gating workflow.
