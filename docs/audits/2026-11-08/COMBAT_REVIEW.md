# Combat, simulation and technology audit

**Domain checkpoint:** CP6 final, 2026-11-08. **Audited production baseline:** `0159042e47a9271e5e3b077756f5c4d57763a688`. The later CP0 documentation commit `43296e95bb675dfef6588f715046673ad0331258` does not change the audited game code. No production file, workflow or release was changed by this audit. Source paths below are relative to the repository root. Finding map: C-01 → F12; C-02 → F13; C-03 → F14; C-04 → F16; C-05 → F17. The [master repair plan](../../DEVELOPMENT_REPAIR_PLAN.md) governs cross-domain order.

## Conclusion

Three functional defects were reproduced against the unmodified compiled game core: navigation consumes an enemy's unseen present position, particle point defense is disabled during withdrawal, and FCM compute donation cannot reach an ally through the normal firing path. A fourth finding is an unfinished player-facing feature: active sensor doctrines exist in the core but cannot be selected or persisted through game/server entry points. Mixed carrier wing manifests likewise remain separate from live launch ownership.

Continuous maneuvering, explicit HOLD_RANGE, physical fighter landing and paid rearm, fitted fighter seeker selection, distinct defensive weapon families, the 48 survival variants, and graduated hull access all exist in the current source. Older documentation claiming these are entirely absent must not be treated as present defects.

The common evidence records that all 392 production/test Java sources compiled under Java 17 and the requested functional Gradle aggregate passed with 118 actionable tasks. This audit separately compiled and ran an isolated scratch probe. Those broad green tests do not cover the three runtime integration failures identified here.

## Evidence and reproducibility

- [Probe source](CombatAuditProbe.java).
- [Exact output](combat-probe.log).
- Dependencies: freshly compiled baseline classes, checksum-verified release resources and external libraries, Temurin 17.0.21+9.
- The [evidence index](README.md) includes commands for isolated compilation and reproduction.
- The final probe process exited **0**. It deliberately asserts the observed defects, so exit 0 means successful reproduction, not a clean game.
- No independent Gradle invocation or source mutation was made. Diagnostic fixture controls are documented below; none are represented as ordinary gameplay.

## Repair order

| Order | ID | Classification | Repair boundary | Acceptance required |
|---|---|---|---|---|
| 1 | C-01 | P1 functional: causal navigation | Carry an observed target state through captain steering | Identical received history produces identical navigation until changed photons arrive; retain real collision geometry |
| 2 | C-02 | P1 functional: retreat defense | Remove the inconsistent PPD-specific withdrawal suppression while retaining common safety/resource gates | Powered PPD defends during physical withdrawal; HOLD_FIRE, disabled mounts, no power and actual escape still suppress it |
| 3 | C-03 | P1 functional: command compute | Real observation/request/reply chronology between firing ships and FCM donor | Two distinct allies can spend one finite donor pool after reciprocal transit; never before |
| 4 | C-04 | P2 unfinished integration | Scan doctrine state, UI and persistence | Menu selection survives save/reopen/deploy and changes real scan behavior before tick 1 |
| 5 | C-05 | P2 planned expansion | Individual mixed wing ownership and runtime carrier launch bridge | One authoritative paid inventory across purchase, fitting, launch, loss, recovery and save |
| 6 | C-06 | P2 diagnostic closure | Measured 200v200/device behavior and PD/rocket calibration | Comparable evidence, resource invariants and terminal-body accounting; balance/appearance remain advisory |

The master repair plan places campaign settlement and network state boundaries according to their dependencies; the order here is specific to combat. No P0 crash or universal launch failure is asserted here.

## C-01 — navigation reacts to hidden current enemy position

**Source chain.** `Battle.chooseTarget` obtains `authorizedContact(...)` and ranks distance from `contact.sample()` at `Battle.java:3177–3182` and `3220–3224`. It then returns the mutable `Ship`. `orderedSteer` reads `target.x` and `target.y` at `3340`, and uses those current coordinates for both FREE maneuvering and HOLD_RANGE through `3366`. Contact permission is therefore causal, but the actual maneuver is not. Additional present-state inputs remain in target ranking: `combatReadiness()` at `3185`/`3228` and hull fraction for VULTURE at `3234`. Those latter ranking paths were statically observed, not separately reproduced.

**Runtime reproduction.** Two equal-seed worlds establish exactly identical immutable observation histories with the same observer and one enemy. Weapons are suppressed and the observer is held at speed 0 during setup. After capture, only the second world's present enemy Y position changes from 100 to 250. Both observers receive the same speed and facing, then run one normal `Battle.step()`.

At tick 13 both tactical observations still report the enemy at `(600,100)`, measured tick 9. Nevertheless, the observers turn to `-0.380506377` versus `-0.287173044` radians. Their velocities become `(0.000802876,-0.000321150)` and `(0.000829312,-0.000244926)` respectively. The small absolute velocity reflects the first acceleration step; the decisive evidence is different decisions with the same received enemy fix. The test uses the ordinary captain/navigation path, not reflective invocation.

**Impact.** A captain can maneuver around a target movement it has not yet observed. This undercuts the light-time sensor model and can make visible contact positions disagree with fleet responses. This is a behavior defect, not a claim about hit rates or desired balance.

**Repair.** Carry a contact/track object from target selection into movement, or reacquire the same causally authorized observation without reading current enemy position. Derive distance, bearing and any extrapolation only from that measurement and known age. Keep actual body positions available exclusively to collision/physics. Decide explicitly which target-health/readiness assessments are observed or estimated rather than retaining live-state shortcuts.

**Acceptance.** Add the two-world test to actual `Battle.step()` coverage for FREE and HOLD_RANGE; prove equal behavior before arrival and a response after arrival. Test contact loss/search, standing order changes and replay fingerprint stability. Do not remove continuous maneuvering to hide the problem.

## C-02 — particle defense turns off during withdrawal

**Source chain.** `Battle.fireParticlePointDefense` skips `defender.withdrawing` at `Battle.java:6415`. This guard is absent from the equivalent laser (`6728`), rotary (`6151`), rail (`5865`) and flak (`5661`) defensive gates. The withdrawal contract in `docs/FLEET_ORDERS_SYSTEM.md:140–144` explicitly retains defensive point defense and repair drones while the targetable ship physically escapes.

**Runtime reproduction.** The paired probe equips a real tier-IX PPD socket on a valid Dreadnought and uses normal enemy missile launch behavior. Both runs use the same seed and equipment, and both create five incoming missile launches over 420 ticks. The non-withdrawing control produces **70 paid beam ticks**; setting only the withdrawal flag produces **0**. Both defenders remain alive and unescaped. A diagnostic stationary target isolates the defense path from changing range; it does not invent missiles or beam events. A follow-up production regression should exercise actual retreat movement as well.

**Impact.** A ship depending on PPD loses all of that paid defensive function precisely when retreat begins, despite having energy, heat margin, viable hardware, eligible incoming threats and defensive orders. No claim about comparative kill counts is needed to establish the defect.

**Repair and acceptance.** Align PPD eligibility with the common defensive withdrawal contract. Preserve suppression after escape, on HOLD_FIRE, for unpowered or damaged hardware, and under heat lockout. Demonstrate actual PPD dwell/damage with matching power and heat during a physical retreat. Keep offensive guns and new offensive sorties disabled as currently intended.

## C-03 — fleet command compute cannot donate to distinct allies in live firing

**Source chain.** `Battle.targetWork` calls `SensorComputeBudget.solveWithCommand` at `3460–3469`. That allocator requires `tick - received.receivedTick()` to cover two actor–commander light-speed hops before debiting the command pool (`SensorComputeBudget.java:113–123`). However, normal `Battle.fire` obtains a freshly read firing fix (`Battle.java:3643`), local `receivedContact` calls `SensorLightTimeHistory.receive` with current `tick` (`2603–2613`), and relay retrieval also reconstructs the observation with `receivedTick=now` (`SensorRelayBuffer.java:44–46`). For a distinct noncoincident ally, the comparison is always zero age against a positive transit interval.

A command ship can consume its own bank at separation 0. That does not provide the promised donation to other ships. Merely having positive `commandAssist` is not proof of shared CPU spending.

**Runtime reproduction.** The probe fits a real FCM on a valid Dreadnought, assigns a nearby Terran ally, and lets that ally fire normally for 600 ticks. The final telemetry reports donor 1 / assigned donor 1, positive donor capacity **0.600000**, separation **80.117194**, **19 local allocations, all 19 exhausted**, and **0 shared allocations / 0 shared exhausted requests**.

**Why existing tests pass.** `SensorComputeBudgetTest.java:151–178` constructs an observation with received tick 1, waits, and directly invokes the allocator at tick 5. That correctly checks the finite allocator in isolation but supplies chronology the normal fire-control path never retains. The docs' opt-in fleet diagnostic truthfully allows zero FCM requests as telemetry, so it also does not establish runtime sharing by itself.

**Repair.** Preserve the actual reception identity/time and model a bounded command request/returned-solution path, or explicitly select a causally mature retained report for command processing. Do not delete the delay check or relabel measured time as reception time merely to make the counter positive. New reports and earlier command results need a defined relationship so a fresh measurement does not indefinitely restart eligibility.

**Acceptance.** Exercise actual main-beam/kinetic/rocket firing by two spatially separated recipients, with real fitted FCM, exhausted local compute and positive donor capacity. Assert no donation before reciprocal transit, then positive shared debit bounded by one donor budget across both recipients. Assert lost power/command conditions stop new help, stale/future reports cannot grant illicit sight, and replay is deterministic.

## C-04 — scan doctrines remain unreachable and unpersisted

A production-source search of `core`, `game` and `server` finds no caller of `Battle.configureSensorScan` beyond its own definition, and no other production assignment to scan mode. `SensorActiveScan.State` defaults to STANDARD. The state captures and fingerprint hooks read the mode, and `Battle.step()` advances it, but normal player flow does not choose it. `ShipStandingOrders` has no persisted scan doctrine field. The latest pending section in `docs/TARGET_SEEKING_LIGHT_MINUTE_AND_UNCERTAINTY_PLAN.md:711–719` agrees that saved scan controls remain outstanding.

The implemented core choices are STANDARD, PASSIVE, BOOSTED_RADAR, FOCUSED_RADAR and XRAY_BURST. Non-default pulse modes spend power/heat and require real arrival history. Completing UI/persistence should reuse these mechanisms and include the focused target identity, eligibility and disabled reason. Current `configureSensorScan` accepts only tick 0, so the first integration should honor that boundary rather than pretend mid-battle changes work.

**Correction recorded during this audit:** unused `configureFighterRadar`/`configureFighterOptical` setters do **not** imply missing fighter seeker UI. `ShipyardPanel.java:281–302` selects the seeker, `Shipyard.Design` validates/persists it, and `Battle.java:1606–1607` initializes real launch state directly from `design.fighterSeeker`. Parent suites report PASS for saved UI seeker choice and CD-SHIP-7/FTR license round trips. The preliminary combined scan/seeker claim was withdrawn before this final checkpoint.

## C-05 — individual mixed carrier wing ownership remains a separate subsystem

`FlightWingManifest` and `CampaignFlightWingCodec` provide explicit wing/bay identities, validation, migration and serialization. A search of production callers finds these used only inside their own bridge classes. The live carrier still has one `fighterProfile`, a generic `fighterReserve`, and a list of physical returned craft. The documented bridge explicitly says it is not live launch authority.

This does not make existing single-profile carrier combat broken. It means the proposed mixed composition is not complete: selection, paid stock, actual launch, losses, recovery, refit and save/load do not yet share those wing identities. The parent campaign/session audit should resolve the exact persistence owner before connecting this subsystem; do not introduce another competing roster authority.

**Acceptance.** Two distinct paid wing types on separate real bays should launch their configured profiles, consume only their own inventory, return to eligible bays, retain their identity after rearm, preserve lost/docked/deployed counts, survive refit/save/reopen, and never produce free replacement aircraft. Existing one-profile saves need explicit migration.

## Implemented systems that should be preserved

### Shared combat authority and movement

`CampaignEncounter` constructs `Battle`; local duel/skirmish/screensaver construction and `BattleServer` use the same `Battle` core. The client advances it in the fixed-step loop and the server calls `battle.step()`. Screensaver captain orders/camera behavior do not substitute a different simulation.

`Battle.orderedSteer` continuously combines radial and tangential maneuver for FREE and other active postures (`3303–3366`). HOLD_RANGE explicitly brakes/corrects within a preferred-range band. `activeWeaponReach` prevents an empty long-range magazine from preserving an unusable standoff. Do not reopen the historic default-stop problem as if it still exists.

### Timing and interpolation

The client (`CatastrophicGame.java:2842–2901`) advances fixed `Battle.DT` ticks and supplies interpolation fractions to `BattleRenderPoses`; camera, hull and projectile presentation use the shared positions. Birth poses, bounded Hermite interpolation, heading smoothing and pause snap are present.

`CombatPace` intentionally maps displayed 0.5–3.0 to simulation multipliers 0.05–0.30. At the low end, authoritative updates are consequently sparse in wall time; that is source-documented pacing, not proof of a tenfold timing defect. Visual effects have a separate clock. Future acceptance must observe 0.5, 1, 2 and 3 speeds, paused/unpaused transitions, newly launched shots and camera tracking on the intended device. Green unit tests do not establish visual smoothness or physical-device FPS.

### Resource orders: current integration versus remaining plan

The resource-order plan is behind the code. `docs/RESOURCE_AWARE_SHIP_ORDERS_PLAN.md:426` still describes 29 settings, three pages, `CD-STANDING-3`, and fuel autonomy as wholly pending. The implementation has **32 settings, four pages, and `CD-STANDING-4`**. `ShipStandingOrders.java:24` supplies the settings; `CatastrophicGame.java:606` cycles four pages; `GameMenuPanel.java:703` and `1160` draw the fourth fuel page. This is a documentation reconciliation task, not a reason to rebuild working fuel policy.

| Resource capability | Current status | Source and qualification |
|---|---|---|
| Immediate fire / hold offensive main battery | Integrated | `ShipResourceOrders.java:10`; `Battle.fire` honors `HOLD_OFFENSE`. Separate fighter missions and defensive PD remain under their existing orders, as the specification says. |
| Unrestricted, auto-ration, hold reserve, spaced volleys, energy only | Integrated | `ShipResourceOrders.java:11`; immutable shared ship policy with threshold, percentage reserve, minimum rounds and spacing parameters. These are not per-family UI overrides. |
| Physical ammunition conservation | Integrated | `ShipFireControl.java:15` checks stock minus committed bodies minus new cost against the protected amount; `Battle.java:3596`, `3611`, `3623` connects decisions and cadence to real firing. Only physical shots debit magazines. |
| Pending guided and rocket salvos | Integrated in authorization | Queued guided bodies and kinetic-priced rocket bodies count as commitments before another salvo can begin. `Battle.java:3704`, `3722`, `3807`, `3831`, `3970` covers missile, kinetic, torpedo, rocket and guided rack initiations. |
| Smooth ration rate / minimum spacing | Integrated | `ShipFireControl.java:27` continuously scales firing frequency below the configured ration threshold; `nextEligible` retains physical reload and optional spacing. No extra randomness is introduced by the policy. |
| Fuel economy and reserve protection | Integrated | `ShipResourceOrders.java:72` defines protected fuel in the existing single physical tank; `thrustSpeedFactor` reduces ordered velocity below the economy band. `Battle.java:3423` passes economy and protected fuel into common thrust expenditure. |
| Withdraw automatically at fuel reserve | Integrated | `ShipStandingOrders.java:180` combines the fuel trigger with existing withdrawal triggers. `BattleRetreatTest.java:110` exercises immediate paid physical retreat and abandonment of queued waypoints. Withdrawal and LAST_STAND can spend the protected fuel, so a reserve does not trap the hull permanently. |
| Persistence / legacy migration | Integrated | `ShipStandingOrdersCodec.java:11` writes V4, reads V1/V2/V3/V4, and V3 orders inherit unrestricted fuel. `ShipStandingOrdersTest.java:17` checks 32 controls; fuel round-trip, legacy migration, pure adjustment, reserve behavior and checksums are covered. Parent reports the functional aggregate passed. |
| Confirmed / precision / opportunistic fire modes and lock wait/fallback | Pending expansion | The live `FireIntent` enum contains only `IMMEDIATE` and `HOLD_OFFENSE`. Existing sensor/target compute is not equivalent to the proposed resource-order precision scheduling or player-selectable confidence thresholds. |
| Per-family policy overrides / target weights / independent hardpoint targets | Pending expansion | The current resource record is ship-wide. The plan's P3 tracking and target-profile work should follow fixes to actual contact/FCM chronology rather than add new authority paths first. |
| Reactor/shield/PD/repair reserve coordination, energy-first scheduling, resupply requests and tender prioritization | Pending expansion | Present energy/heat gates and `ENERGY_ONLY` are implemented; they do not establish the plan's coordinated reactor-budget scheduler. Fuel posture is already integrated and must be excluded from a blanket P4-pending label. |
| Effective order explanation and live hold-reason display | Pending UX work | Player order pages exist. The proposed per-family selector, contextual reason codes and coordinated summaries are not established by those pages alone. |

Repair recommendation: update the staged plan's implementation table immediately, preserve V4 compatibility and working fuel tests, and implement the remaining P3/P4/P5 scopes as separate bounded changes. Do not flatten an existing reserve or reconstruct a second fuel tank. The three reproduced functional defects take priority over adding these discretionary doctrines.

### Carrier launch, landing and rearm

Physical launch bays and crew/resource gates exist. `Battle.recoverFighter` (`5000–5043`) approaches the source ship, requires an operational bay, holds a real orbit when no bay works, schedules a 90-tick service wait, records recovery and returns embarked crew. `serviceReturnedFighters` (`5049–5080`) requires a working bay, sufficient relevant ammunition, power and heat margin; it debits magazines and energy before returning an aircraft to launch reserve. An unserviceable recovered aircraft remains stored. `FighterCraftTest` explicitly checks recovery, paid rearm and missile stock debit around `395–397`.

Fighter survival, finite flare/chaff, escape pods, wing links and research-gated radar/optical seekers are present. Do not claim carrier landing/rearm or saved seeker selection is wholly absent based on older prose. More complete per-airframe/mixed-wing ownership and refueling policy remain development decisions, not evidence that the existing rearm path makes aircraft from nothing.

### Distinct defensive weapon families

Fitted source creates separate precision laser, flak, rotary, rail and particle mount lists (`Battle.java:1437–1487`). Rotary emits finite unguided kinetic rounds with spin/servo behavior (`6150–6195`); rail charges and emits a physical penetrator with its own reservation/cooldown (`5863–5922`); flak uses timed physical shell/fragment behavior; particle uses sustained paid ablation, dwell and thermal hysteresis (`ParticlePointDefense`, `Battle.java:6412` onward). A PPD-only fit does not secretly gain laser/rotary/flak guns. Interception ratios remain calibration results; the withdrawal guard above is a separate functional failure.

### Rocket saturation

`RocketPdSaturation` is explicitly experimental and disabled in ordinary `Battle` (`diagnosticRocketPdPressure` defaults 0). The shipped precision tracking/bandwidth and physical mount behavior should be audited separately from this diagnostic chance multiplier. Its disabled status is not by itself a missing live feature or authorization to select a coefficient.

`docs/ROCKET_PD_SATURATION_BALANCE.md` specifies the 16-round, four-rack versus four-PD tier-IX reference and approximately 30–37% terminal-zone penetration as a tuning goal. The diagnostic now distinguishes terminal penetration, impact, interception and simple non-interception, finishes the first physical salvo and uses paired PD-off controls. Do not certify the target from older seven-second rows that still had bodies airborne. No new balance experiment was run for this audit.

### Survival and hull technology

`SurvivalHardwareCatalog` defines all twelve roots and 48 distinct physical variants with P/C/H/A suffixes, independent tier/price/mass/power/heat/integrity/performance/risk properties. `TechnologyCatalog` generates native faction bases and independent research; `Shipyard.fit` translates the hardware into survival/engineering properties; `Battle.java:1536–1545` loads crew/command/fuel/magazine protection, escape pods, recovery and unsafe-hazard attributes. These are runtime attributes, not just encyclopedia names. Technology and survival suites cover variants and campaign research persistence. Continue the variant work rather than restoring the obsolete defer instruction.

`HullProgression` separates visual size from technology access: starter interceptor/corvette tier 0; escorts tier 1; cruiser families tier 2; carrier/dreadnought tier 3; strike, command and support variants tiers 4/5/6. Shipyard browsing checks those permissions. Campaign module discovery and skirmish/PvP tier caps remain different authorization systems. Rank IX licenses do not automatically buy every survival research branch.

## Consolidated subsystem and issue status

“Implemented” here means traced in the current source, with the recorded passing functional aggregate as supporting evidence. It does not certify every device, balance target or visual experience.

| Area | Current state | Disposition |
|---|---|---|
| Campaign, skirmish, server and screensaver battle authority | Shared `Battle` simulation present | Preserve common authority; retain entry-path regressions. |
| Fixed step, 0.5x–3x control and render interpolation | Implemented | Measure device smoothness and performance; do not infer a tenfold defect from the intentional tempo scale. |
| Always maneuver / explicit HOLD / HOLD_RANGE | Implemented | Preserve behavior while repairing the observed-target boundary in C-01. |
| Captain navigation knowledge | **Reproduced defect C-01** | Delayed contact remains identical while hidden current enemy position changes steering. |
| Target ranking current readiness/hull access | Static concern associated with C-01 | Review separately; this probe demonstrates steering, not all ranking leakage. |
| Local contact acquisition / finite scan power and history | Implemented | Preserve causal acquisition and bounded history. |
| Selected active scan doctrine in player flow | **Integration gap C-04** | Add UI, persistence and deployed initialization; direct test setters are insufficient. |
| Shared sensor reports, tracker history and weapon link budgets | Implemented mechanisms | Continue source-backed causality and conservation tests; do not equate them with working FCM donation. |
| Fleet command compute donation | **Reproduced defect C-03** | Fix reception/request chronology in the live firing path; keep finite pool and reciprocal transit. |
| Main weapon acquisition, finite fire-control budget and physical beams/projectiles | Implemented mechanisms | Preserve real firing/collision gates; advanced estimator/arbitration work remains bounded future work. |
| Defensive laser / rotary / rail / flak / particle identity | Distinct live families | Balance comparison is advisory; shared defensive validity rules remain functional requirements. |
| Particle defense while withdrawing | **Reproduced defect C-02** | Restore parity with the promised defensive retreat behavior. |
| Rocket salvo commitments and physical saturation | Implemented mechanics | Continue terminal-body accounting and paired controls. Diagnostic chance-pressure multiplier remains deliberately disabled. |
| Rocket penetration target | Calibration not certified by this audit | Fresh comparable balance evidence required; do not use unresolved-airborne historical rows. |
| Fighter sortie, recovery and paid ammunition rearm | Implemented | Preserve bay, crew, ammo, heat, energy and real recovery checks. |
| Fighter optical/radar design choice and persistence | Implemented; preliminary contrary finding withdrawn | Actual shipyard control initializes fitted runtime state. UI and blueprint smoke suites pass. |
| Mixed per-airframe wing manifest bridge | **Planned integration C-05** | Connect paid ownership and runtime launches after identity/session foundations; current generic reserve is not the complete bridge. |
| Ammunition policies / fuel posture / reserve-triggered withdrawal | Integrated, including V4 persistence | Correct older plan status; retain compatibility and conservation coverage. |
| Advanced resource targeting / reactor coordination / hold reasons | Pending expansion | Deliver after causal functional repairs; distinguish existing energy-only and fuel controls from new scheduling. |
| Survival hardware variants and research | Integrated physical variants and independent research | Continue the current 48-variant system; obsolete defer prose is superseded. |
| Hull progression | Integrated distinct access tiers | Preserve separation of campaign research, skirmish caps and visual size. |
| 200v200 and physical-device visual/performance behavior | Fresh deterministic 200v200 execution passes; device closure remains required | Report comparable frame/CPU/draw evidence; functional green alone cannot certify smoothness. |
| Current functional tests | Recorded functional aggregate passes | Add the three missing end-to-end invariant cases. Probe exit 0 is successful defect reproduction. |

**Supplementary closure:** the common follow-up passed two identical 480-tick 200v200 combined-arms runs, fighter/wing-codec checks, 506 hull maps with 9,515 anchors, and fixed/variable-frame timing checks. See [supplementary-checks.log](supplementary-checks.log). This establishes execution/determinism within those fixtures, not physical-device FPS.

## Diagnostic and documentation closure

1. Add the three focused integration scenarios to existing functional coverage; the defect is the missing causal/use-path check, not insufficient test count.
2. Verify ordinary player entry paths in addition to direct helper tests: selected controls, fitted designs, deployed state and reopened saves.
3. Maintain one versioned table of implemented/pending scope. `FLEET_ORDERS_SYSTEM` and target-seeking notes append later implementation below earlier pending lists, which makes unsupported retrospective claims easy. Reconcile superseded prose without deleting historic evidence.
4. Measure 200v200 at matched seed, camera, installed modules, hardware and speed, splitting simulation CPU, non-simulation CPU, overlay CPU and draw submissions. Existing software-emulator timing is not physical-device FPS. Do not invent performance improvements without comparable measurement.
5. Keep balance targets, visual smoothness and artistic review advisory under the user's policy. Retain functional gates for invalid firing, free resource creation, broken gameplay paths, chronology and serialization.

## Checkpoint ledger

- CP0/1: traced shared simulation, timing, movement, fighter service; identified candidate navigation and scan integration gaps.
- CP2: saved-fighter-seeker counterevidence was reviewed; traced design initialization and withdrew the preliminary unreachable-seeker claim. Identified PPD withdrawal inconsistency.
- CP3: paired normal-step navigation and real missile/PPD probes reproduced both functional defects.
- CP4: traced fresh reception timestamps through real firing; fitted FCM runtime probe reproduced exhausted ally solver with zero donor requests.
- CP5: consolidated current repair order, preserved working systems, bounded pending work and final evidence. Production tree unchanged by this audit.

- CP6: reconciled current ammunition/fuel order implementation against stale plan prose; added the complete subsystem disposition matrix. Final probe source growth includes the third FCM integration scenario, not a new scan probe.
