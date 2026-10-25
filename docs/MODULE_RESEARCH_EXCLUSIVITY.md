# Per-module mutually exclusive research and technology trees

**Implemented in the authoritatively shared Java shipyard fitting model.** All six factions retain the preexisting technology catalog: OUTPUT, EFFICIENCY, RESILIENCE, SPECIALIST; six ranks per branch, for every component family. There are 120 base components and 2,880 catalog upgrades. The catalog exposes four *parallel choices*, **not four stackable bonuses on one component**.

## Rule: one branch PER installed module socket

- Every fitted module begins as an uncommitted **base component**.
- Selecting any positive stage (1–6) commits **that particular installed module** to OUTPUT, EFFICIENCY, RESILIENCE, or SPECIALIST.
- Higher stages on the same branch remain available (the original same-branch technology prerequisites and cumulative research/industry costs remain active).
- The other three branches become **LOCKED for that socket**. They cannot be combined, and the engineer/UI must not silently replace the active branch.
- To choose a different family, explicitly reduce the current branch to **stage 0**. This **respec abandons** all levels of the former branch on that socket; the module is then uncommitted again and can select another specialization. An actual future research-credit refund would need its own explicit design.
- Other sockets, including two installed KIN/MSL/TOR/PD components of **the same base model**, may commit to **different** tracks independently. One ship can mix a reinforced torpedo launcher, a specialist torpedo launcher, and an output-focused torpedo launcher. The current aggregate-per-family combat system averages per-socket projectile attributes when there are multiple installed launchers; individual shot-by-hardpoint dispatch remains a future integration task.
- Replacing or clearing a module intentionally removes that socket's branch investments; no other modules are changed.

## Mechanics that now reflect research specialization

The existing `TechnologyCatalog` provides quantitative multipliers and costs. `Shipyard.evaluate()` accepts **at most one research branch** and applies the highest selected rank's multipliers; it still charges the cumulative rank 1–N research and industry costs from that branch. Output, accuracy, resilience, power draw, heat, maintenance, and other modifiers cannot stack from incompatible branches on the same socket.

`ProjectileModel` consumes these fitted multipliers. Examples:
- **OUTPUT**: heavier projectile damage/throughput; increased speed/output and signature where applicable, at increased power/thermal load
- **RESILIENCE**: greater projectile health and somewhat larger cross-section; similarly hardened defensive modules
- **SPECIALIST**: improved projectile velocity/signature or PD tracking/sensor capability
- **EFFICIENCY**: lower module power/heat/mass costs rather than receiving every other branch's bonus at once

The shipyard now marks the chosen branch **[ACTIVE]** and the other three **[LOCKED]**. Tapping a locked research control displays how to reset the committed track; it never mutates the ship design on a rejected selection. The engine independently enforces this invariant even when the UI is bypassed.

## Save compatibility and legacy conflict policy

The blueprint identifier **`CD-SHIP-1` and its existing four integer stage fields are retained**, so ordinary unupgraded/single-branch saved blueprints can still load. On write, only one track can carry a positive integer. On read, an old file where two or more tracks are simultaneously positive on *one slot* is **explicitly refused** with an error naming the slot and competing tracks.

The decoder never silently drops an old branch, chooses a "winner", edits the file or zeroes levels. Existing desktop/Android local files remain untouched when loading fails. Users with older mixed-branch blueprints should make a copy, choose which single branch that particular socket should keep, and explicitly respec that module in the source save; a guided migration/branch-selection UI has **not** yet been implemented.

This is a **per-fitted-module specialization constraint**, not proof of a complete strategic research economy. Research amounts and prerequisites are recorded by the existing catalog and fitting reports; a campaign-level research-point wallet, actual unlock purchases and permanent global tech choices are still to be built. In particular, choosing OUTPUT for *one* missile rack does **not** globally forbid RESILIENCE on every other missile rack.

## Regression contract

`gradle :core:moduleResearchSmoke` checks that:

1. Choosing OUTPUT level 2 locks EFFICIENCY, RESILIENCE and SPECIALIST for that individual socket; rejected calls do not mutate the immutable design.
2. Two otherwise identical kinetic modules can independently choose OUTPUT and RESILIENCE, while another module chooses SPECIALIST. Their physical projectile properties differ and their research costs accumulate by socket.
3. Setting the active branch to rank 0 unlocks a new family and removes the old branch's influence on the projectile spec.
4. A normal `CD-SHIP-1` one-branch save round-trips without changing stage values. A legacy mixed-track save is rejected, and the original blueprint is not altered.
5. All six factions, the KIN/MSL/TOR/PD/SEN sample families, and each of the four full six-stage technology chains remain available for **individual** modules.

Run `:core:technologySmoke`, `:core:shipyardSmoke`, `:core:projectileModuleSmoke`, `:core:moduleResearchSmoke`, `:core:massBattleSmoke`, and appropriate desktop/Android launch acceptance before calling this a tested release. These tasks are wired in the repository; actual Java compilation/test execution and APK verification are separate, required checks. No new GitHub Actions workflow was added.

Source files: `TechnologyCatalog.java` (research data and same-branch prerequisite chains), `Shipyard.java` (exclusive per-socket tree and blueprint validation), `ShipyardPanel.java` (locked-track UI), `ProjectileModel.java` (derived physical ammo behavior), `ModuleResearchExclusivityTest.java` (invariants).
