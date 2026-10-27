# Shared combat audio and Drive-sourced soundtrack

## What is implemented

The one shared `:game` libGDX client plays event-driven audio on Android APKs and Windows/Linux desktops. The `:core` deterministic 30 Hz simulation is untouched. Its `VisualEvent` stream supplies fire, impact and interception cues; an authoritative increase in `battle.destructions` triggers an explosion.

- Laser, plasma, particle, missile, torpedo and shield hits use original synthesized WAV effects.
- Kinetic fire, hull impacts, ship destruction and UI confirmation use existing sound files from the connected Drive library.
- Two looped music selections: **Final Surge (hunted)** for combat and **Cold Unresolved Drone** for ambience.
- Max four newly started event sounds per rendered frame, plus cooldowns per cue measured in battle ticks. This prevents 400 ships from starting hundreds of concurrent effects.
- Muting SFX does not mute music; disabling music does not mute SFX.
- Sound/music preferences persist under `catastrophic-diplomacy-audio`, using the platform's app-private preference storage. No platform-specific source fork.
- Failed device audio initialization is logged; combat and deterministic tests continue without sound hardware.

The combat menu now includes touch-operable **MUSIC**, **SOUND EFFECTS** and **SOUNDTRACK** rows. Keyboard shortcuts: **U** music on/off, **I** sound effects on/off, **J** switch tracks. Open the tactical menu with **M**, escape or its touch target. The default track is Final Surge. Android audio respects the app lifecycle pause/resume.

## Provenance

Original sound synthesis (in this project): `laser_zap.wav`, `plasma_pulse.wav`, `particle_arc.wav`, `missile_launch.wav`, `torpedo_launch.wav`, `shield_hit.wav`. PCM WAV, mono 22050 Hz, 16-bit; generated specifically for Catastrophic Diplomacy.

Imported from the user's connected Google Drive audio library (preserve names for provenance):

| Runtime path | Original Drive file |
| --- | --- |
| `audio/music/final_surge.mp3` | `Final Surge(hunted).mp3` |
| `audio/music/cold_unresolved_drone.mp3` | `Cold Unresolved Drone.mp3` |
| `audio/sfx/grenade.wav` | `Granade.wav` |
| `audio/sfx/metal_wonk.wav` | `Metal_wonk3.wav` |
| `audio/sfx/metal_thwak.wav` | `Metal_thwak.wav` |
| `audio/sfx/hollow_metal.wav` | `Hollow_metal.wav` |
| `audio/sfx/ui_confirm.wav` | `Single_click.wav` |

The longer ambient MP3 is stored in four binary `asset_packs/music/cold-unresolved-*.bin` pieces. `prepareMusicAssets` concatenates them exactly and verifies the full SHA-256 `0e0b0e5bbad299d733938f907aa63fd62de9abad3dc61a00a8ee287fbe10a293`. Windows/Linux desktop and Android both mount the generated asset alongside the normal `assets/` root. `verifyBattleAudioResources` rejects missing/malformed audio and verifies the battle music checksum.

**Release-rights gate:** These Drive recordings are user-provided source assets. Drive possession does not independently establish a transferable commercial/public redistribution license. The repository owner must confirm the distribution rights for each sourced recording before publishing a public game release. Do not substitute this integration work for music-rights clearance.

## Verification

```sh
gradle :game:audioSmoke :desktop:classes :android:assembleDebug
gradle :desktop:run
```

The Gradle Android/desktop asset tasks depend on `verifyBattleAudioResources`, which also reconstructs and verifies ambient music. `:game:audioSmoke` tests shared event-to-cue routing and cooldown configuration without an audio device.

**Still required on hardware:** Listen on Windows, multiple Linux distros and Android; test the Android decoder on both MP3 tracks; play 200v200 at 1× and 16× to confirm no voice/channel saturation; verify toggles via touch, pause/resume, background return, music looping and persistence after process restart. CI compilation and source/header checks do not certify audible sound on every device.
