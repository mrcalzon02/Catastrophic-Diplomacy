# Catastrophic Diplomacy — 13 November 2026 pre-release benchmark checkpoint

## Scope and immutable source evidence

- **Audited pre-request main commit:** `d4a3d094c0e3b6e020074890d0637deef845a598`
- **Prior published prerelease:** [`cd-v0.1.5-dev.695`](https://github.com/mrcalzon02/Catastrophic-Diplomacy/releases/tag/cd-v0.1.5-dev.695), source `b5e3695106a2179a0ddf7d37dff5eec37383c649` (11 Nov 2026).
- **Distance to published release:** 78 commits, ahead only, as of the audit. Those changes include simulation/sensor/PD performance work, fighter and campaign ownership, networking and remote interpolation, Android/desktop updater repairs, technology and shipyard changes, and additional parity tests.
- **Source-matched regression:** [run 43352117193](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/43352117193) — completed success on the audited commit (13 Nov, 02:25–02:30 UTC). One `objective-regressions` job including Java 17, Android compile, desktop compile, server checks, core combat/campaign and updater tests. Optional real desktop-GL screenshot capture was **skipped** (not part of the release gate).
- **Prior complete packaged-release proof:** [run 42933752089](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/42933752089) — all 16 jobs passed on the prior published source, including Windows MSI install/launch/uninstall, Linux packaged-app launch, retained-signer Android, emulator and Android campaign matrix, and release publication. This **does not** certify the subsequent 78 commits.

## Reproducible current-source CPU/functional baseline

All samples below were read from the exact-source `objective-regressions` job log of run 43352117193. They are *headless JVM/CI diagnostics*, **not** GPU time, physical-device FPS, or device thermal acceptance. CPU stopwatch values are diagnostic and not release thresholds.

| Workload / seed | Objective result | Observed diagnostic |
| --- | --- | --- |
| Tactical movement, 1 per side / seed 20261112 / 480 ticks | 2 ships, capital mean path 626.348, nominal still 0% | step p50 0.0551 ms; p95 0.1345 ms |
| Tactical movement, 16 per side / seed 20261127 / 480 ticks | 32 ships, capital mean path 668.817, nominal still 0% | step p50 0.4681 ms; p95 0.7919 ms |
| Tactical movement, 200 per side / seed 762244 / 480 ticks | 400 ships (200 capitals, 200 escorts); mean paths 653.879 / 632.313; nominal still 0%; 11,755 fire events, 6,401 hits, peak 322 craft | step p50 **5.3123 ms**; p95 **8.9913 ms** |
| Deterministic combined arms 200v200 / 480 ticks | PASS; 5,008 hits, 83 destroyed, peak 699 missiles and 275 craft; fingerprint `d170613694ec8a65` | full simulation correctness, not FPS |
| Photon arrival shell pruning | PASS; 104,204 exact-oracle cases, 9,924 arrivals | optimized 5.599 ms vs exact 14.032 ms in same-run microbenchmark |
| Swept projectile collision broadphase | PASS; 150,080 parity cases, 37,500 near-path cases, 228 hits | broadphase 10.850 ms vs narrow-only 18.862 ms in same-run microbenchmark |
| Opponent index parity 200v200 / 90 ticks | PASS; same deterministic fingerprint `350dcf00f8f9212` | indexed 641.129 ms vs full fleet scan 699.229 ms in same-run JVM comparison |
| Combat phase observer, 200v200 / 90 ticks | PASS observer/fingerprint parity | mean per-tick captains 5.778 ms; projectiles 3.284 ms; sensor network 0.116 ms; support 0.132 ms; craft/cleanup 0.833 ms (instrumented run) |

The **prior** packaged release's Linux CI combined-arms run recorded 5,086 hits, 84 destroyed, 756 peak missiles and 287 peak craft on its earlier code. Those outcomes changed with code and rules; they are not apples-to-apples frame-time measurements and do not support any claimed percent FPS gain.

## Release decision / qualification

A fresh *benchmark checkpoint prerelease* is justified: the current main is materially ahead of the latest downloadable APK/MSI/ZIP, and source-matched focused regression is green. The candidate is **not certified as released by these smoke results**. Use the existing explicit `.github/release-request.json` trigger on **main**. The release workflow assigns a fresh `cd-v0.1.5-dev.<run_number>` tag and must pass all Windows/Linux/Android build and functional jobs **before** it publishes exact-source APK, MSI, ZIP, manifest and checksums. Verify the published release, tag-to-commit identity, asset names, signer and actual job readback. Never retag or overwrite previous releases. Published prereleases count as update candidates; drafts do not.

**Known acceptance gaps and nonblocking observations:** No fresh physical Android/Fire device FPS, thermal, touch/pinch verification, or GPU frame-time comparison at equal hardware/scene/speed. Real-GL projectile capture was skipped. Previously reported oversized projectiles, fighter/carrier visual scale or docking behavior, and slow/low-motion feel need an actual downloadable-build visual check; do not claim these are solved by CPU-only smokes. Review 0.5×–3.0× pace and campaign balance after playtest; numeric/appearance differences alone are advisory. If release packaging, runtime launch, required multiplayer, updater, signer continuity, checksum or persistence fails, **block** publication.

No automatic packaging workflow is added, and no release or hardware certification is implied by writing this audit document.
