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
