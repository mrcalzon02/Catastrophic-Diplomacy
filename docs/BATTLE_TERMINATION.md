# Finite authoritative battle endings (Pass A)

## Status
Implemented in `BattleTermination`, `Battle`, `CampaignEncounter`, the
native results interface and the authoritative LAN server/client. No RNG,
wall clock, sprite size, artificial damage, physics shortcut, or invented
retreat is used to decide a draw.

### Authoritative outcomes
- **Normal victory:** one faction has no remaining active ships; the other
  can hold the field under the existing engagement/withdrawal rules.
- **Mutual destruction:** both factions have no active combatants.
- **Stalemate:** both sides still have active surviving hulls, but neither can
  settle the encounter under the conservative mechanical inactivity rules.
- **Time limit:** a deterministic absolute simulation horizon is reached while
  both sides still have active ships. This is an **unresolved battle**, not a
  free victory for whichever side has higher fleet strength.

These statuses do **not** modify hull health, ammunition, individual deaths,
crew, fuel, retreat locations, fleet IDs, or campaign ship inventories.

## Deterministic limits (30Hz simulation ticks)

| Policy | Threshold | Additional conditions |
|---|---:|---|
| Disarmed mutual stalemate | 2 minutes | At least 90 seconds since last real hull impact, destruction or escape, and **neither active side retains potential offense** |
| Still-armed deadlock | 20 minutes | No hull impact, destruction or real escape for at least 6 minutes |
| Maximum encounter | 60 minutes | Absolute finite limit, regardless of continually fired or inbound ordnance |

Both early-stalemate paths additionally require **no in-flight physical
missile, rocket, ballistic, fighter/drone, rotary/rail round, timed flak,
or pending guided salvo** and **no active ship making a viable physical
withdrawal**. The 60-minute absolute horizon is the only exception.
Temporarily invisible targets and still-approaching slow physical shells
are never evidence, by themselves, that a battle has ended.

Checks for the longer terminal conditions run once every 30 simulation
ticks to avoid adding an O(fleet-size) scan to every combat frame.
`hitCount`, destroyed hulls and genuine rear-boundary escapes reset the
inactivity timer. Active repair and resupply hardware conservatively prevents
the early disarmed shortcut when it might restore offensive capability.
Even so, every battle has the absolute one-hour bound.

## Campaign and presentation

`CampaignEncounter.Advance.DRAW` never advances to a new wave, records a
mission victory, awards credits, or increments research progress.
Existing `CampaignFleet.afterBattle` and the atomic campaign ledger keep
only **real** surviving and destroyed hulls, ammunition and crew losses,
and fleet service consequences. No free repairs or cloned fighters.

Battle HUD, unattended exhibition result and campaign/skirmish after-action
menus identify an unresolved stalemate or time limit explicitly. A headless
LAN match freezes its final simulation frame, sends its valid final snapshot,
and sends `RESULT|STALEMATE|<tick>` or `RESULT|TIME_LIMIT|<tick>` (or actual
victory/destruction statuses). Late spectators receive the same final result
without restarting simulation. The client accepts results only when the
reported tick matches its last verified authoritative snapshot.

## Tests and release discipline

- `:core:battleTerminationSmoke`: policy boundaries; real 1v1 depletion
  with two living ships; stable terminal fingerprint/tick; real campaign
  stalled outcome with no next wave; actual kill is not relabeled a draw.
- `:core:battleArenaSmoke`: fixed 3840x2160 finite world, 200v200 hard cap,
  all supported finite projectile lifetimes. Depends on termination smoke.
- `:server:terminalProtocolSmoke`: actual trusted-LAN TCP sockets for
  stalemate, absolute time limit, normal victory and late spectator frame
  handling; injects only the terminal status because waiting 60 minutes
  would not be a reasonable protocol test.
- Existing `:server:check`, focused point-defense and `:game:compileJava`
  cover the surrounding multiplayer, launch and graphical contract.
- Build, runtime, replay corruption and required LAN failures are functional
  release blockers. Numerical balance and visual presentation are diagnostic.

**Not yet device-certified:** full signed Android/Fire OS release, extended
200v200 physical runtime and desktop screenshots of the new result text.
Never claim those checks were run from headless CI.
