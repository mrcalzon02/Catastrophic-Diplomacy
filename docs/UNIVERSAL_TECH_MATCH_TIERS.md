# ONE TECHNOLOGY TREE: MODE-SPECIFIC AUTHORIZATION AND INCLUSIVE MATCH CEILINGS

Authoritative design correction, 2026-10-29.

**The engineering technology catalog is universal. There are NOT separate campaign, skirmish and PvP technology trees.** Each mode authorizes the same `TECH-<FACTION>-<SUBSYSTEM>-<BRANCH>-<RANK>` identifiers differently:

- **Campaign:** the faction's durable, purchased and mission-completed `CampaignTechnology.State` inventory controls fitting. A license rank is a prerequisite/capacity gate; it does not automatically grant every module research node.
- **Skirmish:** a scenario sets a **maximum** technology tier `0..9` and (where appropriate) hull-class restrictions. Every catalog upgrade at that rank **or lower** is eligible, irrespective of campaign research purchases. Optional scenario bans are exceptions, not a separate research tree.
- **PvP / trusted LAN:** the HOST chooses one shared inclusive match tier (e.g., Tech 3); both players may assemble different ships with any eligible technology from ranks 0,1,2,3. A Tech 6 match does **not** require every ship to be Tech 6 or upgrade its modules to tier 6. A designer's exported file must never override the host's match cap.

## Physical ships and fleets

Each actual fitted `Shipyard.Design` is immutable and retains its own installed hardware and specialized upgrades. `SkirmishFleet` carries one design per deployed ship (1..200), rather than one default template multiplied across the battlefield. Mixed ranks are intentional: a Tech 6 fleet can contain simultaneously equipped Tech 1 interceptors, Tech 3 missile destroyers and Tech 6 flagships. The selected ship in skirmish deployment opens the common shipyard editor; applying changes modifies only that fleet slot.

`SkirmishFleetCodec` exports/imports actual per-vessel designs (`.cdfleet`) WITHOUT binding the designs to a prior match's rule set. A design can be reused in a higher-cap match; the current match always revalidates every ship against its own ceiling before battle creation. The challenge-rule codec and the earlier single-ship `.cdship` format are supported separately; they are not alternate technology catalogs.

Campaign ownership, skirmish selection, and PvP eligibility must never be conflated with constructed ship instances. A faction can know a technology without installing it on every ship. Tech cap is not a per-fleet minimum or an automatic upgrade.

## LAN server entry

Host-controlled mixed-fleet session (files are created under the skirmish deployment's `shipyard/` local storage):

```text
OpenLanBattleServer 127.0.0.1 27817 6 left.cdfleet right.cdfleet
```

The arguments are **bind address, port, maximum tech tier (inclusive), left fleet file, right fleet file**. The authoritative server validates each actual ship using its own chosen ceiling and spawns the complete supplied fleets. A client cannot raise that cap by editing an export. This is an operator-driven trusted-LAN flow; a full graphical PvP fleet submission/ready-check lobby is separate unfinished work, and no internet-facing service or encryption is implied.

## Regression requirements

- Tech 3 accepts stock, Tech 1, Tech 2 and Tech 3 designs, rejects Tech 4–6.
- Tech 6 accepts mixed stock/Tech 1/Tech 3/Tech 6 fleets without mutating any blueprint.
- Save/export/reload preserves independent hardpoints and per-ship technology identities.
- Campaign research inventory is unchanged by skirmish or PvP selection, battle or import.
- Host validates all imported ships **before starting an authoritative battle**, independent of the player's export metadata.
- All meaningful launch/network failures are blocking; stylistic or discretionary test checks remain diagnostic under repository release policy.

## Actual in-game fleet submission (first bounded lobby slice)

The host can wait for two independently submitted fleets instead of preloading files:

```text
OpenLanBattleServer 127.0.0.1 27817 lobby 6
```

Clients connect from **Multiplayer**, choose an existing local `shipyard/*.cdfleet`, use **Check fleet vs host**, then **Submit fleet to host**. The existing explicitly trusted LAN or pinned-TLS transport is reused. The server owns seat/team assignment, checks the entire checksum-protected fitted fleet against its own single inclusive technology policy, and reports acceptance/rejection. The simulation does **not** start until both seats have accepted submissions; disconnect before start invalidates that seat's submission. The initial protocol deliberately limits each upload to **120,000 UTF-8 bytes** (160,000 encoded bytes) in ordered 2,800-character frames; larger exports need a future streamed transfer revision, not silent truncation. This is a working submission path, **not yet** graphical host configuration, ready-up, matchmaking, or physical two-device acceptance.
