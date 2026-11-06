# Adapting Factorio FFF #264 to Catastrophic Diplomacy

Source: [Factorio Friday Facts #264 — Texture streaming](https://www.factorio.com/blog/post/fff-264) (Wube Software, 2018-10-12).
Status: **audited implementation plan**, not a claim of completed optimization or measured speedup.
Scope: shared battle renderer in campaign, skirmish, and screensaver; Java 17/libGDX, Android GLES2/GLES3 and desktop GL3. No physics, networking, combat behavior, or release-policy changes.

## Important separation of concerns

FFF #264 discusses GPU texture allocation/fragmentation, sprite batching, virtual texture mapping, mip levels, and pixel overdraw. It does **not** present a solution for fixed-step simulation interpolation.

Current game pacing is 30 Hz `Battle.DT` multiplied by `CombatPace.simulationMultiplier(displayedSpeed)` (displayed 1.0x = 0.10 real-time scaling, or approximately three actual battle steps per real second when performance permits). `BattleRenderPoses`, `ShortProjectileFlight`, camera smoothing, and the continuity diagnostics already address interpolation/presentation. Continue debugging ship/projectile teleportation in those systems and use this rendering plan to remove *additional* frame stalls; do not change battle ticks to compensate for GPU pressure.

## Existing code and gaps

- `ShipTextureSampling.load` gives original ship art trilinear minification where supported and safely falls back for non-power-of-two GLES2 textures. This is already the correct platform-guarded policy.
- `TerranPlaceholderSprites` uses individually resident source PNG textures, LRU-like history, and a mobile target of 24 original images; it protects recently drawn art for 90 frames rather than forcing immediate thrash. Its `estimatedResidentAlbedoBytes` excludes the full derived normal/emission/damage/shield footprint, so image count is **not** a dependable VRAM budget.
- `FighterCraftSprites.art` loads and prepares one original image/material per role/path with plain linear filtering; it evicts unused textures after drawing and has a 24-image target. The current render path calls `art(path)` *before* the viewport test, so distant invisible craft can cause an avoidable upload.
- `TerranPlaceholderSprites.render` does viewport checks before source preparation in the first pass, but some subsequent passes acquire artwork before their viewport checks. The strict requirement remains that visible unique hull source art never silently becomes generic geometry.
- `OrdnanceLibrarySprites` already packs missile bodies and exhaust into two shared atlases, but uses linear minification and interleaves plume/body draws for each missile. `ModularWeaponFxRenderer` also uses several independently bound textures. `VisualAtlasBuckets` exists for grouping.
- `ShipSurfaceLighting` samples albedo/normal/emission and optional localized damage and already supports lower-detail rendering. Any hull atlas migration must preserve UV alignment of **all** layers, rotation, alpha masks, and authored hardpoints.
- `RenderPerformanceCounters`, `CombatDiagnosticRecorder`, and `CombatFrameContinuity` already record CPU phase time, SpriteBatch draw calls, material binds, actual physics steps and display continuity. These are **not** GPU timestamps; do not infer fragment-shader cost from them alone.

## Staged rollout

### 0. Capture a comparable, nonblocking baseline

Use fixed encounter seeds and identical camera paths: 16, 50, and 200-v-200 test scenes, displayed speeds 0.5/1/3x, close/mid/far zoom, cold and warm asset caches; repeat on desktop, GLES2-class Android, and GLES3 Android where available. Record frame-time p50/p95/p99, dropped/clipped wall time, actual ticks per wall second, interpolation alpha, ship/projectile jump events, CPU simulation/non-simulation/lighting timings, SpriteBatch calls and material binds. Add *opt-in* rendering counters for texture creations, disposals, uploads/bytes per frame, peak source+derived residency, and viewport-rejected-but-loaded images. Name hardware/backend in each trace. Emulator numbers are diagnostics, not phone FPS.

Gate: baseline capture and existing smoke tests work; do not introduce an FPS release gate.

### 1. Reduce needless uploads and unify minification

1. Move source-image acquisition behind stable, conservative viewport tests for fighters and hull/shield/FX paths, without suppressing needed gameplay objects or breaking alpha/hardpoint access.
2. Route ordinary immutable fighter/missile/weapon-art loads through one capability-checked sampling policy based on `ShipTextureSampling`; prefer mipmaps for small zoomed-out sprites only where supported. Keep GLES2 NPOT fallback. Avoid generating mip chains for frequently updated dynamic damage masks until measured.
3. Preserve source pixel color/alpha and asset identities; validate minified sprites for fringe bleeding and anisotropic shimmer.

Measure texture allocations, bind counts and camera-pan hitching before claiming an improvement. Budget for the roughly 1/3 extra memory used by a complete 2D mip chain.

### 2. Improve batching without replacing the hull renderer

1. Start with the existing ordnance body/plume atlases: group compatible draw passes to reduce repeated texture changes, while preserving sprite depth order, particle/exhaust positioning, timing, tint, and blend mode. Use `VisualAtlasBuckets` as appropriate; **do not** reorder translucent layers across incompatible blend ordering.
2. Pack stable small fighter/weapon effect sprites **offline** into texture pages with extruded transparent-safe borders and a manifest mapping original paths to exact `TextureRegion` rectangles. Respect GLES2 `GL_MAX_TEXTURE_SIZE` and fall back to existing source-image loading if a page cannot be loaded.
3. Avoid converting all hull albedo/normal/emissive/damage assets into one atlas in this stage: the multi-sampler material shader and dynamic shield/damage layers require matched UV transformations and careful validation.

Gate: same live 200v200 scene, source-art identities, projectile dimensions, alpha/rotation, turret/hardpoint alignment, and material bind/draw measurements.

### 3. Replace count-based residency with measured and paced residency

Create a shared *accounting* layer for static source+derived texture byte estimates, texture uploads/disposals per frame, and eviction causes. Do not require all assets to fit a hard 24-item cap. Pin resources used by current SpriteBatch draws and visible ships/craft; evict only after the frame is complete and protect recently required assets to avoid oscillation. Test explicit memory budgets *after* target-device traces, with an upload-time/bytes-per-frame allowance and a deterministic graceful fallback when assets genuinely cannot be resident. Never create/destroy GPU textures from a background thread. Rebuild safely after GL context loss.

Gate: repeated camera sweeps and faction changes do not produce an allocate/evict storm, crashes, missing required art, or worse frame-time tails.

### 4. Reduce GPU pixel work and zoom-dependent shader cost

Use conservative world/camera culling already in `CombatViewBounds` and projected footprint thresholds to avoid insignificant offscreen/subpixel translucent glow, smoke, shield, and laser overlays. Keep actual impacts/contacts and diagnostic markers readable. Validate `ShipSurfaceLighting.flatAt` and normal/emissive detail selection against projected pixels rather than adding new redundant LOD mechanisms. If profiling permits, use GL3/GLES3 timer queries or an overdraw debug view **as optional diagnostics**, with a GLES2-safe path.

Do not assume alpha-discard optimization saves work: Factorio's article explicitly reports limited or negative benefit on several GPUs. Preserve blend order and damage-visibility semantics.

### 5. Virtual texture paging: deferred architectural option, not an immediate implementation

Factorio's 128x128 virtual pages, page-table indirection, partial mip residency, and single physical atlas target massive art collections. For our current libGDX multi-sampler hull shader and GLES2 fallback, implementing these features prematurely risks shader complexity, fragile damage UVs, texture popping, and memory fragmentation. Reconsider only if same-seed hardware traces still show dominant texture allocation/eviction stalls after stages 1–4. Any prototype should remain opt-in with a permanent ordinary-texture fallback.

## Validation, rollback, and delivery

Run existing regression tasks first and after each bounded change: `:game:renderPosesSmoke`, `:game:combatPerformanceSmoke`, `:game:combatVisibilitySmoke`, `:game:combatTimelineDiagnostic`, and the combat/lighting/ship surface smoke suites. Verify full production campaign, skirmish, screensaver, Android GLES2/GLES3 and desktop launch paths. Do not add a separate battle engine or GitHub Actions workflow. Test pixel comparisons at close/far zoom (and moving camera), 200-v-200 scene stability, texture identities, shield masks, fighter size, weapon origins, and no purple color leakage.

For each stage, save same-hardware before/after traces including p50/p95/p99 frame time, worst camera-pan stalls, texture upload/dispose counts, material binds, SpriteBatch draw calls and render continuity; record any quality regression. Make the work reversible, one stage at a time. As established for this project, only build/launch/run and required multiplayer/updater failures are release-blocking; performance/art quality remain explicit diagnostic evidence rather than invented release gates.

**First executable change proposed:** stage 1 viewport-before-texture-acquisition for fighters, with a headless visibility/asset-selection regression and a live GLES2/desktop screenshot check. Only after comparable measurements consider ordnance mipmaps and draw grouping. This plan alone does not implement that change.
