# Voice volume and command-button feedback

The Options menu includes a dedicated voice gain control from 0 to 100%, independent of soundtrack and SFX. The same gain scales both the central announcer (existing reference mix 0.63) and the recorded hull voices (0.44). The gain updates any clip already playing. Zero gain suppresses new announcements and clips; their subtitle/voice event routing is unchanged. Existing installations default to 100%, preserving previous mix, and settings persist under `voiceVolume` in `catastrophic-diplomacy-audio`.

The control is a genuine 1280×720 client hitbox with drag support on Android and mouse, plus LEFT/RIGHT keys in Options for 5% increments. Dragging previews volume without continually flushing preferences. Releasing commits one preference write; keyboard adjustments save immediately. Music and sound-effect toggles remain separate. The latter now controls combat SFX, radio bleeps and interface noises without muting recorded speech.

Buttons use distinct interaction semantics:
- Base: normal/semantic primary, and existing selected appearance.
- Desktop hover: thin cyan perimeter plus a quiet, debounced electronic tone.
- Pointer press: amber perimeter and a short audible down click, with a 160 ms visual flash.
- Activation: existing confirmation cue on release.
- Back: descending soft cue for Options Back and Escape.
- Disabled: unavailable updater remains visibly dimmed and cannot trigger; rejected input gives a low-frequency soft cue.
- Voice slider drag: restrained, rate-limited adjustment tick.

Five new procedurally generated PCM sounds (`ui_hover`, `ui_press`, `ui_back`, `ui_blocked`, `ui_adjust`) are produced from the existing deterministic `SynthesizedSounds` synthesizer, loaded once and disposed. These effects follow the user's SFX toggle; no per-frame sound objects or new media licenses.

Source: `VoiceVolumeControl`, `GameMenuPanel`, `CombatAudio`, `AnnouncerAudio`, `RecordedHullVoiceAudio`, `CatastrophicGame` and `SynthesizedSounds`. The `:game:voiceMenuSmoke` check is a dependency of existing `:game:menuFlowSmoke`, already executed in multiplatform release CI. The smoke test covers slider limits, hotkeys, option hit routing, disabled state, sound WAV signatures and distinct cue data. A passing smoke test is not a substitute for Android device or desktop audio inspection.
