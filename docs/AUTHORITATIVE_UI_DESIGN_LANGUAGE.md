# Catastrophic Diplomacy — authoritative interface and control design language

**Direction:** GRITTY MILITARY / INDUSTRIAL FLEET COMMAND CONSOLE  
**Approved:** 2026-10-27, by explicit user selection following the October 27 interface concept rounds  
**Status:** Authoritative visual standard for all future first-party menus, buttons, HUDs, desktop application icons, settings, tactical orders, campaign screens, and shared Android/Fire OS interfaces. This document governs *design choices*; it does not imply every pictured feature exists.

## Authority and precedence

The approved aesthetic is a **believable, heavily used naval command terminal in space**: scarred gunmetal, dark recessed displays, bolted frames, precision instrument markings, amber operations lighting, restrained cold-blue targeting telemetry, and occasional red emergency signals. The goal is gritty and technical without reducing the game's readability or pretending that an illustrated mock-up is functioning software.

This direction **supersedes earlier flat-blue placeholder menu styling and alternative cinematic/mobile/PSX theme explorations for interface chrome**. It does **not** revoke existing approved ship sprites, hull silhouettes, battle effects, the 24 audited tactical icons, or their provenance rules. Ship art can have faction-specific visual identities; the *control system* is standardized.

The source concept studies were generated and reviewed in the conversation on 2026-10-27. Key visual anchors, identified by their original generation IDs for provenance:

| Concept reference | What to take from it |
| --- | --- |
| `0c6633ea-4a9c-4367-8778-81f505031c0c` | Command-frame main menu, active squadron rows, dock/research/galaxy layout and coherent amber/blue accents |
| `31c97bc8-1fb1-4bef-836e-8300e1ea28f6` | Industrial control library, button hierarchy and state colors, small application emblems |
| `14f63df4-af47-441c-84bb-6c0f1582091b` | Ship detail, tactical operations, status/alert surfaces and instrument density |
| `a67aae66-fada-4718-bebb-6ef011156f4e` | Campaign dock, equipment/research controls and low-noise status bars |
| `cec526db-6d8e-495b-87a7-8d30025df323` | Desktop icon composition, dense fleet management, squadron editor and battle HUD |

These are **references, not approved individual production sprites**. Concept-art text, numbers, controls, gauges, and screenshots may be illustrative or incorrect. Never crop a broken frame or a labeled mock-up into a supposedly complete UI asset. Before runtime import, archive and approve each actual export as a discrete asset with exact bounds, alpha, source ID, and visual inspection.

## Design tokens and roles

The shared first implementation lives in `game/src/main/java/diplomacy/client/MilitaryUiTheme.java`.

| Role | Color | Meaning |
| --- | --- | --- |
| Outside/background | `#040507` | Unlit graphite beyond terminals |
| Recessed panel | `#060A0C` | Readable, low-noise information surface |
| Frame | `#333939` | Worn steel perimeter and divider |
| Bright frame edge | `#575C57` | Sparse edge glints; not a glow blanket |
| Main operation amber | `#FA912B` | Launch/commit/apply or authoritative selected operations |
| Cold telemetry cyan | `#38B0D6` | Active navigation, selected data, sensor information |
| Safe/complete green | `#38B870` | Confirm, fully operational, readiness |
| Caution amber | `#F5A833` | Understrength reserves, resource deficits, reversible warnings |
| Loss/danger red | `#D43D3D` | Destruction, abort, destructive actions |
| Secondary text | `#A8B8BF` | Readable subdued labels, never invisible microprint |

These are semantic anchors, not instructions to saturate every control with color. Different states need differences in **shape, underline, border, label and value**, not color alone.

## Physical construction

- Screens resemble **recessed metal equipment racks** with dark display glass, fine instrument dividers and rivets kept at the edges. Wear belongs in the borders; keep labels, numerical values and touch zones clean.
- Buttons use consistent outer frame, inset face, shallow bevel and a narrow semantic illuminated strip. Normal/selected/hover/pressed/disabled states are distinct; selected is not a synonym for successful or safe.
- Categories: **neutral**, **primary operation**, **confirm/readiness**, **warning**, **danger/destructive**. Define the intended action before choosing the color.
- Fleet and squadron lists prioritize readable names, class, permanent 16-digit IDs, membership, readiness and active/reserve state; do not substitute tactical slot numbers for permanent identity.
- High-density ship grids are allowed. Avoid needless empty cards, glossy mobile candy art, giant illustration blocks under critical diagnostics or novelty pseudo-Latin labels.
- Runtime iconography is specific: ship, fleet, squadron, missile, repair, shield, crew, fuel, torpedo, drone, map, research, retreat, targeting, damage and service. Use the audited icon kit until original replacement binaries pass intake.
- Type should feel like a military instrument display but be **actual readable font glyphs**. Use restrained stencil/condensed titles and dependable native body fonts; no pseudo-text baked into backgrounds.
- Alarm/warning surfaces must show concrete causes, e.g. `AMMUNITION LOW`, `CREW UNDERSTRENGTH`, `FUEL REQUIRED`; casualties are not jokes.
- Tooltips, key bindings and touch hit regions must match what they do. Decorative mock-up controls must never appear as active buttons.

## Required views and modules

Design all of these using a common component kit: main menu and campaign setup; campaign starmap; active/reserve fleet dock and vessel service; squadron profiles and assignments; naming traditions and radio voice; pre-battle placement and per-ship doctrine; shipyard fitting and equipment; research tree; fleet orders; in-battle HUD and tactical overlays; after-action casualty and supply reports; settings/audio; native desktop executable/application icon.

**Viewport rule:** preserve the existing 1280×720 game-space reference with libGDX viewport scaling. Favor legibility and meaningful grouping at 720p and narrow/touch displays. Do not move or narrow a real control's hit region merely to match an illustration.

**Performance rule:** 200-vs-200 combat can require hundreds of simultaneous indicators. Prefer a few cached source textures and cheap procedural rails; never allocate textures per frame, create particles for every rivet, or let UI chrome flood mobile VRAM.

**Platform rule:** Windows/Linux remain native Java 17/LWJGL3 deployments; Android and Fire OS use the shared game module. No Chromium/browser wrappers. Desktop icons require properly reviewed multi-size PNG/ICO and Linux icon export before claiming packaging completion.

## Actual implementation / verification ledger

- **Initial code pass:** shared `MilitaryUiTheme` chrome and semantic primary/confirm/warning buttons are wired into `GameMenuPanel`. Painted frames preserve existing click coordinates and menu actions.
- **Gritty sprite integration (2026-10-27):** `GrittyUiChrome` draws eight reviewed-for-complete-borders, textless technical-edge-pass controls from `assets/ui/gritty/buttons_v1.png` with nine-patch scaling and preallocated command buffers, then renders the original live BitmapFont labels. The other 174 edge-pass sprites remain source candidates; held sprites are not packaged into the runtime. If the atlas is missing or corrupt the previous procedural controls remain functional. Windows/Linux and Android/Fire OS load the same runtime file.
- **Artwork archive:** The full 182 strict technical-pass candidates and nine background panel candidates are preserved under `asset_packs/gritty-ui/` in separate checksummed ZIPs; they are *not* automatically inserted as 182 unused GPU textures or assumed individually visually accepted.
- **Already integrated, unchanged:** `TacticalUiIcons` reviewed 24-image source kit and its exact hash/crop approval workflow. This document does not approve the rejected icon sheet or its provisional crops.
- **Full-screen background integration:** five curated original concept-derived textures are now hash-pinned as a small JPEG source bundle, staged via `prepareUiAssets`. Plain metal plates serve dense live interfaces; dimmed illustrative art is limited to skirmish/shipyard. The gameplay battle remains a real renderer. Lazy single-texture loading and fallback avoid loading every background into memory.
- **Campaign-specific spending interfaces:** the Campaign Shipyard now quotes fitted construction directly from campaign credits and research licenses; its BLUEPRINT action only saves a design, while PURCHASE requires two distinct confirmations before issuing an atomic new-hull debit. The Campaign Technology Tree has six license nodes, four laboratory nodes, progression requirements, wallet/remaining-credit previews and two-step spend authorization. Both retain native touch and keyboard actions and existing ledger save semantics.
- **Paid commissioned-hull refitting:** the dock's SERVICE ALL row now has a separate REFIT HULL action. The campaign shipyard can edit the selected reserve ship in place, quote installation work, and require two explicit authorizations before debiting the atomic wallet/fleet ledger. Permanent vessel IDs, names, reserve placement and squadron group survive. Damaged hulls must first undergo paid repairs; changed weapon loads cannot magically convert old ammunition; changed fighter hangars do not provide free replacement craft. This remains subject to the normal GitHub Actions and visual acceptance gates.
- **Pending:** additional authored individual control sprites, real hover/pressed/disabled states, consistent `FleetOrdersPanel` chrome, platform icon exports, screenshot-based clipping/contrast QA and device review.
- **Acceptance:** GitHub Actions compile and functional tests across Windows/Linux, Android debug and 200v200 regression; optional visual diagnostics separately; signed Android gameplay/emulator proof remains a release blocker until passed. A source commit is not a visual or Android-release certification.

This file is the **single authoritative design-language specification**. Later component implementations should link back here rather than establish competing themes.
