# Campaign narration production and script index
**Version 1 · 231 unique authored cues · all unrecorded**

## Production contents

- [STORY_BIBLE.md](../STORY_BIBLE.md) — characters, factions, narrative rules, major reveals and chapter structure
- [ATLAS_500.json](../ATLAS_500.json) — one JSON record for each of 500 geographic mission nodes; locations and script keys
- [GALACTIC_ROUTE.svg](../GALACTIC_ROUTE.svg) — strategic route visualization for 10 regions / 50 fronts
- [VOLUME_01_BORDER_SIGNALS.md](VOLUME_01_BORDER_SIGNALS.md) — expanded opening prologue and missions 001–010
- [CHAPTERS_AND_FRONTS.md](CHAPTERS_AND_FRONTS.md) — 10 chapter openings, 50 front introductions, 50 front conclusions and 10 sector-capital after-action voiceovers
- [VOLUME_10_FINAL_OFFENSIVE.md](VOLUME_10_FINAL_OFFENSIVE.md) — seven final 200-vs-200 engagements and three proposed ending variants
- [VOICE_CUE_MANIFEST.csv](VOICE_CUE_MANIFEST.csv) — slicing ledger for all 231 cues (stable key, actor, status, trigger, complete line text)

## Actor setup and recording order

The user is the sole human voice actor. Use natural voice for ARCHIVIST; then record **all VOSS**, **all THALE**, **all HALDE**, **all KADE**, and **all CONCORDANCE** lines in a separate continuous audio pass for each role. Optional Voicemod settings:

| Role | Base setting | Clarity guard |
|---|---|---|
| ARCHIVIST | unprocessed, neutral storyteller | no game-announcer overlap |
| VOSS | close-mic, slight compression | keep consonants sharp |
| THALE | small upward formant adjustment / restrained room response | avoid chipmunk pitch |
| HALDE | lower formant, light comms EQ | never bury important words |
| KADE | slightly rougher, warmer, slower | no heavy distortion |
| CONCORDANCE | subtle dual-harmonic or robotic resonance | calm, not robotic gibberish |

Record the cue ID as a **spoken slate preceding each take**, or leave an obvious one-second separation and maintain a take log. **The slate is not part of the runtime line.** For every source cue: `CD-M001-PRE-01__TAKE01.wav`; if one long MP3/WAV is supplied, retain original recording, trim copies, OGG exports, and a source-timecode CSV. Do not synthesize lines from these scripts or claim existing audio was automatically repurposed.

## Cue behavior

- `PREMISSION`: optional skippable story briefing; no compulsory voice gate before battle.
- `AFTER_SUCCESS`: only after victory (not upon wave transition, retreat or partial result).
- `INTRO`: a skippable chapter scene; do not repeat `VOLUME_01` prologue every time.
- `FUTURE_EVENT`: mid-battle narration is not yet wired. Don't play just because a random projectile hits.
- `EDITORIAL`: reference or alternate ending. The three ending variants require an actual implemented and saved player choice before use.

Story audio is independent of the **existing 30-clip gameplay announcer**, including WELCOME, BATTLE_START, VICTORY and MISSION_FAILED. Per-ship combat chatter remains text-only with calm radio bleeps, never replaced by a single performed voice. No cue should mute the tactical commands or break pause/resume.

## Completion standard

The authored text and manifests are ready for recording, **not yet recorded or included in the game build**. Packaging, WAV/OGG conversion, staging, cue lookup, language subtitling and actual playback require separate implementation. Visually evaluating prose / audio mixing must not block compilation; missing required runtime references or crashes remain functional blockers.

**Counts:** ARCHIVIST: 43 · VOSS: 64 · KADE: 29 · THALE: 47 · HALDE: 37 · CONCORDANCE: 11. Total 231 cues.
