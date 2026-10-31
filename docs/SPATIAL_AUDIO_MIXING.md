# Spatial combat audio / zoom-driven soundscape

This augments the already integrated Drive soundtrack and existing projectile SFX.
No core combat, fleet mechanics or deterministic simulation logic was modified.

## Soundscape policy

- **Close examination (camera zoom below 0.95):** Up to three *audible* local
  engines, with loop pitch tied to vessel class and volume/pan tied to the viewport.
  Navigation thruster puffs fire only when steering accumulates sufficient rotation.
  Point defense and individual fighter launches are proximity-only details.
- **Tactical view:** Laser/kinetic/particle beams, impacts and interceptions follow
  on-screen distance and stereo direction. Distant small weapons fade out.
- **Fleet overview:** Heavy plasma, torpedo and capital-ship fire retain long reach.
  Ship destruction and shield collapse remain prioritized over repetitive shots.
  A single low-level pooled machinery sound bed replaces hundreds of individual
  engine voices.
- **Shield changes:** Distinct synthetic up/down effects trigger on actual vessel
  shield state crossing documented thresholds, not on random decorative intervals.
- **Budget:** At most 6 new event one-shots per frame, 80 priority candidates,
  3 near-field engine loops, and 1 strategic engine-bed loop. The music channel
  remains independent of effects muting.

Seven already integrated short effects and both original Drive tracks are retained;
nine new reproducible synthesized 22,050 Hz, signed 16-bit mono WAV profiles
cover engine loop, fleet bed, navigation thruster, shield rise, shield collapse,
heavy cannon, reactor collapse, metal strain and point defense. They are rendered
once to app-private local cache using the shared Java game code and loaded through
libGDX, avoiding platform-native synthesizers and duplicate source packaging.

## Controls

- Mouse wheel zooms the tactical camera. **Z** zooms in, **X** zooms out,
  and **C** restores automatic framing.
- Android/desktop touch hitboxes near the bottom left: **ZOOM +**, **ZOOM -**, **AUTO**.
- Tap/click a ship in the arena to follow it; empty battlefield space clears the focus.
- Existing **U**, **I**, **J** music/effects/track controls and tactical menu remain.
- At full overview zoom the player should hear fleet engines, major weapons, and
  impacts, but not hundreds of individual fighter/engine thrusters.

## Verification and outstanding acceptance

```sh
gradle :game:audioSmoke :game:spatialAudioSmoke :desktop:classes :android:assembleDebug
```

The headless spatial-audio test checks zoom gates, pans, far-field audibility,
fleet-bed scaling, and the headers/size/dynamic range of all nine synthesized WAVs.
Real device acceptance remains required: Windows x64 and Linux desktop ALSA/Pulse/
PipeWire, Android emulator and handset, zoom/pan controls, 200v200 polyphony,
weak mobile speaker bass reproduction, Android local-file Sound loading, app
background/resume, stereo panning and final destruction audibility.

Drive audio distribution rights remain subject to the original
[Audio System](AUDIO_SYSTEM.md) release-rights gate; these synthesized cues are
original programmatic source.


## Automatic anti-repetition pitch variants (October 2026)

Every accepted one-shot combat sound now selects a fresh playback-rate variant
from the authored source WAV. This includes weapons, shield hits and facing
transitions, hull impacts, ship explosions, and the dedicated point-defense
synth. The same cue cannot repeat either of its previous TWO pitches.
It retains spatial volume, stereo pan, event priority, actual hit timing,
cooldown and the existing 6-shots-per-frame cap.

- Combat one-shots: **0.86×–1.14×** pitch (nine notes), matching the Nut Buster
  percussion variation approach.
- UI buttons, menu confirms and adjustments: **0.95×–1.05×** (seven notes).
- Communication bleeps: **0.96×–1.04×** (seven notes).
- Steering thruster bursts: **0.91×–1.09×** (seven notes).

Voiceover, spoken radio, background music, continuous engine loops and fleet
beds are **not** randomly pitch shifted. Ship-class engine pitch remains
as-designed for each looping engine. The pitch selector uses its own render-side
PRNG and per-cue anti-repeat history, not the simulation RNG. Each variant reuses
the exact original file without creating another decoded sample per variant.
Muting, pause/resume, battle reset and polyphony controls still apply.

Test with `gradle :game:audioPitchVariationSmoke` (standalone diagnostic, not
a release-blocking aesthetic check); then audition rapidly repeated close-up
volleys, UI controls and radio bleeps on an actual Android handset and desktop.
