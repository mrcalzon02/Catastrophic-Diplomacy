# Level System I — hundreds of replayable wave battles

**Design goal (planned, not implemented):** a 500-level initial campaign comprising ten sectors of fifty stages, each with increasingly demanding enemy waves, distinct doctrines, sustained ammunition attrition, sector bosses and optional replay grinding. Preserve Sandbox and the deterministic simulation as independent modes. Do not conflate this with a strategy layer.

Every level supplies its own fixed maximum deployment count C, base credits B and a wave roster. D is the number of commissioned vessels deployed (1..C); S is the number of those vessels that survive the **entire level** (0..D). Fighters, defensive drones and expendables do not increase D or S.

**Victory reward:**

`credits = floor(B * (S / D) * (1.0 + 0.10 * (C - D)))`

One unused slot yields 1.1×, two 1.2× and so forth. The multiplier is an **additive 0.1 step per missing ship**, not exponential compounding. Enemy defeat/all mandatory waves must be confirmed before any award; defeat, abandonment or incomplete levels pay zero. Replay completed levels indefinitely for the same formula without a once-only earnings nerf. Future economy tuning may introduce bounds only with explicit review; preserve this initial contract.

Illustrative 20-capacity, 10,000-credit level: 20 deployed/20 survive = 10,000; 16/16 = 14,000; 10/10 = 20,000; 1/1 = 29,000; 16/12 = 10,500. Do not apply repairs or rearming between individual waves unless explicitly introduced as a scenario modifier. Vessel condition carries across waves within an attempt; each new replay starts from the chosen saved fleet design.

Research must be aggressively expensive to motivate refinement and grinding. Draft tier prices in credits: I 25,000; II 75,000; III 250,000; IV 900,000; V 3,500,000; VI 15,000,000. These are **not balanced or live prices**. Tie purchases to the existing 120 components / 2,880 technology upgrade catalog after verifying progression and save migration. Avoid treating ownership of a technology CSV entry as an already purchased upgrade.

Implementation backlog: seeded level definitions and waves; fleet-size validation and underdeployment tracking; strict survivor registry; pure reward function and economy tests; bank persistence and schema upgrades; technology purchase ledger; replay UI; telemetry and balancing; regression of difficulty and performance under the new directional-protection model.
