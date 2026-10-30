# Combat readability and offscreen work reduction

The 200-v-200 stress battle, campaigns and randomized exhibitions now share camera-aware draw culling. It applies to approved hull PNGs (including damage/shield masks), GPU-instanced silhouettes, geometry fallback, fighters and drones, beam strips, support links, visual projectile payloads, kinetic tracers, event flashes, and fallback geometry. **Simulation still steps every ship and munition** and performs all original targeting, collision, damage, radio and range checks. Culling happens only before draw submissions.

The bounds are conservative: they include hull wings and partially visible craft. The beam test evaluates the entire line segment bounds, so a beam with both endpoints off-screen still renders when it crosses the battle viewport. Interpolated poses, not raw tick positions, are used to decide whether the ship or projectile is visible. The GPU renderer's instance-count assertion compares against visible *non-sprite* hulls, not the full fleet.

The screensaver director now follows the same interpolated target positions that its ship sprites display; its 5–8 second camera rotation is unchanged.

Regression tasks:
- `gradle :game:combatVisibilitySmoke`: edge and long beam intersection, pan/zoom cases, and a synthetic 400-ship viewport where most draw operations should be omitted.
- `gradle :game:screensaverCameraSmoke`: timed cinematic shot selection, faction alternation, retargeting, seeded path, and interpolated focus.

Both run through existing `:game:menuFlowSmoke` in the multiplatform functional-release pipeline. These headless checks do **not** prove a device-frame-rate improvement; profiler or on-device traces are required for quantitative FPS claims.
