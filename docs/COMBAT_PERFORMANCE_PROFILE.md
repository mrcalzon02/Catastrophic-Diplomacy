# Combat performance investigation — local light receiver culling

## Verified pre-change baseline

The Android 34 Pixel 2 **software-rendered emulator** from [release run 192](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/41174234219) reported:
- 400 source-art ships rendered, 26 graphics frames and 499 combat ticks.
- **1,883.6 ms mean / 3,660.3 ms peak CPU time per measured frame**; this wraps both combat tick catch-up (the diagnostic explicitly starts at 8x game speed) and all rendering calls.
- 2,168 peak additive glow quads, 640 smoke quads, about 1,680 draw calls per frame.
- These measurements **do not show which subsystem is slow**, nor do they certify physical-device FPS. The measured total-CPU upper-bound rate was about 0.53 FPS at this exaggerated 400-ship/8x/SwiftShader diagnostic load.

## Audit: what was really being calculated

- There is **no full-scene shadow casting or per-object per-light shadowmap recomputation**. `CombatLightingRenderer` renders glow and smoke as additive/alpha quads from shared textures. `CombatLightingModel` captures authoritatively timed effects once per simulation tick, and repeated rendered frames never spawn duplicate smoke.
- `ShipSurfaceLighting` used **two shared strongest screen-visible light sources** as GLES-compatible shader uniforms, not a shader uniform upload or light simulation per vessel. But those two were chosen mostly according to *camera-center distance*. Lights illuminating empty space could displace lights actually touching a vessel.
- Some expensive emission shapes were prepared even when completely outside the viewport or when their projected halo was subpixel. On a large fleet this adds useless trigonometry and GPU submissions.
- Runtime `renderCpuAvgMs` included simulation, potentially many 30Hz steps per rendered frame, so attributing this total to lighting was unjustified.

## Changes

1. `ShipLightProbes` uses a **32x20 reusable object-occupancy grid**. Alive ships and active craft mark conservative projected physical footprints, and real authoritative beam/impact/missile emitters can occupy the two shader-light slots only if their finite radii overlap occupied cells. This is a conservative broad-phase (false positives possible at cell boundaries), not expensive pixel-perfect shadowcasting. It does not mutate simulation, change missile/beam trajectory, or add per-ship draw calls.
2. Reuse a selected light set for the same battle tick and camera bounds. Camera pan, zoom, scenario changes and new ticks invalidate it. `reset()` remains unfiltered for isolated editor/probe tests.
3. `CombatLightingRenderer` rejects completely offscreen ship thruster lights, missile light sources and beam coronas **before** performing their full math and quad submissions. It also skips halos/smoke smaller than one projected screen pixel, leaving visible source/burn/impact graphics present.
4. `RenderPerformanceCounters` now measures separately: **total CPU time**, **fixed-step combat simulation**, **non-simulation CPU time** and **additive smoke/glow overlay submission**. The other render work still includes sprite materials, FX and HUD. These CPU times are not GPU timestamp timings.
5. The Android 400-ship verification extracts and checks the split timings into `test-output/android/performance-diagnostic.json`. CPU phases must reconcile and the measured additive pass must be contained within render time. In-game physics, source-art and actual-frame progression remain functional release gates. Runner-speed/FPS thresholds remain **diagnostics only**.

## Required evidence before claiming speedups

Run `:game:shipLightProbesSmoke :game:renderPerformanceSmoke :game:menuFlowSmoke` and the actual Android emulator 200v200 test; verify all functional systems keep passing. Compare the new `simCpuAvgMs`, `nonSimCpuAvgMs`, `lightOverlayCpuAvgMs`, `avgDraws`, `glowQuads` and `smokeQuads` at equal camera/seed/hardware conditions. **Do not claim any percentage FPS improvement before comparable measurements.**

Following profiling, further optimizations should target the measured highest-cost operation: large batches of fixed-step battle logic, weapon/shield FX submissions, atlas material switching, or screen-space overdraw. Real Android hardware profiling remains necessary for practical frame-rate acceptance. No independent art/appearance gate may block a functional build.
