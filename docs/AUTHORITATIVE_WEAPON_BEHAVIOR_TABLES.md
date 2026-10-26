# Authoritative weapon behavior tables (combat simulation)

**Current status:** This specification accompanies `WeaponBehaviorTable.java`, which is consumed by the *actual* battle simulation. The table is a reproducible source of truth, not a replacement for artwork or a claim that individualized hardpoints have been built. Values below are **unmodified base weapon-family rules**, prior to race-specific components, installed Mk patterns, the selected exclusive six-rank research track, crew condition, subsystem damage, ECM, environmental modifiers and tactical orders. Simulation advances at **30 Hz**.

## 1. Main battery behavior and delivery

| Module | Actual damage type / delivery | Maximum range (world units) | Min distance | Base reload (s) | Power/shot | Heat/shot | Thermal firing gate | Aim cap |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| LAS | LASER / instant beam | 530 | 0 | 1.05 | 9 | 7 | below 91% of maximum heat | 98% |
| KIN | KINETIC / physical unguided ballistic | 445 | 0 | 0.75 | 5 | 4 | below 95% | 97% |
| PLA | PLASMA / instant beam | 245 | 0 | 1.80 | 14 | 17 | below 86% | 94% |
| PAR | PARTICLE / instant beam | 565 | 0 | 2.10 | 17 | 10 | below 90% | 98% |
| MSL | MISSILE / guided, interceptable | 690 | greater than 80 | 3.20 | 13 | 5 | no additional heat hard-stop | n/a |
| TOR | TORPEDO / guided, interceptable | 840 | greater than 110 | 6.40 | 18 | 9 | no additional heat hard-stop | n/a |

An installed module multiplies effective reach, cooldown, damage, tracking and physical projectile statistics through its own fit, exclusive research and manufacturing generation. A weapon may fire only if its installed damage is positive, power exceeds the shot cost, its cooldown is zero and its range gate is met. Launchers must also have ammunition; missiles require weapon and magazine condition above 0.15; torpedoes require magazine condition above 0.15. Thermal gates are deliberately NOT fabricated on missiles and torpedoes. **There are no built-in hull weapons** in properly fitted vessels.

Hitscan hit probabilities use actual crew readiness, weather, target ECM, targeting lock, module/faction accuracy and family-specific tuning. Aim formula is bounded by the appropriate listed cap:

| Family | Base aim coefficient | Lock bonus coefficient | Minimum weapons-condition fraction used in reload |
| --- | ---: | ---: | ---: |
| Laser | 0.70 | 0.45 | 0.30 |
| Kinetic | 0.72 | 0.40 | 0.25 |
| Plasma | 0.80 | 0.40 | 0.25 |
| Particle | 0.76 | 0.45 | 0.25 |
| Missile | Guided; no upfront hit roll | n/a | 0.40 |
| Torpedo | Guided; no upfront hit roll | n/a | 0.25 |

Kinetic miss produces a **real** flight path displaced by ±0.24 radians. Missiles/torpedoes continue moving and have finite HP; point defense and defending fighter/drone systems may destroy them prior to contact. No damage is awarded simply because a projectile was launched.

## 2. Physical projectile properties (ProjectileModel authoritative)

| Body | Speed (units/s) | PD hit points | Radar cross section | Emission signature | Lifetime / steering |
| --- | ---: | ---: | ---: | ---: | --- |
| Kinetic shell | 285 | 1.60 | 0.24 | 0.54 | Swept ballistic collision, no homing; lifetime max(1.5 s, 760/speed) |
| Missile | 220 | 2.50 | 0.82 | 1.25 | 10 s propulsion; base 0.040 steering coefficient/tick |
| Torpedo | 135 | 5.10 | 1.35 | 1.65 | 14 s propulsion; base 0.025 steering coefficient/tick |

These are base reference bodies, not every installed module's final projectile: faction engineering, research, fitted integrity, output and precision alter speed/HP/signature/RCS. The physical data is in `ProjectileModel`; the **engagement and steering law** is in `WeaponBehaviorTable`. Swept motion prevents shells and guided rounds tunneling through targets at 30 Hz. Projectiles expire or are removed on contact/interception. The firing and impact visuals must follow real shot IDs.

## 3. Directional hit consequences (WeaponBehaviorTable.damage)

A baseline attack consumes shield and armor pools according to the corresponding multiplier, then applies structural damage. These numbers are *not* three separate full-damage hits: defense absorption reduces remaining incoming energy before subsequent layers. Per-facing armor technology can override default response.

| Type | Shield effectiveness | Armor effectiveness | Structural damage multiplier | Penetrating secondary effect |
| --- | ---: | ---: | ---: | --- |
| LASER | 1.00 | 0.80 | 1.00 | None |
| KINETIC | 1.30 | 1.25 | 1.00 | None |
| PLASMA | 0.65 | 1.00 | 1.15 | Adds heat: 0.35 × actual hull damage |
| PARTICLE | 1.40 | 1.00 | 1.30 | Removes power: 0.45 × hull damage; damages sensors: 0.15 × hull-damage fraction |
| MISSILE | 1.00 | 1.00 | 1.00 | None |
| TORPEDO | 1.50 | 1.50 | 1.30 | None |
| FIGHTER | 1.00 | 1.00 | 1.00 | Generic fallback damage family |

This table **now initializes** every directional shield, armor tile and structural multiplier in `DirectionalDefense`, while `Battle.hitAt` consumes the same secondary-effect entries. No longer two sets of independent magic constants.

## 4. Point defense / flak and orders

PD/FLK modules are installed hardware with physical reach, tracking and intercept damage. The current simulation **aggregates** equipped PD/FLK modules into one shipwide controller and single shared cooldown; a distinct flak area-of-effect simulation is **not yet implemented**.

| Behavior | Actual rule |
| --- | --- |
| Defensive priority | Incoming hostile guided missile, torpedo, or interceptable slow ballistic shell, ranked by estimated closing ETA |
| Target acquisition | Base PD range × fitted scan multiplier × sensor condition × weather × fourth root of target RCS × emissions |
| Hit probability | 0.68 × tracking × crew/condition × weather accuracy × cross-section factor × target speed factor, capped at 96% |
| Versus ordnance | 0.16 s basic cooldown, spent energy 1.5, heat 0.8, damage from installed PD profile |
| Versus fighters/drones | 0.18 s basic cooldown, same energy/heat, installed intercept damage × 6.5 to craft |
| PD integrity gate | Above 0.20 PD subsystem condition, powered, positive PD range and operator crew readiness |
| BALANCED | Fires on ordnance **and** hostile fighters/drones |
| INTERCEPT_ONLY | Ordnance only |
| ANTI_CRAFT | Craft only |
| HOLD_FIRE | Neither; no energy expended for either class |

PD uses the same shared reload across both target classes. Craft interception and ordnance interception are separate targeting sweeps, with finite damage and actual failure possibilities; both respect toggles. A sensor-poor, small/fast/stealth missile is genuinely harder to detect and hit than a large, slow, high-emission target.

## 5. Autonomous fighters (FighterModel authoritative)

These six kinds are **independent craft entities with own hit points, armor, fuel, radar signature, sensors, acquisition, weapons, ammo where relevant, and physical projectiles**. Their stats are defined in `FighterModel.forFaction()`, so all eleven factions have distinct physical tradeoffs, not simply a visual recolor.

| Fighter hull | Weapon | Baseline hull HP | Armor | Speed | Sensors | Weapon damage | Range | Reload | Carried shots |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Interceptor | Cannon | 23 | 4 | 240 | 280 | 6.5 | 128 | 0.65 s | 28 |
| Assault gunship | Cannon | 56 | 20 | 152 | 245 | 14 | 155 | 1.02 s | 36 |
| Laser fighter | Laser | 32 | 7 | 202 | 315 | 10 | 185 | 1.10 s | Energy/no shell magazine |
| Particle fighter | Particle | 35 | 10 | 181 | 350 | 11.5 | 215 | 1.35 s | Energy/no shell magazine |
| Missile fighter | Missile | 39 | 11 | 175 | 390 | 20 | 365 | 2.4 s | 5 |
| Torpedo bomber | Torpedo | 68 | 24 | 117 | 420 | 53 | 440 | 4.5 s | 3 |

Interceptor fighters may attack hostile craft; defensive drones target ordnance and physical repair drones service allied hulls. **Fighter-launched missiles must not be mistaken for the parent ship's main-battery missiles** in the module inheritance tests.

## 6. Mechanical boundaries, visual requirements and next-stage research

The following are NOT completed by this baseline table:
- True **per-mount** range, cadence, ammo, firing arc, unique muzzle coordinate, damage application and target selection (the engine still aggregates the *same-family* gun modules per ship).
- Flak burst/area suppression, different PD intercept ammo/laser types, independent mount cooldowns and anti-fighter tactical doctrine by module.
- Fully separate fighter-class hardware module records, fighter research variants/tiers and individual fighter equipment fitting.
- Campaign-researched unlock inventory per player faction/subfaction; all eleven use the same legal catalogue and fitting system, but strategic unlock enforcement is future work.
- Distinct operational templates and AI target profiles for support-heavy vessels and additional advanced weapon families.
- Certified in-engine animation, sound, and curated muzzle/projectile/impact PNGs. `WeaponFxProfile` defines the three-stage visual convention but absence of runtime imagery must never be reported as certified art.

**Tests:** `:core:weaponBehaviorSmoke` validates all six configured ship families, actual fire events, range/ammo/heat/power/cycle/aim gates, every directional damage type, four PD toggle modes, and all 66 fighter faction-class profiles. The existing projectile/PD/fighter, support, exclusive research and 200v200 suites remain part of the regression chain.

The exact values above are intentionally versioned, so any balance adjustment is a change to the Java source-of-truth and its tests, *not merely a spreadsheet edit*.
