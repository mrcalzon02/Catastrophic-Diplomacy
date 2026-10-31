# Field Encyclopedia — in-game reference

The game includes a **native, read-only, fully searchable in-game wiki** on desktop and Android. Open **FIELD WIKI** in any normal menu, use **F1**, or tap the **FIELD WIKI (F1)** battle HUD command. The viewer preserves your previous screen; an active battle is paused during reference reading and resumes at its previous pause/play state.

On a desktop keyboard, type into the wiki to filter, use BACKSPACE to edit, UP/DOWN and PAGE UP/DOWN to scroll the selected article, LEFT/RIGHT to move through search pages, and ESC/F1 to return. On Android tap the search field for the platform text keyboard, use the large category tabs, selectable result rows, and explicit list/content paging controls. Categories include guides, orders, hulls, modules, upgrades, ammunition, ordnance, fighters, manufacturing and factions.

## Live reference authority

`GameWikiCatalog` generates its complete content once from the loaded **production `TechnologyCatalog`**, not manually maintained parallel CSV files. Every actual base component and research upgrade stage gets a distinct article showing its exact ID, faction, subsystem, rank, prices, physical modifiers and tradeoffs. The full catalogue includes 11-faction technology and all nine stage grades.

Every physical `Shipyard.Hull` (including its specialist variants) receives a separate article with its authentic size, role, source-art family, technology unlock tier, mass and volume budget, structure, mobility, and socket counts. Exact `ModuleVersions.Pattern` / Mk I-IV articles explain manufacturing modifiers. Live ammunition profile, missile/torpedo salvo, propulsion and fighter profile metadata each has its own article. All 23 permanent `ShipStandingOrders.SETTINGS` and target/PD/movement enumerations are indexed.

Handwritten guides cover actual campaign ownership and repairs, skirmish/PvP caps, research authorization, shields, guns, signatures, audiovisual feedback, performance, autonomous battle screensaver and faction identities. Those descriptions must not claim unfinished optional narrative campaign objectives are active gameplay.

## Engineering rules

- Read-only UI: searching and selecting encyclopedia records cannot mutate `Battle`, `CampaignProgress`, `CampaignFleet`, `Shipyard.Design` or `CampaignTechnology.State`.
- Article search is re-evaluated on input/category change, not by scanning 13k upgrades every rendered frame. Native rendering only draws the current result page and visible description lines.
- The regular functional release test `:game:wikiSmoke` validates **all live hull variants, components and upgrades**, categories, search filtering, touch regions, paging and no-match behavior. It runs as a dependency of `:game:menuFlowSmoke` on Linux and Windows, not as a decorative image/quality gate.
- The in-game encyclopedia is a reference for existing implemented mechanics. The external campaign story bible remains a writer's narrative authority; speculative story missions and possible ending branches are not listed as already playable.
