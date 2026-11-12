# Combat camera and weapon-effect footprint

Android battles: tap to follow on release, drag one finger to pan, pinch with two fingers to zoom and translate about their centroid; AUTO restores automatic camera, pan and focus. Gesture input is isolated from menus, orders, shipyard and deployment.

`CombatEffectScale.LINEAR = 1/3` reduces authored firing, payload, impact and intercept sprite dimensions; beam/support strip widths, ballistic tracers, smoke, optical coronas, missile lighting and flash dimensions, plus geometric ordnance fallbacks. Real muzzle-to-target beam lengths, projectile position, simulation damage, physics and hit locations do not change. Engine lights and ship sprites are intentionally not part of weapon effects.

`gradle :game:battleTouchSmoke` runs the JVM-only gesture regression. Review an Android screenshot and real two-finger navigation before claiming device-level acceptance.

## Projectile-to-muzzle consistency — November 2026

The moving missile, torpedo, rocket and ballistic sprite pass applies the
additional `PROJECTILE_FOOTPRINT` render-only reduction (0.45) after the
common one-third effect scale. Source-art rocket/guided launch *FIRE* flashes
now use 0.55 of their previous displayed width/height; `IMPACT` and
`INTERCEPT` clips remain unchanged. This avoids large spawn bursts being
mistaken for larger physical projectiles. The one-shot VFX culling broadphase
uses actual post-scale effect radius instead of unscaled atlas metadata.
No sensor, collision, warhead, fuel, physics or damage behavior is modified.
`WeaponStageArtTest` covers the visual scale contract in the existing **manual**
art diagnostic, independent of the required build/run release gate.
Actual Android/desktop screenshots remain necessary before aesthetic signoff.
