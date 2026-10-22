# Catastrophic Diplomacy

*An Unreasonable Space Warfare Simulator* — six rival civilizations, engineering-driven fleet combat and entirely unnecessary quantities of ordnance.

## Current scope and proof

- **Java 17 shared `:core`** is deterministic, renderer independent and physically advances combat at 30 Hz. It has faction-specific ship stats, self-directed target selection with uncertainty, movement, power/heat/shield budgets, ablative armor, SIF, lasers, homing missiles, point defense, subsystem casualties and destruction.
- **`:game`** is the same libGDX client on desktop and Android. The tactical arena has pause, speed, reset, faction cycling and touch/keyboard controls; the GPU path uses OpenGL 3 instanced ship geometry with a fallback renderer when GL30 is unavailable.
- **`:desktop`** is LWJGL3. **`:android`** is the Android launcher. Both share every rules implementation. Desktop/Android graphics and native APK packaging are not yet accepted on physical devices.
- A headless 16-vs-16 Java 17 smoke test passed locally. The online repository must be inspected at the commit SHA before claiming deployment. Balance and large-fleet GPU performance remain unverified.

## Requirements and commands

Install **JDK 17**, **Gradle 8.11.1 or compatible AGP 8.9.2 toolchain**; Android builds also require Android SDK platform 35 and Android Build Tools. A checked-in Gradle wrapper has not yet been generated, so use an installed `gradle` executable rather than pretending `gradlew` is available.

```text
gradle :core:smoke
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

The first actual slice is the sandbox: accurate ship-fitting constraints, physics and component damage, fleet AI, shared clients, technology CSV ingestion, fleet editor and replay tests. Campaign strategy and diplomacy follow a playable combat loop. Technology planning and primary lore are staged in the Catastrophic Diplomacy Google Drive workspace.

Inspirations include the *concept* of preprogrammed space fleet battle sandboxes. All code, lore, graphics and rules here are original and not copied from proprietary games.
