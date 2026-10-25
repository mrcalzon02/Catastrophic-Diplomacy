# Directional damage model — System II (implementation foundation)

Status: **Java 17 deterministic core integrated; rendering effects are placeholders**. Do not call GPU normal mapping, shader-based cutouts, specific compartment topology or device acceptance complete.

## Geometry and orientation

The user's initial octant diagram is canonical. Every ship uses a local +X forward direction, +Y port, and an eight-slice polar diagram (eight 45-degree slices). Adjacent octants form four protection facings, each covering 90 degrees: FORWARD (±45 degrees), PORT (+45 to +135), AFT (+135 through 180 to -135), STARBOARD (-135 to -45). A clockwise visual representation may use a nose-up image with the same angle transform. The player's red Cartesian overlay becomes an independent **16 by 16 tile grid** over sprite-local coordinates. The current initial occupancy is an ellipse; replace only with a per-hull alpha/armor mask after real art is integrated. Tile and direction identifiers must be stable for replay/save serialization.

## Ordered layers

1. **Powered shields**: four independent energy capacities with facing-specific maximum capacity, current state, recharge/second, hit recharge-delay and reactor/power/heat gating. Incoming bearing selects shield facing, before the impact grid is considered. Damage type multiplies how quickly shield integrity drains. A disabled arc does not borrow capacity from a healthy arc. Recharge consumes power.
2. **Localized armor**: hit resolves one occupied tile. Each tile inherits a base capacity from its facing's quarter of the hull armor budget, initially divided by the count of occupied tiles in that facing. Face-specific armor modules increase only the matching tiles' capacity and current integrity. Damage-type modifiers control the physical armor consumption; excess raw damage continues. No globally pooled armor absorbing hits on other locations.
3. **Structural sublayer**: excess damage from the tile damages local exposed structural integrity and the ship's hull HP. Existing SIF mitigates the remaining hull damage after armor. System casualties still use the existing seeded combat pathway. A tile's armor exposure fraction and internal damage fraction are exposed independently for rendering.

Current implementation lives in `core/src/main/java/diplomacy/sim/DirectionalDefense.java`; `Battle.hit` resolves attacker direction in ship-local space and syncs the existing aggregate shield/armor HUD values. `DirectionalDefenseTest` verifies sector isolation, local tile loss, powered recharge delay, damage types and deterministic replay. The 200v200 simulation and legacy battle tests must still be rerun after integrating this model, since encounter lethality may shift substantially.

## Typed resistances

Use configurable multiplicative damage factors at each layer (less than 1 = stronger resistance; more than 1 = weaker). Current initial defaults are **simulation parameters, not final balance**:

| Weapon | Shield loss | Armor loss | Structural damage |
| --- | ---: | ---: | ---: |
| Laser | 1.00 | 0.80 | 1.00 |
| Kinetic | 1.30 | 1.25 | 1.00 |
| Plasma | 0.65 | 1.00 | 1.15 |
| Particle | 1.40 | 1.00 | 1.30 |
| Missile | 1.00 | 1.00 | 1.00 |
| Torpedo | 1.50 | 1.50 | 1.30 |
| Fighter | 1.00 | 1.00 | 1.00 |

The effect is applied independently to the shield-sector pool, a selected armor tile, and the exposed structural sublayer. Modules will eventually use validated technology records to change these profiles; the current core offers explicit `addArmor`, `addShield`, `configureRecharge`, and `setDamageFactor` APIs but is **not yet connected to upgrade items or UI controls**.

## Asset compositing plan

Four layers per vessel/variant with consistent UV and orientation: (A) intact armor RGBA sprite, (B) reusable interior/structure RGBA beneath, (C) per-tile armor-depletion alpha mask, and (D) emissive/heat damage mask on exposed edges. A fifth tangent-space normal/bump map supports moving directional lights; a material/roughness mask is optional. Gameplay protection values drive tile alpha and edge glow, never vice versa. **Do not pre-bake dynamic lighting or force the physics to rely on pixels.** The current Terran sprite client now composites a procedurally generated generic structural sublayer beneath intact armor art. When a local tile loses armor, its exterior sprite region fades according to tile exposure and reveals structure; approximate amber/red tile indicators provide temporary breach emphasis. This is not yet a normal-mapped shader or authored ship-specific internals. Future implementation should keep GPU instancing and fallback support, and should run a CPU headless mask-state test.

## Acceptance and remaining tasks

- Direction changes select the matching arc; damage must not drain unrelated shields.
- Repeated impacts remove the struck tile; neighboring untouched tile values remain intact.
- Per-facing armor/shield modules change exactly their intended regions.
- Shields recharge only when powered and after their hit delay; shields consume reactor output.
- Hits of equal raw strength but different type have measurable layer-specific results.
- SIF, internal structural damage and hull HP are distinct and reproducible.
- Signed checksums/replays include directional state; platform file migration is required if persistent battle snapshots begin storing it.
- Improve real projectile surface contact location, ray/shape intersection, tile masks from sprite alpha and per-compartment casualties.
- Add actual composited armor/structure sprites, tile-alpha destruction and dynamically lit normal maps.
- Profile hundreds of ships with 16x16 grids at 30Hz on desktop and Android; avoid per-frame allocation in the hit path.
- Validate all old/headless suites and new visual/device acceptance before claiming the system production-ready.

## Design protection

The current directional simulation uses the attacker's location to estimate the incoming ray and surface tile. That is sufficient for the foundational melee of arcs/tiles, **not** pixel-accurate missile impact or laser contact on a rendered sprite. It must be superseded by real intersection/contact data once projectile bodies and sprite masks are instrumented.
