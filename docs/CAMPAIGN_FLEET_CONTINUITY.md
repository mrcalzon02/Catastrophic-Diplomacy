# Campaign fleet continuity — implementation and qualification

This document tracks the active ship-ownership implementation in the authoritative
`main` source tree. Treat feature existence and CI/release qualification as different
claims. The stable source of truth is the committed code and GitHub Actions evidence.

## Ship identity and owned rosters

- `CampaignFleet.Roster` is the single owned registry. `Vessel.id` is immutable,
  globally distinct within the current campaign and never recycled after a loss;
  `nextId` advances when a new hull is purchased.
- `Vessel.deployed` selects the active sortie group. All other owned hulls are
  available reserves; changing selection never buys or clones the ship.
- `Roster.afterBattle` preserves battle survivors and physical escapees, including
  hull/armor/shield tile condition, subsystem damage, crew, fitted ammunition, fuel
  and remaining carried craft. A destroyed hull is removed from ownership. Escaped
  and still-withdrawing hulls move to available reserve.
- Captain escort/defend relationships persist by vessel ID and translate to active
  battle indices only when creating a sortie; a reserve hull must not redirect
  a standing order to the wrong commissioned ship.
- `CampaignFleetCodec` uses a versioned roster header, SHA-256 corruption check,
  fitted-capacity validation and a recognized legacy roster format. The UI keeps
  a last-valid backup and does not issue free replacements if both copies fail.

## Shipyard and dock economy

- Purchased hulls arrive as new reserve IDs with empty crew, propulsion fuel,
  ammunition and carried craft. The hull itself costs campaign credits.
- Real services cover physical hull/armor/shield/subsystem repair, crew recruitment,
  three types of fitted munitions, propulsion fuel, and fighters/drones.
- The Service All operation computes a single quote using `Roster.costAll`, debits
  the existing campaign wallet once if affordable, then applies `Roster.serviceAll`.
  Each component service remains individually available; a fully stocked ship has
  zero service cost.
- `GameMenuPanel` offers actual touch targets for all ten compact right-pane
  controls, including a combined-service action, not static labels.

## Commissioned ship identities

- Existing name generators `NAVAL`, `MYTHIC`, `CELESTIAL`,
  `INDUSTRIAL`, and `ALIEN` retain historical enum positions and
  deterministic output.
- Additional philosophies are `IMPERIAL`, `FRONTIER`, `SCIENTIFIC`,
  `CEREMONIAL`, `ORGANIC`, `SYNTHETIC`, and `DESIGNATION`.
  Different naming structures distinguish lineages, reconnaissance craft,
  astronomical research ships, ceremonial fleets, biological hulls, machine
  platforms and serial-numbered military vessels.
- Ship IDs ensure unique generated serials. Manual renaming uses native
  text input on desktop and Android and rejects blank, duplicate,
  overlong or control-character-containing names.

## Functional checks

- `CampaignFleetTest`: separate rosters; preservation of escaped but damaged
  hulls; destroyed-hull replacement; genuine shortages and paid restoration;
  combined-service quotes; unaffordable full service; deterministic naming
  styles; manual naming validation; corruption-resistant codec.
- `GameMenuFlowTest`: dock actions and nonoverlapping manual/generated-name
  controls; usable Service All control; campaign/map/deployment routing.
- Additional existing suites cover the 200v200 fleet and shared desktop/Android
  battle mechanics. Platform builds and signed Android artifacts are gated by
  GitHub Actions; do not claim release qualification before the whole gate passes.

## Remaining milestones — not yet represented as complete

1. **Atomic wallet/fleet durability:** wallet and fleet still live in separate
   files, so power loss between the two writes can desynchronize a purchase or
   service. Implement crash-safe composite transactions/recovery before claiming
   guaranteed exactly-once persistence after power failure.
2. **Detailed after-action ledger:** show individual named survivors/escapees/
   withdrawing hulls/losses and spent ammunition, fuel, fighters and crew,
   with pagination for large fleets.
3. **Contextual comms completeness:** current `CrewChatter` provides typed
   attack, damage, retreat, escape, cover and fighter launch/attack messages,
   with per-ship cooldowns. Broader actual shield-collapse, subsystem, casualty,
   low-supply, allied emergency and mission-outcome event wiring remains.
4. **Ship readiness and individually persisted positioning:** audit launch rules
   for empty crew/fuel and positional persistence across missions; maintain
   explicit deliberate risk-taking rather than silently restoring stores.
5. **Android emulator acceptance:** the prior signed-release workflow built and
   verified the signed APK, but the in-emulator 200v200 proof timed out. Diagnose
   from retained logs/telemetry; do not disable functional gating or falsely
   advertise a completed prerelease. Graphics/visual checks remain separate
   from functional acceptance as specified by the project.

## Validation policy

Follow INTENT -> EXECUTE -> OBSERVE -> VERIFY -> CLAIM. Inspect `main` and actual
GitHub Actions run statuses before describing a feature as verified. Keep existing
Android/Fire OS/Windows/Linux distribution paths and retained signing identity.
Do not bypass the emulator acceptance gate; correct the underlying failure.
