# Rail-interceptor implementation and real collision-based balance review

**Tested:** November 2, 2026. **Repository:** `mrcalzon02/Catastrophic-Diplomacy`, `main`.  
**Status:** Normal gameplay uses true swept relative moving-body collision for charged railgun shots. **Numerical weapon balance remains advisory.**

## What is physically implemented

RPD is the fourth genuine hard-kill family after PD laser, FLK and RCW. All eleven native faction technology catalogues have separately mounted `RPD` weapon modules with rank I–IX output/efficiency/resilience/specialist tuning. Each actual purchased emitter keeps its own firing socket, nominal 120–335-unit fitted engagement envelope, per-rank detection/tracking profile, capped pulse-Doppler resolution from the **incoming body's measured radial closing velocity**, 120-unit inner safe-bore exclusion, servo bearing, 0.20-second target lock, precharged ship-power capacitor, high recoil, three paid kinetic cartridges per launch and multi-second reload. Rail projectiles travel unguided at **1210 world units/second** with finite lifetime and actual swept collision. They are **not** laser pulses, flak bursts or rotary ammo streams.

Capacitors receive their initial charge from real ship reactor stores at battle deployment and charge with paid energy/heat during watch; changing targets resets optical lock but cannot create or destroy capacitor charge. `HOLD_FIRE` consumes no defense-shot ammunition. Narrow sabots have high penetration against real torpedo integrity, with poor close-in and small-swarm throughput. World-space moving penetrators and individually servoed rail turrets use approved kinetic atlas/sprite cells. No distribution build or APK was needed to validate Java headless simulation and renderer compilation.

The original implementation could miss an entirely legitimate mid-tick intersection because the physical projectile advanced while treating the hostile body as a **stationary point at its final per-frame position**. Missiles, rockets and kinetic shells now retain previous and current coordinates, and the rail collision uses both relative paths over the actual partial/full 30 Hz timestep. Fighter prior-position data were already available. A pure geometry regression verifies an actual moving crossing intersects and an off-path trajectory does not.

## Verified runs and retained artifacts

| Run | Physical evidence | Conclusion |
|---|---|---|
| [41600726201](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41600726201) | 36 RPD-vs-HOLD_FIRE paired base/rank-IX trials, paid mounting and launch, arena-limit smoke, live renderer compile | Success |
| [41603400426](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41603400426) | Additional **192** rank-III/VI paired range/lateral geometry trials and actual net defender losses; corrected to remain inside the physical attacking gun's true maximum range | Success |
| [41603953046](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41603953046) | Additional **192** matched same-seed moving-body relative-sweep versus historical endpoint-only *enabled defense* A/B comparisons | Success |
| [41604234413](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41604234413) | **Production** swept-body RPD, 36+192+192 scenario sets, finite projectiles and 200v200 arena-bound check, live client renderer compile | Success |

The counts above are distinct experiments; 192 A/B pairs are **two enabled-defense collision methods**, not 192 enabled/HOLD_FIRE comparisons. The 200-vs-200 arena test verifies legal fixed-world spawning, maximum fleet size and projectile lifetimes, **not** sustained 400-ship combat FPS. The workflow artifact `rail-pd-physical-results` contains the machine CSVs `rail-trials.csv`, `rail-role-geometry.csv`, `rail-moving-body-sweep-ab.csv` and `summary.md` (workflow retention configured for 30 days).

## Measured results: why geometry mattered

**Four matched seeds per row**, physical weapon stock and actual ordnance bodies unchanged between A/B arms:

| Real fitted RPD | Threat and distance | Endpoint-only killed | True relative-sweep killed | Shots |
|---|---|---:|---:|---:|
| Tier VI x1 | Guided missiles, 205-unit head-on | 0 / 4 | **4 / 4** | 4 |
| Tier VI x4 | Guided missiles, 205-unit head-on | 0 / 4 | **4 / 4** | 4 |
| Tier III x1 | Standard kinetic, 275-unit offset | 2 | **4** | 4 |
| Tier III x4 | Standard kinetic, 205-unit head-on | 3 | **8** | 8 |
| Tier VI x4 | Standard kinetic, 205-unit head-on | 6 | **8** | 8 |
| Tier III or VI x1 | Armored torpedoes at tested distances | 4 / 4 | 4 / 4 | 4 |

The corrected tier-VI head-on missile cases now prevent **four real impacts** and preserve 125 net simulated shield/armor/hull damage units across the four seeds. No target accuracy cap, overpowered damage multiplier, or invented interception was introduced. The tier-III x4 head-on shell example improved 3→8 tracked shell interceptions; its **actual hull impacts** improved only 1→0, illustrating why body kills and net protection are separate. Physical shot and ammunition counts are unchanged in these matched conditions.

The **36-pair normal production base/rank-IX comparison** also measures:
- Rank 0 RPD x1 versus three heavy torpedoes: three actual intercepts with **three** paid penetrators; x4 also uses three, thanks to reservations held until an actual moving shot exits.
- Rank IX x4 against six fast kinetic shells: **six** physical interceptions using six charged penetrators, versus a disabled-defense comparison of three impacts; not all shells would have hit the defender.
- Rank 0 RPD still fires **no shots** at the particular short-duration base guided-missile and small-shell fixtures. It cannot reliably acquire/lock those targets inside its finite range, and this remains a balancing weakness, not a broken hit check. RPD is not a replacement for cheap RCW or laser CIWS.

## Differentiation and next bounded work

- **PD laser:** fitted independent precise instant pulses, tracking/energy/thermal scheduling, expensive vs saturation.
- **FLK:** paid kinetic flak shell, later detonation of a physically intersected fragment volume, radial fragment-density falloff and anti-air priority. Shells still need continuously integrated flight geometry.
- **RCW:** independent physical spinning gun, short-range radar/IR-gated tracking, fast paid small cartridges, 705-unit/s unguided tracers, effective vs close rockets and drones but weaker against hardened warheads.
- **RPD:** own long-range Doppler signal processing, precharged capacitor, 0.2s target lock, expensive three-cartridge 1210-unit/s unguided sabot, 120-unit blind zone, high damage against hardened torpedoes, poor rate of fire against cheap swarms.
- **PPD, PLS, IMD:** **not implemented**. Next weapon should be PPD sustained single-target charged particle ablation with actual dwell, armor/cooling and failure on power loss, followed by PLS short-range expanding thermal cloud and IMD real guided paid interceptor missile.

Next numerical checks should hold equal paid build+ammunition cost, not simply one versus four mounts; add simultaneous armored torpedoes/fast shells/micro rockets, moving protecting ships, point-defense doctrine settings, multi-axis arrivals, and an actual sustained 200-vs-200 CPU/heap/heat test. Keep sensor sharing, track fusion and command coordination for their separate fleet module implementation and preserve known 200v200 simulation-area caps.

**Release discipline:** Real build, start/run, save/update and required network features may block release. Hit percentages, graphics, style and balance goals are advisory diagnostics only; no full APK package was created during these focused runs.
