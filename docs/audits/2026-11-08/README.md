# Audit evidence — 8 November 2026

This directory preserves the observations behind the [project audit](../../PROJECT_AUDIT_2026-11-08.md) and [development repair plan](../../DEVELOPMENT_REPAIR_PLAN.md). Production code was not repaired during the audit. Several probes deliberately assert existing defects: a successful probe process establishes reproduction, not successful product behavior.

## Baseline and environment

- **Audited game source:** [`0159042e47a9271e5e3b077756f5c4d57763a688`](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/0159042e47a9271e5e3b077756f5c4d57763a688). Later audit commits change documentation/evidence only.
- **Public matching release:** [`cd-v0.1.5-dev.693`](https://github.com/mrcalzon02/Catastrophic-Diplomacy/releases/tag/cd-v0.1.5-dev.693), published prerelease, not a draft.
- **Downloaded Linux ZIP:** 195,158,254 bytes; SHA-256 `321e46748165638c56ee661295dd4f71e5eb8b5133cfa151dd4ac20b18e71ac7`, verified against the published checksum.
- **Local compiler/runtime:** Temurin 17.0.21+9, Java release 17; Gradle 8.11.1. The direct `javac` check used release external dependency JARs but excluded project core/game/desktop JARs. Probe resources included the verified release's baked hardpoint map. Normal Gradle tests generated their own resources.
- **Local limits:** the Xvfb display could not create Unix sockets (`EPERM`). No fresh graphical launch, physical Android session or rendered touch walkthrough is claimed. Android packaging was not rerun locally.
- **Hosted evidence:** [release run 42349197474](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/42349197474) has 16 successful jobs at the audited SHA; [objective smoke run 42349197600](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/42349197600) has one successful job at the same SHA. Artifact metadata were checked; their screenshots were not independently visually inspected during this audit.

The initial local dependency download failed until the environment's existing proxy and CA truststore were applied to the downloaded JDK. That environment problem was resolved without changing project files. Logs below omit credentials and do not require signing material.

## Reports and retained observations

| File | Purpose |
|---|---|
| [CHECKPOINTS.md](CHECKPOINTS.md) | Baseline, intermediate findings, explicit corrections and final audit checkpoint |
| [CAMPAIGN_REVIEW.md](CAMPAIGN_REVIEW.md) | Actual campaign paths, ownership, research, transaction failures and repair acceptance |
| [COMBAT_REVIEW.md](COMBAT_REVIEW.md) | Causal navigation, withdrawal defense, command sharing and current implemented systems |
| [NETWORK_RELEASE_REVIEW.md](NETWORK_RELEASE_REVIEW.md) | Multiplayer, updates, trigger inventory and exact-source release evidence |
| [INDEPENDENT_REVIEW.md](INDEPENDENT_REVIEW.md) | Skeptical review of fixtures, claims, limitations and repair hazards |
| [compile-result.json](compile-result.json) | Independent Java 17 compilation result: 392 relevant production/test sources |
| [functional-result.json](functional-result.json) / [functional-checks.log](functional-checks.log) | Executed main command and all 106 named Smoke tasks; 118 actionable Gradle tasks |
| [supplementary-result.json](supplementary-result.json) / [supplementary-checks.log](supplementary-checks.log) | Combined-arms replay, fighters, approved hardpoints and timeline follow-up |
| [timeline-scenarios.csv](timeline-scenarios.csv) / [timeline-irregular-frame-scenarios.csv](timeline-irregular-frame-scenarios.csv) | Fixed-step and variable-frame summaries; generated diagnostics, not physical-device FPS |
| [python-checks.log](python-checks.log) | 22 existing Python unit tests |
| [android-api-check.log](android-api-check.log) | Static compatibility scan, with its bounded scope |
| [campaign-atlas-check.log](campaign-atlas-check.log) | Story/voice atlas checks and missing-recording count |
| [wiki-results.json](wiki-results.json) | Broad-index success, arbitrary-search OOM at 16 MiB, fallback measurements at 32 MiB |
| [CampaignFlowAuditProbe.java](CampaignFlowAuditProbe.java) / [campaign-probe.log](campaign-probe.log) | Five actual controller reproductions, with temporary test saves |
| [CombatAuditProbe.java](CombatAuditProbe.java) / [combat-probe.log](combat-probe.log) | Three isolated normal-step combat reproductions |
| [NetworkMirrorAuditProbe.java](NetworkMirrorAuditProbe.java) / [network-mirror-audit-probe.json](network-mirror-audit-probe.json) | Session interpolation, generic mirrors and semantically invalid framed-state observations |
| [documented-probes-result.json](documented-probes-result.json) | Final reproduction-command check against normal Gradle-built classes/resources; all three probe programs ran successfully and reproduced their recorded defects |
| [FILE_MANIFEST.json](FILE_MANIFEST.json) | Byte counts, SHA-256 digests and Git blob IDs for the final audit documents and retained evidence; excludes itself |

## Normal verification commands

Use Java 17 and Gradle 8.11.1 from the repository root. Match the audited source to reproduce the recorded baseline. On repaired source, expect changed defect probes and record a new result rather than overwriting these historical observations.

```bash
gradle --no-daemon \
  :core:compileTestJava :game:compileTestJava \
  :server:compileTestJava :desktop:compileJava

gradle --no-daemon --continue \
  :core:gameFlowSmoke :core:campaignLedgerSmoke \
  :core:campaignTechnologySmoke :core:campaignAfterActionSmoke \
  :core:mixedTierFleetSmoke :core:shipStandingOrdersSmoke \
  :core:updaterPolicySmoke :core:desktopUpdaterPolicySmoke \
  :server:check :game:menuFlowSmoke \
  :game:combatPerformanceSmoke :game:battleTouchSmoke

gradle --no-daemon --continue \
  :core:massMixedOnlySmoke :core:fighterCraftSmoke \
  :core:approvedHardpointsSmoke :game:combatTimelineDiagnostic

python3 -m unittest discover -s tools -p 'test_*.py'
```

The broad command completed in about 96 seconds on this host. It passed real loopback TLS fixtures with 2, 32 and 400 ships, server-owned seats/orders and authenticated reconnect sequence watermarks, plus trusted-LAN terminal and submitted-fleet fixtures. It does not replace encrypted custom-fleet lobby and multi-device visible-play acceptance.

The supplementary command completed in about 30 seconds. Two 480-tick 200v200 mixed runs produced fingerprint `9192d36f3d2785df`, 3,797 hits and 104 destroyed ships. The hardpoint check covered 506 graphics/headless-identical hulls and 9,515 anchors, fingerprint `3060a0147a828cf5`. These are fixture observations, not a device-performance promise.

## Compile and run the focused probes

The probes are documentation evidence outside production/test source sets. They use reflection and deliberately constructed fixtures to reach a specific failure. They never read a real player save. Keep them outside ordinary release gates; convert a repaired behavior into a proper positive regression in the appropriate existing suite.

After the normal Gradle tests have generated classes and resources, the following Linux/macOS shell example builds the retained probes separately. Set `CD_AUDIT_GDX` to the resolved `gdx-1.14.2.jar` used by Gradle. `CD_AUDIT_OUT` must be a temporary output location, not a player data directory.

```bash
CD_AUDIT_GDX=/absolute/path/to/gdx-1.14.2.jar
CD_AUDIT_OUT=$(mktemp -d)
CD_AUDIT_CP="core/build/classes/java/main:game/build/classes/java/main:server/build/classes/java/main:core/build/resources/main:$CD_AUDIT_GDX"

javac --release 17 -cp "$CD_AUDIT_CP" -d "$CD_AUDIT_OUT" \
  docs/audits/2026-11-08/CampaignFlowAuditProbe.java \
  docs/audits/2026-11-08/CombatAuditProbe.java \
  docs/audits/2026-11-08/NetworkMirrorAuditProbe.java

java -cp "$CD_AUDIT_OUT:$CD_AUDIT_CP" diplomacy.client.CampaignFlowAuditProbe
java -cp "$CD_AUDIT_OUT:$CD_AUDIT_CP" diplomacy.sim.CombatAuditProbe
java -cp "$CD_AUDIT_OUT:$CD_AUDIT_CP" diplomacy.client.NetworkMirrorAuditProbe
```

### Meaning and boundaries of each probe

**Campaign:** calls actual frontend handlers with minimal Gdx application/file stubs and temporary authority files. It reproduces mission-cap disagreement, inert Results Retry, a conflicting late-license card, blocked explicit new-campaign recovery after corrupt authority, and an uncommitted winning settlement after storage failure. The settlement fixture directly defeats an opposing hull to supply the terminal outcome. This is controller/transaction proof, not a naturally played or rendered victory.

**Combat:** runs the current `Battle` against controlled observations. It reproduces different steering under identical known history, PPD suppression under the withdrawal flag despite real incoming launches, and zero remote FCM donation despite assigned capacity and exhausted local allocation. The PPD fixture uses a stationary diagnostic defender to isolate eligibility. Repair acceptance must also demonstrate physical retreat, finite resource debit and the normal order/escape gates.

**Network:** uses interpolation/mirror objects and an in-memory socket carrying correctly framed, checksummed content. Its observations demonstrate missing session context, generic bodies and late semantic parsing. It is not a TLS bypass or a remote unauthenticated exploit. Several observations are printed rather than asserted; compare the retained JSON and source when interpreting exit status.

## Reproduce the bounded encyclopedia profile

The classes already belong to the existing test source set. After `:game:compileTestJava` and normal resource generation, use the classpath above with test classes added:

```bash
CD_AUDIT_PROFILE_CP="game/build/classes/java/test:core/build/classes/java/test:$CD_AUDIT_CP"
java -Xmx16m -cp "$CD_AUDIT_PROFILE_CP" diplomacy.client.WikiMemoryProfile
java -Xmx16m -cp "$CD_AUDIT_PROFILE_CP" diplomacy.client.WikiArbitrarySearchProfile
java -Xmx32m -cp "$CD_AUDIT_PROFILE_CP" diplomacy.client.WikiArbitrarySearchProfile
```

At the audited baseline the middle process exits 1 with `OutOfMemoryError`; that expected reproduction must not be described as a passing product test. The other two recorded processes exit 0. Exact heap occupancy and milliseconds depend on JVM/host conditions. The 16 MiB HotSpot heap is a diagnostic envelope, not a claim about available heap on every Android device.

## Checkpoint rules

Preserve these receipts as historical evidence. Each repair batch adds its own baseline, decisive reproduction/regression, minimal change, affected-path verification and immutable remote commit receipt. Keep source-level, controller, transport, rendered-flow, installer and physical-device evidence distinct. The [development plan](../../DEVELOPMENT_REPAIR_PLAN.md) defines dependencies and closure criteria for F01–F26.
