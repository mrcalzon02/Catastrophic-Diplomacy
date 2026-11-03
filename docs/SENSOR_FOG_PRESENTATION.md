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

The contact layer evaluates **actual, local direct detections** using the
same radar-cross-section, thermal damping, visual cloak, X-ray armor
attenuation, gravitic mass, weather, reactor and sensor condition factors
as the combat acquisition model.

| Channel | Visual | Information exposed |
| --- | --- | --- |
| Optical | Real ship hull and its surface effects | True visual identification |
| Radar | Cyan outline traced from the true class/faction hull contour | Profile return, no actual hull textures |
| Infrared | Orange/yellow warm point | Thermal location, not class artwork |
| X-ray | Violet diamond | Penetrating instrument detection |
| Gravitic | Green concentric rings | Mass anomaly location |
| Holographic ECM | Extra radar hull traces at real deterministic seeker ghost positions | False echoes, no extra damageable ships |

Overlapping channels combine glyphs and a compact RAD/IR/XR/GRAV label when
the zoom and contact density make labels readable. An unseen hull cannot be
opened in the full ship-inspection panel merely by clicking a thermal or
radar contact.

The tactical UI collects sightings from the ships owned by the player as a
command **presentation** only. It does not extend the combat AI's existing
limited contact relay, alter ship-to-ship communications, or implement the
planned sensor sharing, contact processing, target unification, or command
capacity modules. Those remain separate development work.

## Validation and current limits

The pure-core `SensorPresentationTest` checks smooth falloff, independent
channel signatures, radar stealth, thermal suppression, power loss, optical
cloaking, X-ray and gravitic return classification. `SensingModulesTest`
continues to exercise physical module fitting, research, persistence and
original combat detection.

Graphic results should be reviewed visually in a running desktop/Android
battle; visual quality findings are diagnostics only, never release blockers.
Live network spectator snapshots currently use their own separate drawing
path and **do not yet apply server-authorized per-player fog**. A multiplayer
implementation must avoid sending hidden hostile states to unauthorized
clients, not merely paint fog over an omniscient network snapshot.
