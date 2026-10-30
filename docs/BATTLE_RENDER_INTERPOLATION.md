# Shared ship and munition visual smoothing

The fixed-step simulation remains authoritative; we preserve pre-step poses and draw between the previous and current positions using the frame accumulator remainder. Hull PNGs, damage/shield overlays, GPU silhouettes, software geometry, fighters, missiles, torpedoes, ballistic rounds, kinetic tracers, missile ignition glows and engine exhaust now all use shared interpolated positions and headings. Beam endpoints and damage events remain exact simulation outputs, and stationary smoke retains its existing event history.

A newly spawned projectile or craft appears at its actual launch position (no interpolation from origin). Reused IDs are purged on battle switches and large warps snap rather than streaking across the scene. Pausing uses the latest exact pose. The same render path applies to campaign, skirmish and randomized screensaver battle; LAN snapshot presentation explicitly avoids borrowing local camera poses.

Test `gradle :game:renderPosesSmoke` runs through the existing `:game:menuFlowSmoke` release suite. The real APK still requires visual confirmation of motion at normal and low frame rates.
