# Catastrophic Diplomacy

**Authoritative interface direction:** [Gritty Military / Industrial Fleet Command Console](docs/AUTHORITATIVE_UI_DESIGN_LANGUAGE.md) (user-approved 2026-10-27). This standard governs menus, buttons, HUDs, squadron/dock controls and native desktop icons. Current code integrates initial procedural steel-and-instrument chrome without replacing audited assets; visual acceptance and complete icon exports remain pending.


*An Unreasonable Space Warfare Simulator* — six rival civilizations, engineering-driven fleet combat and entirely unnecessary quantities of ordnance.


## Combat audio and soundtrack

Shared libGDX audio now turns simulation weapon fire, shield and hull hits, interceptions,
ship destruction and UI actions into audible effects, with an intentionally throttled
200v200 mix. Two user-Drive music tracks are packaged for desktop and Android.
The tactical menu exposes music/effects toggles and track selection (keyboard **U/I/J**),
with persistent preferences and graceful audio-device failure handling. See
[combat audio, Drive provenance and verification](docs/AUDIO_SYSTEM.md).
Hardware playback and release music-rights clearance are still acceptance gates.

## Graphics stage 2 (faction hull silhouettes and source-backed UI)

All six factions now receive class-specific fallback tactical silhouettes derived from the
existing shipyard hull profiles (66 combinations; no runtime dependence on alien concept
sheets). The approved HUD atlas, buttons, and new tactical menu are wired into shared
desktop/Android code. The separate ten-Terran-ship runtime art pack is **not** yet
committed to GitHub; a checksum-gated importer installs seven provisionally acceptable
hull images and 35 beam frames, quarantining three severely clipped large hulls.
See [graphics stage 2 and verification](docs/GRAPHICS_STAGE2.md) and run
`gradle :game:geometrySmoke` once the build toolchain is available. This is not
an Android hardware acceptance claim.

## Current scope and proof

- **Java 17 shared `:core`** is deterministic, renderer independent and physically advances combat at 30 Hz. It has faction-specific ship stats, self-directed target selection with uncertainty, movement, power/heat/shield budgets, ablative armor, SIF, lasers, homing missiles, point defense, subsystem casualties and destruction.
- **`:game`** is the same libGDX client on desktop and Android. The tactical arena has pause, speed, reset, faction cycling and touch/keyboard controls, including the shared **Shipyard**; the GPU path uses OpenGL 3 instanced ship geometry with a fallback renderer when GL30 is unavailable.
- **`:desktop`** is LWJGL3. **`:android`** is the Android launcher. Both share every rules implementation. Desktop/Android graphics and native APK packaging are not yet accepted on physical devices.
- A headless 16-vs-16 Java 17 smoke test passed locally. The online repository must be inspected at the commit SHA before claiming deployment. Balance and large-fleet GPU performance remain unverified.

## Exclusive six-rank module doctrines and fleet servicing

Every installed module chooses **one** mutually exclusive technology tree from **OUTPUT, EFFICIENCY, RESILIENCE, SPECIALIST**, with six incremental ranks per path. Weapon and point-defense branches now change actual damage, firing interval, range, tracking and missile guidance in addition to the existing research cost, mass, power and heat tradeoffs. A second installed copy of the same base module may select a different branch without violating exclusivity.

Functional support mechanics include **powered self hull repair**, **allied hull repair beams**, **allied directional shield charging beams**, and **finite autonomous repair drones** from Specialist drone-controller modules. Non-specialist controllers retain independently tracked interception drones; a mixed-fitting ship can launch both types. The support visual links reuse the existing curated green and blue beam atlases, with honest rendering fallbacks when assets are missing. [Module Support Branches](docs/MODULE_SUPPORT_BRANCHES.md) documents mechanics, balance parameters, ongoing limitations and the `:core:supportSystemsSmoke` acceptance suite. This source change is **not** proof of passing Java/desktop/Android regression tests.

## Projectile and point-defense module attributes

Fitted kinetic, missile and torpedo modules now define **speed, projectile health, radar cross-section and signature**, including existing weapon-upgrade and faction modifiers. PD/FLK and sensor modules define **nominal detection reach, tracking ability and intercept damage**. Detection is based on the target's actual signature/cross-section and weather; intercept chance accounts for projectile speed and size; successful PD shots remove physical projectile health. Fast missiles and ballistic shells use swept collisions to avoid bypassing targets between ticks. The shipyard displays the installed module's values. See [Projectile Module Stats](docs/PROJECTILE_MODULE_STATS.md), and run `gradle :core:projectileModuleSmoke` for the new headless acceptance test.

## Tactical orders and point defense

Ships now support switchable point-defense fire control: **Balanced**, **Intercept Only**, **Anti-Craft** and **Hold Fire**. While in battle press **P** (left fleet) or **O** (right fleet) to cycle PD modes, or open the **Tactical Command** menu and choose **Fleet Orders Editor** (keyboard **F**). The two-column editor controls per-fleet class priorities and engagement ranges, targeting doctrines (nearest/cooperate/vulture/retaliate/protect), movement stances and cautious retreat. These settings persist in `fleet/orders-v1.dat` independently of the existing ship-fitting saves. Per-ship escort and formation orders are supported in the simulation API; individual parent assignment and multi-select deployment UI remain work to do. See [Fleet Orders System](docs/FLEET_ORDERS_SYSTEM.md). Run `gradle :core:battleOrdersSmoke` for the new headless rule tests; desktop/Android visuals still need actual build verification.

## Modular weapon visuals

Weapon animation is split into **three independently controlled stages**: a muzzle/firing clip, an authoritative moving beam/projectile payload, and a true hit/interception impact clip. Timing events and projectile IDs are emitted by the deterministic core; libGDX only renders existing textures. The initial four beam families have 63 original-atlas firing/impact cutouts plus 35 existing beam payload frames preserved in the project's binary transfer package (not yet GitHub PNG commits). No new artwork or procedural-pill replacement was generated. See [Modular Weapon FX](docs/MODULAR_WEAPON_FX.md); run `gradle :core:weaponFxSmoke` once the Java toolchain is available.

## Requirements and commands

Install **JDK 17**, **Gradle 8.11.1 or compatible AGP 8.9.2 toolchain**; Android builds also require Android SDK platform 35 and Android Build Tools. A checked-in Gradle wrapper has not yet been generated, so use an installed `gradle` executable rather than pretending `gradlew` is available.

```text
gradle :core:smoke
gradle :core:shipyardSmoke
gradle :desktop:run
gradle :android:assembleDebug
```

Java-only test with no external dependencies:

```text
mkdir -p out
javac --release 17 -d out core/src/main/java/diplomacy/sim/Battle.java core/src/test/java/diplomacy/sim/BattleSmokeTest.java
java -cp out diplomacy.sim.BattleSmokeTest
```

## Signing/bootstrap

On your own trusted development machine, install `gh` and log in (`gh auth login`) with repository secret-write privileges; install a JDK with `keytool`. From repository root run **one Python command**:

```text
python tools/bootstrap_secrets.py
```

This generates and privately stores one Android PKCS12 upload key and password set in `.local-secrets/`, uploads four Actions secrets to **this repo only**, and reads back the secret *names* for verification. It will not replace existing keys unless you explicitly restore the local files. Keep `.local-secrets/` backed up in a secure offline location: loss of the signing key can prevent compatible updates. It does not make Windows Authenticode keys, GitHub PATs, or arbitrary API credentials. Public GitHub release checking does not need a PAT. See `docs/RELEASES.md`.

## Roadmap

The first actual slice is the sandbox: accurate ship-fitting constraints, physics and component damage, fleet AI, shared clients, technology CSV ingestion (strict catalog now checked in), fleet editor and replay tests. Campaign strategy and diplomacy follow a playable combat loop. Technology planning and primary lore are staged in the Catastrophic Diplomacy Google Drive workspace.

Inspirations include the *concept* of preprogrammed space fleet battle sandboxes. All code, lore, graphics and rules here are original and not copied from proprietary games.

## Technology database (data-backed)

The repository includes **120 base components and 2,880 upgrades** in Java-resource CSVs under `core/src/main/resources/technology/`. `TechnologyCatalog.loadDefault()` loads both sources and rejects noncanonical IDs, duplicate IDs, broken same-track prerequisites, missing base components, invalid faction/track names, nonfinite or nonpositive multipliers and invalid costs. Four tracks — OUTPUT, EFFICIENCY, RESILIENCE and SPECIALIST — each provide six ranks per faction/subsystem pairing. **Only one branch may be chosen per installed module socket**; identical modules in other sockets may choose different branches. [Exclusive Module Research](docs/MODULE_RESEARCH_EXCLUSIVITY.md) describes enforcement and legacy-save handling. The authoritative editable source is the native Google Sheet in the project Drive folder; any updated CSV export must pass `:core:technologySmoke` before it is considered game data. Module-level fitted upgrades and their combat effects are implemented, but a campaign-wide research wallet, purchasing/unlock progression and final balancing are **not yet implemented**. Catalogue presence alone does not prove that every upgrade is balanced or available through a playable research campaign.

## Shipyard — first integrated fitting slice

Open **SHIPYARD** (keyboard **Y**/**Tab**, or the upper-right touch button). Choose the left/right fleet, cycle all eleven supported fitting hulls (from Interceptor to Dreadnought, including the saved legacy Cruiser), change the faction inside the shipyard, select a physical hardpoint on the faction-specific top-down hull diagram or in the synchronized scrollable list, install a compatible faction component, then choose exactly **one** mutually exclusive OUTPUT/EFFICIENCY/RESILIENCE/SPECIALIST research branch for each equipped module and advance it from stage 1 to 6. Competing branches are visually marked **LOCKED**. To switch, explicitly lower the current branch to 0 before choosing another; changes affect only the selected socket. The fitting report accumulates the chosen branch's rank 1–N research/industry costs, and the highest selected rank defines its multipliers rather than recursively compounding stages. **APPLY** deploys the design to the battle only if the fitting is legal. **SAVE/LOAD** preserve class-and-faction-specific text blueprints under the application's local `shipyard/` directory; legacy filenames are read only when the selected hull matches, and loaded designs must pass schema and engineering validation. Android and desktop call the identical `Shipyard` rules and same `Battle` simulation.

The fitting checks mount category, faction ownership, hull mass/volume, reactor generation versus installed draw, thermal rejection, basic propulsion and at least one weapon. [Fitting-slot counts, racial placement doctrines and acceptance limits](docs/SHIPYARD_HULL_LAYOUTS.md). The battle actually uses installed shield capacity, armor, SIF, engines, laser and missile damage, point-defense range, output headroom, radiator capacity and active repair bays. A missing missile mount means no missiles fire. Launchers remain shared, with no duplicated platform rules. The simulator's original faction-presets can still be used by existing tests.

Java-only verification independent of GPU/Android dependencies:

```bash
mkdir -p out
javac --release 17 -encoding UTF-8 -d out $(find core/src/main/java core/src/test/java -name '*.java')
java -cp out:core/src/main/resources diplomacy.sim.BattleSmokeTest
java -cp out:core/src/main/resources diplomacy.sim.TechnologyCatalogTest
java -cp out:core/src/main/resources diplomacy.sim.ShipyardTest
java -cp out:core/src/main/resources diplomacy.sim.HullLayoutsTest
```

**Still gated:** actual libGDX/Android builds, platform-specific loading/saving, touch usability on devices, physical compartment damage, custom weapons with distinct projectile behaviors, and mass-fleet performance profiling. Fighter hangars, drone control, electronic warfare and ordnance magazine mechanics are not yet active beyond their fitting loads, costs and prerequisites; do not treat these as implemented combat effects. Do not infer successful physical device verification from headless Java tests.

## Fleet persistence, mobile input and combat machinery (next development slice)

Fitted left/right fleets are now serialized into a checksummed, versioned, cross-platform `CD-FLEETS-1` save under the app-private libGDX local storage. The client restores on startup, saves when a fleet changes, retains a validated previous save as backup, and refuses corrupt or incompatible fittings. Shipyard touch actions occur on release rather than on finger-down, so a long-list drag is not misinterpreted as a component tap; secondary pointers cannot hijack the active scroll.

Dedicated kinetic, plasma, particle and torpedo damage now coexist with lasers and standard missiles. Mounted fighter hangars launch finite attack craft, drone controllers launch finite missile-interception drones, ECM reduces lock/guidance, and magazines supply finite extra ammunition. These modules consume fitting budgets; damaged hangars and magazines lose combat effectiveness. The shared 2D client draws fighters, drones and heavier torpedoes on both platforms. These are first operational models, not full carrier logistics or per-compartment damage.

Portable test commands using a JDK 17+ with Java 17 release targeting (and no renderer dependencies):

```sh
mkdir -p out
javac --release 17 -encoding UTF-8 -d out $(find core/src/main/java core/src/test/java -name '*.java')
cp -r core/src/main/resources/* out/
for test in BattleSmokeTest TechnologyCatalogTest ShipyardTest PlatformPersistenceTest CombatMachineryTest FleetScaleTest; do java -cp out diplomacy.sim.$test; done
```

Gradle `:core:smoke` chains these suites. See [platform acceptance checklist](docs/PLATFORM_ACCEPTANCE.md) for outstanding real desktop and Android builds, signing verification, save/restore device tests, and release gates. No GitHub Actions workflow was introduced.

## Massed fleet verification (200v200)

The staged Java 17 headless campaign in `core/src/test/java/diplomacy/sim/MassBattleCampaignTest.java`
checks **every fleet size from 1v1 to 200v200**, 860 valid weapon/hull/faction pairings
(4 intentionally invalid), **all six weapon families**, 568 accepted drive-technology stages,
3,404 accepted weapon-technology stages, repeated deterministic engagements and **two 480-tick
200v200 mixed-arsenal carrier battles**. Fighters, drones, magazines and ECM participate in
the mixed fleet. Ship deployment coordinates, draw-effect metadata, numerical state and
in-flight ordnance are asserted, not simply reported.

`BattleViewTest` verifies screen-safe camera framing across all 200 fleet sizes and
750 moving 200v200 ticks. `BattleGeometryRasterTest` is a separate Java2D proof that
**400 ship polygons are visible** in a headless diagnostic image. These do **not** verify
that actual libGDX graphics reach a physical display; desktop graphics drivers, the
OpenGL instancing path, Android UI and frame-rate remain hardware acceptance gates.

From a local checkout with JDK17 and Gradle:

```text
gradle :core:massBattleCampaign
```

From JDK17 alone (Java sources compiled to Java17 bytecode; test-only raster requires `java.desktop`):

```bash
mkdir -p out
javac --release 17 -d out $(find core/src/main/java core/src/test/java -name '*.java')
java -cp out:core/src/main/resources diplomacy.sim.MassBattleCampaignTest mass-battles.csv
java -cp out:core/src/main/resources diplomacy.sim.BattleViewTest
java -Djava.awt.headless=true -cp out:core/src/main/resources diplomacy.sim.BattleGeometryRasterTest headless-hulls.png
```

Game controls: `[ / ]` or on-screen **− / +** adjust each side's fleet from **1 to 200**
by fixed steps, `Y` opens the shipyard, `Q / E` change factions, `R` restarts,
`Space` pauses, `+ / -` adjusts simulation speed. Large engagements use a separate
world camera that zooms to keep surviving ships inside the battle panel; the HUD stays fixed.
The hulls are presently simple colored top-down geometry with visible engine trails,
not finished sprites or 3D ship art. Directed-fire lines are colored by laser, kinetic,
plasma and particle weapon type; missiles, torpedoes, fighters and defensive drones
are separate on-screen shapes. See `docs/MASS_BATTLE_ACCEPTANCE.md`.

## Directional protection, damage and first-faction sprite placeholders

The combat core now separates four powered shield facings from 16x16 localized armor tiles and an exposed structural sublayer. Damage types have independent shielding, armor and structural multipliers; per-facing armor and shield upgrade APIs are present. The external Terran fleet sprite cutouts may be installed under `assets/ships/terran/`; missing PNGs keep the tested fallback renderer. A basic visual damage marker exists, but transparent armor gouging, normal maps and custom structural-image compositing are **not yet implemented**. [Directional design and acceptance](docs/DIRECTIONAL_DAMAGE.md) · [Sprite placeholder manifest](docs/TERRAN_SPRITE_ATLAS.md) · [Level System I roadmap](docs/LEVEL_SYSTEM_I.md).
