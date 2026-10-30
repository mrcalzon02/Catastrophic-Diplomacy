# Catastrophic Diplomacy — source-derived hull materials

All approved faction hull sprites, original legacy fleet hulls, Terran ships, and the 50 approved alien fighter/drone sprites retain their original albedo and alpha pixels. The build now generates two additional materials **from each source sprite**:
- `*_n.png`: alpha-preserving tangent-space normals reconstructed from local image luminance gradients (Sobel), with transparent-margin clamping to avoid black outline bevels.
- `*_e.png`: alpha-preserving RGB emissive mask computed from sufficiently bright, saturated source details and pale pinlights. Ordinary dark painted steel is not classified as a lamp.

No new faction silhouettes, windows, thrusters, or equipment pixels are invented. A painted 2D source cannot reliably reveal actual ship geometry or depth; these are **inferred surface textures**, not artist-sculpted normals.

## Live rendering

`ShipSurfaceLighting` supplies one shared-style GLES2/GL2-compatible three-sampler shader for each sprite renderer. It retains the original albedo, samples normal/emissive textures with matching UVs, produces diffuse directional lighting, tight specular glints, and low-intensity rim reflections; RGB emission is added without modifying the sprite silhouette. A fixed **world** light is transformed into ship-local tangent space from heading encoded per-quad in SpriteBatch vertex colors. This avoids uploading a per-vessel rotation uniform and forcing an additional draw call for each of 400 ships. The blue vertex channel carries remaining hull integrity to dim disabled/damaged ship lighting; pilots/fighters use full nominal gain. Existing engine/weapon bloom remains separate and additive after hulls, so the new material shader does not replace the established combat lighting system.

Ship material maps load once for each distinct original texture (not per physical vessel). The Android, Linux and Windows packaging paths use the exact same `prepareShipSurfaceMaps` producer, which depends on all original art reconstruction tasks and adds `build/generated/cd-ship-surfaces` as a runtime asset directory. Source sprites are unchanged; derived maps are capped at **320px maximum axis** with preserved UV/aspect ratio, reducing GPU memory compared with full-resolution normals/emissives while preserving high-resolution albedo art. Missing maps and UV mismatches fail explicitly rather than silently painting colored blobs.

Damaged hulls retain the already-installed alpha-peel/interior masking. Shield quadrants remain a separate original-alpha overlay, outside the material lighting shader. Fighters with not-yet-approved art continue using the existing neutral wireframe diagnostic, not an invented faction sprite.

## Verification

`ShipSurfaceMapGenerator.selfTest` checks fully transparent pixels, lit-pixel classification, Sobel normal gradients, and map resolution limits during every asset build. `:game:shipSurfaceSmoke` checks paths for both modern and legacy factions plus shader alpha/normal/emission/rotation contracts. This test is required by `:game:menuFlowSmoke`, which runs on release CI. The actual shader must compile and render during the Android emulator and Windows/Linux native runtime steps; these steps must complete before calling the feature released. Verify low-end performance, shading under rotation, and visual quality on real hardware before graphical acceptance.

Further upgrades available after device QA: LOD-specific material quality, true combat-light interaction in the tangent frame, and damaged/disabled emissive flicker tied to system state. None is claimed implemented here.

## Additional pass: nearby weapon light, scalable detail and scorch

A new render-only `ShipLightProbes` selector finds at most two high-priority, visible light sources from simulation-confirmed beam origins/impacts, active ignited missiles, and recent FIRE/IMPACT/INTERCEPT events. It skips TIMEOUT to prevent invented explosions, bounds work to 96 recent events, 128 beams and 128 missiles, and cannot alter combat state. World positions reach the GLES2 shader as two shared uniforms per sprite pass. A world-position varying and the sprite's packed heading rotate each local incident ray into tangent space, so illuminated plating reacts to **nearby live weapons**, not only fixed lighting. Material texture uploads and shaders are still shared by hull type: there are no per-ship light uniforms or new framebuffers.

`ShipLightProbes.detail` fades normal/specular/dynamic shading from 100% at a view width of 1,000 to flat authored albedo at 4,000 world units. The low-detail path leaves original alpha/palette unchanged and avoids expensive material sampling at strategic zoom. At damaged armor cells, `ShipDamageScorch` darkens the existing source pixels according to actual interpolated armor exposure and stable pre-existing noise; it never changes their original alpha or invents holes. These modifications apply to ordinary battles and screensaver battles through the same renderer.

`:game:shipLightProbesSmoke` is a required dependency of `:game:menuFlowSmoke`; it checks visible-source ranking, strict two-source budget, LOD endpoints, original-alpha preservation, and shader integration. Full pixel-level validation awaits platform runtime evidence.

## Shield interference and CPU allocation follow-up

The existing four shield-facing overlays now have a source-alpha-preserving two-pixel cyan interference rim baked into the **same** per-facing textures. The rim is derived only from exterior hull silhouette transitions; the energy-sector boundaries do not produce artificial glowing seams. It adds **no draw calls, no extra shader samplers and no new per-ship textures**. Shield opacity smoothly varies on the authoritative battle tick (not wall time), with actual independently tracked facing energy still gating each draw. Fully exhausted sectors remain invisible. Shield shells become non-rendered when projected height falls below 5.5 px in the strategic overview; their simulation remains active. This is a convincing alpha-overlay interference treatment, **not expensive background framebuffer refraction**.

The fighter/drone render pass now reuses its source-ship lookup table across frames instead of allocating a new HashMap on every draw. A headless `:game:shieldSurfaceSmoke` runs as a dependency of the release's existing `menuFlowSmoke` gate, checking original-alpha preservation, shield energy gating, deterministic pulsing, outer-edge extraction and distance culling.

## Strategic shader bypass

At extreme tactical zoom-out (4,000+ world units across), the normal/emissive renderer uses the actual built-in SpriteBatch unlit shader rather than sampling unnecessary normal/emissive textures. Its vertex colors remain white to prevent packed heading values from tinting approved original art. Hull material uploads are deferred while this flat LOD is active, avoiding first-frame material allocation during distant surveys. Close views still use full surface lighting and current emitted-light behavior. The headless shader contract checks both full and flat routes; this performance gate requires no new draw passes.

The same `CombatLightingRenderer.eventColor` palette now controls both outer bloom and per-pixel hull lighting for all five installed laser families. Point-defense beams are separately subdued green emitters, preventing dozens of tiny interceptors from overwhelming capital-ship lighting. The headless probe test compares all five laser-family light colors to their authoritative combat-rendering counterparts.

## Frame budget observation (no synthetic performance claims)

`RenderPerformanceCounters` now collects battle-only CPU frame duration (simulation plus render), SpriteBatch draw calls and actual normal/emissive texture bindings with a 180-frame window. It logs average/peak costs and platform-reported FPS under `CD-RENDER-PERF`; the mandatory 200v200 runtime verification also reports the current sample's metrics. This is **CPU/GPU submission telemetry**, not a GPU timestamp query or a claim of a fixed framerate. Windows, Linux, and Android builds can use the log values to identify unnecessary overdraw or material swapping. CI tests counter arithmetic and reset without gating releases on arbitrary runner-speed thresholds.
