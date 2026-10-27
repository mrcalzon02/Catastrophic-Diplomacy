# Kinetic ammunition and guided warhead schematics

The **physical round** is distinct from a gun, its hardware manufacturing Mk, mutually exclusive research path and the timed firing doctrine. Ships without mounted weapons cannot obtain damage from a payload selection. `AmmunitionTable.java` is the authoritative balance table; the impact effects are actually consumed in `Battle.java` and `DirectionalDefense.java`.

## 5 ballistic ammunition choices

| Round | Functional tradeoff |
| --- | --- |
| STANDARD | Existing 285 world-units/s shell, normal directional shield/armor damage |
| ARMOR_PIERCING | Less primary damage, faster/harder shell, **55% post-shield armor bypass**; concentrates on hull penetration rather than surface plating |
| FRAGMENTATION | Smaller, slower shell and **55-unit radial damage falloff** on real contact |
| HIGH_EXPLOSIVE | More substantial **78-unit area burst**, reduced velocity, greater power/heat and slower reload |
| RELATIVISTIC | Fictional mass-driver ammunition: ×2.4 simulation speed, ×1.8 damage, heavier kinetic resistance, ×2.65 energy, ×2.5 heat and ×3.6 reload; **not a simulated near-light-speed projectile**, still a finite target with swept collision and PD HP |

Every shot costs one real round from `kineticAmmo`. Fitted kinetic loadouts start with a limited magazine; unfitted legacy fixtures retain their test-only stock. The ammunition's speed/HP/RCS/emissions are applied to the physical body immediately at launch and persist after module condition changes. Point defense sees the actual physical cross-section and velocity, not the sprite dimensions.

## 6 guided missile/torpedo warheads

| Warhead | Functional tradeoff |
| --- | --- |
| STANDARD | Existing explosive guided impact |
| ARMOR_PIERCING | Faster/narrower penetration round, 52% post-shield armor bypass |
| FRAGMENTATION | Reduced direct damage, actual 72-unit nearby impact burst |
| HIGH_EXPLOSIVE | Increased direct blast, 105-unit nearby falloff, heavier/slower launcher payload |
| EMP | Reduced direct damage, additional power depletion and sensor suppression after real contact, with modest 65-unit explosive burst |
| SHIELD_BREAKER | Reduced raw damage, additional actual directional shield discharge and smaller system-power disruption |

All six are compatible with missiles and torpedoes, with power/heat/reload tradeoffs. `OrdnanceDoctrine` independently supplies SINGLE, RIPPLE, SATURATION, MICRO_SWARM, HEAVY_STRIKE, COLD_LAUNCH, and DECOY_CLOUD. A decoy can never call the damaging impact path, irrespective of the installed warhead. Every real warhead and decoy has a trackable shot ID, finite PD HP and radar/thermal signature.

### Combat and safety rules

- **Armor penetration** begins only after ordinary directional shield absorption. It routes a bounded share of remaining energy past the impacted armor tile; it does not deal duplicate raw damage or eliminate the protective armor itself.
- **Explosive/fragmentation** bursts originate only from an actual impact. Nearby ships within the radius take reduced radial damage proportional to distance. This includes allied vessels; missile salvos require tactical spacing.
- **EMP / shield disruption** occur only on real missile/torpedo contact. An intercepted or timed-out warhead does not produce free effects.
- **Relativistic-style** rounds are a fictional high-speed gameplay option measured in world units/s, not relativistic mass-energy or orbital physics.
- **Physical rounds** do not need or create new artwork. The existing firing–projectile–impact VFX pipeline may attach a different payload effect later; approved art is still a separate asset-completeness requirement.

### Current user-facing limitation

This stage exposes `Battle.setShipKineticAmmunition(shipId,type)` and `Battle.setShipWarhead(shipId,DamageType.MISSILE/TORPEDO,type)` as real mission/simulation control APIs. **Per-physical-hardpoint selection, blueprint save encoding, faction-specific unlock inventories, and the player shipyard payload selector still remain to be built.** Each ship currently has one kinetic ammunition type and one missile/torpedo warhead selection for the aggregated family fire controller. Do not count this as 11 freshly modeled art types or new faction-exclusive research; variants apply to the existing mounted guns and are ready for later per-slot engineering.

### Acceptance

`:core:ammunitionTypesSmoke` must prove all 11 payload identities, finite kinetic magazine cost, real submunitions’ velocity/PD statistics, heavily armored versus AP comparisons, actual fragmentation/HE splash, EMP energy drain, shield-buster effects and exact deterministic replay. `:core:ordnanceDoctrineSmoke` checks 14 missile/torpedo firing schematics including decoys, delayed ignition and PD. The combined `:core:massBattleCampaign` remains the 200-vs-200 acceptance gate.
