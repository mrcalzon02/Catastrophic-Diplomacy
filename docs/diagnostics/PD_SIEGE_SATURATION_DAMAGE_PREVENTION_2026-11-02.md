# Point-defense saturation and actual protection: verified tuning report

**Local review date:** November 2, 2026 (Alaska). **Repository:** `main`.
**Status:** Physical CIWS and researched sustained rocket-motor adjustments implemented and verified with headless GitHub Actions; balance remains an advisory iteration.

## Engineering changes actually applied

1. Installed precision laser CIWS mounts retain their separate researched hardpoints, firing timers, muzzle positions and target locks. Per-hull expected-damage reservations prevent needless duplicate targeting of one fragile rocket, while armored torpedoes can receive combined fire.
2. A live installed rocket rack now retains the **actual purchased research tier** across its shipyard fitting, per-ship clone and launched physical rocket. Only its sustained-burn motor gains a linearly increasing thrust factor from 1.0 at research rank 0 to 2.0 at rank IX. The previously attempted `Spec.speed()/size.terminalSpeed` inference was discarded after an experiment showed that it made no measurable difference. Boost/coast motors and unguided flight headings remain unchanged. The researched rocket maximum speed and finite physical flight/magazine are still enforced.
3. Production fitted precision CIWS now services **four optical tracks per physical emitter** at full acquisition speed. Beyond that load, new-target acquisition time multiplies by `min(4.0, max(1, tracks/(mounts*4))^0.90)`. This changes elapsed tracking time, not laser accuracy, penetration, target hit points or free ammunition.
4. The previous no-overload controller, optional alternate capacities/exponents, extra motor thrust and alternate optical dwell are explicitly **pre-tick-one laboratory controls only**. A production battle uses the researched thrust and bounded four-channel CIWS scheduling without calling any debug setter. The legacy unfitted simulation-fixture path is preserved.

Primary source commits: [rocket stage model](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/c4a176ed2dd9d3346464ee8ed68f68de99232e33), [shipyard research propagation](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/8a3001ed89bb979c385874fc2a078de4c1e566e3), [actual rocket flight](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/1d437b640f9bf9e7c9bcdc2bb007550d5912c4ac), [fitted-motor invariants](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/97c8e122f6d3f81b801bbacf181e6f95352cda0d), [production optical scheduling](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/0fa49ae9c5e7fc7fcb3d7dab9471941c3bb45a48), [diagnostic baseline semantics](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/7ff1bf4f8afdb52ee275f631b38bddbdd70a55b7) and [three-seed no-PD controls](https://github.com/mrcalzon02/Catastrophic-Diplomacy/commit/d672b93ece07de3311375be2453ea04a05d8eae4).

## Evidence and full machine-readable reports

- [Motor stage verification and true physical thrust](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41569948353): all four headless tasks pass, 256 load-test pairs, zero load errors. The diagnostic checks real fitted rank 0/III/VI/IX, copied rack state and live commissioned ship. Motor sensitivity: `motor-thrust-physical-ab.csv`.
- [Four-channel capacity sensitivity](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41570456412): all diagnostics pass. CSV: `optical-track-capacity-ab.csv`.
- [Optical exponent sensitivity](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41570906245): all diagnostics pass. CSV: `optical-workload-exponent-ab.csv`.
- [Current normal-gameplay CIWS run](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41571311510): all four headless tasks pass; 256 load-test pairs, zero invalid cases.
- [Physical fighter-wave regression](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41571255945): 528 real enabled/HOLD_FIRE pairs, zero invalid experimental cases.
- [Corrected three-seed impact-prevention run](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41571681852): 792 ladder cases, 12,096 physically launched PD-active rockets, 7,099 physical rocket interceptions and 3,249 real rocket impacts across mixed modules, tiers and motors. All four Gradle diagnostic tasks passed, including 736 additional physical-threat scenarios and 256 timed-load scenario pairs with zero load-test failures. Endgame tier-IX siege scenarios now have THREE matched no-PD seeds. Download machine-readable files from the workflow artifact `full-point-defense-interception-matrix` (retention configured to 30 days).

The 792/736/256 scenario counts cover different experimental dimensions and **must not** be summed as equivalent battle trials. None triggered a full desktop or Android package rebuild.

## Same-rank siege saturation in ordinary gameplay

All comparisons below are real four-rack SIEGE batteries (16-round each) against four fitted precision CIWS hardpoints, matching stage IX/IX, at 440 world-unit start distance. Each aggregate has 3 deterministic attack seeds and 192 real launched rockets. Optical lock uses **production 1.00 dwell** and **production four-channel, exponent-0.90 scheduling**.

| Motor / defender | Intercepted | Escape PD | Terminal 90-unit entries | Actual impacts | Matched PD-off impacts | Impacts prevented |
|---|---:|---:|---:|---:|---:|---:|
| Boost/coast / stationary | 140 / 192 | 27.1% | 62.5% | 50 | 72 | 22 |
| Boost/coast / moving | 135 / 192 | 29.7% | 63.0% | 45 | 45 | **0** |
| Sustained burn / stationary | 162 / 192 | 15.6% | 51.6% | 28 | 72 | 44 |
| Sustained burn / moving | 155 / 192 | 19.3% | 43.2% | 12 | 48 | 36 |

Measured *protection-loss reduction* over those paired controls: boost/stationary 2,693.5, boost/moving 0.0, sustained/stationary 5,386.9 and sustained/moving 4,407.5 simulation damage units. The protection accounting includes shield/armor/hull losses; do not equate it to number of direct hull hits. These are only three seeds per case: percentages are useful exploratory readings, **not confidence bounds**.

**Crucial deficiency:** The boosted salvo on a moving ship still lands 45 impacts with and without PD, despite 135 intercepted rockets when PD is active. This is not evidence that the laser events were fake—the live interception counters and physical bodies are real—but it demonstrates that body-kill counts can grossly overstate net protection. The current urgency ranking uses approximate time-to-range; it does not comprehensively rank unguided rockets by their physical probability of striking the defended hull after evasion. Investigate an actual closest-approach/target-motion score with live geometry and same-seed damage outcomes **before** adjusting weapon accuracy.

## Cross-tier fairness and adverse effects

The four-channel rule preserves low-volume base-tier optical acquisition. In the explicit 0/0 rank experiment with four real 4-rocket racks, all five scheduling exponents (0.50, 0.65, 0.75, 0.90 and 1.00) returned the same actual base-tier counts in each matched formation. Boost/coast stationaries had 11/48 intercepted and sustained stationaries 27/48 intercepted at the normal lock duration. This is intentionally weak baseline PD against concentrated rockets, not a proposed new nerf.

The previously tested two-track per emitter policy was **not** selected: although it caused stage-IX siege escapes of approximately 29.7% boosted and 24.0% sustained, it also worsened base-versus-base sustained escapes from 43.8% to 58.3%. Four tracks with exponent 0.90 preserves those smaller base salvos while allowing larger researched waves to saturate optical scheduling.

The expanded earlier [genuine 0/0, IX/IX, IX/0, 0/IX research parity](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41567586907) verified the expected broad direction—advanced offensive hardware can break base defenses, while advanced PD suppresses base rockets. This is not a final economic credit-for-credit parity finding.

The 152 summarized cases of the [additional physical-threat matrix](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41571311510) were compared with the pre-scheduling [control](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41569948353): 146 retained the same intercept/impact/laser-shot totals. Six changed slightly, chiefly micro-missiles, cascade/cluster carriers and related one-emitter saturation fixtures. Examples: stage-IX single-mount micro swarm fell from 6 to 5 interceptions and 7 to 8 hits; missile cascade from 4 to 2 interceptions and 10 to 12 impacts. These **advisory** regressions deserve more seeds and closer-threat analysis, not suppression or release blocking. FLK's physical shell/ammunition logic and defensive-fighter mechanics were not modified.

## Required next engineering/testing work

**P0 – Prevent damage, not just score kills.** Introduce a physics-grounded future closest-approach calculation for dumb rockets and fast shells using known velocity/flight lifetime, actual threatened-hull trajectory and the real collision radius. Test whether selecting rockets likely to intersect the protected hull can improve moving-target damage prevention while preserving `EARLY_INTERCEPT`, `CLOSE_IN`, `FASTEST_IMPACT` and protection-scope doctrines. Track each shot ID against later terminal breach/impact rather than inventing an effectiveness bonus.

**P1 – Multiple engagements and confidence.** Expand measured rocket defense to at least three launch distances (near, existing 440, far), multi-axis simultaneous arrivals, mixed armored torpedoes plus micro-rockets, and at least eight deterministic seeds before accepting tight numerical claims. Compare 1/2/4 identical mounts, 2+2 laser/flak, and four separate ships at equivalent paid costs. Distinguish missed unguided rockets, rocket timeouts and successful physical intercepts.

**P2 – Efficiency and large fleet throughput.** The existing per-hull reservation map is reused, but per-emitter threat-list rescanning persists. Cache physical contacts once per hull per tick with per-socket research/range filtering, then profile CPU time, allocations and actual 200v200 frame progression. Do not replace physical threat objects with synthetic Monte Carlo counters.

**P3 – Expand hard-kill mechanisms carefully.** Preserve physical, separate FLK ammunition and burst physics; then finish its shell-flight fragmentation falloff, followed by real functional RCW, RPD, PPD, PLS and IMD weapon methods. ECM/chaff/decoys, defensive fighter craft and reactive armor remain separate systems, not repackaged PD types.

No visual/art/style or target damage percentage is a release blocker. Only objective failure to build, launch, run, update or deliver required multiplayer functionality can block publication. Routine headless balancing must not package the full game.
