# Announcer voice batch 001 — recorded original

The user supplied one continuous recording, **Audio voiced.mp3** (89.485 seconds).
Thirty segments were cut on audible pauses against the sequential
30-line master-announcer script. Every exported clip has stable numeric naming,
a source timecode, and a matching entry in the included CSV/JSON manifest.

## Source archive and rights

The editable, lossless and original files are retained in the owner's connected
Google Drive under \`Sounds/Catastrophic Diplomacy/\`:

- \`Voice_Batch_001_Complete_Master.zip\` — original MP3, WAV edits,
  OGG runtime clips, source-timecode manifests
- \`Voice_Batch_001_Game_OGG.zip\` — compact runtime OGGs and manifests

The game repository stores a byte-for-byte copy of the compressed production
archive at \`asset_packs/audio/announcer-batch-001.zip\`.

SHA-256 (runtime archive):
\`646777366a257a15faea1e4ad5af742b5940f22d77c0fb3ad16e57b44bcd26ea\`

The owner made and provided this recording for use in this game. Do not replace it
with a synthetic voice or mix it into individual ship radio chatter.

## Runtime integration

\`prepareAnnouncerVoiceAssets\` validates archive SHA-256, manifest size and
each Ogg Vorbis file header, then writes all 30 cues to
\`build/generated/cd-announcer/audio/announcer/*.ogg\`. Both desktop and Android
package the same staged assets. This means no Drive connection is needed by
players or by build jobs.

\`AnnouncerAudio\` is the single-voice streaming announcer:
no overlapping lines, lower playback volume, sound-effect mute integration,
pause/resume support, and disposal after each playback. All 30 cues have stable
enumeration identifiers; initial gameplay triggers are:

| Event | Cue |
| --- | --- |
| First arrival at main menu | \`WELCOME\` |
| Starting a campaign mission or skirmish | \`BATTLE_START\` |
| Completed battle won by the player | \`VICTORY\` |
| Completed battle lost by the player | \`MISSION_FAILED\` |

Screensaver combat does not repeatedly play the battle-start voice.
Unassigned cues are available for future confirmed UI events. The optional
humorous lines (21–30) must remain rare rather than routine background chatter.

**Ship-to-ship combat communications remain TEXT ONLY**, with their quiet
communication bleeps. The announcer is the voice of the game, not a particular
ship, officer, faction or pilot.

## Validation limitations

The clips were separated by acoustic pauses and paired to the original
announcer script in order. The words were not independently transcribed;
listen for any misreads before a public release.

\`\`\`sh
gradle prepareAnnouncerVoiceAssets :game:audioSmoke :game:announcerSmoke
gradle :desktop:stageDesktopAssets :android:assembleDebug
\`\`\`

A successful Gradle stage verifies checksum and encoding headers; it does not
replace actual listening tests on Linux, Windows and Android.