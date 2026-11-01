# Fleet tenders: actual ammunition and propellant logistics

All 11 civilizations now receive four new SUPPORT-slot module families:
- **KTS** kinetic ammunition tender stores and transfers cannon shells
- **MTS** missile tender stores and transfers guided missile rounds
- **TTS** torpedo tender stores and transfers heavy warheads
- **FTS** propellant fleet tender stores and transfers reaction mass

All four have the same four exclusive nine-rank technology tracks as existing
support hardware, with faction-specific research costs, mass, power and heat.
Output increases carried inventory and delivery rate, efficiency reduces the
engineering footprint, resilience improves durability and specialist emphasizes
delivery capability. Each module has real mass/heat/power/industry costs and
must be fitted to a physical support socket.

Donor inventory comes from stockpiles loaded when the module is commissioned,
not infinite generators. Stores are in existing ship kinetic/missile/torpedo/fuel
fields, so after-action persistence and paid dock refill continue to apply.
Recipient capacity is fixed by its own installed magazines, not the tender.
Ammo types and guided warheads must match exactly; no free premium ammo.
Dedicated missile/torpedo rack magazines are incremented as rounds arrive.
Fuel donors retain a minimum propulsion reserve; transfers require range,
reactor/power, functioning crew and real inventory. There is no enemy support,
infinite aura, or background stock regeneration. At most two search pulses
per second and fractional ammo transfer is accumulated until one whole round
can be moved. Fuel transfers allow fractional units.

`gradle :core:fleetTenderSmoke` runs physical stock-transfer and research
checks; the optional research and support diagnostics do not cosmetically
block releases. Balancing and fleet endurance results are reported separately.
