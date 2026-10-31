# Heavy Cruiser voice — Batch 012

Original owner recording: `heavy crusiervoiced.mp3` (~153.6 seconds). Its spelling is preserved as supplied. The recorded Heavy Cruiser is a separate canonical hull identity, not the regular Cruiser or Light Cruiser.

## Provenance and archives

**Required order followed:** audio slicing → 26 verified OGG clips and 26 editable WAV clips with CSV/JSON cue manifests → **Google Drive upload** → Google Drive metadata verification and raw binary readback → GitHub source asset.

- [Complete Drive master](https://drive.google.com/file/d/1FK09f_GfDH7BQhSgjFXQyNyD8UphXidj/view): original MP3, 26 WAVs, 26 OGGs, and recording manifests.
- [Drive production ZIP](https://drive.google.com/file/d/1Rd1ry3TPUIc4qGodLjJgyerEY4r5FnTV/view): 26 OGGs plus manifest/README, 835766 bytes, SHA-256 `4f44e9465b1ab7823966e39e8f94f3ba9f43a64461a04a03a13fe2979e20a3d8`.
- GitHub archive: `asset_packs/audio/heavy-cruiser-voice-batch-012.zip`.
- 13 events × 2 variants, mapped to `HullVoiceChatter.Theme.HEAVY_CRUISER`. OGG Vorbis mono 24 kHz.
- Two long final passages were split at measured acoustic pauses; intra-sentence breaks elsewhere were retained. ZIP CRC and all 26 independent OGG decode checks passed.

**Important:** Text assignments follow the established recording/script order. They have not been verified word-for-word by an independent transcript or full listening review.

## Hull coverage

**9 of 11 canonical hull families now recorded.** The outstanding canonical families are **FRIGATE** and **MISSILE_CORVETTE**. The separately recorded specialist `FRIGATE_MISSILE` is counted as its own tenth audible voice role, but does not replace the FRIGATE voice. The total original recorded cue collection now contains **260** files across ten voice roles.

## Camera-relative radio voice

`Battle` now stores stable source ship IDs in parallel with its log history. This ensures that duplicate ship names do not misassign a recorded message.

`RadioVoiceProximity` uses the same camera position and zoom as combat audio to calculate a smoothly varying gain. The selected **nearby** recorded voice line fades in from zero, then continuously fades down as the camera pans away and fades up again when the source vessel comes back into view. Pan-off and death cause a smooth fade to silence. Distant messages remain in the text event log, but do not monopolize recorded voice playback. The centralized narrator remains separate and has priority, while the existing voice-volume slider and voice cooldown are preserved.

`RadioVoiceProximityTest` verifies distance order, camera pan, zoom, invalid input and smooth attack/release. The existing 260-line voice routing test is in normal release CI.

## Reproduction

```sh
gradle prepareAdditionalHullVoiceAssets :game:fighterVoiceSmoke
gradle :desktop:stageDesktopAssets :android:assembleDebug
```

These checks validate asset extraction, source integration and functional audio logic. They do not replace audible in-game testing on real Android/Windows/Linux devices.
