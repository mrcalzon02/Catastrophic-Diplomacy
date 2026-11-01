# Independent multi-role fighter wings

Fighters are independent small combatants, not missile skins. The existing
repair/defense drones are retained. All gameplay remains in the shared Java 17
core used by Android APK, Windows and Linux.

## Flight-capable classes

| Fighter | Main weapon | Combat role |
|---|---|---|
| INTERCEPTOR | Actual tracked kinetic cannon shells | Dogfights hostile strike craft |
| ASSAULT_GUNSHIP | Heavy tracked kinetic cannon shells | Armored ship attack |
| LASER_FIGHTER | Line-of-sight laser | Fast, close-range strikes |
| PARTICLE_FIGHTER | Particle beam | Longer-range particle strikes |
| MISSILE_FIGHTER | Real guided missiles | Standoff finite-munition strikes |
| TORPEDO_BOMBER | Real guided torpedoes | Slow armored capital strike platform |

Each has a validated, faction-adjusted immutable profile with independent HP,
armor, velocity, fuel, sensors, radar cross-section, emissions, range, accuracy,
reload and ammo. Firing uses Battle's existing Ballistic, Missile and Beam
objects, not an instant invisible hull-damage shortcut. Kinetic shells also
sweep against hostile strike-craft bodies and can damage them. Interceptors
prioritize hostile aircraft when they can acquire one; otherwise they engage
ships like conventional light craft. Existing vessel point defense detects
fighters through the same cross-section/emissions and tracking calculations as
ordnance. Fighter kills and projectile activity are independently observable.

## Fitting, persistence and interaction

Install a faction's **FTR** hangar in a shipyard support slot, select that
slot, then tap **FIGHTER** information near the lower center of the shipyard or
press **K** to rotate through all six carried types. Press **APPLY** to deploy.
A single design-wide fighter payload selection applies to installed hangars
on that design; individual hangar flight-group manifests are a follow-up.
No FTR hangar means zero fighters regardless of selected craft doctrine.

Ordinary gunship designs retain the existing CD-SHIP-1 four-field blueprint
header exactly. A nondefault fighter selection appends an optional validated
header field. New decoders accept both formats and reject unknown hulls.
The FleetPresets outer checksum is unchanged; Android and desktop can load
the same encoded fleet state.

## Functional acceptance

```sh
gradle :core:fighterCraftSmoke
gradle :core:massBattleSmoke
gradle :core:combatMachinerySmoke :game:classes :desktop:classes :android:assembleDebug
```

The fighter-specific test builds real fitted carriers for **every** type and
runs two identical seeded combats, checking launched hull profile, finite
munitions, weapon-family counters, real nested ordnance, historical save
round-trip, legacy save decode, sensor/armor differences and interceptor
air-to-air sensor locks.

The standard 200v200 mixed test separately deploys one carrier for each of
the six classes on BOTH teams while preserving the remaining mixed fleet
loadouts. The test rejects deployments that fail to fire all five distinct
weapon families and rejects non-reproducible 480-tick battle fingerprints.

**Not completed by core testing:** Physical GPU rendering, Android frame rate
and audio, aircraft artwork per faction, specialist individual-hangar loadouts,
resupply/refueling/docking cycles and full dogfight projectile guidance.

## Campaign flight-wing save bridge (not yet live launch authority)

`CampaignFlightWingCodec` now provides a checked `CD-ROSTER-WINGS-1`
envelope containing the unchanged `CampaignFleetCodec` roster plus one
`FlightWingManifest` per commissioned vessel ID. Decoding an older
`CD-ROSTER-1..4` save migrates its **existing paid** fighter count into
physical `ORG:n` and `FTR:<socket>` bays without adding aircraft.
The bridge rejects missing/foreign/duplicate ship ownership, mismatched
paid stock, overfilled or nonexistent bays and altered checksums.
`CampaignFlightWingCodecTest` runs through the existing
`:core:fighterCraftSmoke` functional test.

**Integration boundary:** The game client still calls
`CampaignFleetCodec.encode/decode` and `CampaignFleet.Roster` still uses
`Stores.fighters` as the paid-stock authority. This bridge is a validated
migration/serialization prerequisite, **not** proof that mixed wing types
survive live campaign refit, deployment, launch, recovery or client save.
Those operations must be wired to one authoritative manifest owner before
switching the client save format; do not silently discard paid aircraft
from an occupied bay during a refit.
