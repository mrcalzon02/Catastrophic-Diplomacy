# Fleet orders and point-defense mode system

**Implementation status: foundation active, full GSB-scale orders feature set incomplete.**
Functional inspiration: public documentation for Gratuitous Space Battles' pre-battle deployment and ship orders, https://www.gratuitousspacebattles.com/faq.html, and Positech community descriptions. Our code, UI, assets, rules and serialization are original. Do not copy another game's proprietary code or artwork.

## Current playable slice (2026-10-25)

- Battle ships carry individually editable `BattleOrders.Orders`. Fleet template changes apply to **both existing and subsequently spawned ships**, and `setShipOrders(shipId,orders)` can override an individual ship with an optional allied escort or formation anchor. Unchanged fleets follow the established skirmish behavior; PD-only toggle never changes ship movement or attack prioritization.
- **Point defense** can be switched to `BALANCED`, `INTERCEPT_ONLY`, `ANTI_CRAFT`, or `HOLD_FIRE`. Point defense checks are independent of main battery targeting. Incoming missiles, torpedoes and **real tracked kinetic shells** share defensive attention; fighters/drones form the other category. Successful interception removes the physical projectile instead of merely hiding its sprite.
- PD firing now produces a `POINT_DEFENSE` firing notification and a short real beam payload. Projectile destruction produces an `INTERCEPT` notification at the interception location; ordinary missed shots do not trigger a fake impact.
- Independently configurable **three class targeting priorities (0–100%) and engagement ranges (30–1500 world units)**: Fighter (interceptors), Escort (corvettes/frigates/destroyers), Capital (cruisers/carriers/battlecruisers/dreadnoughts). Zero removes that class from an *offensive* target pool; does not disable defensive PD.
- Main-weapon targeting doctrines: **nearest, cooperate** (teammate focus fire), **vulture** (wounded hull bias), **retaliate** (favor previous attacker), **protect** (favor attackers of nominated friendly ship). Cooperative tally is incrementally indexed once each simulation tick to avoid searching every ally for every possible target in 200-versus-200 battles.
- Movement doctrines: **free attack, hold position, keep moving, cautious withdrawal, last stand, escort, formation**. Escort/formation require a valid allied anchor ship ID in individual ship orders. At fleet-template level those two anchor-specific modes are deliberately not available without an anchor, avoiding a switch that appears to work but does nothing.
- A two-column **Fleet Tactical Orders** panel: adjust left/right fleet PD mode, target doctrine, movement doctrine, class priorities and ranges, and retreat threshold. Open from Tactical Command > FLEET ORDERS EDITOR or keyboard **F**. Keyboard **P/O** cycle the two fleet PD modes. Changes immediately affect the battle and are retained for the next restart.
- Team tactical orders are saved separately from existing blueprint/fleet fitting formats at `fleet/orders-v1.dat` with checksum, temporary-file write, and last-good backup fallback. Missing/invalid orders fall back to `BattleOrders.DEFAULT`; ship fittings remain untouched.
- Core smoke coverage: `:core:battleOrdersSmoke` is chained after `:core:weaponFxSmoke` and checks all four PD modes, live missiles and craft, zero-class priority, invalid parent assignments, deterministic replay, and versioned checksum-protected round-trip serialization.

## Known boundaries: the full order system is NOT implemented

| Requested GSB-style capability | This pass | Remaining implementation |
| --- | --- | --- |
| Intercept/anti-fighter/mixed/cease-fire PD selector | Integrated team + ship engine, visible fleet UI | Explicit point-defense module-by-module policy and offensive anti-hull mode if desired |
| Three size-class priorities and engagement ranges | Engine and fleet editor live | Individual **weapon** target controls and fire arcs; corresponding shipyard hardpoint origins |
| Vulture, cooperate, retaliation, protect | Engine doctrine scoring | Detailed weapon-specific target choice; target status filters (shields/armor/hull) |
| Escort and exact relative formation | Per-ship API + navigation behavior | Pre-battle fleet visual placement, allied leader selection, drag-to-assign formation groups |
| Cautious retreat / fight to last hull | Hull threshold and tactical modes | Actual withdrawal/escape boundary, victory/mission implications and post-battle survival scoring |
| Saved tactical settings | Fleet template file | Full per-ship deployment data, selected modules, individually saved order stacks and group presets |
| Multi-ship order editing | Fleet-wide editing | Select multiple deployed ships and apply/merge orders without overwriting unrelated commands |
| Tactical priorities and movement | Functional initial behaviors | Advanced contradictory-order resolution, escort reassignment after leader loss, protect/rescuer triggers, collision/avoidance and tactical UI feedback |
| Fire control instrumentation | Visual FIRE/INTERCEPT events | Full shot audit per hardpoint and after-action visual review, including PD shots against each weapon class |

The game's current combat loop has **aggregate ship weapon stats**, not separate target-selection AI for every actual installed hardpoint. That is a major architectural gap before any claim of full GSB-equivalent behavior. Similarly, a simple two-column orders editor is not the same as GSB's pre-battle deployment canvas.

## Technical details

All orders are defined in `core/src/main/java/diplomacy/sim/BattleOrders.java` as validated immutable records. `BattleOrdersCodec` supplies the versioned text save. `Battle` reads orders only while resolving target selection, movement and PD, never from the rendering layer. `FleetOrdersPanel` is the cross-platform libGDX editor consumed by `CatastrophicGame`. The existing `WeaponFxProfile` / `ModularWeaponFxRenderer` receive simulation events and do not decide interception outcomes.

**Regression checklist** for this slice:
1. `gradle :core:battleOrdersSmoke` passes, and existing `smoke`, `combatMachinerySmoke`, `weaponFxSmoke` and `massBattleSmoke` remain functional.
2. Cycle PD while missiles/shells and fighter craft approach. INTERCEPT_ONLY must ignore craft. ANTI_CRAFT must ignore ordnance. HOLD_FIRE must spend no PD energy or trigger PD visual shots.
3. Adjust fighter/escort/capital priority and range on each side; 0% target types must not be selected offensively by that ship.
4. At 200v200, cooperative targeting must not turn the simulation into an uncontrolled quadratic/cubic ally scan.
5. Save / restart / reload / corrupt the orders file: previous valid file remains usable and legacy ship fittings remain unaffected.
6. Visual acceptance on desktop and Android still requires actual rendering and device checks; **not verified by this document or the presence of source code**.

Next integration priorities: **individual ship selection on a prebattle fleet deployment canvas**, parent/escort formation assignment, per-module targeting doctrine, and stackable ordered rules with visible priority resolution. Avoid shipping the words “complete GSB orders” until these are demonstrably working.


## Individual persistent standing orders (2026-10-26 integration)

**Distinction:** The primary *Ship Orders* deployment screen is for persistent **standing combat doctrines**,
not a linear waypoint/task sequence. Select any commissioned ship on the tactical canvas (or use
Previous/Next) and change independent rules. All configured rules remain in effect simultaneously
through an engagement and subsequent campaign reinforcement waves.

13 editable settings per individual ship:

- Aggression (0–100): higher values close to shorter target engagement distances.
- Caution (0–100): higher values preserve longer separation, competing with aggression.
- Hull withdrawal minimum (0–100% remaining HP) and separate shield fall-back minimum (0–100%).
- Movement policy: free attack, hold, keep moving, cautious, last stand.
- Target-selection policy: nearest, cooperate, vulture, retaliate, protect.
- Point-defense policy: balanced, intercept-only, anti-craft, or hold fire.
- Independent fighter, escort and capital target priorities (0–100; zero suppresses offensive selection).
- Independent preferred range for fighter, escort and capital targets (30–1500 world units).

Both hull and shield triggers currently command a vessel to *fall back to its own side*, suppressing
its offensive battery while the threshold condition holds; defensive weapons stay independent.
**Withdrawal/escape accounting at a map boundary, repair/return rules, persistent casualty
statistics and tactical victory resolution for retreats are not finished.** Do not claim that
ships physically escape the engagement or automatically survive because they retreat.

Settings are stored for each faction and numbered commissioned fleet slot in
`fleet/standing-FACTION-v1.dat`, with checksum, temporary writes and last-valid backup
recovery. This preserves legacy `fleet/orders-v1.dat` team templates and ship blueprints.
Saved policies automatically reload when preparing the same faction's next campaign or
skirmish battle. Fleet-wide policy changes serve as defaults and must **not** overwrite
explicit captain-specific standing orders, including during battle resets.

The earlier sequential MOVE/HOLD/ENGAGE `ShipCommandQueue` machinery is separate.
It is no longer exposed as the primary standing-orders UI. It does not substitute
for orders that should remain effective all battle long.

**Functional acceptance:** `:core:shipStandingOrdersSmoke` tests all 13 fields,
checksum round trips of 200 independently indexed ship profiles, tamper rejection,
actual commissioned ship doctrine, wave retention and precedence over fleet-wide orders.
`:game:menuFlowSmoke` checks all standing editor hit targets; the signed Android
navigation script edits multiple simultaneous per-ship standing policies and checks the
live client's resulting state. Hardware visual/behavioral verification is a separate gate.

Still to build: group selection and apply-to-group controls, separate *physical module
hardpoint* fire-control doctrines, direct escort-parent selection, full retreat/escape
accounting, deeper behavior for aggression and caution beyond engagement separation,
and device-verified tuning against the 200v200 battle.
\n## Expanded independent orders (implementation slice)\n\nPer-ship directives now include engine speed limiting (25-100% of fitted drive), physical point-defense interception strategy (early / close-in / fastest impact), point-defense scope (self / selected protected targets / all allies), fighter sortie behavior (strike / screen / protect / reserve), four separate protected friendly targets, and one assigned escort target with adjustable distance. These are simultaneous orders that persist in a versioned **CD-STANDING-2** format. Legacy **CD-STANDING-1** files remain readable. References are zero-based commissioned fleet slots and resolve to real friendly ship IDs at deployment, not opaque saved runtime IDs.\n\nThe orders editor has two compact pages: combat policies and support/relationships. Long-range intercept is limited by real point-defense module detection and reach. *Screen* launches fighter sorties for nearby threats and *Protect* for ships actually threatening designated friendlies; craft still use their own fitted weapon and sensor profiles. The individual policies are distinct from the older optional waypoint program.\n\nThis does not yet model weapon-by-weapon PD fire control, squadron wing redistribution among multiple carriers, or completed retreat/escape accounting. Do not claim full GSB order parity.\n