# Expanded defensive-weapon roster and interception balance plan

**Status:** Original design specification 2026-11-02, implementation audit
updated 2026-11-03. This document does not itself change gameplay, costs,
balance or ship loadouts; actual code and tests are linked below.

## Goal

Build layered, economically meaningful point defense across many distinct
interception mechanisms. No single defense family should reliably defeat all
threats. Mixed defenses should provide wider coverage than any one uniform
battery, but should be less optimal against a narrow threat than a dedicated
same-budget battery. An attacker can exploit observed defensive weaknesses.
Do **not** force artificial misses or build arbitrary 100% global ceilings;
make effective limits emerge from physics, track acquisition, cooldown,
effective range, speed, target durability, ammunition, power, heat and
multiple inbound contacts.

Physical *kill*, *mission kill*, *soft-kill diversion*, and *natural miss*
are separate outcomes in telemetry and gameplay.

## Historical starting state (verified before specialization)

- **PD** weapon component already exists for each original faction, along
  with **FLK** (flak projector); each has its own existing research branches.
- **ECM** is a system and **DRN** is a support component. Both already have
  real simulation roles; preserve those roles rather than replacing them.
- The current Shipyard aggregates PD and FLK into the same
  `ProjectileModel.Defense` fields and `pdCycle`. `WeaponMuzzles.family()`
  maps both to `POINT_DEFENSE`. `Battle.updateMissiles()` and the craft
  loop share interception formulas and an aggregated per-ship reload.
  A differently named module is **not** sufficient specialization.
- Projectile bodies expose actual speed, health, cross section, signature,
  guidance/thrust state, targets and immutable visual shot IDs.
- The first 184-row PD weapon matrix tests essentially laser-style PD
  at base/rank IX, not a cross-family PD-versus-flak study.
- Existing `TechnologyCatalog` accepts stages 1..9, with virtual 7..9
  derived from higher-stage ancestors. Integrate any new subsystem without
  breaking original blueprint and research IDs.

## Point-defense weapon roster — seven hard-kill families

**PD, FLK, RCW, RPD and PPD** now have real fitted combat paths.
**PLS and IMD** remain proposed, not yet working.
Never add a purchasable component before its own physical combat and
renderer mechanisms function. RCW was added only after its researched,
paid-ammunition travelling projectile, mounted sensor, live collision and
client compile were tested. This section excludes ECM, fighter
craft, decoys and ship armor.

| Code | Type | Mechanics that must genuinely differ | Primary jobs | Vulnerabilities |
|---|---|---|---|---|
| PD | Pulse laser CIWS (existing) | Ultra-fast aim; instant single-body beam; low pulse damage; per-mount retarget/thermal recovery | Fast kinetic shells, small rockets, micro missiles | Thick armor, fighter hull integrity, large continuous waves that exhaust aim windows |
| FLK | Flak projector (existing) | Physical timed/proximity bursts with local fragmentation volume, finite shell travel and dispersion | Fighter groups, assault drones, tightly packed missile formations | Relativistic shells, single tiny evasive bodies, friendly-fire/exclusion arcs |
| RCW | Rotary kinetic CIWS (**implemented**) | Independently fitted spin-up turret; genuine inertial 705-unit/s tracer bodies; swept collision, paid kinetic ammunition, separate short-range radar signature and tracking | Close rockets, drones, conventional guided weapons | Ultra-fast rounds, hard torpedoes, ammunition depletion |
| RPD | Railgun interceptor (**implemented**) | Paid precharged capacitor, independent target lock and servo, narrow 1210-unit/s physical solid sabot; actual relative-body swept hit, three kinetic cartridges per shot, long cooldown and pulse-Doppler sensor on measured radial closing | Hardened torpedoes, sufficiently tracked fast large shells, large missiles | Weak base-tier small-missile tracking, limited shots per second, expensive stock and thermal consumption |
| PPD | Particle-beam PD (**implemented**) | Accurate per-body armor-ablation beam with real sustained optical lock, continuous reactor draw, mounted local thermal shutdown and cooling | Armored torpedoes, hardened missiles, medium craft | Micro swarms, heat/continuous power, rapid target switching |
| PLS | Plasma burst projector | Short-range expanding, dispersing plasma cone; decaying energy volume | Dense rocket salvo, slow close clusters, near-range craft | Fast tiny shells, distant threats, heavy shielding; severe heat |
| IMD | Guided interceptor missile cell | Genuine long-range counter-missile; launch/reload ammo, guidance, warhead and minimum arming distance | Heavy torpedoes, big guided missiles, distant high-value tracks | Micro missiles, decoys, short-range rockets, repeated cheap barrages |

### Other defense systems: separate scope

- **Electronic countermeasures (ECM)** already have their own planned system.
  They are not point-defense gun modules; no redesign is authorized here.
- **Chaff, flares and IR/radar decoy dispensers** belong in that existing
  electronic-countermeasures section. Their implementation status needs a
  separate audit; do not claim that they are already working.
- **Defensive drones are fighter/craft assets**, not eligible in this PD
  weapon roster. They belong to the hangar, fighter and drone system.
- **Reactive armor and reactive countercharges** are protection/armor
  mechanics, not a new point-defense weapon class. The proposed reactive
  fragmentation curtain is removed from this PD plan.
- **Dedicated EMP disruptors are not included.** Any EMP arising from
  nuclear area-denial weapons belongs to the ordnance/area-effects design,
  not to a new PD gun.
- **Guided interceptor missile cells (IMD) remain part of this roster.**
  Unlike ordinary offensive missile racks, they use paid, finite counter-
  missile stocks to engage hostile physical ordnance. They must have
  physical flight, sensors, guidance, reload, and arming limitations.

This hard-kill roster contains seven weapon families: PD, FLK, RCW, RPD,
PPD, PLS, and IMD. Other fleet defenses can still operate alongside them
without being silently counted as point-defense weapons.

## Suggested relative effectiveness matrix

These are intended *roles*, **not measured kill rates** or final balance
coefficients. H=strong, M=meaningful, L=weak, X=not a useful counter.
A defense can be H against an isolated threat yet fail under saturation.

| Defense | Fast kinetic | Dumb rockets | Micro swarms | Smart missiles | Armored torpedoes | Fighters | Drones |
|---|---|---|---|---|---|---|---|
| PD pulse laser | H | H | H | M | L | L | M |
| FLK flak | X | M | M | M | L | H | H |
| RCW rotary CIWS | L | H | M | H | L | M | H |
| RPD rail interceptor | H | L | X | M | H | L | L |
| PPD particle beam | M | L | L | M | H | M | M |
| PLS plasma burst | X | H | H | M | L | M | M |
| IMD interceptor missile | L | X | X | H | H | M | L |

**Caveat:** H/M/L/X here describe proposed **physical interception roles**, not
ECM guidance disruption. Flak and plasma may hit grouped physical bodies via
real timed proximity effects, never false instant multi-target eliminations.
Do not combine physical kills and guided-ordnance diversion into one percentage.

## Proposed base effective range envelopes, world units

For reference, the current installed baseline Terran PD nominally derives
a range around 155 world units before environmental/signature adjustment.
The following are initial design envelopes, **not existing implementation**:

| Family | Useful range (approx.) | Inner weakness | Dominant constraint |
|---|---:|---|---|
| PD | 55–205 | Tracking/dwell on multiple targets | Heat and independent aim cycles |
| FLK | 65–180 | Shell travel/fuse timing near hull | Burst reload, arcs, ammunition |
| RCW | 35–155 | Some lead/arming distance | Ammunition and spin-up |
| RPD | 120–335 | Charge/lead and point blank | Power, cooldown |
| PPD | 90–275 | Dwell and optical lock | Reactor and cooling |
| PLS | 20–115 | Short-range thermal burst | Heat, poor reach |
| IMD | 180–480 | 70-unit minimum arming radius | Ammo cost, guidance |

Range falls with damaged sensors, weather, and hostile low-observable
signatures; it does not become an artificial fixed detection circle. Shield
interference and actual field of view should influence some types.

## Equal-tier and cross-tier experimental design

For each **physical weapon loadout** and **each defense family**, measure:

1. Base attack vs base defense.
2. Stage-IX attack vs stage-IX defense.
3. Stage-IX attack vs base defense.
4. Base attack vs stage-IX defense.
5. Stage-III and stage-VI equal-tier interpolation after extremes pass.

Also vary **attack count** (1 incidental, 4/8/16 burst, multi-ship saturation),
**defense composition** (1 mount, 4 homogeneous mounts, 2+2 mixed, 4
independent ships), target maneuver and approach vector, radar/IR concealment,
near/mid/far engagement, finite magazines and sustained real reloads.
A separate combined-arms validation pass may include existing ECM, decoys
and fighter wings, but their non-PD interventions must be measured separately.
Do not equate four installed mounts with four independent ships.

A representative threat set:
- kinetic standard/AP/frag/HE/relativistic;
- all four rocket hull sizes, both motors, 4/8/16 barrage and moving target;
- missiles, torpedoes, warhead profiles, ripple, saturation, cluster,
  cascade/micro swarms and seeker decoys;
- all six fighters, attack and repair drones, fighter-launched ordnance.

Factions differ in mass, sensor, recoil, throughput, thermal economy, and
manufacture, while retaining the intended **family counter roles**.
Run cross-faction designs after baseline Terran equality calibrations, with
real installed modules and raw per-mount research. Do not interpret a rank-IX
defender versus a baseline launcher as proof of IX/IX balance.

## Counters, not arbitrary chance caps

Use physical constraints for anti-universality:
- Independent per-mount fire-control state, cooldown, power/heat expenditure
  and ammunition; weighted sensor tracks can be shared across the ship.
- Per-target signature/speed/armor/flight-state suitability, travel time,
  seeker quality, salvo composition, projectile durability and attack angles.
- Area effects require actual radius, flight, burst timing and friendly/neutral
  safety rules; submunitions have unique IDs and may be shot individually.
- **LOCK_BROKEN / DIVERTED** outcomes belong to separate electronic warfare
  diagnostics and must never be counted as PD projectile kills. Any chaff,
  flare, and decoy usage must be charged to its separate physical inventory.
- Priority rules must value *expected harm*, *time to impact*, and *chance
  of completing an effective interception*; prevent defenses wasting
  premium interceptor ammo on harmless, already-missing contacts.
- Extra turret mounts increase simultaneous controlled engagements with
  bounded shared sensor work. Avoid per-ship × every-body × every-emitter
  unbounded loops in 200-vs-200 fleet tests.

## Recommended implementation order and release safety

1. **Instrumentation and inventory.** Per-emitter ID, mounted family, slot,
   research tier/track; physical attack ID and per-family engagement counters.
   Maintain existing PD/FLK behavior while separating it; ECM, craft/drone
   and armor code stays out of scope.
2. **De-aggregate existing PD and FLK.** Separate independent physical
   firing states, targeting, valid targets, hit/damage logic, ranges and
   proper flak burst/fragmentation. No damage model swap for unrelated
   campaign weapons.
3. **RCW and RPD implemented.** RCW has its own spinning kinetics, 705-unit/s
   bullets and close-range radar head; RPD has its own charged 1210-unit/s
   solid sabot, 120–335-unit fitted bore/sensor reach, real per-socket
   Doppler/servo tracking, impact-first collision and prolonged reload.
   Both have paid stocks and compiled authored moving renderers. Continue
   unequal-tier, mixed-defense and fleet-load testing before balance sign-off.
4. **Add PPD and PLS** with real energy/volume interaction and thermal constraints.
5. **Add IMD**: guided counter-missiles, physical magazine, reload and arming,
   target acquisition, and per-projectile intercept accounting.
6. **Full seven-family × weapons × tech × approach × load experiments**
   measuring actual interception, damage prevented and cost-effectiveness.
7. **Fleet integration and cost parity**: mixed guns, 200-vs-200 performance,
   save/load/backward compatibility and responsive mobile UI regression.
   Run separate combined-arms tests with existing ECM/craft as needed.

Track physical interception before terminal entry, terminal-ring breaches,
post-entry interceptions, actual impacts and shield/armor/hull damage,
point-defense shots, ammunition cost, power, heat, turret busy time,
and credits of damage prevented per credit of defense. Keep lock diversion
and jamming as **separate ECM outcomes**, not point-defense physical kills.

**Release policy:** cosmetic/visual/balance/stylistic thresholds remain
advisory diagnostics only. Objective build/install/launch/run, save
compatibility and required multiplayer/update failures can block releases.
Do not convert a proposed target band into a release gate.

**Historical verified continuation (November 2, 2026):** Four of seven
physical hard-kill families are live. RCW's [84-pair firing/aircraft report]
(docs/diagnostics/PD_ROTARY_CIWS_PHYSICAL_IMPLEMENTATION_AND_TARGET_EFFECTIVENESS_2026-11-02.md)
is verified with client compilation. RPD completed 36 base pairs,
192 intermediate-tier geometry pairs, and 192 controlled actual moving-body
collision A/B pairs, and the correction is active in normal battles; see
[the rail implementation and balance report]
(docs/diagnostics/PD_RAIL_INTERCEPTOR_MOVING_BODY_BALANCE_2026-11-02.md).
Full release packaging was intentionally skipped. **This historical
checkpoint preceded PPD implementation.** As of November 3, the fifth
family, PPD, has separately installed powered sustained-ablation combat,
all eleven research lines through stage IX, physically verified same-seed
HOLD_FIRE pairs including equal-rank fitted attackers, and a compiled
particle turret/beam render path:
[PPD focused physical test](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41680995537).
PLS and IMD still have no working installed combat path. Fleet sensors,
tracks, command fusion and complete 200v200 five-family balance remain
separate unfinished work.
