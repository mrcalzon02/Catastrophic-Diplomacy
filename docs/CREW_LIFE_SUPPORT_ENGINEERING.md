# Crew accommodation and life support — fitted equipment, not free hull stats

This increment directly continues the hull-first physical engineering and manufacturing version branches. It is based on PR #13 and **is not yet merged to main**.

## Gameplay contract

Every native hull has exactly **zero built-in crew berths and zero breathable-air capacity**. Its structural baseline already supplies physical hull HP, armor, selective shields, speed, power, sensors, radar cross-section, and module-slot counts. The hull merely reserves two *empty* physical HABITAT sockets. A valid operational fit has both:

1. **CRW — Crew Accommodation:** provides bunks, crew facilities and a maximum living complement.
2. **LFS — Life Support:** independently provides atmosphere processing, water circulation, and support for living personnel.

Both occupy real dedicated sockets, consume real ship mass/volume/electrical power/cooling budget, cost credits/industry, and may use the same independent hardware marks and exclusive research tiers as other modules. Their quantity is proportional to the class's vessel volume, representing the physical scaling of compatible habitat equipment. Their capacity must independently meet the minimum staffing requirement of the actual installed machinery.

Removing either module produces a rejected operational fitting. There are no hidden emergency crew cabins or automatic oxygen grants. Existing weapon and reactor module numeric hardpoint indices remain unchanged because the new sockets were **appended**, not inserted.

Per-faction ships use the exact same equipment system on both battle teams. Five added alien civilizations have their own CRW/LFS module IDs and researched prerequisite chains; equipment inheritance is no longer an arbitrary faction alias.

## Physical combat integration

A fitted `Battle.Ship` receives actual `crewRequired`, `crewBerths`, `lifeSupport` and `crewCount`, bounded by its installed equipment. A crew-short ship becomes less combat-ready, loses sensor targeting efficiency and has reduced reactor recharge. Penetrating structural damage can kill crew; repaired armor/structure does not instantly resurrect them. Legacy unfitted simulation fixtures remain available for older tests and must not be represented as finished player shipyard outfitting.

**Pending:** crew deaths are currently a coarse deterministic attrition model, not per-deck life-support breach or individual crew occupations; damage to the life-support module itself, crew replenishment at docks, life-support consumption per tick, crew automation, morale, and boarding are future separate systems. Do not claim those features are complete.

## Legacy ships and persistence

All original hardpoint IDs are retained. Crew and air records are written as normal equipped module rows in `CD-SHIP-1` or `CD-SHIP-2` blueprints; other installed versions and exclusive research branches are preserved.

The decoder only backfills both new habitat modules on **old unversioned CD-SHIP-1** designs that lack both habitat records entirely. Explicitly versioned `CD-SHIP-2` crew-free designs are never magically staffed and fail normal fitting validation. This is a transparent migration, not native hull crew capacity; the migrated file now contains two real installed modules.

## Current technology size

- Eleven playable race identities × 22 native module chassis = **242 base component types**.
- Each base has four mutually exclusive six-stage research branches: **5,808 technology entries**.
- Each native component has five engineering patterns × four manufactured marks: **4,840 independently fitted hardware configurations**.
- Counts represent logical equipment/research **choices**, not thousands of separate approved art sprites. No fabricated icon or particle art is implied.

## Acceptance

`:core:crewAccommodationSmoke` validates all 121 combinations of eleven factions and eleven hull classes, explicit equipment on the two new physical habitat slots, legacy-save migration, strict rejection for either missing habitat module, independent research rank effects, and actual manning capacity reaching the Battle simulation. `:core:massBattleCampaign` invokes the crew suite, in addition to fitted projectiles, point defense, repair, shielding, fighters and 200v200 deterministic replay.

**Outstanding earlier art blocker:** PR #11 still awaits the real approved PNG binary uploads and QA before it can merge. Crew engineering regression tests do **not** replace that asset-inclusion gate, and earlier signed Android RC1 does not contain these changes.
