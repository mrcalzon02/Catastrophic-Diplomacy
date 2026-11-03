# Tactical fog and multispectral contact display

The interactive local battle now uses a **render-only** optical visibility
field. Simulation, hit points, ammunition, target acquisition and point-defense
balance remain authoritative and unchanged. This is not a new fleet-datalink
module or a grant of omniscient target locks.

## Optical field

- The player team's surviving ships contribute independent optical circles.
- The basic optical distance is 390 world units, or 310 in nebula weather,
  consistent with the existing combat optical model's baseline.
- A deterministic smoothstep blends a readable core at 84% of that radius
  to completely obscured space at 114%, with no hard black circular border.
- A 320x180 alpha mask is smoothly filtered over the 1280x720 logical battle
  viewport; it updates at most every six simulation ticks unless the camera
  moves or changes zoom.
- True optically identified large hulls receive a local feathered reveal when
  their silhouette would extend beyond the generic visibility radius.
- Enemy hull sprites, source-art damage/shields, turrets and shield overlays are
  not drawn unless actual allied *direct optical* contact exists. Friendly
  hulls are always visible to their owner.
- Optical camouflage applies its existing power, ECM condition and target
  signature rules. Rendering does not secretly disable a cloak to reveal art.
- Screensaver exhibition and forensic armor-screenshot modes deliberately
  remain fully visible.

## Sensor-only contacts

The command contact layer reads `Battle.tacticalPicture(team)` from the
same read-only local/relay/retained sensor route used by actual physical
acquisition, rather than directly scanning every owned ship in parallel.
It evaluates radar cross-section, thermal damping, visual cloak, X-ray armor,
gravitic mass, weather, reactor and sensor condition through the same
independent channel thresholds. The first functioning FCM-equipped command
ship takes priority as the viewer; otherwise the first living owned ship
is the display anchor. A missing SHR/LNK route is never a free global feed.

| Channel | Visual | Information exposed |
| --- | --- | --- |
| Optical | Real ship hull and its surface effects | True visual identification |
| Radar | Cyan instrument ring; identified original hull outline only after sufficient live classification confidence | No actual hull textures, no free low-confidence identity |
| Infrared | Orange/yellow warm point | Thermal location, not class artwork |
| X-ray | Violet diamond | Penetrating instrument detection |
| Gravitic | Green concentric rings | Mass anomaly location |
| Holographic ECM | Extra radar hull traces at real deterministic seeker ghost positions | False echoes, no extra damageable ships |

| SHR / legacy live relay | Instrument-only VIS/RAD/IR/XR/GRAV + LINK | Live observation from a physically reachable source, but not local optical hull visibility |
| TRK / LNK memory | Last-fix crosshair with age-dependent uncertainty | Recorded position/time only, zero live channels and zero new weapon locks |

The command screen must never draw a stale fix at a target's currently
hidden physical position or let relayed optical returns reveal full original
ship artwork. The fog mask and contact markers use the same scanned tick.

Overlapping channels combine glyphs and a compact RAD/IR/XR/GRAV label when
the zoom and contact density make labels readable. An unseen hull cannot be
opened in the full ship-inspection panel merely by clicking a thermal or
radar contact.

The friendly optical field is a render-only local line-of-sight
presentation. The sensor command overlay is distinct: it receives just
locally sensed, actually linked, or finitely recorded contacts of its viewer.
The combat simulation continues to independently enforce fitted sensor
sharing, resolution, tracking, track relay, fleet command and target
capacity modules. Neither renderer may change auto-battler orders, grant
free acquisitions or add weapon locks.

## Validation and current limits

The pure-core `SensorPresentationTest` checks smooth falloff, independent
channel signatures, radar stealth, thermal suppression, power loss, optical
cloaking, X-ray and gravitic return classification. `SensingModulesTest`
continues to exercise physical module fitting, research, persistence and
original combat detection. `SensorTacticalPictureTest` adds actual live SHR,
historical TRK/LNK and FCM command-viewer routing, source-loss behavior and
the invariant that UI reads cannot modify battle state. All are in existing
focused Gradle tests; no new full-package workflow is required.

Graphic results should be reviewed visually in a running desktop/Android
battle; visual quality findings are diagnostics only, never release blockers.

### Auto-battler visibility policy
Players configure fleet orders, then run and observe the autonomous battle.
Fog of war is a **visual presentation mechanic**, not an information-security
boundary. Drawing a genuinely obscuring, softly blended fog layer over the
complete simulation is sufficient. Client packets and spectator snapshots
do **not** require server-side hostile-state concealment, filtered enemy
serialization, or privacy-oriented per-player state redaction. Do not add
packet concealment as a prerequisite to shipping this visual effect.

The intended experience is limited visual awareness, useful differentiated
sensor signatures, and fog that responds to the fleet's functioning detection
hardware. Any network spectator view should use appropriate visual fog
presentation when desired; its underlying packets may retain full state.
The combat simulation continues to enforce real acquisition, range, contact
sharing, weapon locks, and ammunition rules independently of rendering.
