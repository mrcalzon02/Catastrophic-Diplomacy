# Combat camera and weapon-effect footprint

Android battles: tap to follow on release, drag one finger to pan, pinch with two fingers to zoom and translate about their centroid; AUTO restores automatic camera, pan and focus. Gesture input is isolated from menus, orders, shipyard and deployment.

`CombatEffectScale.LINEAR = 1/3` reduces authored firing, payload, impact and intercept sprite dimensions; beam/support strip widths, ballistic tracers, smoke, optical coronas, missile lighting and flash dimensions, plus geometric ordnance fallbacks. Real muzzle-to-target beam lengths, projectile position, simulation damage, physics and hit locations do not change. Engine lights and ship sprites are intentionally not part of weapon effects.

`gradle :game:battleTouchSmoke` runs the JVM-only gesture regression. Review an Android screenshot and real two-finger navigation before claiming device-level acceptance.
