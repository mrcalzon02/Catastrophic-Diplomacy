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

## Diagnostic coverage

`gradle --no-daemon :core:sensorNetworkSmoke` checks all eleven factions,
six standard modules, 24 alternatives, independent research stage IX,
manufacturing budget validity, save identities, technology caps, primitive
versus advanced performance, sensor-only amplification, contact-relay range,
transmitter power loss and parallel target capacity.

The existing `sensingModulesSmoke` task now depends on this focused test.
This is an objective functional test: **visual clarity, artwork and balance
polish remain separate nonblocking diagnostics**.

Further balance trials should compare basic versus primitive, standard,
compact, specialist and advanced fittings for 1v1 to 200v200 battles,
with tracked-contact counts, stale-record ages, links serviced, lock reacquisition
latency, CPU cost, and number of independently assigned targets recorded.
