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
