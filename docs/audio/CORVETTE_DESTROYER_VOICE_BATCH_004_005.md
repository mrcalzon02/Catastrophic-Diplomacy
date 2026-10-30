# Original Hull Voice Recording — Corvette 004 / Destroyer 005

These are the next two **user-recorded** voice themes in the Catastrophic Diplomacy hull dialogue catalog, following the interceptor voice batch 003. No individual vessel is assigned a random voice: variants use the canonical `spriteHull()` family.

## Correct archival provenance

The user uploaded `Corvettevoiced.mp3` (~92.6 s) and `Destroyervoiced.mp3` (~99.1 s). Each is preserved verbatim. Acoustic silences were measured and manually reconciled with the authored Batch 002 script to produce 26 **separate** event/variant clips per hull (13 existing radio events, two variants each). WAV editing copies are included in the master archives; the runtime ZIPs contain Ogg Vorbis 24-kHz mono files.

**Required chronological path was followed:** Uploaded MP3 → sliced master and OGG archive → **Google Drive** `Sounds/Catastrophic Diplomacy/` → Google Drive metadata/readback → GitHub blobs → repository packaging.

| Hull / batch | Complete Drive master | Game OGG ZIP on Drive | GitHub archive | SHA-256 of Game OGG ZIP |
| --- | --- | --- | --- | --- |
| Corvette 004 | [Complete master](https://drive.google.com/file/d/1KoVeBxXhDHZAkhIATIFk6s14cjpWATzl/view) | [Game archive](https://drive.google.com/file/d/1-lQ5eOoGlJUm7XlV7Evnh8UI07M4Ld5P/view) | `asset_packs/audio/corvette-voice-batch-004.zip` | `5f65fb476fe84d98d2f6d9746bc90e0553b089b4ba6803ec57c0408c20261346` |
| Destroyer 005 | [Complete master](https://drive.google.com/file/d/1zNhiKPOzb8bUHMBq9nMO_6eBqGroq4w6/view) | [Game archive](https://drive.google.com/file/d/1ixNyAlEZKf0Nw_30ba-J3CtqYMZIH3XI/view) | `asset_packs/audio/destroyer-voice-batch-005.zip` | `1b1705174e305c8894d38750965e72b0961d7fb8242cf55fdb1cc3aa4d9431ec` |

The ZIPs each contain a source-timecode CSV and a JSON manifest with all per-clip SHA-256 digests, event IDs and expected lines. The game can reconstruct the recording library offline without requiring a user's Drive access.

## Runtime and tests

`prepareAdditionalHullVoiceAssets` verifies the two ZIP checksums and 52 individual OGG digests. Both desktop and Android stage `audio/hull_voice/{corvette,destroyer}_<event>_<variant>.ogg` from the generated runtime folder. The original interceptor recordings remain in `audio/fighter/interceptor_*.ogg`.

`RecordedHullVoiceAudio` provides one shared voice channel for all **78** recorded cues. It resolves the existing in-game radio subtitle into a recorded clip, rate-limits playback to one line every five seconds, permits no overlapping lines, and respects SFX muting and lifecycle suspension. Unrecorded hulls continue using text and calm radio bleeps. The game's central announcer is kept separate.

`FighterVoiceAudioTest` is retained as the existing smoke-test entrypoint, but now verifies all 78 subtitle-to-file mappings across the three canonical hulls.

**Human QA still required:** the 26 line-to-text assignments per recording follow the Batch 002 authored reading order; independent word-for-word ASR transcription was not performed. The segmentation includes corrections for dramatic pauses inside lines and short gaps between distinct spoken lines. A final listening pass and device tests are still required before certifying spoken-word accuracy or audible playback.

```sh
gradle prepareAdditionalHullVoiceAssets :game:fighterVoiceSmoke :game:audioSmoke
gradle :desktop:stageDesktopAssets :android:assembleDebug
```
