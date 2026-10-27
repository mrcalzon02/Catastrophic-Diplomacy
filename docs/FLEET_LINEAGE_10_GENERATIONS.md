# Ten fleet generations — alternating tactical design ethos

Source: the supplied **Flagship Squadrons: The 3-Character Design (Niche Compression)**
reference. This is a design integration record, not a claim that every described
electronic warfare or battlefield interaction is fully implemented.

## The two philosophies

**Support Fleet — Niche Protection (four functions).** Low-tonnage hulls focus
on one economical job: point-defense Flak Screen (tank), Torpedo Striker
(glass-cannon assassin), Electronic Controller (EW), or Repair Tender (healer).
The screen works as an interdependent unit. Destroying the tender can collapse
the screen because surviving flak defenders lose remote repair.

**Flagship Squadron — Niche Compression (three functions).** Larger, expensive
ships combine two functions per vessel: Apex Vanguard (tank + heavy kinetic
striker), Fleet Command Hub (target coordination + electronic disruption), and
Logistics Juggernaut (repair/shield support + defensive mitigation). A single
loss hurts more but surviving flagships retain a second capability.

The artistic size rating is **not a unique ship class**. A single curated sprite
category can show multiple distinct physical chassis, weapon/module budgets,
engine strengths, cross sections, scales and subdued class-identifying liveries.
Per-socket exclusive research and manufacturing versions add combinations
without creating duplicate ship art.

## Fleet-size progression

The ten approved sprite categories are addressed as *generations*, each with
a roster of independently selectable sub-chassis. Historic hull enum names and
blueprint formats are retained for compatibility. Both carrier and dreadnought
use the tenth curated capital category where faction atlas content is shared.

| Generation | Approved artwork family | Design ethos | Role coverage |
|---|---|---|---|
| 1 | Interceptor | Specialized support | Flak, torpedo, EW, tender |
| 2 | Corvette | Compressed flagship | Vanguard, command, logistics |
| 3 | Missile corvette | Specialized support | Flak, torpedo, EW, tender |
| 4 | Frigate | Compressed flagship | Vanguard, command, logistics |
| 5 | Destroyer | Specialized support | Flak, torpedo, EW, tender |
| 6 | Light cruiser | Compressed flagship | Vanguard, command, logistics |
| 7 | Cruiser | Specialized support | Flak, torpedo, EW, tender |
| 8 | Heavy cruiser | Compressed flagship | Vanguard, command, logistics |
| 9 | Battlecruiser | Specialized support | Flak, torpedo, EW, tender |
| 10 | Carrier / dreadnought | Compressed flagship | Vanguard, command, logistics |

The lineup currently contains **46 physical `Shipyard.Hull` chassis**, including
all eleven historical enum members. New chassis reuse existing runtime sprite
paths and silhouette geometry. The specialized *new* variants fit real PD,
torpedoes, ECM or repair machinery by swapping modules in explicit hardpoints,
not by assigning free arbitrary damage bonuses. Engine speed, armor, capacity,
support limits, signature and ammunition still come from authoritative
`Shipyard.fit` and `HullBaseline` computations.

Legacy original hulls remain unchanged to avoid silently changing replay/save
performance. Newly named prototypes represent the deliberately role-fitted
chassis. Several legacy stock prototypes are generic, not role-pure.

## Menus and progression

- **Main menu workshop:** unrestricted factions, technology branches, hardware
  patterns and chassis. Saves/apply affect sandbox/skirmish blueprint, not the
  commission of existing campaign hulls.
- **Skirmish shipyard:** all factions and all research, with the selected scenario
  and its two current fleet loadouts. The canonical 200v200 verification fixture
  remains protected from player replacement.
- **Campaign shipyard:** only the faction commissioned at campaign start;
  physically selectable chassis families unlocked through mission milestones,
  purchased global research-license ceiling, hardware pattern and mark checks.
  Pressing APPLY queues a **new purchase blueprint**, not a free refit.
- **Campaign dock:** buy a fitted hull, pay for its machinery, then independently
  pay for crew, ammunition, fuel and craft. Old owned hulls stay in active or
  available reserve. Campaign credits and research license rank persist together
  in `CD-CAMPAIGN-4`; `CD-CAMPAIGN-1/2/3` saves migrate with credits and already earned
  credits.
- **QoL:** main Shipyard, campaign Shipyard/Research, dock Shipyard/Research,
  direct ten-category touch/keyboard navigation, variant scrolling within a
  category, authored ten-chapter campaign intel on the star map.

**Current progression policy:** the first two art categories are available at
mission 1. Every further 50 cleared stage unlocks the next category, up to
generation 10. All six paid research license tiers cap fitted module upgrades.
This is *tier licensing*, not yet a per-component laboratory purchase ledger.
Skirmish never uses these campaign locks.

## Verification obligations

`ShipLineagesTest` asserts each of ten family generations, role coverage, all
faction/chassis fitting validity, real module effects, blueprint serialization,
legacy progression migration, spend-before-license and anti-bypass rules.
The existing `shipyardSmoke`, `gameFlowSmoke`, and `menuFlowSmoke` are the
headless gates. GPU/Android emulator functional acceptance remains a separate
release gate and cannot be replaced with deterministic unit tests.

### Remaining implementation milestones

1. Per-component research ownership/purchase ledger (the global rank ceiling
   is a minimum necessary restriction, not full detailed technology ownership).
2. Fitted-ship paid refit transaction preserving each commissioned ship's
   physical damage, crew, stored ammo and fuel; current purchase-only behavior
   deliberately avoids free restoration.
3. Improved art-visible class paint variation and individual loadout overlays
   without weakening actual artwork authority or corrupting damaged hull tiles.
4. Expanded support mechanics: electronic comm-jam / drive-disable and ally PD
   network commands must be verified as *real simulated effects* before listed
   as operational; don't infer them from an ECM value on a fitting sheet.
5. Mission-specific tactical goals, branching diplomatic decisions, sector
   bosses, unique rosters, and campaign economy balance. The ten campaign chapter
   briefs are real UI text; choices/goal triggers are NOT implemented.
6. Crash-safe composite fleet + wallet transaction, emulator combat runtime proof,
   physical-device validation and signed Android release qualification.
