# Physical projectile modules and point-defense acquisition

**Status: core and shipyard code integrated on main; acceptance smoke task configured, Java/Android device execution not yet confirmed.** No additional art required. The artwork and three-stage VFX composer are consumers of these simulation events and never determine projectile health, collision, or whether an interception occurs.

## New authoritative projectile properties

Every missile, torpedo and *slow* kinetic shell now spawns with a snapshot of the fitted launch module's physical properties, derived from its faction and any existing 0–6 upgrade stages:

| Projectile / standard neutral baseline | Speed (units/s) | Health (PD damage) | Radar cross-section (relative) | Signature (relative) |
| --- | ---: | ---: | ---: | ---: |
| Slow kinetic shell | 285 | 1.60 | 0.24 | 0.54 |
| Missile | 220 | 2.50 | 0.82 | 1.25 |
| Torpedo | 135 | 5.10 | 1.35 | 1.65 |

These are **initial tuning values**, not immutable faction canon. `ProjectileModel.module(faction,kind,outputRatio,accuracyRatio,integrityRatio)` also incorporates the individual faction's propulsion, armor and emission style:
- **OUTPUT:** modifies projectile acceleration/effective speed and slightly raises thermal/emission signature
- **RESILIENCE:** gives the projectile greater physical intercept health, with a small increase in cross-section
- **SPECIALIST:** raises speed/precision and reduces cross-section/signature through an effective targeting/stealth package
- **EFFICIENCY:** continues to trade energy/heat and operating cost without inventing free improved missile armor

Existing component IDs, blueprint serialization `CD-SHIP-1` and technology chains remain intact. `Shipyard.projectileAt(design,catalog,slot)` exposes individual fitted module attributes, while `Shipyard.Fitted` aggregates several modules of the same weapon family into the ship's current one-launcher-per-family combat model. **Per-hardpoint independent ammunition, reload, guidance, launch origins and targeting** are still pending.

Each spawned `Battle.Missile` and `Battle.Ballistic` now owns a constant `ProjectileModel.Spec` independent of the displayed sprite. Dynamics use its actual `speed()`; PD hits reduce live `integrity` initially equal to its `health()`. The shell remains a tracked collision-tested projectile throughout flight.

## Point-defense physics

The PD module and sensor-suite quality are combined into `ProjectileModel.Defense(detectionMultiplier,tracking,damage)`. A fitted PD/FLK battery's output upgrade increases per-shot intercept damage and its specialist/accuracy upgrade improves tracking. An upgraded `SEN` array boosts detection. Damage to ship sensors or point-defense machinery degrades live performance.

1. **Detect:** the target must enter a true acquisition radius based on the defending ship's PD range, sensor condition, weather and installed detection multiplier, multiplied by the **fourth root** of effective projectile cross-section × emitted signature (bounded for gameplay). A low-emission needle round can pass undetected until very close; a hot, bulky torpedo can be seen and engaged farther out.
2. **Prioritize:** compare ETA from actual *closing velocity* toward the defender, not merely current distance. Receding shots remain eligible after dangerous approaching targets. Missile/torpedo and kinetic shells compete for the same real point-defense cooldown and power.
3. **Acquire firing solution:** actual RNG hit chance varies with PD tracking quality, point-defense subsystem condition, weather accuracy, sqrt(cross-section) and a bounded speed penalty. High-speed low-cross-section ammunition is difficult to hit.
4. **Apply damage:** on a successful PD shot, subtract the fitted `Defense.damage()` from projectile integrity. Heavily armored torpedoes may need several hits; a fragile shell typically only one. Only destruction removes the projectile and publishes `INTERCEPT`; otherwise it continues its real path.
5. **Visual feedback:** PD `FIRE`, projectile `PAYLOAD`, `INTERCEPT`, actual `IMPACT` and `TIMEOUT` are separate events. Artist-rendered particle glow does **not** change a projectile's radar cross-section or interception hitbox.

Defensive drones use the same projectile sensor/detectability and interception-chance calculations against missiles and ballistic shells while preserving their separate movement/one-use interception behavior. `BattleOrders.PointDefense` modes (`BALANCED`, `INTERCEPT_ONLY`, `ANTI_CRAFT`, `HOLD_FIRE`) remain independent of these attributes.

## Shipyard UI

Selecting an installed KIN/MSL/TOR module in the shipyard now displays **SPEED, HEALTH, CROSS SECTION, SIGNATURE**. Selecting a PD/FLK module shows base **TRACKING, PD DAMAGE, DETECTION MULTIPLIER**. The full effective acquisition distance depends on the defender's sensor condition, weather and **each target's** signature/cross section, so it is not one constant circle on the map.

## Test coverage and outstanding acceptance

`gradle :core:projectileModuleSmoke`, chained after the existing battle-orders smoke task, checks:
- Original module upgrades really alter speed, health, radar cross section and signature;
- PD specialist upgrades raise tracking, and output upgrades raise actual intercept damage;
- A large/high-signature missile is acquired at distance where a tiny silent missile is **not**, using the actual `Battle.step()` logic;
- Sensor degradation and battlefield weather reduce acquisition reach;
- Fast/small projectiles reduce hit chance;
- Fitted kinetic/missile/torpedo stats are inherited by real spawned world projectiles;
- Repeated seeded battle execution yields the same simulation fingerprint.

**Not yet verified:** execution of this new Java smoke task on a build runner, visual in-game firing tests, Android deployment, or the 200-versus-200 performance regression. The existing large-fleet test remains an important release gate. Current main batteries for LAS/PLA/PAR remain instant-hit beams and are deliberately not interceptable—only simulated physical ordnance is targetable. Some future weapons, especially slow plasma bolts or guided energy spheres, can opt into tracked projectile mechanics and reuse these four physical properties.

Source of truth: `ProjectileModel` (physics and detection), `Shipyard` (module fitting), `Battle` (combat and event triggers), `ShipyardPanel` (preview).
