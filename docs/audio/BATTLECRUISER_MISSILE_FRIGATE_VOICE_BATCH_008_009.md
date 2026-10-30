# Batch 008 / 009 — Battlecruiser and Missile Frigate voice performances

## Provenance and exact transfer order

The owner supplied `Battlecruiser voiced.mp3` (107.799 seconds) and `Misslefrig voiced.mp3` (121.557 seconds) on October 30, 2026. The filenames are kept exactly as uploaded; the misspelling in the second recording is not a new hull name.

Both recordings were sliced into **26** ordered line files, with 13 radio events and two variants each. Each master package preserves the original source MP3, 26 WAV edits, 26 OGG production clips, and both CSV and JSON cue manifests including source timecodes and per-file SHA-256.

**Required provenance:** original MP3 → sliced and decoded production files → Google Drive upload → Google Drive metadata verification and raw readback → GitHub blob commit. The GitHub production ZIPs were created using the **read-back bytes from Google Drive**, not from a direct local binary upload.

| Hull | Editable master in Drive | Runtime OGG ZIP in Drive | Committed production archive | SHA-256 |
| --- | --- | --- | --- | --- |
| Battlecruiser 008 | [Drive master](https://drive.google.com/file/d/1rvGeYAD1KO1Sc4R0KlcdDIRWjG4mNYfq/view) | [Drive OGG](https://drive.google.com/file/d/1vuAuYXASlm_QndXZ96rcrIxUQDyXtEb5/view) | `asset_packs/audio/battlecruiser-voice-batch-008.zip` | `2081b79f05a850edc4d1ca0938ba5e64d0701389465b50eae94af39c7eb6a9fb` |
| Missile Frigate 009 | [Drive master](https://drive.google.com/file/d/1U7sY8y0c6-HR3hl7e8BBTz86WxLAB-_Z/view) | [Drive OGG](https://drive.google.com/file/d/1jzInmzVrxEE_FIPDBLxDOR63jC6Ckvct/view) | `asset_packs/audio/missile-frigate-voice-batch-009.zip` | `b425ceb64adc0b024205ef4fed7fb0571d3da33e1956e5cb0124c0ef76e65954` |

## Engine integration

The repository now has **182** recorded radio cues across seven identities: Interceptor, Corvette, Destroyer, Carrier, Dreadnought, Battlecruiser, and Missile Frigate. The existing `prepareAdditionalHullVoiceAssets` task now validates six archives (156 OGG clips). The Interceptor's 26 clips remain in the original fighter archive and path. All deployed assets are internal for Android and desktop, independent of Drive at runtime.

**Missile Frigate is exactly the `Shipyard.Hull.FRIGATE_MISSILE` chassis**, not a missile corvette and not every ordinary frigate. Its recording uses the existing Frigate event vocabulary but **its own recorded voice performance**. Since a regular Frigate may have the exact same text, the battle event now includes a human-readable `/ Missile Frigate` radio label and the audio lookup requires it. Unrecorded ordinary Frigates remain text-only, as do Missile Corvettes until their voice bank is recorded. Battlecruiser and Battlecruiser specialist variants use the Battlecruiser bank via `spriteHull()`.

The single shared hull-voice playback channel retains the five-second gate, the central announcer's priority, effect mute, pause/resume, and one-playing-at-a-time discipline.

## Verification / limitations

All 52 production OGG files were independently decoded successfully. Both ZIPs passed integrity checks and include 26 nonempty recordings each; individual cue SHA-256 entries are in the manifests. The audio cuts follow the authored script order; **words were not independently transcribed**. In particular, the Missile Frigate performance has close-together utterances and should receive a human listening review against the assigned Frigate script. If it contains separate Missile Frigate phrasing, correct the subtitle-to-clip mapping after listening rather than silently playing a wrong line.

CI verifies manifest inventories, archive integrity, and the 182-event audio lookup. Playback on actual hardware and spoken-text correctness require separate validation.

```sh
gradle prepareAdditionalHullVoiceAssets :game:fighterVoiceSmoke :game:audioSmoke
gradle :desktop:stageDesktopAssets :android:assembleDebug
```
