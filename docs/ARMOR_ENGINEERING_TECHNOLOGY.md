# Defensive technology chain: armor materials and reinforcement

Each of the 11 civilizations has **five new physically mountable DEFENSE-slot
families**. All five families have four exclusive lines of nine paid research
ranks: OUTPUT, EFFICIENCY, RESILIENCE, SPECIALIST. Old armor (ARM), structural
integrity fields (SIF), shields, saves, module IDs and upgrade histories remain.

| Family | Installed role | Tradeoff |
|---|---|---|
| CMP Composite Plating | balanced resistance to all weapon families | moderate armor and mass |
| ABL Ceramic Ablative Laminate | excels against lasers and plasma | weak against kinetic / missiles |
| DEN Dense Kinetic Plating | excels against shells, fighters and torpedoes | heavy; vulnerable to energy |
| RAC Reactive Armor Cells | excels against missiles and torpedoes | heavier power/heat and energy vulnerability |
| BLK Reinforced Bulkheads | directly raises max hull structure and internal plate HP | competes for defense space; no free gun |

The installed material's armor capacity participates in an armor-capacity
weighted average of damage-family resistance. The naked hull and legacy ARM
contribute neutral resistance; combining modules cannot multiply resistance
infinitely. DirectionalDefense applies the combined resistance on the physical
ARMOR layer only; shields and the penetrating structure layer remain independent.
The protective armor has finite hit capacity and erodes under incoming fire,
not a cooldown-resetting infinite reactive bubble.

New technology is authored as native ID families from existing faction ARM/SIF
engineering baselines; no old rows or ordinals are changed. Research costs,
power, heat, mass, component price and upgrade branch tradeoffs remain real
shipyard constraints. Separate OUTPUT upgrades give stronger physical plating
or bulkheads; RESILIENCE and SPECIALIST improve resistance, while EFFICIENCY
favors fitability. Rank upgrades require their predecessors under the ordinary
campaign mission-based research rules.

Run `gradle :core:armorEngineeringSmoke` for all 11 native factions and real
fitted dreadnought module upgrades. It checks capacity, structural HP,
per-weapon resistance, upgrade ranking and legal physical fitting.
