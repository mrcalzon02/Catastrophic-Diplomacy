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

## Passive optical fighter seeker integration (November 2026)

Fitted FTR tier 3 unlocks a narrow passive optical interceptor head; tier 7
can choose a shorter-range wide-angle camera. Selection is a pre-engagement
ship order `Battle.configureFighterOptical(shipId,true)`, exclusive with the
older tier-five active radar choice and the default infrared seeker. The
existing fighter magazine, cartridge counts, physical interceptor body,
fitting costs and blueprint serialization are preserved.

The optical missile receives only historical visible-light contrast and motion
from the fighter's recorded one-way photon emission. Optical contrast is a
separate timestamped signature, NOT infrared heat or a new radar ping. Actual
receiver worldline crossings, field of view and finite scan schedules apply.
Visible-light targeting has deterministic finite error that depends on
installed angular optics, signal quality, photon age and paid guidance.

Radar chaff cannot emit a counterfeit visual aircraft. Bright flares may cause
temporarily lost camera acquisition only when their own historical photons
actually reach the camera within the aircraft's angular image. Even then the
seeker receives no invented decoy coordinates, proximity fuzes or free hit
modifiers. Physical swept impact geometry is unchanged.

Focused objective test: `gradle :core:fighterOpticalSeekerSmoke`. It is also a
dependency of `:core:sensorNetworkSmoke` and tests real fitted interceptor
engagements, research/selection exclusivity, band isolation, future emissions,
old light-front expiry, glare angular separation and deterministic replay.
Optical seeker choice now persists in a backward-compatible, append-only
`CD-SHIP-7` blueprint header when a researched non-default head is selected.
Existing CD-SHIP-1..6 data retains its exact infrared default. FTR specialist
rank 3 permits OPTICAL, rank 5 permits RADAR, with only one fitted seeker
family per interceptor. A hangar removed or downgraded below the selected
head's research requirement is rejected rather than silently granting gear.

The FTR shipyard control cycles researched options by touch/Y; the saved
design automatically configures a physically launched fighter missile.
Changing other approved components, exporting fleets or importing a saved
ship preserves the selection. The existing campaign refit/purchase authority
remains unchanged; no unpaid ships or upgrade hardware is created.
Hybrid/remote handoff remains a separate future integration stage.

## Environmental optical obscuration

Passive optical missiles now sample the actual `SpaceWeather.Preset` of
their encounter when photons reach their camera. Nebula optical depth
attenuates historical visible contrast as a function of the photon path
length and severity; weak distant images can fall below the sensor's
threshold while near targets remain visible. Solar bursts reduce passive
optical contrast at their exact arrival ticks and also affect photons
historically emitted during the burst. The existing 30 Hz emission/receipt
timing and light-front intersection still determine whether any optical
measurement is possible. Weather never creates spoofed coordinates.

Infrared and radar retain independent spectra; the new environmental
contrast factor is not applied to their receiver. A physically arriving
flare uses the same visible path attenuation as the observed fighter, so
weather cannot grant a decoy free, unattenuated optical light. The
`:core:fighterOpticalSeekerSmoke` verifies static/dynamic visibility,
pre-arrival isolation, normal IR under solar glare, and weathered combat
replay. This stage does not claim asteroid-body line-of-sight occlusion,
which would require actual physical occluding geometry.

## Very-short-range fighter-wing sensor exchange

Every autonomous fighter can broadcast at most one **locally detected**
fighter or ship contact per simulation tick. A nearby same-team wingman
receives that immutable observation after **one full simulation tick** only
if the recorded transmitter was within **60 world units** of the receiving
aircraft. The direct radio bubble is far shorter than even the smallest
fighter's onboard sensor reach. Pilots from separate carrier wings may exchange
their actual observations only while flying together inside that bubble.

Packets carry the original target identity, observed coordinates, velocity,
source aircraft identity/position and emission tick. They cannot be forwarded
through a second aircraft, reused without a new local sighting, transferred to
the fleet sensor network, or delivered to enemy wings. Each receiver selects
one nearest valid report, prioritizing hostile fighter sightings; a cell-based
spatial index checks only the immediate nine 60-unit neighboring cells and
keeps fleet-scale cost bounded. No hardware is silently added to missile bodies.

A received contact can **cue fighter movement and local reacquisition**, but
does not set `targetCraftId`, authorize cannon fire or missile launches,
install a target lock, or reveal a hostile's current position. The target's
own sensors still determine firing authority and an interceptor missile still
needs its autonomous infrared, optical or radar measurements. Reports are
replaced each tick and cannot become permanent ghost contacts.
`:core:fighterWingLinkSmoke` verifies strict range boundaries, radio latency,
team isolation, sender exclusion, no retransmission, and real aircraft in a
seeded battle. This is distinct from long-range capital-ship network modules.
