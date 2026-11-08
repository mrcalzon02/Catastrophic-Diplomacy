# Audit checkpoints — 8 November 2026

## CP0 — source and release baseline (08:20 AKST)

Status: **audit in progress; no completion or whole-product certification claimed.**

- Request: comprehensive Catastrophic Diplomacy audit of intended player flows and systems, with regular durable checkpoints and a development document in repair order.
- Authoritative source: `mrcalzon02/Catastrophic-Diplomacy`, `main`, audited code SHA `0159042e47a9271e5e3b077756f5c4d57763a688` (7 November 2026 UTC). Complete local clone obtained and clean at start.
- Public prerelease `cd-v0.1.5-dev.693` targets this exact SHA and has Android APK, Windows MSI, Linux ZIP, checksums, identity, manifest and signature evidence assets. Draft `dev.692` is not a published release. Release metadata alone is not a fresh device test.
- Repository inventory: 215 production Java files (46,129 lines), 179 Java test files (24,853 lines), 115 Markdown documents. Counts include core/game/server/desktop/Android directories.
- All 22 workflow files were parsed: one path-filtered automatic main smoke job; one explicit-release workflow; one PR workflow; one manual publication recovery; 18 manual specialist diagnostics. No workflow is being added or dispatched by this audit. Documentation-only checkpoint paths match none of the automatic push filters.
- Current `docs/CI_WORKFLOWS.md` is stale: it claims every-main-push publication and four entry points; current YAML and `docs/UI_OPERATIONAL_AUDIT.md` describe explicit releases and manual diagnostics. Treat live source as authoritative.
- Prior font/volume/button-centering/updater-feedback changes are present in the audited source and must not be recorded as missing implementation merely because older notes predate them.
- Current open project items: #3, #24, #25, #26, #28, #29, #30, #31, #32. Existing PR #23 targets a separate old Play branch; it is not current-main release evidence.
- Separate review lanes: player progression/save/shipyard; combat/AI/sensing/fighters/research; multiplayer/updaters/CI/platforms. Root review owns cross-system synthesis and test evidence.
- Java 17 (Temurin 17.0.21+9) and Gradle 8.11.1 downloaded for isolated local verification. No signing secrets or key generation used. No game, workflow or release code changed.

### Evidence classification

Every finding will distinguish **reproduced defect**, **source-confirmed defect**, **integration gap**, **verification gap**, **design decision**, and **already implemented**. Functional build/launch/run and required multiplayer/updater failures retain the user's release-blocking rules; artistic quality, balance and presentation diagnostics are not converted into release gates.

### Next checkpoint

Record actual compile/test results, first verified flow breakages, and source references. Final deliverables will be `docs/PROJECT_AUDIT_2026-11-08.md` and `docs/DEVELOPMENT_REPAIR_PLAN.md`, with bounded evidence under this audit directory. No result will be called fixed merely because it appears in a plan.

## CP1 — compilation and first verified findings (08:26 AKST)

- **Fresh compile PASS:** Gradle 8.11.1 with Temurin 17.0.21+9 compiled `:core:compileTestJava :game:compileTestJava :server:compileTestJava :desktop:compileJava`. Initial plugin-resolution failure was resolved by honoring the executor's configured HTTP proxy and system CA truststore; it was not a source defect. Independent `javac --release 17` also compiled all 392 relevant production/test files, with project release JARs excluded from the compile classpath. Android packaging not claimed locally.
- **Tool checks PASS:** all 22 existing Python unit tests; Android API compatibility scan; story atlas integrity (500 missions, 250 optional schematic lanes, 231 unrecorded unique voice cues).
- **Release bytes verified:** published Linux ZIP `dev.693`, 195,158,254 bytes, SHA-256 `321e46748165638c56ee661295dd4f71e5eb8b5133cfa151dd4ac20b18e71ac7`, matches published FULL-SHA256SUMS. Exact release-baked hardpoint resource retained for direct tests.
- **Reproduced constrained-heap defect:** `WikiMemoryProfile` passes at 16 MiB with 75,476 articles / 73,260 upgrades. `WikiArbitrarySearchProfile` fails at 16 MiB with `OutOfMemoryError` at `TechnologyCatalog.get:297` / `GameWikiCatalog.search:639`. At 32 MiB it passes; `plasma` took ~667 ms, `power x1.00` ~1,360 ms, broad `tech` ~0.088 ms in this host run. Timings are diagnostic; this is not physical Android acceptance.
- **Source-confirmed campaign mismatch under reproduction:** dock activation caps selected branching mission using `min(selectedMission, campaign.unlocked())`, despite `CampaignProgress.available()` allowing later front missions. Example after mission 1: available mission 31 has cap 5; linear frontier 2 has cap 1. Detailed handler-level check pending.
- **Source-confirmed PvP integration gap:** server emits missiles/ballistics/craft but remote client consumes only ship rows and maps hulls to CORVETTE. The authoritative host battle may be functional while client presentation/selection lacks actual entity fidelity. Exact repro/repair criteria under review.
- **Source-confirmed unreachable configuration:** active-scan and fighter optical/radar configuration methods have test callers but no normal game/server selection path. Core mechanics do not establish playable integration.
- **Historical issues reconciled:** continuous FREE maneuvering, explicit HOLD_RANGE, fighter landing/rearm, atomic campaign ledger and detailed after-action code exist; older documents describing them as absent are stale. Remaining lifecycle failures are still being traced.
- **Verification boundary:** local Xvfb could not create its Unix display sockets (`EPERM`); no fresh graphical launch or screenshot claimed. Existing hosted-runner evidence must be attributed to its exact source SHA.
- Normal functional Gradle regression groups are running, including campaign, mixed fleets, standing orders, update policies, real server checks, menu routing and timing. No results will be inferred before the processes complete.

Next: reconcile functional failures with source and preserve focused reproductions; finish coverage matrix, risk classification and dependency-ordered repair batches.
