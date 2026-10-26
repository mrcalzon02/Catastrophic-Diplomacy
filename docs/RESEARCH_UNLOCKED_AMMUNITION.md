# Ammunition unlocked by individual weapon research branches

**Authority:** `AmmunitionResearch.java` maps each fitted KIN / MSL / TOR module's one mutually exclusive branch and its rank 0–6 to actual permitted `AmmunitionTable` payloads. `Shipyard.ammunitionAt` previews one socket; `Shipyard.ammunitionAccess` supplies live combat fitting restrictions. `Battle.Ship.setKineticType` / `setWarhead` reject a locked load from a **real fitted** hull before it can fire. Legacy low-level `Battle.addShip(faction,...)` simulation fixtures without module designs remain exempt for headless ammunition physics tests, not for real player/AI fleets.

## Base rule

Every weapon begins with **STANDARD** physical rounds, free of research. Special ammo only becomes available as the *installed weapon module itself* gains the listed rank within **one** branch. The other three branches remain mutually exclusive in that socket. Mk I-IV hardware patterns are orthogonal to ammunition entitlement; buying Overcharged Mk IV does **not** secretly grant Specialist rounds.

| Installed module | Research branch | First purpose-built ammunition (rank 2) | Later purpose-built ammunition (rank 5 or 6) |
| --- | --- | --- | --- |
| KIN | OUTPUT | HIGH_EXPLOSIVE | RELATIVISTIC (rank 5) |
| KIN | EFFICIENCY | FRAGMENTATION | ARMOR_PIERCING (rank 5) |
| KIN | RESILIENCE | ARMOR_PIERCING | HIGH_EXPLOSIVE (rank 5) |
| KIN | SPECIALIST | ARMOR_PIERCING | RELATIVISTIC (rank 6) |
| MSL | OUTPUT | HIGH_EXPLOSIVE | SHIELD_BREAKER (rank 5) |
| MSL | EFFICIENCY | FRAGMENTATION | EMP (rank 5) |
| MSL | RESILIENCE | ARMOR_PIERCING | HIGH_EXPLOSIVE (rank 5) |
| MSL | SPECIALIST | EMP | SHIELD_BREAKER (rank 6) |
| TOR | OUTPUT | SHIELD_BREAKER | HIGH_EXPLOSIVE (rank 5) |
| TOR | EFFICIENCY | FRAGMENTATION | EMP (rank 5) |
| TOR | RESILIENCE | ARMOR_PIERCING | SHIELD_BREAKER (rank 5) |
| TOR | SPECIALIST | EMP | ARMOR_PIERCING (rank 6) |

Ranks 1, 3 and 4 still improve real weapon engineering through existing module tech multipliers; these are **not** dead research levels. The purpose-built rounds have real physical cost and effects in `AmmunitionTable`: per-round power/heat/reload penalties, speed, health, radar cross-section and signature, armor bypass, splash, subsystem EMP, shield drain. Ballistic bodies are individually interceptable; each guided payload is a real missile/torpedo, not a cosmetic FX swap.

## Per-socket versus aggregated-family controller

The present combat core has **one KIN/MSL/TOR controller per ship and weapon family**, even if two or more separate installed launchers of that family exist. For safety, `Shipyard.ammunitionAccess` computes the **intersection** of unlocks across all installed modules of each relevant family: a newly upgraded gun cannot lend advanced munitions to an unupgraded neighbor. On mixed unlocks, only ammunition independently unlocked by **all** the family's launchers can be selected. This is a deliberate temporary limitation, *not* per-hardpoint ammo control. Later independent firing controllers must select and consume rounds **by socket**, at which point different fitted guns can fire different researched ammo simultaneously.

Until then, designers can acquire a special warhead by upgrading all participating launchers of the family to compatible ranks or installing a single launcher. A laser, plasma or particle beam must not load solid-projectile ammunition.

## Physical salvo consistency

Changing a selected missile/torpedo warhead during a multi-tick RIPPLE, SATURATION, MICRO_SWARM, HEAVY_STRIKE, COLD_LAUNCH or DECOY_CLOUD salvo affects *the next* loaded salvo, not the already queued one. `PendingLaunch.loadedWarhead` snapshots the intended payload at launch scheduling and is included in the deterministic battle fingerprint. Decoy hardware still consumes real finite ordnance count and has zero terminal damage. The existing launch pattern controls are separate from payload entitlement: this initial increment **does not** claim that all seven guided firing modes are individually researched or that ammo is purchased from an actual campaign wallet.

## Shipyard ammunition selection and saved fleet preferences

Select a fitted KIN, MSL or TOR module in the shipyard and tap the **A / TAP AMMO** row or press **A** to cycle the currently researched rounds. The list is based on the **shared-family intersection** of all installed launchers of that type. The current selection appears next to the available rounds; switching to a locked payload is rejected, and the user must explicitly select STANDARD before downgrading or replacing a supporting researched launcher.

The selected physical rounds are part of the immutable `Shipyard.Design.ammoSelection`. On deployment, `Battle.addShip(design,...)` equips the **player and AI** with exactly those permitted missile/torpedo/kinetic types. The same combat APIs enforce any subsequent changes. When a nonstandard load is saved, `Shipyard.encode` writes **`CD-SHIP-3`**, preserving the fighter loadout, six-stage exclusive research, Mk I–IV manufacturing variants, and all three selected ammunition families. `Shipyard.decode` verifies the payload against installed modules, rejects forged/off-tree saved loads, and does not silently downgrade them. Old default `CD-SHIP-1` and `CD-SHIP-2` saves continue to serialize **unchanged**, with STANDARD ammunition.

**Remaining limitation:** These are per-ship *family bank* selections, not truly independent per-hardpoint magazines. Until individual socket fire-control is implemented, two KIN modules share one selected kinetic round, and two MSL modules share one selected missile warhead. Designing distinct loads on simultaneous independent guns still requires per-hardpoint controllers and a later save schema; the current system refuses to lend an advanced research unlock to a stock gun. Campaign research purchase/unlock wallets are also separate work, so the choice is research-rank-bound but not yet campaign-inventory-gated.

## Regression

`gradle :core:ammunitionResearchSmoke` proves rank thresholds for 4 branches × 3 ammunition families, independent research along all 11 races' module IDs, mixed-module bank restrictions, rejection of unauthorized loads, live kinetic projectiles, real multi-shot guided volleys, deterministic state and snapshot integrity. This task gates `:core:massBattleCampaign` and is run with `:core:ammunitionTypesSmoke`, `:core:ordnanceDoctrineSmoke`, desktop classes in `research-ammunition.yml`. Android-device UI and independently mounted magazine verification are still required before release.


### Extended saved-ammunition regression

`:core:ammunitionSelectionSmoke` validates CD-SHIP-1/2/3 roundtrips, unchanged legacy format, actual loaded shells on both combat teams, live EMP missile bodies, researched load persistence on all eleven races, resistance to malicious/invalid blueprint ammo, anti-downgrade rules, mixed launcher bank restrictions and simultaneous Mk/branch/ammunition preservation.
