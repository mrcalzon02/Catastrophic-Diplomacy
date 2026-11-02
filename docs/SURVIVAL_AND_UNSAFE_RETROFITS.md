# Damage control, armored survival and wildly unsafe retrofits

This is the next engineering family after the nine sensing/deception families and
their 36 alternative equipment lines. All designs occupy real hull sockets and
retain unique faction-native module IDs and four exclusive nine-rank research
paths, with existing save formats and original components untouched.

| ID | Hardware | Mount | Tier | Intended battlefield role |
| --- | --- | --- | --- | --- |
| DMC | Distributed Damage-Control Station | Support | I | Powered repair and containment assistance |
| HCR | Hardened Crew Compartment | Habitat | II | Physical berths with reduced penetrating-hit casualties |
| CIC | Armored Command Citadel | System | III | Resist command subsystem damage and bridge casualties |
| FBN | Armored Fuel Bunker | Support | I | Extra protected propellant capacity |
| AMV | Isolated Ammunition Vault | Support | II | Paid ordnance capacity and magazine containment |
| EPD | Dedicated Escape-Pod Battery | Support | I | Evacuate a finite number of surviving personnel on destruction |
| RCB | Emergency Recovery Beacon | Support | I | Mark a real wreck; only a won postbattle recovery grants limited hull-only salvage credits |
| PGO | Power Governor Override | System | III | Extra recharge with heat and stability penalties |
| JPB | Jailbroken Plasma Confinement Battery | System | V | Extreme recharge with catastrophic failure potential |
| IBR | Unauthorized Battery Brick | System | 0 | Cheap emergency power and terrible reliability |
| IST | Improvised Thermal Shunt | System | 0 | Cheap additional heat rejection and dangerous failure modes |
| IRG | Unlicensed Reactor Tap | System | II | Heavy surplus power with instability and crew exposure |

The unsafe gear is game-fiction only: no physical battery wiring procedures are
provided. Its effects must be visibly labeled **UNSAFE RETROFIT** and implemented
with seeded deterministic reliability checks, not arbitrary per-frame RNG.

Engineering contract: normal modules must not randomly fail. Improvised gear
adds explicit instability; elevated heat, damaged cooling, reactor injury and
battle stress make faults more likely. Faults may trip the reactor, disable
communications, injure crew, ignite stores or breach internal compartments.
Benefits have real reactor power, heat, mass and research cost. Escape pods save
actual personnel counts, never regenerate a destroyed ship or grant free crew.
Recovery beacons identify a wreck but do not instantly award salvage. On victory,
the postbattle settlement may recover only a modest amount of scrap from a
destroyed, beacon-bearing friendly hull; the destroyed ship, crew and purchased
modules are never automatically restored.

Acceptance: verify eleven native families per subsystem, nine levels x four
parallel tracks, functional fittings, survival outcomes, unsafe-only fault
probabilities, deterministic replays, and persistence through existing designs.
