# Fleet sensor network: six fitted information systems

Implemented on shared Java 17 combat, native shipyard and all eleven faction
technology catalogs. This is **not** a single percentage-based sensor upgrade.
Each family owns a different physical processing or distribution capability,
has its own socket and paid research tree, and remains subject to reactor,
heat, mass, industry, crew, combat damage and license rules.

## Technology and availability

Each of six original modules has **five hardware generations**:

| Generation | Required technology tier | Tradeoff |
| --- | ---: | --- |
| Primitive | 0 | Cheap, power-light analog hardware; substantially reduced performance |
| Standard | 1 | Baseline native module |
| Compact | 2 | Space-saving, relatively fragile processor with reduced capability |
| Specialist | 4 | Powerful focused unit with noticeably higher power and heat demand |
| Advanced | 6 | High-output expensive equipment; additional weight and thermal load |

Every one of the six standard families and 24 hardware alternatives is fitted
under its own physical 3-character ID for each of eleven native factions.
Four exclusive OUTPUT / EFFICIENCY / RESILIENCE / SPECIALIST branches
independently upgrade each item through stages I-IX.
Existing ship IDs, weapon/module slots, save formats, nine prior sensing
families, and primitive/higher sensing variants remain untouched.

| Family | Function | What physically improves |
| --- | --- | --- |
| SHR — Sensor Sharing Transceiver | Broadcast *current real detections* | Local relay radius, finite contact broadcast capacity |
| RES — Sensor Contact Resolver | Correlate/resolve real sensor returns | Radar, infrared, X-ray and gravitic sensitivity and target assessment; **no fabricated channel**, optical remains physical |
| TRK — Sensor Tracking Computer | Retain observations across interruptions | Bounded number of records, last-known fix lifetime, estimation precision; stale tracks **cannot authorize shots** |
| LNK — Tactical Track Datalink | Transmit timed track history | Limited broadcast count and inter-ship range; both sides require powered, functioning LNK hardware, source requires TRK records |
| FCM — Fleet Command Fusion Center | Allocate tactical correlation and fleet assistance | Powered local command radius, number of assisted allies, error correction, cooperative engagement weighting |
| CAP — Target Capacity Processor | Parallel fire-control scheduling | Number of independent guided-launcher assignments and usable precision CIWS tracking channels; does not create guns or ammunition |

The `Shipyard.Network` record exposes each capability independently.
Fitted production values are calibrated from real manufactured component
effect and research modifiers, then bounded to protect 200v200 combat CPU.

## Actual contact physics

Radar is affected by radar cross-section; IR by thermal signature and working
heat dampers; X-ray by armor attenuation; gravitic by hull mass. All non-optical
channels require operating sensor electronics, sufficient power, available
crew, and environmental attenuation. RES and FCM improve the *quality* of
available signals. They never supply IR, X-ray, gravitic or radar hardware
that does not exist on the ship.

An unfitted hull still has its original close optical and baseline radar
sensing. Its original limited formation-radio behavior remains unchanged.
SHR provides additional bounded contact broadcasts; a powered LNK receiver
can extend the range of supported contact distribution. TRK records store
the last actually observed position, velocity and tick rather than repeatedly
reading hidden enemy positions. LNK's exported histories are limited to its
most recently recorded contacts. Track data may guide search/pursuit when
direct sight is lost, but **cannot grant a weapon lock on a stale contact**.

Command cores operate only when powered, sufficiently staffed and in working
sensor/command condition. Their contribution is local and limited by the
number of ships they can service. Target-capacity hardware permits multiple
fitted guided racks to select different *detected* hostile vessels and
increases real per-mount precision point-defense concurrency. No free missile,
gun, salvo or PD shot is ever created.

## Read-only command picture and sensor provenance (November 2026)

`Battle.tacticalPicture(team,viewerId)` now reports the actual physical
observation route of **one living receiving ship**. A default
`tacticalPicture(team)` chooses the first operational FCM-equipped command
ship by bounded command capacity, falling back to the first surviving
friendly vessel. It does **not** grant every hull a free sensor network.

Each `TacticalContact` carries source ship ID, true observed channel
flags, measured position/velocity, observation tick, derived
classification confidence, and distinct `relayed`/`stale` flags.
The live route is shared with the combat target-acquisition algorithm,
but its read-only query cannot mutate track memory, consume ammunition
or change targeting decisions.

- **Direct:** the receiving hull itself observes the current target
  through a genuinely functioning optical/radar/IR/X-ray/gravitic channel
- **Live relay:** the receiving hull gets a currently observed contact
  over the exact limited legacy formation-radio or equipped SHR route
  already used for weapon acquisition
- **Historical:** a TRK record from the receiving ship, or at most the
  sender's finite freshest LNK records across a valid powered two-ended
  link, at the **last measured coordinates and tick**; zero live channels
  and never a new weapon lock
- **Power/circuit damage:** a failed transmitter, receiver, local sensor
  processor, missing LNK or expired TRK memory stops those reports
- **FCM:** selects a functioning command display anchor and improves
  bounded classification/correlation within the existing local command
  network; no unlimited fleet-wide source visibility

The graphical `SensorContactOverlay` reads this core report rather than
walking all allied direct scanners independently. A remotely relayed
optical report displays a `VIS ... LINK` instrument mark, not a
revealed physical hull sprite. Retained memory renders a growing
uncertainty circle centered on the **old** fix, never at hidden current
target coordinates. A low-confidence live radar mark likewise cannot
display a known enemy hull outline. The fog mask and contacts refresh
against the same sampled display tick.

The user's separate friendly optical field remains a rendering policy,
not a change in which physical enemy target a ship may shoot.
None of this requires secret server-side entity packets or filtered
spectator state.

**Remaining deeper work:** truly reconciled multi-source confidence,
source covariance and fleet-wide FCM capacity scheduling across
overlapping contact networks. Existing FCM currently provides a bounded,
powered local command-assist and cooperative target weighting, rather
than a complete independent multi-sensor state estimator.

## Diagnostic coverage

`gradle --no-daemon :core:sensorNetworkSmoke` checks all eleven factions,
six standard modules, 24 alternatives, independent research stage IX,
manufacturing budget validity, save identities, technology caps, primitive
versus advanced performance, sensor-only amplification, contact-relay range,
transmitter power loss and parallel target capacity.

The new `SensorTacticalPictureTest` also runs as a dependency of
`sensorNetworkSmoke`, exercising real SHR links, TRK/LNK stale fixes,
power loss, no phantom local sight, nonmutating tactical reads and FCM
command-anchor selection.

The existing `sensingModulesSmoke` task now depends on this focused test.
This is an objective functional test: **visual clarity, artwork and balance
polish remain separate nonblocking diagnostics**.

Further balance trials should compare basic versus primitive, standard,
compact, specialist and advanced fittings for 1v1 to 200v200 battles,
with tracked-contact counts, stale-record ages, links serviced, lock reacquisition
latency, CPU cost, and number of independently assigned targets recorded.
