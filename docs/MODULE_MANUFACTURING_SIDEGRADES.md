# Manufactured module marks and mutually exclusive research

This feature extends the hull-first engineering work in PR #12. It is **not** permission to add magical weaponry or crew to a bare hull. A module must be mounted in a compatible physical socket.

## Two independent technology dimensions

Each installed module has **one equipment pattern** and **one manufactured Mk I–IV generation**. This is distinct from the existing **single exclusive research path** (OUTPUT, EFFICIENCY, RESILIENCE or SPECIALIST) at rank 0–6. An installed version does not unlock incompatible research branches, and changing research does not erase its physical equipment version.

- **Standard**: stable chassis; Mk I exactly matches every existing blueprint; later marks modestly improve precision/integrity and cost.
- **Compact**: lower displacement and volume, less physical durability, greater power/heat cost; useful for tight escort hulls.
- **Reinforced**: much stronger machinery and intercept resistance, at greater mass and industry cost.
- **Overcharged**: higher actual output, power draw, heat production and maintenance.
- **Low Emission**: lower thermal signature/heat with improved precision, somewhat higher power needs and reduced mechanical durability.

Every pattern changes the fitted engineering report. Applicable gun and projectile stats, PD interception, ship speed/cooling/armor and other existing module effects consume the modified base part properties. The game never grants abilities based only on art IDs.

### Counts: real selections, not fictional artwork

The current technology platform contains **11 factions × 20 baseline types = 220 module chassis**. Each has **5 patterns × 4 marks = 4,400 valid manufacturing configurations**, in addition to its four mutually exclusive six-rank research paths (5,280 research nodes). These are valid programmatic module configurations, **not** 4,400 approved or rendered sprites, nor independent hardpoint simulations yet.

The player and mission fleets use exactly the same `TechnologyCatalog` and `Shipyard.fit` machinery. No NPC-only stat overrides or bypasses of research-stage validation exist.

### Shipyard controls

Select an installed socket, tap the pattern/mark line at the lower right of the shipyard or use **V** to cycle hardware patterns and **B** to cycle Mk I–IV. Research path controls remain independent. Fitting mass, thermal and cost budgets update immediately. A new pattern that exceeds reactor, radiator or displacement limits cannot deploy.

### Save format contract

- `CD-SHIP-1` continues to be emitted for every fit using **Standard Mk I** hardware, preserving old files byte-for-byte.
- `CD-SHIP-2` is used only if any socket has different hardware. Each occupied slot record adds `|PATTERN|MARK`, with strict enum and 1–4 validation.
- Both formats round-trip through fleet presets and existing checksum policies. Never silently downgrade incompatible files or silently discard a chosen research branch when changing hardware.
- Per-socket installed module IDs remain tied to one of the eleven factions.

## Still pending

Actual **crew capacity modules**, crew count/morale and life support; **individual weapon hardpoint simulation** with independent targeting and reload; many additional sidegrade *families*, per-faction visual language for all manufactured module marks, actual approved icon/sprite integration, strategic campaign research ownership and unlocked inventories, and unique hull-class marks/art variants across the 500-level campaign. Those should be separate tested increments, not declared complete by this mark system.

**Acceptance:** `:core:moduleVersionsSmoke` checks all 4,400 legal combinations, real performance/cost differences, research independence and legacy save compatibility. `:core:massBattleCampaign` depends on it; desktop/Android rendering also needs actual device verification before shipping.
