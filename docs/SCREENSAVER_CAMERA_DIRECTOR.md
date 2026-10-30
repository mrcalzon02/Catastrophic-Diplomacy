# Screensaver cinematic camera

Exhibition mode no longer locks itself to the automatic full-fleet rectangle. An independent, seed-replayable camera director follows random surviving ships, switches subjects every **5–8 seconds**, prefers alternating between opposing fleets when available, and interpolates position and zoom each rendering frame. The aim follows the vessel's *current* position during the shot, not the position it occupied at selection. Zoom varies within a moderate range to keep weapons and nearby hulls readable. Ships never teleport due to camera switching.

When the subject dies, it retargets immediately to another live ship. With no survivors it glides back to an overview during the victory pause. Each randomly generated battle creates a new director state. Pan duration uses elapsed display time, **not** simulation time speed or FPS frame count. The existing static text overlay, tap/key-to-exit behavior and 4-second round pause are retained.

Player controlled campaign and skirmish cameras are unchanged. This only affects the automatic screensaver. Interactive Android screensaver gestures remain a separate follow-up: tapping the current screensaver still exits, rather than using the normal battle's pinch controls.

Regression `gradle :game:screensaverCameraSmoke` runs automatically from existing `:game:menuFlowSmoke` in the multiplatform release pipeline. A functioning build is distinct from an inspected on-device moving-camera test.
