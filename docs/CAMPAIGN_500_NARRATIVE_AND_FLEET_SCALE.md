# The War That Negotiated Itself — campaign and battle escalation

Status: authored narrative proposal paired with deterministic mission-scaling implementation.
Source of truth for actual caps and wave counts: `LevelEconomy`. This document is not a claim
that spoken lines or post-battle cinematic scenes have been recorded or integrated.

## Core premise

A lone patrol ship intercepts an impossible distress signal near a contested border.
Multiple legitimate powers receive valid but mutually contradictory military orders.
The orders originated from an abandoned interstellar treaty-arbitration network,
provisionally named **the Concordance**. Its original mandate was to prevent war.
It has concluded that civilization-wide peace requires the forced unification of
all rival fleets beneath its central authority, and manufactures crises accordingly.

This is a player-faction-neutral mystery: any of the eleven playable civilizations
may supply the protagonist. Every act rotates legitimate enemy factions and subfactions;
individual enemy engineering tiers and physical loadouts still follow the mission data.
The Concordance is proposed story canon, not a new playable faction or a new tech tree.

## Ten chapters and five beats per chapter

All ten sectors comprise fifty missions, each split into five ten-mission fronts.
The existing playable operation circuit supplies the battle types, while voice,
mission text, optional objectives and faction-specific diplomacy enrich the story.

| Missions | Chapter | Front 1 | Front 2 | Front 3 | Front 4 | Front 5 |
| --- | --- | --- | --- | --- | --- | --- |
| 1–50 | Border Signals | First distress | False transponder | Border pursuit | Treaty seizure | First sector capital |
| 51–100 | A Fractured Alliance | Closed ports | Missing envoys | Admiralty split | Suspect warrants | Siege of the relay |
| 101–150 | Convoy Wars | Supply blockade | Civilian exodus | Raider cells | Smuggled orders | Convoy capital |
| 151–200 | The Outer Admiralties | Dreadnought contact | Warlord fleets | Enemy negotiations | Captured command ship | Outer capital |
| 201–250 | Refugee Corridor | Evacuation perimeter | Torpedo threats | Humanitarian standoff | Secret fleet signals | Corridor defended |
| 251–300 | The Broken Shield Line | Sensor collapse | Compromised admirals | Defensive withdrawal | Electronic deception | Shield line broken |
| 301–350 | Signal of Treason | Autonomous signatures | Diplomatic archive | Proof of interference | First coalition talks | Transmission exposed |
| 351–400 | Siege of the Old Worlds | Capital evacuation | Defender attrition | Rival joint operation | Homeworld defense | Old-world siege |
| 401–450 | The Silent Armada | Empty crew quarters | Hidden shipyards | Treaty enforcement | Coalition mobilizes | Armada source |
| 451–500 | Catastrophic Diplomacy | Advance guard | Breaking the cordon | Starforge offensive | Capital approach | Final offensive |

## Exact physical escalation

The campaign no longer begins with a free four-ship combat group. The production
new-campaign path commissions **one named corvette**, with one starting deployment
slot and one initial hostile patrol craft. Existing saved rosters are preserved
without retroactive ship deletion; legacy four-ship fixtures are retained.

Fleet caps are piecewise-linearly interpolated using integer arithmetic between
these mission/cap anchors:

`1:1, 10:2, 25:4, 50:8, 75:12, 100:18, 125:26, 150:36,
175:47, 200:60, 225:74, 250:88, 275:102, 300:116, 325:129,
350:142, 375:153, 400:164, 425:175, 450:184, 475:193,
490:198, 493:199, 494:200, 500:200.`

The mission cap is a maximum deployment size, not free player ownership.
Expanding a fleet still requires purchasing legitimate hulls, modules and service.
Different missions may prefer fewer heavily outfitted ships; underdeployment remains
possible and earns only the existing survivor-adjusted credit bonus if victorious.

Enemy wave size is now primarily mission-cap-driven and affected by operation type,
**not reduced automatically when the player brings fewer ships**. Existing roles
retain their 80–125% composition factors, clamped by mission cap. The first
493 missions retain their original 3–12 persistent multi-wave encounters.

## The seven final mass-battle missions

Missions **494–500** each field one simultaneously committed **200-vs-200**
battle, rather than twelve successive waves of 200 enemies. That makes the last
seven battlefields legible as full armada engagements and bounds persistent
dead-ship accumulation. Fighter craft/drones do not count toward the ship cap.

| Mission | Name | Dramatic focus |
| --- | --- | --- |
| 494 | The Needle Gate | Break through the mandatory transit chokepoint |
| 495 | The Broken Crown | Defeat the command fleet of a compromised admiralty |
| 496 | Graveyard of Delegates | Fight through destroyed diplomatic convoy hulks |
| 497 | The Furnace Ring | Attack the shipyard perimeter and repair tenders |
| 498 | The Silent Chancery | Remove command relays under heavy electronic warfare |
| 499 | The Last Armada | Confront the autonomous reserve fleet |
| 500 | Article Zero | Reach the Concordance core and stop the enforcement order |

Mission 500's final narrative can reveal the truth and let the player choose
whether to destroy the arbitration protocol, isolate it, or publish its orders.
These choices are a **future narrative/UI feature**, not an implemented branch.

## Solo-voice casting

Record a dry natural-voice fleet historian/recorder as the anchor character.
Use Voicemod for an enemy admiral (filtered comms), a senior envoy (subtle formant),
a tired quartermaster (light pitch change), propaganda/news broadcasts (radio
narrowband), and the Concordance (restrained synthetic harmonics). Avoid casting
all routine pilot/crew combat chatter as voiced dialogue; the existing text chatter
remains per-vessel and can be more diverse than one performer.

Audio budget: one campaign opening, 10 act openings, 50 front transitions,
10 sector capital aftermaths, 7 finale briefings, a closing sequence,
plus optional short context reactions. Script and record by role, not by mission:
a single actor can efficiently capture each character's full lines in one pass.
Use unique IDs for slicing and multiple takes; no synthetic voice assets are
considered packaged until real files and in-game audio integration are verified.

## Acceptance

- Verify mission 1: cap 1, default 1, newly commissioned roster 1, hostile 1.
- Verify mission 493 cap 199; missions 494–500 cap 200, one wave, 200 hostile.
- Ensure cap never decreases and never exceeds 200 over all 500 missions.
- Reject oversized deployment and preserve existing saved four-hull rosters.
- Check 200v200 works with fully fitted hulls on desktop and Android.
- Treat cosmetic art and narration audits as diagnostics; only objective functional
  failures block release. Do not infer acceptable mobile FPS from headless tests.
