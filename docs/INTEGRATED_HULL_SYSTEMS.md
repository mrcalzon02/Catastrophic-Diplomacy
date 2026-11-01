# Integrated chassis systems and modular specializations

## Core rule (effective from 2026-10-31)

**Every ship is a functioning vessel with no installed modules.** The hull
contains a permanently integrated reactor, reaction-control/propulsion, cooling,
crew berths, breathable life support, finite reaction mass and physical stock
magazines. Its basic sensors, hull structure, armor, and class-dependent native
shielding are also integrated. A zero-module ship can move in combat, consume
fuel, carry crew, survive damage and receive non-weapon orders, but **cannot fire
a weapon that has not been mounted**.

The source of truth is `HullBaseline.core(hull)`, alongside
`HullBaseline.forHull(faction,hull)`. The baseline grows with displacement and
hull generation; specialist classes vary by their real chassis attributes.
Intrinsic fighter decks remain independently defined for dedicated carriers.

## What modules do

Modules **augment** the hull, rather than pay a mandatory occupancy tax for
reactors, thrusters, radiators, oxygen, or personnel. A valid bare ship has
nonzero integrated power output, cooling, speed and crew accommodations.

Module mass, reactor demand, heat generation, crew demand and capacity remain
real fitting constraints. More power and cooling must be installed to support
extreme specializations. Shield generator modules add both shield capacity and
powered recharge throughput; recharge consumes real ship energy during combat
and still respects disruption delays and thermal safety.

Hardware sockets retain stable serialized IDs, but support these conversions:

- Weapon socket: actual gun/launcher **or** reactor, radiator, thruster, armor,
  shield generator or structural integrity field.
- Defense socket: defense plating/field **or** reactor, radiator or thruster.
- Support socket: support machinery **or** reactor, radiator, thruster, armor,
  shield generator, structural integrity field, crew quarters or life support.
- System socket: systems machinery **or** armor, shield generator or structural
  integrity field.
- Habitat socket: remains dedicated to installed extra accommodation or
  breathable atmosphere, on top of the free intrinsic capacity.

`Shipyard.choices(catalog,faction,hull,slot)` provides the per-socket options;
the shipyard paginates them in six visible rows plus a seventh MORE action.
`Shipyard.bareHull()` and the two-tap **STRIP HULL** control create an actual
zero-module blueprint. Ordinary factory starters are retained as preinstalled
reference loadouts; existing blueprints, socket indices and variant counts
are **not renumbered or deleted**.

## Two concrete dreadnought designs

*Armored Fist* gives up most weapon sockets for armor, retains three kinetic
guns, and uses several dedicated reactors and cooling plants to support armor
mass and system draw. It survives primarily through structural plating.

*Shield Spire* trades most weapons for shield generators, retains two
low-consumption lasers, adds reactors and radiators, and uses support sockets
for extra crew and life support. It survives through its enormous shield
capacity and significantly greater power-paid regenerative throughput.
Neither design receives uncosted hardware, phantom weapons, or a magical
reactor/cooling bypass.

Both are constructed and tested as real `Shipyard.Design` instances by
`ShipyardTest`, including actual battle-spawn shield recharge.

## Campaign economy

A newly purchased hull now includes its **basic crew, native fuel and native
ammunition inventory in the purchase quotation**. They are not free:
`CampaignFleet.initialProvisionCost(hull)` is added once to the procurement
quote. Refit calculations strip that one-time provisioning cost out before
pricing the changed machinery. New fighter complements, drone complements,
expanded magazines, casualty replacement, and post-battle fuel/rearm remain
separately paid.

Removing an unrelated launcher does not delete purchased munitions stored in
the permanent hull magazines. A ship with no launcher **cannot fire those
rounds**. Changing ammunition formulation still requires compatible stores.

## Compatibility and validation

- Historical variants retain old hull ordinals, installed socket indices,
  physical maximum hardpoint counts and existing encoded ship blueprints.
- Stock factory starters remain as templates; bare and specialized hulls can
  be authored without forcing mandatory system modules.
- Functional regression targets include all faction/chassis bare fits, full
  campaign commissioning charges, crew and fuel persistence, shield Spire vs.
  Armored Fist active regeneration, brownout and thermal rejections, and
  per-slot type legality.
- Asset/art and balance diagnostics remain nonblocking; only objective broken
  functionality can block builds.
- A future optional small-craft slot-count migration must preserve existing
  commissioned ships and saved module IDs rather than silently delete their
  installed equipment. Fewer than nine sockets on legacy interceptors is **not**
  claimed as part of this revision.
