# Dumbfire rocket barrage implementation

Status: rocket gameplay and sensing modules have passed the shared functional suite.
Per-hardpoint rocket blueprint persistence and shipyard controls are committed,
with their new cross-platform functional acceptance still requiring readback on
the implementing commit. GPU screenshots are optional diagnostics, not gates.

## Physical hardware and gameplay

Four new WEAPON-mount hardware lines per faction:
- `RKS` small rocket pod: 4-round default, 48 onboard rounds, 11s basic reload
- `RKM` medium rocket rack: 8-round default, 48 rounds, 15s reload
- `RKL` large rocket battery: 8-round default, 32 rounds, 21s reload
- `RKG` siege rocket battery: 16-round default, 32 rounds, 29s reload

All racks support independent 4, 8, or 16-round selection through
`Battle.setShipRocketBarrage(shipId,slot,count,motor)`, while the four
installed modules provide playable default sizes without a hidden setting.
The salvo-count modifier adds a larger reload multiplier for larger barrages;
successive projectiles fire two simulation ticks apart from the *real* module
hardpoint. Rockets use fixed predictive launch bearing with staggered dispersion,
**no post-launch targeting/lock updates**, finite life, swept real hull contact,
high-explosive payload resolution, independently damageable low-health projectile
bodies, and finite on-rack storage.

Two actual propulsion envelopes:
- `BOOST_COAST` (small and large defaults): strong straight-line thrust for
  0.46s, then unpowered inertial coast.
- `SUSTAINED_BURN` (medium and siege defaults): gentle straight-line
  acceleration for 2.4s, then coast.

Neither motor can steer after firing. Target velocity is sampled once at salvo
commit: evasive lateral movement after launch can miss the entire salvo.
High motor signature and large cross section make rockets conspicuous to
laser point defense, point-defense drones and interceptors; small rocket HP
means a successful laser hit generally destroys the incoming body.

## Paid fittings and cheap persistent ammunition

New append-only technology identities: `BASE-<faction>-RKS/RKM/RKL/RKG`
plus separate OUTPUT, EFFICIENCY, RESILIENCE and SPECIALIST ranks I–IX.
Existing component IDs, hulls, blueprints and saved tech unlocks are unchanged.

Each physical body consumes one on-rack round plus 1/2/3/4 **existing kinetic
resupply units** respectively. The campaign already prices these simple stores
at 12 credits per unit, versus 55 credits for a guided missile: per rocket,
the actual persistent replenishment costs are 12/24/36/48 credits. This
reuses campaign resupply accounting and makes unguided rockets cheaper than
seekers without inventing free stores or breaking save format. Both kinetic
guns and rockets compete for the same inexpensive ammunition pool.
The rack is reloaded between sorties but cannot shoot without paid stores.
The magazine and ship power/heat are charged **per launched body**;
queued but interrupted rounds never appear or cost ammunition.

## Existing approved artwork only

Original `effects/projectile_fx/curated/missile_bodies_6.png` atlas cells
2, 5, 1 and 4 respectively provide four actual visible rocket body sizes.
They are size-scaled and motor-state tinted in the *existing* renderer, with
no extra texture allocations per rocket and no invented ship artwork.
Existing authored missile launcher turret and muzzle/impact/intercept
effects are reused. A strictly fallback tiny geometry dot is used only if
the actual atlas and original missile PNG cannot be loaded.

## Release acceptance

`core:rocketBarrageSmoke` tests all four native racks, 4/8/16-round
volley sizes, campaign-priced finite kinetic stock, isolated per-ship
magazines, stored blueprint identity, both acceleration curves, evasive
target dodging, high-visibility laser PD, actual warhead hits, and seeded
replay. Hooked into the existing functional game chain.

### Live controls added

Tap a **friendly** ship in combat to follow/inspect it. If it carries rockets,
a 46-pixel-high row appears beneath the selected-ship status. Choose **NEXT
RACK** (for multiple independently mounted launchers), **4 / 8 / 16** rounds,
or **BOOST/COAST / LONG BURN**. Each setting directly calls the actual
`Battle.setShipRocketBarrage(...)` API for that physical socket. A salvo already
in flight cannot be reprogrammed. The same controls work with mouse and touch.
The HUD shows the selected rack, rocket magazine, paid kinetic stock, reload
time, and a warning when there is insufficient ammunition for the selected
barrage. No enemy weapon rack can be controlled.

`game:rocketHudSmoke` is a functional menu test wired into
`game:menuFlowSmoke`, with exact button hit targets and evidence that
different physical racks retain independent settings.

### Blueprint persistence and pre-battle controls (CD-SHIP-6)

In the Shipyard select a physical rocket WEAPON hardpoint (RKS/RKM/RKL/RKG),
then open **ORDNANCE** (desktop `G` or the existing touch tab). The first two
74px-high controls select the **4/8/16** volley count and **BOOST_COAST** or
**SUSTAINED_BURN** motor. Tracking and warhead are explicitly inertial and
cheap kinetic stock: neither becomes an independently selectable seeker.
Each rocket socket is always programmed independently, never through the
guided family-wide launch settings. SAVE or APPLY carries the settings into
future ship spawns; changes made on the *live battle HUD* remain tactical
and do not silently rewrite a previously saved blueprint.

`Shipyard.Design.withRocketBarrage(slot,count,motor)` stores only validated
per-socket fire settings. `Shipyard.rocketRacks(...)` initializes every
fresh per-ship physical rack from the immutable design; ammunition and reload
remain mutable per-ship runtime state. Old `CD-SHIP-1` through `CD-SHIP-5`
blueprints keep their original default configurations unchanged. A nondefault
configuration uses `CD-SHIP-6` with one `R|slot|count|motor` record per
programmed hardpoint, beside optional older `L|...` guided launcher rows.
Invalid count, motor, duplicate socket, stale rocket hardware or crafted
non-rocket settings are rejected. A replaced rocket hardpoint drops only its
abandoned program. Both core rocket smoke and the existing touch-sized
shipyard acceptance test now exercise save/reload and controls.

A recently fired short-booster rocket remains thermally conspicuous while
coasting (emission factor 1.20, compared with 2.50 under active boost); it
does not gain the silent-ghost signature of a purpose-built guided missile.

Pending objective verification: latest Java 17 build, Android launch and
live battles, rocket smoke **and** HUD assertions, and 200v200 endurance
(including real PD/rocket projectile saturation). Screenshot review is
diagnostic only, not a release gate. Inherited guidance/deception and damage-
control completion gates remain in force.
