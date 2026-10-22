# Catastrophic Diplomacy

*An Unreasonable Space Warfare Simulator* — six rival civilizations, engineering-driven fleet combat and entirely unnecessary quantities of ordnance.

## Current scope and proof

- **Java 17 shared `:core`** is deterministic, renderer independent and physically advances combat at 30 Hz. It has faction-specific ship stats, self-directed target selection with uncertainty, movement, power/heat/shield budgets, ablative armor, SIF, lasers, homing missiles, point defense, subsystem casualties and destruction.
- **`:game`** is the same libGDX client on desktop and Android. The tactical arena has pause, speed, reset, faction cycling and touch/keyboard controls, including the shared **Shipyard**; the GPU path uses OpenGL 3 instanced ship geometry with a fallback renderer when GL30 is unavailable.
- **`:desktop`** is LWJGL3. **`:android`** is the Android launcher. Both share every rules implementation. Desktop/Android graphics and native APK packaging are not yet accepted on physical devices.
- A headless 16-vs-16 Java 17 smoke test passed locally. The online repository must be inspected at the commit SHA before claiming deployment. Balance and large-fleet GPU performance remain unverified.

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

The repository includes **120 base components and 2,880 upgrades** in Java-resource CSVs under `core/src/main/resources/technology/`. `TechnologyCatalog.loadDefault()` loads both sources and rejects noncanonical IDs, duplicate IDs, broken same-track prerequisites, missing base components, invalid faction/track names, nonfinite or nonpositive multipliers and invalid costs. Four tracks — OUTPUT, EFFICIENCY, RESILIENCE and SPECIALIST — branch separately in six stages for every faction/subsystem pairing. The authoritative editable source is the native Google Sheet in the project Drive folder; any updated CSV export must pass `:core:technologySmoke` before it is considered game data. Player loadout application and balancing are **not yet implemented**, so catalogue presence does not imply 2,880 presently playable component upgrades.

## Shipyard — first integrated fitting slice

Open **SHIPYARD** (keyboard **Y**/**Tab**, or the upper-right touch button). Choose the left/right fleet, cycle Corvette/Frigate/Cruiser/Dreadnought, select a hardpoint in the scrollable list, install a compatible faction component, then adjust OUTPUT/EFFICIENCY/RESILIENCE/SPECIALIST ranks independently from 0 to 6. Each rank consumes research/industry costs; the selected highest rank per parallel track defines the multiplier rather than compounding the same branch's stage values on top of each other. **APPLY** deploys the design to the battle only if the fitting is legal. **SAVE/LOAD** preserve text blueprints under the application's local `shipyard/` directory; loaded designs must pass schema and engineering validation. Android and desktop call the identical `Shipyard` rules and same `Battle` simulation.

The fitting checks mount category, faction ownership, hull mass/volume, reactor generation versus installed draw, thermal rejection, basic propulsion and at least one weapon. The battle actually uses installed shield capacity, armor, SIF, engines, laser and missile damage, point-defense range, output headroom, radiator capacity and active repair bays. A missing missile mount means no missiles fire. Launchers remain shared, with no duplicated platform rules. The simulator's original faction-presets can still be used by existing tests.

Java-only verification independent of GPU/Android dependencies:

```bash
mkdir -p out
javac --release 17 -encoding UTF-8 -d out $(find core/src/main/java core/src/test/java -name '*.java')
java -cp out:core/src/main/resources diplomacy.sim.BattleSmokeTest
java -cp out:core/src/main/resources diplomacy.sim.TechnologyCatalogTest
java -cp out:core/src/main/resources diplomacy.sim.ShipyardTest
```

**Still gated:** actual libGDX/Android builds, platform-specific loading/saving, touch usability on devices, physical compartment damage, custom weapons with distinct projectile behaviors, and mass-fleet performance profiling. Fighter hangars, drone control, electronic warfare and ordnance magazine mechanics are not yet active beyond their fitting loads, costs and prerequisites; do not treat these as implemented combat effects. Do not infer successful physical device verification from headless Java tests.
