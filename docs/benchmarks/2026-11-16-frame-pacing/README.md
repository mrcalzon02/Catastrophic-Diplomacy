# Fleet frame pacing, second pass — 16 November 2026

The 400-ship moving battle improved in all four hosted pairs. Median frame time crossed the 30 FPS budget, from 40.77 to 32.82 ms. Slow frames remain visible. The updated build exceeded 33.33 ms in 217 of 480 frames, and all 480 exceeded the 16.67 ms budget for 60 FPS. The smaller active solo skirmish regressed slightly on this runner.

## Complete desktop clients

GitHub-hosted Ubuntu ran the two revisions on the same four-vCPU AMD EPYC 9V45 runner with Mesa llvmpipe LLVM 20.1.2. Both clients ran at 1280 × 720. Each process opened an actual LWJGL window, used the revision’s own packaged game code and assets, and warmed up before recording. The fixed-input trials time the whole render call through GL completion. Live trials measure wall time between frame starts, including presentation. Percentiles pool equal-length trials within each scenario. The raw output also gives each run separately.

| Scenario | Frames per revision | Baseline p50 / p95 / p99, ms | Updated p50 / p95 / p99, ms | Frames over 33.33 ms, before → after |
| --- | ---: | ---: | ---: | ---: |
| 400 ships, active single-player overview | 480 | 40.77 / 62.50 / 73.17 | 32.82 / 49.94 / 56.09 | 467 → 217 |
| 400 ships, single-player close camera | 80 | 83.91 / 117.32 / 141.86 | 57.30 / 78.12 / 93.82 | 80 → 80 |
| 400 ships, tactical pause | 160 | 44.15 / 55.85 / 63.79 | 27.96 / 36.34 / 38.18 | 160 → 26 |
| Live 16v16 Escort Clash single-player | 320 | 15.95 / 22.54 / 24.87 | 16.58 / 22.94 / 29.18 | 3 → 2 |
| Live 16v16 multiplayer, fleet camera | 200 | 2.60 / 5.27 / 6.22 | 1.94 / 3.22 / 5.28 | 0 → 0 |
| Live 200v200 multiplayer, close camera | 160 | 17.88 / 22.56 / 24.17 | 11.72 / 15.04 / 18.28 | 0 → 0 |

The fixed single-player overview reached tick 98, 1,014 hits and the same 64-bit battle fingerprint in all eight processes. The ordinary Escort Clash entered tick 127 with one hit, 32 missiles and 50 ballistic rounds. With real elapsed time one baseline process finished at tick 137, the other three at tick 138. Among those skirmish frames, 101 baseline and 155 updated frames exceeded 16.67 ms. All 480 active overview frames and all 320 live skirmish frames per revision had a moving camera. Only the paused scenario reused the sky throughout.

An independent local check repeated the live Escort Clash in eight fresh JVMs, four per revision, with 720 measured frames per revision. Local p50 / p95 / p99 went from 24.90 / 77.10 / 184.86 to 26.78 / 66.56 / 201.59 ms. The median and p99 rose on both machines, and the direction varied across individual local pairs. Local host load was substantially higher; all 1,440 local frames exceeded 16.67 ms.

The multiplayer fixture starts a dedicated host in a separate JVM, connects two seats over loopback, submits orders from both, and renders one seat using the production multiplayer panel. Both clients received acknowledgements. The remote renderer uses its existing presentation path, so its absolute times should be compared within that row. Over the 400-ship hosted session, frames above 16.67 ms fell from 107 of 160 to 2 of 160. A more heavily loaded local 400-ship network trial showed the opposite direction in end-to-end frame timing despite reduced render-thread work. At zoom 0.35 its p95 frame interval rose from 404.57 to 505.20 ms. Neither trial measures WAN jitter or physical GPU timing.

## Readability and checks on rendered output

Four matched 1280 × 720 single-player framebuffers covered the overview and close camera. Between 759 and 2,051 pixels differed in each image. Across all four frames three pixels differed by more than 2/255 on any channel, and the largest difference was 5/255 including alpha. Two matched multiplayer views used the same captured 494-entity host snapshot. Fewer than 550 pixels changed per image. Each had one pixel differing by more than 8/255, with a maximum channel change of 11/255. Raw network PNGs in the original artifact use the OpenGL bottom-left origin. The benchmark now writes them upright. A fresh replay reproduced every pixel of the vertically flipped original while preserving the raw framebuffer CRC.

The separate sky sequence compared every framebuffer at two sizes, six sky formations, stationary and panning cameras, and zoom transitions. All 192 paired images matched on the GL3 path and all 192 on the GL2 fallback. The isolated hosted stationary GL3 sky median fell from 5.65 to 2.87 ms; p95 increased from 7.89 to 8.45 ms. On genuine local Mesa OpenGL ES 2.0 and 3.0 contexts, 64 shield-edge frames per API tested rotation, filtering and recharge. Thirteen pixels across 262,144 differed per API by one color level.

[Original tick-98 overview](baseline-tick-98.png) · [Updated tick-98 overview](updated-tick-98.png) · [Complete hosted frame artifact](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/43977721018/artifacts/14283175616)

## Android 10

Four clean debug APK installs ran the in-game 200v200 verifier on one API 29 x86_64 Pixel 2 emulator under SwiftShader OpenGL ES 3.0. Each reached tick 499 with 400 hull sprites, 12,403 shots, 6,767 hits and 8,890 impacts. Each rendered 26 frames at the verifier’s forced 8× speed, roughly 19 simulation ticks per frame. The figures are averages from the two runs of each build. They time elapsed work inside the render callback, including any driver waits, and do not measure physical-device FPS.

| Mean elapsed time | Baseline | Updated |
| --- | ---: | ---: |
| Entire callback | 2,476.47 ms | 1,903.05 ms |
| Simulation per rendered frame | 1,587.89 ms | 1,041.14 ms |
| Remaining render callback work | 888.58 ms | 861.91 ms |
| Captain decisions per simulation tick | 28.11 ms | 10.09 ms |
| Sensors per simulation tick | 43.81 ms | 33.58 ms |

The Android artifact’s legacy JSON labels its output Android 34 and calls these CPU times. The device build fingerprint, GLES report and the client’s monotonic timer establish Android 10 and elapsed callback time. The raw files preserve those labels for traceability. The corrected diagnostic read back the actual Android build and reproduced the four original numeric results. [Android run, screenshots and receipts](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/43977721018/job/133685896139)

## What changed

- **Shields and lasers.** The renderer no longer shades the transparent margins around each shield facing or authored laser frame. The quad still rotates about the authored origin. A one-texel guard preserves linear filtering, including neighboring atlas cells. All original texture samples, shield scans, beam endpoints and animation frames remain available. The build derives bounds separately for the full desktop and mobile textures. The 20 tables, covering 976 frame entries, matched an independent RGBA scan. Older or incomplete texture sets use the full laser cell.
- **Stationary skies.** On eligible GL3 displays the client copies the finished sky at native resolution into an attachment capped at 8 MiB. The first camera movement, zoom, resize, resume or level change forces a fresh draw. Ships, weapon effects, fog, lights and the HUD are drawn over the sky each frame. The moving camera and GL2 path retain the procedural sky pass.
- **Simulation.** A captain can skip a sensor observation when it cannot beat the current target under the existing orders, priority, distance and tie rules. Scouts use a conservative bound on the full direct-observation history window. Fitted tracking memory still records eligible observations, and delivered relay packets still participate. Across an independent 480-tick asteroid replay, exact direct sensor queries fell from 17,697,284 to 10,272,757. Both builds recorded 6,451 hits and matching tactical displays.
- **Connected clients.** Ships reuse weapon emitters until the authoritative hull or weapon fitting changes. The multiplayer renderer culls hulls outside the camera while retaining offscreen sources capable of projecting visible decoys. Local refit, session-reset, bounded snapshot, real two-seat orders, fleet lobby and pinned TLS tests passed. A fixed 400-ship snapshot at zoom 0.35 produced a byte-identical PNG for this change alone, with vertex/index submissions down from 21,669 to 10,383.

## Authoritative combat and transport

The same GitHub job ran both 400-ship builds with identical hardpoints, seed and weather. It discarded a full 480-tick warmup per scenario and measured four more replays. All 4,800 paired tick fingerprints, including warmup, matched. Times below are calling-thread CPU milliseconds per simulation step.

| 400-ship workload | Baseline p50 / p95 / p99 | Updated p50 / p95 / p99 | p95 reduction |
| --- | ---: | ---: | ---: |
| Clear space | 1.96 / 2.51 / 3.17 | 1.28 / 1.75 / 2.16 | 30.4% |
| Asteroids | 4.39 / 5.27 / 6.05 | 3.40 / 4.16 / 4.90 | 21.1% |

Four real two-client socket runs delivered 92 observed snapshots apiece across both seats, with every order acknowledged and equal hashes at matched ticks. Pooled p95 arrival gaps went from 105.06 to 104.33 ms against a 100 ms broadcast interval. The worst individual host tick increased from 54.64 to 61.21 ms across two trials per revision. Isolated local 400-ship tests measured 353,328 to 42,928 allocated bytes per fitting refresh and 99.26 to 26.30 microseconds of CPU. The graphical multiplayer trials above measure the screen separately.

## Where the graphics time went

On the local 1280 × 720 software renderer, a within-process comparison alternated both clipping changes while the same battle advanced from tick 90 to 104. Keeping the integrated simulation and sky identical, p50 / p95 / p99 completed frame time fell from **160.44 / 372.92 / 428.59 ms** to **116.41 / 287.15 / 369.14 ms**, with 120 frames per treatment. The maximum in each shorter, isolated 80-frame shield or laser treatment increased. Those runs and the component-level GPU completion measurements are included in the evidence.

A separate 60-frame profile synchronized after each pass, changing pipeline overlap. On the updated build the median hull stack was about 30 ms, moving sky about 23 ms, fog plus HUD about 14 ms, weapon effects about 6 ms, additive glow about 4 ms and smoke below 1 ms. These figures locate expensive work under the local load. The separate whole-client measurements above include the normal pipeline overlap.

A second profile of the moving overview split the sky into a 20.45 ms nebula shader and 0.49 ms of stars. The fog blend took 6.79 ms, sensor marks 1.74 ms and the HUD 2.52 ms. Fog-mask rebuilds took a median 36.21 ms on the three frames that needed one. The next candidate is limiting the existing sky draw to the region where the optical fog can reveal it. A single guarded rectangle excluded 20.0% of screen area at the ordinary overview and 90.56% at strategic zoom in these two samples. Those are measured areas. Frame-time savings for that change are unmeasured.

## Reproduction and evidence

From separate checkouts of the two pinned revisions, use Java 17, Gradle 8.11.1 and the normal Android SDK. With a 1440 × 900 Xvfb display, run `bash tools/performance/compare-frames.sh BASE_CHECKOUT CANDIDATE_CHECKOUT OUTPUT`. It packages both desktop distributions, compiles the same fixtures once, runs independent processes in balanced order and writes the frame rows, complete images, visual differences and hardware inventory. Run `bash tools/performance/compare-fleets.sh BASE_CHECKOUT CANDIDATE_CHECKOUT OUTPUT` after processing core resources for the authoritative simulation and two-seat snapshot trials. The Android job installs each revision's debug APK on the same emulator in baseline, candidate, candidate, baseline order.

[Measured source comparison](https://github.com/mrcalzon02/Catastrophic-Diplomacy/compare/aec40264e909489083d292a499eca0b8fc7e0190...360f5374a65fe7c41e3f181e96089e0181afe038) · [GitHub Actions run and full artifacts](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/43977721018) · [Raw benchmark tables, local diagnostics and checksums](frame-pacing-evidence.zip)
