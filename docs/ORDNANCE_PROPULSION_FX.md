# Physical missile propulsion and matching combat FX

This pass makes four flight schedules observable and mechanically distinct, without making cosmetic FX decide physics:

- **CONSTANT_BURN**: standard single, ripple, saturation and decoy salvos continue burning at their current flight speeds. This preserves legacy default cruise behavior.
- **COLD_START**: the COLD_LAUNCH salvo ejects slowly and unpowered for its configured ignition delay, then lights its cruise motor. Cold canisters remain physical, interceptable projectile objects.
- **BOOST_COAST_TERMINAL**: HEAVY_STRIKE rounds burn strongly for an initial boost, coast without engine flame or smoke, and re-ignite for a faster and more responsive terminal approach when near the target. Their existing total flight-life timer still applies.
- **PULSE_BURN**: MICRO_SWARM salvos alternate short thrust pulses and unpowered glides.

The simulator updates each projectile's `thrustPhase` every 30 Hz tick. Phase influences actual speed and guidance, so visual differences represent true movement. Projectile collision remains swept, and targets can still intercept cold or coasting weapons. Module launch origin, ammunition consumption and damage remain unchanged. The phase and flight tick are included in deterministic battle hashing.

The FX renderer samples **every simulation tick**, not every display frame, so 4x/8x combat cannot skip between key motor transitions. Cold and coasting projectiles have no ignition light, engine glow, or freshly emitted smoke. Each motor restart has a bright, brief ignition flash, including redder terminal burns. Missile exhaust uses a shared irregular soft mask; torpedoes have heavier smoke and decoys have a subdued blue-grey signature. Smoke remains briefly after projectile removal, and point-defense INTERCEPT bursts are smaller and greener than successful hull impacts. Particle budgets remain capped, all effects expire, and no per-projectile GPU textures or FBOs are created.

Verification: `:core:propulsionSmoke` for the flight schedules and physical multipliers; `:game:combatPropulsionFxSmoke` for the fixed-step launch/coast/restart effects, wired into existing `:game:lightingSmoke`. Actual Android motion and frame rate require on-device review after CI passes.
