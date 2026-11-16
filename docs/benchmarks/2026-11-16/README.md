# Catastrophic Diplomacy fleet performance, 16 November 2026

Source comparison from `c0435a33da3b9b38e591d6474fbf881f51b961e1` to `a70f0b1d72ae37c476acf2d71a0b38a533f6de8b`. The largest verified simulation gain was in asteroid weather. The renderer also got faster, while difficult frames and late multiplayer snapshots remain visible in the measurements.

[Source changes](https://github.com/mrcalzon02/Catastrophic-Diplomacy/compare/c0435a33da3b9b38e591d6474fbf881f51b961e1...a70f0b1d72ae37c476acf2d71a0b38a533f6de8b) · [Raw local and hosted samples, fixtures and reproduction commands](fleet-benchmark-evidence.zip) · [Independent GitHub run](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/43972797718)

## Independent GitHub results

GitHub-hosted Ubuntu 24.04 completed the baseline and candidate in the same job. Both clear-space and level-one asteroid scenarios used a 400-ship deployment, seed 762244, a full 480-tick warmup pair and four measured 480-tick pairs. All 4,800 per-tick combat fingerprints matched across both scenes, warmup included. The artifact records both commit SHAs and the identical hardpoint-resource digest.

| GitHub benchmark | Baseline | Candidate | Change |
| --- | ---: | ---: | ---: |
| Asteroid battle, p95 simulation tick CPU | 9.912 ms | 6.318 ms | 36.3% lower |
| Asteroid battle, p99 simulation tick CPU | 10.762 ms | 6.803 ms | 36.8% lower |
| Clear-space battle, p95 simulation tick CPU | 3.650 ms | 2.963 ms | 18.8% lower |
| Clear-space battle, p99 simulation tick CPU | 4.370 ms | 3.722 ms | 14.8% lower |
| Two clients, 400 ships, combined snapshot-reader CPU | 282.16 ms | 130.12 ms | 53.9% lower |
| Same clients, p95 interval between snapshots | 106.21 ms | 105.61 ms | 0.60 ms lower |

Four independent socket trials (two per revision) delivered all 46 observed snapshots per seat and accepted each client's order. Comparing the raw rows found equal simulation hashes and entity counts at every matched tick across builds. The workflow also passed the normal desktop and Android compilation, campaign, networking and updater checks. Three real 1280 × 720 projectile framebuffers at ticks 19, 57 and 95 passed the existing capture checks and were archived.

[GitHub paired benchmark artifact](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/43972797718/artifacts/14279282285) · [GitHub projectile graphics artifact](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/43972797718/artifacts/14278763878). The evidence ZIP alongside this report also contains the complete hosted benchmark CSVs and the projectile capture manifest.

## Single-player simulation

Local results use Java 17, the packaged 110-hull hardpoint map and the same 400 fitted ships (seed 762244). Both versions ran in separate class loaders in one JVM. The harness alternated which version ran first on every tick, checked each production fingerprint and discarded a full warmup replay before measuring four more. Times are calling-thread CPU milliseconds per simulation step. The two scenarios had distinct weather and durations.

| Workload | Metric | Baseline | Candidate |
| --- | --- | ---: | ---: |
| Asteroids, first 120 ticks per replay | Mean whole step | 12.73 ms | 6.10 ms |
| Asteroids | p95 / p99 whole step | 18.57 / 29.70 ms | 8.98 / 13.17 ms |
| Asteroids | Mean sensor phase | 8.92 ms | 3.15 ms |
| Clear space, first 480 ticks per replay | Mean whole step | 4.00 ms | 3.68 ms |
| Clear space | p95 / p99 whole step | 5.74 / 10.19 ms | 5.30 / 10.76 ms |
| Clear space | Mean projectile phase | 1.30 ms | 0.71 ms |

Weather enables the original short-range formation radio. A scout without personal tracking memory now skips a sensor query only when every recorded position of the target is too far away to improve either the nearest-spotter ranking or the scout’s broadcast list. Scouts with tracking memory retain their observation path. In a separate 480-tick asteroid replay, sensor queries fell from 32,733,062 to 11,500,093. Both builds recorded 6,451 hits and matching combat and sampled tactical-display results. Across the two timed scenarios, all 3,000 tick fingerprints matched, including warmup.

A large kinetic volley now indexes each target’s collision diameter by horizontal position before testing intersections. Candidate checks preserve original roster order, hull/craft tie handling and the exact swept collision solver. The tests exercised boundary crossings, overlapping targets, moving/teleported contacts, reinforcements, retirement, fitted sensor equipment and a full-scan reference.

## Desktop rendering and readability

The first captured 400-ship frame issued 1,975 real GL draws. Synchronized pass measurements attributed 1,837 to hulls and their directional shields. Each facing used a separate texture. The change packs the original shield facing pixels into one sheet per hull art, with padding and edge clamping. Overlapping ships retain their draw order, also during recharge. The original single-texture material remains available on devices that cannot compile the atlas shader.

| 1280 × 720 Mesa llvmpipe | Metric | Baseline | Candidate |
| --- | --- | ---: | ---: |
| 400 deployed, fixed scene and camera | Median GL draws / texture binds | 1,973 / 2,137 | 213 / 377 |
| Balanced alternating shield paths, 200 measured frames each | Median CPU time submitting draw work | 36.80 ms | 11.67 ms |
| Same alternating test, GPU completion synchronized | p50 / p95 / p99 completed frame | 76.73 / 121.13 / 164.49 ms | 68.21 / 108.78 / 163.62 ms |
| Live 16v16 active skirmish, 80 uncapped frames per build | p50 / p95 start-to-start interval | 29.92 / 45.74 ms | 28.74 / 43.26 ms |
| Same live skirmish | Frames over 33.33 ms | 26 of 80 | 16 of 80 |

The alternating test switches material paths in one process, balances their order against physics steps, and times through GL completion. The live test used real frame intervals with no forced GPU synchronization. It entered at tick 127 with 32 missiles, 50 ballistic rounds and one hit. Both runs ended at tick 136 with three hits and the same fingerprint. The live result covers a short engagement on a shared processor. The 400-ship test still exceeded the 33.33 ms budget even at its median.

The first experimental shader batched eight simultaneous texture samplers. It preserved the desktop screenshot bytes but performed worse when timing through GPU completion. It was discarded. The final implementation uses a single atlas sampler. A separate real GLES 2.0 software context exercised all eight atlas regions with the recharge scan on and off. Using high precision for the atlas coordinates reproduced all 16 original-material RGBA readbacks byte for byte in this synthetic fixture.

The two matched 1280 × 720 desktop captures each changed 228 of 921,600 pixels (0.025%), with a maximum change of one level on a 0–255 color channel. The deliberately forced fallback reproduced the original tick-90 capture byte for byte.

| Capture | Original | Optimized |
| --- | --- | --- |
| Tick 90 | [Before](before-tick-90.png) | [After](after-tick-90.png) |
| Tick 94 | [Before](before-tick-94.png) | [After](after-tick-94.png) |

The 400-ship fixture started with 200 ships per side. At tick 90 the in-battle HUD showed 195 and 189 survivors, 968 hits, 180 craft and art for all 384 surviving hulls. The independent skirmish size selector at the top retained its 16v16 default in this injected fixture.

## Complete client frame times

The shield comparison isolates one renderer change. Separate runs also compared the **complete baseline client against the complete candidate client**, in fresh JVMs on the shared software renderer. The 400-ship test used the same 100 measured frames per process and reached tick 97, 997 hits and the same fingerprint in all eight processes. The 32-ship test started each process at the same active engagement. With real elapsed time and a fixed display-frame count, the faster and slower runs reached different final ticks.

| Whole client, p50 / p95 / p99 in ms | Baseline | Candidate |
| --- | ---: | ---: |
| Fixed-input 400-ship frame through GL completion, four runs per version | 98.01 / 220.05 / 340.91 | 112.27 / 200.86 / 320.18 |
| Live 32-ship frame interval, two runs per version | 47.81 / 108.93 / 247.59 | 33.24 / 105.13 / 185.51 |

The complete 400-ship client improved its slowest frames while its median got 14.26 ms slower on this software renderer. The controlled atlas test and the full-client test therefore support different conclusions about typical frame time. The live 32-ship client exceeded 33.33 ms in 231 of 280 baseline frames and 139 of 280 candidate frames. All 400 fixed-input frames on each version exceeded that budget. The integrated 400-ship run and the alternating material test have different timing characteristics. See the raw per-frame rows and run order in the evidence ZIP before extrapolating to a hardware GPU.

## Multiplayer and projectile presentation

Two production clients used actual loopback TCP sockets against the authoritative server, acknowledged independent orders and agreed on the hashes of every compared snapshot. The 400-ship series used three fresh JVMs per version and the 32-ship series used two. Measurements span 135 ticks after engagement warmup. Each timing below is the median of the run-level statistics.

| Workload | Metric | Baseline | Candidate |
| --- | --- | ---: | ---: |
| 400 ships, two clients | Combined client reader CPU over the window | 415.4 ms | 189.0 ms |
| 400 ships | p95 snapshot arrival interval | 206.4 ms | 214.6 ms |
| 32 ships, two clients | Combined client reader CPU over the window | 125.0 ms | 90.1 ms |
| 32 ships | p95 snapshot arrival interval | 134.2 ms | 162.1 ms |
| Replay of one 494-entity snapshot, 24 decodes | Median allocated bytes | 92.79 MB | 42.21 MB |

The client shares a single buffered stream between handshake and snapshot reads and reuses validated fittings across identical loadouts in one snapshot. The snapshot bytes and protocol stayed compatible. Local TLS, reconnect, order ownership, fleet readiness, malformed snapshot handling and terminal game results passed. The intended snapshot interval is 100 ms. The worst 400-ship interval observed after the changes was 414.5 ms.

The network renderer previously interpolated hulls but drew missiles, ballistic rounds and craft at their most recent host coordinates. Those bodies now share the hull presentation clock. New bodies appear at their first confirmed position and removed bodies disappear immediately. The renderer reuses the decoded frame and updates fittings only on an authoritative refresh. At six display frames per snapshot, the focused moving-body test produces six ordered positions from each pair of confirmed endpoints. Early arrivals, reused IDs in different entity kinds, reconnects and rewound sessions are covered. The measured socket cadence limits how much a client can smooth.

## Coverage and reproduction

The local desktop measurements used software GL on an AMD EPYC shared host. They establish what changed in the same fixture. A physical Android/Fire or desktop GPU, sustained thermal behavior and a real WAN were outside this measurement. Desktop and Android compilation, authoritative gameplay and networking checks are recorded in the linked GitHub job. Simulation CPU time, GL submission, completed-frame time and network arrival intervals measure different work.

The current 400-ship rendering limit is the time through frame completion. Fewer shader submissions freed CPU time, but all 400 frames of the complete candidate still exceeded the 30 FPS budget. Physical GPU pass timings could distinguish overdraw and shader execution from driver/host contention. In multiplayer, the variable arrival tail on the shared host also warrants measuring host tick scheduling and packet delivery separately.

The supplied shell comparison expects Java 17, two checked-out revisions and `:core:processResources` output from the candidate. It puts both builds on the same production resources. The opt-in GitHub step uses the previous main revision and retains its reports as an artifact. The hosted comparison covers 480 ticks in both weather settings. The local asteroid table above covers 120.

```bash
gradle :core:processResources
bash tools/performance/compare-fleets.sh /path/to/baseline "$PWD" /path/to/results
gradle :desktop:combatFrameBenchmark -Dbench.output=/absolute/path/to/frames
gradle :desktop:combatFrameBenchmark -Dbench.live=true -Dbench.engaged=true
```
