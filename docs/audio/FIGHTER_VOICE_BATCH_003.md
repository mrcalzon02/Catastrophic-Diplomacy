# Catastrophic Diplomacy — Fighter voice recording, Batch 003

**Source:** user-uploaded `Fitgher.mp3` (74.61 seconds), recorded as the 26 INTERCEPTOR lines in `HULL_VOICE_SCRIPT_BATCH_002.md`.

## Correct archival chain

1. The continuous 74.61-second source was sliced by acoustic pauses into 26 lines (the final sentence's deliberate mid-phrase pause was preserved as a single clip).
2. The editable master and compressed runtime packages were **uploaded to Google Drive first**: `Sounds/Catastrophic Diplomacy/`.
3. The 26-line OGG ZIP was then **read back from Google Drive** and committed to this repository under `asset_packs/audio/fighter-voice-batch-003.zip`. No locally generated ZIP was directly pushed in its place.

| Package | Google Drive | SHA-256 |
| --- | --- | --- |
| Master (MP3 original, 26 WAV and 26 OGG, timecoded CSV+JSON) | [Fighter_Voice_Batch_003_Master.zip](https://drive.google.com/file/d/1HcV01jcIlAAlplLUwywkdnT3AZnpA1rI/view) | `d4fc7a18c6732d01ef18db90cecee8d32bb345bee6c0bb3ef0c3d0b902015609` |
| Game OGG ZIP (26 OGG, manifests) | [Fighter_Voice_Batch_003_Game_OGG.zip](https://drive.google.com/file/d/1CQVHYqUppODI9iSHiPcJ1lvbBDh6VZ_v/view) | `82db162b06fccd39ff504ade08edc080f0e5c5c09f55430899f1e489a3ead861` |

## Runtime

- `prepareFighterVoiceAssets` validates the ZIP checksum, every OGG's individual checksum and the exact 26 manifest entries, and stages audio into `audio/fighter/interceptor_<event>_01/02.ogg`.
- Android and desktop stage the same OGG assets. A standalone `FighterVoiceAudio` maps **exact recorded transcript text** to the precise sound file. Fighter pilot communications and INTERCEPTOR hulls use the fixed interceptor dialogue theme.
- The normal radio transcript continues to display; it never becomes the generic voice of a cruiser, carrier bridge, or dreadnought. Other unrecorded hull communications remain text and quiet radio bleeps.
- The voice playback is single-channel streaming, has a five-second minimum start spacing, respects mute/pause/resume, and is independent of combat RNG. It never starts simultaneously with the game announcer if the announcer is already playing.
- No player-side Google Drive access is required: runtime audio is a reproducible build asset in the repository.

**Validation:** Cue assignments follow the authored text's recording order. Without independent speech transcription, verify each spoken line against its cue ID before final release; splitting and decoding alone do not certify exact wording. Also test on physical Android and desktop devices before claiming audible playback.

## Tests

```sh
gradle prepareFighterVoiceAssets :game:fighterVoiceSmoke :game:audioSmoke
gradle :desktop:stageDesktopAssets :android:assembleDebug
```
