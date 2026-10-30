# Hull voice recordings — Carrier Batch 006 and Dreadnought Batch 007

Two new original performances were supplied by the project owner on October 30, 2026. Each recording was split into exactly 26 clips, ordered by the 13 canonical `CrewChatter.Event` categories with professional and veteran variants from `docs/audio/HULL_VOICE_SCRIPT_BATCH_002.md`.

**Established provenance: source MP3 → sliced WAV/OGG masters → Google Drive → Drive readback → GitHub.** GitHub's binary blob objects were created from the exact Base64 bytes fetched back from Google Drive, rather than directly from a local archive. Git blob SHA-1 matches the Git blob SHA-1 calculated from the local game ZIP bytes, confirming byte-for-byte consistency.

## Master archives and game packages

| Hull | Original | Google Drive master (MP3, WAV, OGG, manifests) | Google Drive production OGG package | SHA-256 game ZIP |
|---|---|---|---|---|
| Carrier, 006 | `Carrier voiced.mp3` (107.407 s) | [Carrier complete master](https://drive.google.com/file/d/1xM0GlVVIh_MkAF_AzuSdHl7_xntSU6I6/view) | [Carrier runtime ZIP](https://drive.google.com/file/d/17QmBkR_ImK3RON6Q7EVmwTng9Q5ldlfa/view) | `77e20b9e589459f77581b81d8071797f030346a5cb5256d9fb52506144e0ed39` |
| Dreadnought, 007 | `Dreadnoughtvoiced.mp3` (118.916 s) | [Dreadnought complete master](https://drive.google.com/file/d/1dBIFeHNwqpiXz06NobvRPmPj3Rjw_iBD/view) | [Dreadnought runtime ZIP](https://drive.google.com/file/d/1hikZS3Dr2Wn1HxE0g2IRW5xYgzL7bnIQ/view) | `90c65e4af2d71f759be3762fa8d320a513d7d405a6327fac77cec5575da511b6` |

The original MP3 is retained byte-for-byte in each master. Both master archives contain 26 WAV files, 26 OGG files, a CSV timecode manifest and a JSON per-clip checksum manifest. Runtime OGGs are 24 kHz mono. All 52 clips passed OGG decode validation and ZIP integrity checks.

## Gameplay wiring

- The two production ZIPs reside at `asset_packs/audio/carrier-voice-batch-006.zip` and `asset_packs/audio/dreadnought-voice-batch-007.zip`.
- The existing `prepareAdditionalHullVoiceAssets` Gradle task now recognizes all four additional recorded hull families (Corvette 004, Destroyer 005, Carrier 006, Dreadnought 007). It validates each archive SHA-256 and each OGG's SHA-256, file header, length, event inventory and runtime path before staging 104 OGG assets at `audio/hull_voice/`.
- `RecordedHullVoiceAudio` now maps **130** distinct dialogue lines across five fixed voice themes: Interceptor/Fighter, Corvette, Destroyer, Carrier and Dreadnought. The first 26 Interceptor cues still load from `audio/fighter/`; the other four families use `audio/hull_voice/`.
- Both Android APK and Windows/Linux desktop asset staging already depend on `prepareAdditionalHullVoiceAssets`; the existing shared playback system retains rate limiting, muting, pause/resume, a single simultaneous hull voice, and priority for the central game announcer.
- Specialist hull variants inherit the correct parent hull voice via `Shipyard.Hull.spriteHull()`. **Carrier bridge communications must never be substituted for launched fighter-pilot speech.**
- Unrecorded ship families continue with text-only radio communications and soft radio bleeps.

## Validation and release caveat

Clip segmentation follows the prescribed script order, using measured acoustic pauses. Multi-sentence lines retain intentional internal pauses; word-for-word alignment has **not** been independently verified by a human listening pass or speech transcription. Before claiming perfect speech-text alignment, audition the full batch against the CSV manifests. CI asset verification and Java audio routing smoke tests establish integration consistency, not audible playback on actual devices.

```sh
gradle prepareAdditionalHullVoiceAssets :game:fighterVoiceSmoke :game:audioSmoke
gradle :desktop:stageDesktopAssets :android:assembleDebug
```
