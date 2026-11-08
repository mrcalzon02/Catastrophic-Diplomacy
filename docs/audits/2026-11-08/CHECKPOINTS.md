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

## CP2 — fresh tests and reproduced cross-system defects (08:32 AKST)

- **Existing regression pass completed:** 106 named Java `*Smoke` tasks passed within the normal Gradle invocation; 118 actionable tasks, 108 executed, 10 up to date; elapsed 96 seconds. All 22 Python unit tests also passed. These checks cover core campaign/tech/orders/fighters/sensors, menu routing, timing/interpolation, update policy and real networking.
- **TLS is now proven for loopback fixtures:** unchanged `BattleServerTest` passed with 2, 32 and 400 ships, verified server-owned teams, orders, authenticated reconnect and sequence watermarks. Trusted-LAN terminal/result, mixed-tier and submitted-fleet tests also passed. Historical failed TLS receipts are not current failure evidence; multi-device/WAN acceptance remains separate.
- **Five actual campaign-controller failures reproduced** with temporary files and minimal Gdx application/files stubs: available mission31 incorrectly capped at1 instead of5; Results pointer RETRY does nothing; TechVII confirmation selects old TechVI budget card (15m displayed versus28m actual quote); corrupt authority+backup prevents both Continue and New Campaign; failed settlement goes to ordinary victory Results and leaves earned settlement uncommitted even after storage recovers and Return is selected. These are handler executions, not screenshots or whole-device tests.
- **Two actual Battle failures reproduced:** causally identical observed enemy tracks produced different navigation after only unseen present position changed; real researched particle-PD fixture produced70 paid beam ticks normally versus0 while withdrawing despite5 actual incoming missile launches in each fixture. The stationary retreat flag isolates defensive eligibility; full moving-retreat acceptance remains required after repair.
- **Network component/source probes:** same-tick session replacement retains old positions; later-tick replacement blends from the old session; mirror class is CORVETTE and contains no projectile/craft entities; malformed faction frames can pass transport framing/hash and fail on render parsing. These are controlled fixtures, not an attack or a physical network walkthrough.
- **Correction to CP1:** fighter optical/radar selection IS integrated through persisted `design.fighterSeeker` into Battle spawning and shipyard controls. The early inference from unused convenience setters was invalid and is withdrawn. Both existing UI and blueprint/runtime seeker tests passed. The remaining separate gap is active sensor scan doctrine selection/persistence.
- **Correction to preliminary network note:** TLS custom-lobby constructor exists. Its command-line/UI exposure and encrypted submitted-fleet acceptance still need completion; do not describe the core constructor as absent.
- **Live release verification:** all16 jobs (15 build/runtime plus publication) in run42349197474 are SUCCESS and match baseline0159042; existing screenshots are artifacts of that run, not new local graphical tests.

Evidence saved in this directory: normal functional log/result manifest, Python/API/atlas logs, constrained-wiki logs/results, focused campaign/combat/network reproduction source and observations. Reproduction exit0 means the specified defect was successfully observed; it does not mean the defect was repaired. No production Java or workflow change has been made.

Next: finish dependency ordering and subsystem/issue reconciliation, publish the complete audit and development plan, verify immutable remote content.

## CP3 — complete audit, repair plan and independent review (08:48 AKST)

- **Completed the master audit:** 26 numbered findings, intended campaign/skirmish/network/update paths, implemented-system protections, issue reconciliation and explicit evidence boundaries. Five campaign controller defects, three combat defects and the constrained-heap search failure were reproduced; controlled network components established additional integration faults. Source-only observations and design decisions remain labeled separately.
- **Completed the development repair plan:** batches B0–B9 specify dependencies, minimal repair boundaries, decisive acceptance tests, data/protocol compatibility and rollback. Each future batch uses baseline → reproduction → minimal change → applicable platform acceptance → immutable remote checkpoint. Repair priority is not a new release gate.
- **Third combat defect confirmed:** ordinary fitted firing exhausted local solver capacity while a distinct eligible FCM donor received no shared reservations. Fresh reception timestamps prevent causal eligibility from maturing. The repair must preserve finite budgets and signal transit rather than deleting the delay.
- **Supplementary execution PASS:** two identical 480-tick 200v200 combined-arms runs, fingerprint `9192d36f3d2785df`; six armed fighter types and wing-codec bridge; 506 approved hull maps / 9,515 anchors; 18 fixed-step timing environments plus six variable-frame stall cases. Device FPS, thermal behavior and rendered presentation remain unverified locally.
- **Independent skeptical review completed:** the principal findings survived source/fixture review. The late-license correction is precise: the selected card is wrong, while the pending quote and confirmation notice are correct. The TLS lobby factory and persisted fighter seeker selection already exist. No additional crash or universal launch failure was invented.
- **Reconciled documentation:** current README technology/research, live carrier/drone/ECM/magazine and TLS/build claims now match the baseline; the CI guide describes the actual 22-workflow trigger inventory. Two early campaign design receipts are explicitly historical where their six-tier, split-save and missing-ledger claims are superseded. Broader content/device acceptance remains open.
- **Reproduction instructions checked:** all three retained probe sources compiled and ran against ordinary Gradle-built classes/resources. Their exit 0 confirms the recorded defects, not a repair. The final command receipt is `documented-probes-result.json`.
- **Delivery preparation:** main audit, repair plan, three domain appendices, independent review, logs, probe sources and a content manifest are assembled. Relative document links and whitespace checks pass. The final delivery commit and exact remote content must be read back before claiming publication.

Production Java, assets, workflows, release request, signing configuration and published binaries are unchanged. The next implementation work begins with B0's single-job coverage/contract corrections and B1's recoverable campaign settlement. Combat, search and updater work can proceed independently once their relevant regression boundaries exist.
