# Campaign narration production and script index
**Version 2 · 231 unrecorded story cues · preserves 260 existing recorded hull cues**

## Production contents

- [STORY_BIBLE.md](../STORY_BIBLE.md) — characters, factions, narrative rules, major reveals and chapter structure
- [ATLAS_500.json](../ATLAS_500.json) — one JSON record for each of 500 geographic mission nodes; locations and script keys
- [GALACTIC_ROUTE.svg](../GALACTIC_ROUTE.svg) — strategic route visualization for 10 regions / 50 fronts
- [VOLUME_01_BORDER_SIGNALS.md](VOLUME_01_BORDER_SIGNALS.md) — expanded opening prologue and missions 001–010
- [CHAPTERS_AND_FRONTS.md](CHAPTERS_AND_FRONTS.md) — 10 chapter openings, 50 front introductions, 50 front conclusions and 10 sector-capital after-action voiceovers
- [VOLUME_10_FINAL_OFFENSIVE.md](VOLUME_10_FINAL_OFFENSIVE.md) — seven final 200-vs-200 engagements and three proposed ending variants
- [VOICE_CUE_MANIFEST.csv](VOICE_CUE_MANIFEST.csv) — slicing ledger for all 231 cues (stable key, actor, status, trigger, complete line text)

## Casting ground rule: existing Voicemod hull-voice presets are established audio

The owner has supplied a visual reference of the **Voicemod presets they actually used to voice the different ship hulls**. Those are the relevant sounds to build around, not an imaginary bank of generic voice effects. Their actual chosen preset names and exact controls should be transcribed only after a readable in-app audit; **do not invent preset names based on the screenshot or replace any previously approved recorded hull voices**.

**Confirmed performer experience:** small pitch changes and many ordinary Voicemod voice presets still sound recognizably like the owner. A few markedly transformed **PRO voices** are genuinely useful, especially for *alien* dialogue. Design the cast accordingly: one natural narrator, existing specialized hull-voice recordings, and a limited number of unmistakably alien character voices.

### Three separate audio layers

| Layer | Actual state | How it should be used |
|---|---|---|
| **Game announcer** | 30 original recorded cues in `asset_packs/audio/announcer-batch-001.zip` | Global battle/menu events. Preserve the already recorded performer and existing integration. |
| **Hull radio personalities** | **260 existing original clips across ten separately recorded hull-voice roles**; see `docs/audio/` | Keep these actual voices, lines, stable cues, rate limiting and spatial radio mixing. Unrecorded hull chatter still uses text and calm bleeps. |
| **Story / diplomacy** | 231 authored **unrecorded** script-cue records; no runtime voice playback yet | Natural narration, short optional officer dispatches and selective dramatically transformed alien characters. Never assume all 231 require separate voiced performances. |

**Important correction to earlier writing guidance:** ship-to-ship communications are **not universally text-only**. Exact lines that match the ten *already recorded* hull banks are voiced. Other ship-to-ship chatter remains text and bleeps. The campaign must not overwrite, resynthesize, or reinterpret those recordings.

The ten existing recorded voice roles (not claims about specific preset names) are: **Interceptor/Fighter, Corvette, Destroyer, Carrier, Dreadnought, Battlecruiser, Missile Frigate, Cruiser, Light Cruiser and Heavy Cruiser**. The baseline Frigate and Missile Corvette are still outstanding separate canonical hull families according to their archived status. These distinctions are already part of the game and must be preserved. See `docs/audio/HEAVY_CRUISER_VOICE_BATCH_012.md` and `game/src/main/java/diplomacy/client/RecordedHullVoiceAudio.java`.

### Story roles — what to record and what to write

| Role | Primary approach | Voicemod assignment rule |
|---|---|---|
| **ARCHIVIST** | Main narrated campaign history in the owner's unmodified voice | None required. Recognition is intentional. |
| **VOSS** | Crisp short mission briefings; natural narration or written operational dispatches | No promise to make the same performer sound like another human. |
| **KADE** | Dry practical observations; use natural delivery or text | No fake old-man rasp or mandatory pitch changer. |
| **THALE** | Written/narrated treaty testimony unless a particular alien casting calls for a genuinely distinct PRO voice | No mandatory artificial feminine voice. |
| **HALDE** | Subtitled fleet transmissions, optionally read in the owner's normal voice | Radio-style texture may establish *channel*, not a convincing second human actor. |
| **CONCORDANCE** | Calculated artificial intelligence: clear on-screen declarations, optional uncanny voice | Audition truly nonhuman voice options. A bad AI filter is worse than clear text. |
| **Named alien admirals, envoys and witnesses** | Small recurring guest cast chosen for major chapter scenes | **First priority for the user's strongest actual PRO alien voices.** |

The alien characters should have clearly **different vocabulary, cadences and aims**, so the writing still works if a given PRO preset changes or disappears. Each approved alien voice maps to one recurring *character*, not automatically to an entire faction. If a PRO voice resembles an existing hull voice, that can be used **intentionally** for a shared factional sound, but never casually assign the exact same recognizably distinctive voice to two unrelated characters in the same scene.

### Voicemod is software with specific voices, not an abstract audio studio

Voicemod publicly documents **Voicebox**, **Voicelab**, real-time AI voices and radio/robot effects. These are *possibilities*, not a guarantee of particular installed modules. The official [alien examples](https://www.voicemod.net/en/alien/) include `Orbital`, `Space Trooper` and `The Visitor`, but those examples **must not be falsely identified as the owner's pictured/used presets**. The official [AI voices page](https://www.voicemod.net/en/ai-voices/) describes stronger voice transformations, whereas simple effects often just color an already identifiable voice. See also [Voice FX](https://www.voicemod.net/en/voice-fx/).

**Before assigning any preset to a narrative character**, capture its exact on-screen name, its relevant visible controls, and a 15–30-second audition of a test passage. Mark the candidate as `APPROVED`, `REJECTED` or `NOT_TESTED` based on whether it is recognizably alien, intelligible and stable. Do not prescribe made-up formant sliders, pitch values, signal chains or unavailable AI models. Keep dry originals whenever feasible.

The performer can record entire batches in one session per successful preset. Keep cue slates (spoken ID or silence gap), export the dry source and transformed take independently, and record exact preset names in the take manifest. Runtime clips are only produced once they have actually been recorded, sliced and inspected. Existing recorded hull-bank packages remain unchanged.

### Character-first script rules

1. **Your own narration is an asset.** Present the campaign as an archive and evolving military history rather than pretending one person voices six independent humans perfectly.
2. **Use outstanding PRO voices for aliens:** reserve two to four genuinely differentiated recurring alien identities if testing supports them. At major negotiations, one or two distinctive alien transmissions can make the whole galaxy feel populated.
3. **Retain hull identity:** existing recorded fighter/carrier/dreadnought/etc. voices stay attached to their current ship roles and their already recorded dialogue. Do not repurpose the clips into character dialogue merely because the timbres fit.
4. **Separate scripted voices from tactical chat:** normal combat messages not matched to the recorded banks remain text + quiet bleeps; campaign narrative speaks at safe intervals and never masks player commands.
5. **No studio assumptions:** don't write dialogue that requires a real other human voice, simultaneous performers, controlled accents, perfectly realistic gender conversion or an unavailable preset.

## Cue behavior

- `PREMISSION`: optional skippable story briefing; no compulsory voice gate before battle.
- `AFTER_SUCCESS`: only after victory (not upon wave transition, retreat or partial result).
- `INTRO`: a skippable chapter scene; do not repeat `VOLUME_01` prologue every time.
- `FUTURE_EVENT`: mid-battle narration is not yet wired. Don't play just because a random projectile hits.
- `EDITORIAL`: reference or alternate ending. The three ending variants require an actual implemented and saved player choice before use.

Story audio is independent of the **existing 30-clip gameplay announcer**, including WELCOME, BATTLE_START, VICTORY and MISSION_FAILED. The previously recorded exact hull-radio lines remain voiced; other combat chatter stays text-only with calm bleeps. Neither is replaced by the campaign narrator. No cue should mute the tactical commands or break pause/resume.

## Completion standard

The authored text and manifests are ready for recording, **not yet recorded or included in the game build**. Packaging, WAV/OGG conversion, staging, cue lookup, language subtitling and actual playback require separate implementation. Visually evaluating prose / audio mixing must not block compilation; missing required runtime references or crashes remain functional blockers.

**Counts:** ARCHIVIST: 43 · VOSS: 64 · KADE: 29 · THALE: 47 · HALDE: 37 · CONCORDANCE: 11. Total 231 cues.
