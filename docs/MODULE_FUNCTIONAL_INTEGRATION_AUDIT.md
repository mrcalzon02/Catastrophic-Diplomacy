# Concrete module behavior audit — 2026-11-01

This is a source-level engineering audit, **not** a claim that pending Android
builds or 200v200 endurance runs passed. New module families and their existing
research levels remain distinct from the **48 additional survival-hardware
variants that have NOT been added yet**. Do not expand those variants until
functional regression checks are green.

## Sensing and deception — nine equipment families plus 36 existing variants

| Hardware | Simulation consequence | Observable player consequence |
| --- | --- | --- |
| RDR radar | Increases actual range of radar acquisition | More distant true target contacts |
| STC radar-signature plating | Reduces radar cross-section used by hostile lock and seekers | Followed ship indicates radar stealth; opponents lose distant radar contact |
| IRT thermal imager | Adds independent infrared contact range | Infrared scanner identified in live ship status |
| HTD thermal suppression | Reduces thermal signature against hostile IR | Thermal stealth shown on followed ship |
| VCL optical cloak | Powered cloaking reduces optical-only acquisition; shutdown fails open | Original ship art becomes semi-transparent only while powered |
| ACU targeting processor | Better actual accuracy and sensor acuity | Improved weapon lock and resistance to holographic spoofing |
| XRY X-ray sensor | Independent non-infrared detection channel | Scanner identified in selected ship status |
| GRA gravitic sensor | Detects massive hulls despite ordinary visual/IR stealth | Gravitic scanner identified in selected ship status |
| HLP holographic emitter | Powered ECM reduces physical weapon lock and guided seeker tracking | Moving translucent source-art false contacts; missed beam/kinetic shots can track a false image |

Holographic contacts are *electronic/image projections*, **not** second physical
ships, independent hit-point pools, or direct AI fleet roster units. The original
source artwork is reused and no fabricated substitute ship silhouette is introduced.

### Current functional implementation (November 1 follow-up)

- **Cloak:** running VCL fades the original art and reduces visual acquisition.
  A damaged ECM circuit or power loss fails open; the actual emitter now draws
  power and produces heat each combat tick. Radar, thermal, X-ray and gravitic
  counters remain distinct and can defeat a cloak by their real sensing channels.
- **Holo projector:** powered HLP creates one to three moving world-space
  contacts, with count derived from module performance and ECM condition.
  Live beam/kinetic/fighter misses can target the *identical coordinates*
  drawn by the renderer. Projectors consume power/produce heat and shut down
  at insufficient reactor headroom; they never gain hit points or invent hulls.
- **Guided ordnance:** actual missiles and torpedoes may lock onto a projected
  false contact for a stable 45-tick guidance epoch. Their real momentum,
  thrust, finite propellant, seeker detection, point defense and hull collision
  remain authoritative. Losing emitter power switches surviving locked seekers
  back to actual target telemetry; there is NO free instant retarget to a new hull.
  Higher-quality seekers resist deception. Each physical diversion contributes
  to the target's tactical **SPOOFED SEEKERS** counter.
- **Strike fighters:** individual craft must acquire a target with their own
  radar/optical limits before shooting; carrier datalink does not grant
  omniscient fire control. Active stealth can deny contact, and holographic
  countermeasures reduce hit probability and redirect missed cannon/beam shots.
  Physical fighter ammunition, projectile collisions and hits remain unchanged.
- **Observability:** selected-ship tactical status displays powered cloak,
  live holo contact count, and accumulated counts of actual diverted guided
  seekers and weapon shots. Replay fingerprint records those counters and each
  missile's false-contact lock for deterministic comparison.

### Verification requirements (DO NOT mark completed based on code existence)

1. `sensingModulesSmoke` must prove sensor channel counterplay including
   damaged/unpowered cloaks and fighter-range suppression.
2. `ordnanceSeekerSmoke` must launch **real missiles** against a powered holo
   emitter, observe a physically spoofed missile's last-known world coordinates
   matching the displayed ghost, and observe reacquisition after emitter shutdown.
   Deterministic replay must retain the same simulation fingerprint.
3. `combatShipStatusSmoke` must show actual spoof counters, not synthetic values.
4. Platform runtime/screenshot diagnostics must capture original-art translucent
   cloaks and multiple holograms at useful resolution; cosmetic differences are
   diagnostic only. GPU rendering and startup failures are release-blocking.
5. Measure CPU/memory impact in actual 200v200 fleets; ensure expensive texture
   layers are culled and no per-contact hull image duplicates are uploaded.
6. Confirm power/thermal drain, ECM degradation, seeker quality, and real range
   counterplay do not create the never-ending late-battle deadlock or Android OOM.

**Not implemented:** independent AI roster entities for ghosts (intentionally
not required for functional deception); self-propelled escape-pod craft;
48 extra survival-family hardware variants. Do not confuse planned items with
verified implementation.

## Survival and engineering — 12 existing physical families

| Hardware | Simulation consequence | Observable consequence |
| --- | --- | --- |
| DMC damage-control | Actual hull damage repaired while powered; self-repair support | Repair shimmer using the genuine damaged or intact source-art silhouette |
| HCR armored crew | Physical berths and fewer crew casualties on penetration or internal accidents | Survivor/casualty results, crew readiness |
| CIC command citadel | Reduced command damage from direct penetration and comm-bus incidents | Continued ship command/control ability |
| FBN fuel bunker | Additional fuel capacity; lower finite-store fuel loss on real penetration | Persistent fuel/mobility advantage |
| AMV magazine vault | Extra physical magazines and lower accumulated missile/kinetic/torpedo store loss | More remaining ammunition, no phantom refills |
| EPD escape-pod battery | Finite surviving crew evacuated on genuine hull destruction | Counted survivors and anchored PODS tactical wreck signal |
| RCB recovery beacon | Activated only on genuine wreck; only on victory can postbattle salvage recover a capped hull-scrap value | Persistent recovery marker; awarded scrap in committed mission result |
| PGO governor override | Increased power regen and heat; reactor output trips on failure | Golden power-fault flash, named failure |
| JPB plasma battery | Increased power regen and heat; containment collapse destroys hull, personnel and reactor condition | Violet plasma-fault flash, named failure |
| IBR battery brick | Cheap additional regen, unstable heat; overload damages communications and target sensors | Blue comms fault flash, named failure |
| IST thermal shunt | Extra cooling, abnormal heat; failure damages cooling manifold and hull | Orange thermal failure flash, named failure |
| IRG reactor tap | Large regen gain, extreme heat; failure reduces reactor, power, crew and hull | Red core-fault flash, named failure |

Unsafe hardware faults are deterministic, use fitted physical family identities,
and apply damage via genuine battle state. Safe modules never receive fabricated
random retrofit malfunctions. All crew, fuel and ammunition losses stay finite.
The mission ledger grants no beacon salvage from a lost battle and never replaces
a destroyed ship for free.

## Verification and outstanding scope

- `sensingModulesSmoke`: real fitting, 36 variants, tier and save checks,
  optical/IR/X-ray/gravity detection, radar stealth, power-off cloak and holo
  lock/visual contact positions.
- `survivalModulesSmoke`: all 12 physical families, tiering, actual protected
  store damage, distinct deterministic engineering faults, real destroyed-ship
  pod counts, beacon-only settlement eligibility and no duplicate destruction.
- `combatShipStatusSmoke`: tactical indication of powered cloaks, holo contacts,
  sensors, survival gear and the specific unsafe hardware fault.
- `gameFlowSmoke` and the platform-specific game builds must verify the
  real Android/desktop GL renderer. Any visual review should produce screenshots
  but remains diagnostic, not a release-blocking aesthetic gate.
- **Pending:** reliable passing release and full endurance tests for the newest
  SHA. Previously observed randomized 200v200 seeds 2026103105/06 have not
  reliably reached terminal victory; do not disguise those stalls.
- **Pending:** physical pod craft and an explicit retrieval mission are not
  implemented; pods currently prevent crew casualties and produce a wreck
  signal. The beacon has limited after-action salvage but does not resurrect
  physical hulls or installed equipment.
- **Pending:** visually inspect cloaks, original-art holo contacts, source-art
  fault flashes and wreck overlay on a real Android/desktop runtime.
- **Pending:** measure Android peak RAM and GL texture residency after recent
  mipmapped, lazy-loaded ship-art changes; prior Zippledrow shard had an OOM.

**Expansion gate:** No 48 new variants until existing effects and functional
build are independently checked. Prioritize missing functionality before
adding a new catalog entry; art/balance diagnostics never block a functional
release by themselves.
