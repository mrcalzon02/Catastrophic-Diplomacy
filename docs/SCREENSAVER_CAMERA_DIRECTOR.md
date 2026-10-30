# Screensaver cinematic camera and wake controls

The exhibition battle continues using its existing randomized real ships, ordnance and
battlefield backdrops. A seed-replayable presentation-only director now rotates between
three live camera subjects: **capital/escort ships** (roughly 50–80% of eligible
shots), **active fighters** (up to 30%) and **guided missiles/torpedoes** (up to 20%).
Unavailable targets automatically fall back to surviving ships. Destroyed targets
are immediately replaced; victory pauses glide to a fleet overview. Combat simulation
and ship RNG are untouched.

## Much closer shots

Ship shots now target approximately **0.16–0.41** camera zoom instead of
0.48–1.25, with fighter shots at 0.095–0.18 and missile shots at 0.075–0.145.
Lower zoom numbers mean a tighter view. The director follows the same interpolated
pose used by the sprites, with stronger tracking response for fast-moving
fighters and ordnance once the new subject has been eased into view. Missile
flight tracking carries a restrained short look-ahead in the velocity direction.
Shot timing uses display seconds, not accelerated battle ticks.

## Touch and HUD

- One tap never exits the exhibition. A valid **double tap within 420ms**
  in the same small screen area wakes/hides a compact controls bar.
- Drags pan; two-finger pinch zooms around the gesture center. Both temporarily
  hold the manual view for nine seconds, then resume the smooth director.
- Controls bar: **ZOOM −**, **ZOOM +**, **PAUSE/PLAY**, **SLOW**, **FAST**,
  **AUTO CAM**, **NEXT**, **HIDE**, **EXIT**. EXIT is intentional and explicit.
- Long presses, drags, pinches and widely separated taps do not trigger wake.
  Android BACK and desktop ESC still provide direct exit. Desktop SPACE toggles
  controls; P pauses, Z/X zoom, +/- change speed, C resumes automatic camera.
- Each new randomized battle resets the director's target and any manual camera
  overrides; wake HUD visibility persists across automatically advancing rounds.

The touch wake detector is pure Java, allowing a headless
`ScreensaverWakeGestureTest`; camera interpolation and manual override are
checked in `ScreensaverCameraDirectorTest`. A passing JVM smoke test is not
equivalent to checking touch feel on actual Android hardware.
