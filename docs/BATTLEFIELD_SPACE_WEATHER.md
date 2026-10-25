# Battlefield environment and space-weather simulation

Stage I implementation, built on the deterministic core of Catastrophic Diplomacy.
**Weather is an encounter definition, not merely a visual backdrop.** It does not add collidable asteroids to the movement map and does not change weapon sprites, save format or the existing ship-fitting rules.

## Hazard matrix

| Condition | Real simulation effects | Visual indication |
|---|---|---|
| Asteroid shower | Timed, seeded off-plane objects fade in 0.8 s before a strike; some target vessels and may hit, others strike empty space. Actual hits use kinetic directional shields, armor and subsystem damage. | Growing warning ring, emerging point and impact/miss flash |
| Dense nebula | Effective sensor range ×0.47; hit chance ×0.68; guidance reduced; cooling ×0.89. Fleets search forward when no hostile is visible. | Seeded color field from existing sky shader, weather indicator |
| Solar flare | Three-second burst every eighteen seconds; extra heat and reduced cooling, sustained particle impacts that drain shields and may eventually reach armor/structure; poorer sensing and aim while active. | Sweeping amber stream lines |
| Neutron-star sweep | Nine-second rotating radiation beam every twenty-four seconds; ships in a 76-unit corridor periodically suffer directional particle hits, while ships outside are unharmed. | Rotating blue-white beam, five parallel lines |
| Radiation storm | Persistent radiation pressure every 45 simulation ticks. Particle hits erode shielding and may cause sensor/subsystem injury. | Hazard status label (additional effects artwork pending) |
| Ion storm | Sensor range ×0.67, accuracy ×0.78, power drain, temperature rise and weaker missile guidance. | Cyan electric streaks |

Every eleventh campaign level combines two different weather types, with independently seeded stage severity. All 500 existing `LevelEconomy.level(n)` seeds map to repeatable weather presets. The weather controller has **its own RNG** and advances once per fixed 30Hz battle tick.

## Damage fidelity and behavior

- `Battle.configureWeather(preset)` is only legal before the first tick. The legacy `Battle.duel` and `new Battle` default to CLEAR so previously passing combat tests are unaffected unless environment is explicitly enabled.
- Weather uses `Battle.hitAt()` to respect facing-specific shields, local armor tiles, structural damage, SIF and existing 10-subsystem casualty logic. Damage attribution is environmental, not a fictitious hostile ship.
- Meteors are never physical entities or navigation obstacles. On the scheduled strike tick the simulation decides whether the previously selected ship is still within the impact zone, then makes a separate seeded hit roll. Meteor trajectories and empty-space strikes have no movement physics.
- Missile launch guidance, point defense, fighter shots, direct-fire hit chances and initial target acquisition respond to weather, without mutating fitted baseline weapon stats.
- `Battle.fingerprint()` includes environment seed, event outcomes and active meteor state for deterministic replay checks.

## Current sandbox controls

- **B / N** or the sky preview arrows: choose one of the 500 sky recipes, without interrupting the current battle.
- **R**: restart the encounter using the currently previewed sky number's weather preset.
- **W** or tap the environment status strip: toggle hazards on/off and restart the encounter. Off means CLEAR.
- The HUD displays the ACTIVE encounter hazard, which may differ from the previewed sky number until R is pressed.

These controls are a development preview. The campaign level UI, once implemented, should bind the selected `LevelEconomy.Level.index()` to the sky AND environment during encounter construction.

## Verification and limitations

- Java 17 `SpaceWeatherTest` checks all 500 seeded definitions, reproducibility, asteroid hits/misses, storm timing, radiation and neutron strikes, and rejects invalid tick sequences.
- Java 17 `BattleWeatherTest` checks real core integration for target acquisition, heat, damage, replay and default clear behavior.
- Run with `gradle :core:spaceWeatherSmoke`. This does not validate OpenGL, Android touch handling or effects artwork.
- Desktop/Android builds, visual acceptance, accessibility/flashing controls, performance on a 200v200 engagement, and longer-term hazard tuning still require device verification.
