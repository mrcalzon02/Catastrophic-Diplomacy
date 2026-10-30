# Original hull voice recordings — Cruiser Batch 010 and Light Cruiser Batch 011

These two owner-recorded audio performances extend the established Catastrophic Diplomacy hull-specific communications catalog. **CRUISER** and **LIGHT_CRUISER** have different voice personalities; their corresponding specialist hull variants inherit the correct canonical bank via `Shipyard.Hull.spriteHull()`.

## Required transfer provenance

Both recordings followed the owner-mandated sequence: **slice original audio → validate and package OGG/WAV clips → upload the master and game-ready archives to Google Drive → verify Drive metadata → fetch the exact game ZIP bytes back from Drive → create GitHub binary blobs from those Drive-returned bytes → commit on main**. The production files were *not* uploaded directly from local slicing into GitHub.

| Hull | Original | Master archive (Google Drive) | Game-ready OGG ZIP (Google Drive) | SHA-256 of game ZIP |
| --- | --- | --- | --- | --- |
| Cruiser, 010 | `cruiservoiced.mp3` (103.895 s) | [Master](https://drive.google.com/file/d/17e_mzaMfkcaGG4Q5olt0ehNIJGYDWQi3/view) | [Production](https://drive.google.com/file/d/1807_zDUE0chKeVMjIdmeRDKBpNtVJOpY/view) | `0d8e996fc8544b34796d575716f84ea25f6e38c993d9bdccf92be7c9a7523dc1` |
| Light Cruiser, 011 | `Light cruiser.mp3` (99.745 s) | [Master](https://drive.google.com/file/d/1Ek-7O8LI4koHcakyT_LJVzIbASI2ifKo/view) | [Production](https://drive.google.com/file/d/17CyX7keDgM_fgahRCFB2AwhHJ7Q35xWF/view) | `11437b903d0fae6e1eb68705ed402ce5d68c9e511459b2f0dce7fa0388be7d20` |

Each master archive contains the *unchanged* original MP3, 26 editable WAVs, 26 mono OGG Vorbis clips at 24kHz, a timecoded CSV/JSON cue manifest and a README. Each game ZIP contains 26 OGGs plus cue manifests, retaining individual SHA-256 checksums, file lengths, and paths.

## In-game use

- GitHub production archives: `asset_packs/audio/cruiser-voice-batch-010.zip` and `asset_packs/audio/light-cruiser-voice-batch-011.zip`.
- `prepareAdditionalHullVoiceAssets` validates eight Drive-origin archives / **208 OGG clips** plus the original separate 26-fighter archive. It checks ZIP hashes, cue IDs, complete 13-by-2 event coverage, OGG headers, lengths, paths and per-clip SHA-256 before staging into `audio/hull_voice/` for desktop and Android.
- `RecordedHullVoiceAudio` resolves **234** distinct voice cues across nine recorded hull roles: Interceptor, Corvette, Destroyer, Carrier, Dreadnought, Battlecruiser, Missile Frigate, Cruiser and Light Cruiser. It preserves per-hull style, single-channel playback, five-second spacing, mute and pause handling, and the central announcer's priority.
- Ordinary Frigates remain unrecorded. The Missile Frigate bank remains explicitly chassis-tagged so that a normal Frigate never borrows that performance. Unrecorded hulls continue text radio with soft bleeps.

## Validation limits and verification

All 52 new OGGs decoded successfully; both archives passed ZIP CRC integrity checks. Acoustic segmentation was reconciled with the 26 cue slots from the existing script; a deliberate pause in the Cruiser's last cue was retained within that one recording.

**Caveat:** Line text was assigned in original script order, not independently transcribed from speech. A listening review is still needed to prove exact word-for-word clip-to-subtitle alignment, followed by device playback checks. ZIP integrity and a passing build do not establish that the spoken text matches.

```sh
gradle prepareAdditionalHullVoiceAssets :game:fighterVoiceSmoke :game:menuFlowSmoke
gradle :desktop:stageDesktopAssets :android:assembleDebug
```
