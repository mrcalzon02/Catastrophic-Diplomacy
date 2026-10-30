# Five additional alien civilizations — approved fleet-art integration

**Source:** five newly approved transparent 1672×941 RGBA fleet sheets, ten top-down craft each, 50 actual distinct ships total. The original transparent images have not been regenerated or recolored.

## New factions (selectable in the game)

| New playable civilization | Visual language | Interim gameplay/technology archetype |
| --- | --- | --- |
| VAERITH_PRISM — Vaerith Prism Dominion | Purple crystal armor, silver blades, luminous sapphire cores | Draxian (shield / energy) |
| ZELTHAR_LUMINARI — Zelthar Luminari Accord | Teal crescent rings, silver-white resonators, cyan propulsion | Uzipt (sensors / precision) |
| AUREATE_SYNOD — Aureate Cathedral Synod | Ivory-gold cathedral hulls and vaulted blue windows | Mandrally (repair / survivability) |
| NYX_BIOLITH — Nyx Biolith Enclave | Violet biological vessels, psychic circular membranes | Carnelian (durable hull) |
| UMBRA_FOUNDRY — Umbra Foundry Compact | Blocky armored purple-charcoal industrial ships | Zippledrow (fast skirmish — provisional) |

Each has an independent enum identity, name, color, selectable shipyard fitting, campaign-save identity, combat roster and 10 unique hull-art paths. For now the original six mechanical technology catalogs are shared via deliberate archetype aliases; **five completely new balance trees, faction-exclusive components, voice and lore assets are not represented as finished**.

### Art layout and source provenance

A separate lossless extraction produced `Catastrophic_Diplomacy_50_Alien_Ship_Sprites_20261025.zip`, containing exactly 50 cropped alpha PNGs, five source-sheet digests and a `faction_registry.json` with **each sprite's own SHA-256**. The extraction uses alpha-component separation rather than rigid sprite rectangles, so adjacent translucent engine glow does not cut through the physical hull. A 5×10 checkerboard contact sheet has been visually inspected.

Repository installation:
```bash
python3 tools/install_alien_fleets.py Catastrophic_Diplomacy_50_Alien_Ship_Sprites_20261025.zip .
git add assets/ships/factions/
git commit -m "assets: ship 50 approved alien hull sprites for five new playable civilizations"
```

**Current binary status (2026-10-29):** All 50 approved individual PNG binaries and their checksum index are now committed on `main` under `assets/ships/factions/`. The former "not yet pushed" status is obsolete. The five original non-Terran faction archetypes also resolve these existing approved visual families: Draxian → Vaerith, Uzipt → Zelthar, Mandrally → Aureate, Carnelian → Nyx and Zippledrow → Umbra. The five new playable faction identities remain distinct. Runtime texture caching shares each PNG across legacy and new factions; no re-tinting or duplicated GPU texture upload is required. The source checker is `tools/verify_faction_ship_assets.py`; the desktop JAR / Android APK binary check is `tools/verify_runtime_ship_art.py`. This is code/packaging integration, not yet a visually accepted device release.

**Terran exception:** The original ten approved Terran PNGs are preserved in `Catastrophic_Diplomacy_Runtime_Art_Stage1.zip` in the project Library but are still missing from `assets/ships/terran/` on GitHub; do not silently use alien images for Terran. A restored original-source import is still required to eliminate the Terran fallback. The source archive also preserves 35 approved beam frames missing from GitHub.

With the committed binary art the existing `TerranPlaceholderSprites` (now multirace-capable despite historical class name) loads `ships/factions/{id}/{hull}.png`. Shipyard previews, both instanced GPU and GL fallback battle paths skip geometry for successfully loaded sprites. Ships face +Y within their file; the renderer rotates by ship heading. Damage masks and current shield overlay use the same individual sprite for their original pixels.

The ten illustrated ship classes map, in order, to interceptor, corvette, missile corvette, frigate, destroyer, light cruiser, cruiser, heavy cruiser, battlecruiser and carrier. The game's **eleventh** `DREADNOUGHT` class intentionally reuses the carrier sprite for now; this is a flagged class-art shortage, not a silently duplicated eleventh ship.

### Acceptance gates

- `:core:alienFactionSmoke` validates all 11 total faction identities, all 50 new sprite identities, the dreadnought fallback, legal shipyard fittings, saved blueprint roundtrips and real skirmish combat.
- `:core:massBattleCampaign` uses all 11 playable factions across the 200v200 mixed-weapon fixture instead of cycling only six.
- Binary acceptance: the importer must verify every RGBA PNG, per-file digest, source hash, file path, and refuse to overwrite artist-edited sprites; manually inspect `QA_contact_sheet.jpg`.
- Device acceptance **still required**: run on Android and desktop at zoomed-out 200v200, verify textures are actually present in the APK, correct on-screen scale, rotation, alpha glow, preview mapping, memory pressure, and readable tiny fighter sprites.
- Do not release an updated signed APK with the five new *art factions* until PNGs are genuinely packaged and reviewed in an actual battle.

This is **runtime source integration under automated tests**, not yet a completed physically verified release. Original Terran artwork and source-backed weapon beam frames remain pending binary import.
