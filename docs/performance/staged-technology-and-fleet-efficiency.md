# Staged technology and fleet efficiency implementation

Status: **In progress**. Scope: Java 17/libGDX Android 10+, Windows, Linux. Branch: `main`.
This is an execution ledger, not permission to skip production correctness tests.

## Implementation ledger (2026-11-04)

- Stage 0 / diagnostic instrumentation: committed `TechnologyMemoryProfile` and `WikiMemoryProfile` plus optional `technology-efficiency-diagnostics.yml`. Runs 16 MiB and 32 MiB headless profiles without building APK/MSI, without suppressing stack traces, and without gating release on optional performance.
- Stage 1 / wiki: committed virtual filtered search results and canonical-ID selection restoration. Focused objective regression **41914420404 passed**.
- Stage 2a / sensing: removed 9,504 eagerly cloned Stage I–VI rows and resolved all 14,256 sensing-model IDs on demand. Exhaustive multiplier/prerequisite checks. Focused objective regression **41915528624 passed**.
- Stage 2b / survival: removed another 3,168 eagerly cloned Stage I–VI research rows from twelve survival/retrofit families, resolving all 4,752 family stages by canonical ID. Exhaustive parity tests **passed** in focused regression **41916297353**. Survival *variant* research remains separate and virtual as before.
- Stage 2c / common lookup fast path: built-in Stage I–VI records are looked up before virtual-ID parsing to reduce CPU allocations in routine fitting/campaign reads. Commit `9b26711`; objective smoke is **pending** at this ledger revision.
- Baseline 16 MiB HotSpot before Stage 2a: OOM in `addSensingHardwareVariants`. After Stage 2a: OOM moved to `addSurvivalAndUnsafeFamilies`. After Stage 2b: **still OOM** (variously late in CSV-family expansion or record parsing, including `addParticlePointDefenseFamilies`), not a passing memory budget.
- The 32 MiB profile **41916324356** reported 2,035 components, 73,260 upgrades, ~12.39 MB catalog heap retained after GC (down from ~14.03 MB after Stage 2a), ~21.23 MB wiki heap retained (down from ~22.69 MB) and broad wiki search ~3,707 ms. Exact run-time and retained memory vary with JVM/GC; these HotSpot diagnostics do not guarantee Android ART memory/FPS.
- The profiling workflow has `continue-on-error` steps, so a green job only means the diagnostic script completed and archived logs; inspect `TECH_PROFILE`/`WIKI_PROFILE` or `OutOfMemoryError` lines before declaring an individual workload successful.
- Next: complete Stage 2c objective regression, then plan further memory reduction around temporary cloned CSV rows. Do not introduce a broad data-format migration, changed save IDs, disabled tests or test-bypassing heap flags.
- Release run **41908916178** passed all game packaging and emulator functional jobs but failed its publication job due GitHub HTTP 403 permissions. Publication remains a separate unresolved repository authorization problem.

## Non-negotiable invariants

- Keep `TechnologyCatalog.get(id)`, `upgrades()`, `baseComponents()` and canonical technology IDs stable. Preserve every faction's nine stages, four mutually exclusive tracks, costs, tradeoffs, and prerequisites.
- Retain valid existing campaign/research/shipyard saves; keep published network and combat semantics. Never silently return a missing technology because its partition was not loaded.
- Battle startup must fully resolve every equipped module on **both** sides (including fighters, opponents, campaign reinforcements and multiplayer-supplied designs) before running authoritative ticks. Loading is not a gameplay unlock.
- Deterministic physics, targeting, network synchronization, and fixed-step behavior take precedence over frame-time shortcuts.
- On actual constrained Android heap (observed 16 MiB), the game must reach the main menu and complete campaign/save/restore without unhandled OOM; optional graphics may degrade, not core technology.
- Only objective failure to build, launch, run, save/restore, update or required multiplayer blocks releases. Memory/performance deltas, art, aliasing, FPS, and aesthetic screenshots are **diagnostic** except where they cause actual functionality failure.
- Do not trigger a packaging/release build for every small refactor; use focused Java smoke tests.

## Stage 0 — pin baseline and add nonblocking measurements

- Record technology load time and Java heap before/after, upgrade/component counts, selected ID lookup parity and wiki search time. Use a **standalone profiling task**, separate from release gates.
- Record Android graphics workload separately: resident hull textures and approximate GPU bytes, draw calls/texture switches, and 400-ship sim vs rendering time. Existing `RenderPerformanceCounters` and combat diagnostics should be reused, not duplicated.
- Compare on the same fixed heap, same synthetic work, same platform and same commit. Keep raw numbers; no unsupported claimed percentage gains.
- Baseline gate: `:core:technologySmoke`, `:game:wikiSmoke`, `:game:menuFlowSmoke`, `:core:hardpointsLowHeapSmoke`, and real API29 campaign shards for packaging when the release is explicitly requested.

## Stage 1 — low-risk wiki and lookup allocation reduction

- Preserve the full encyclopedia, search semantics, ordering, unique article IDs and seven-row touch pagination.
- Store filtered upgrade **IDs**, not thousands of formatted `Article` objects. Resolve descriptions only for visible/selected records; avoid a full materialization when restoring a selection from another category.
- Keep arbitrary keyword searches correct even when they require a slower full text pass; profile them separately. Do not drop results or silently cap the list.
- Add functional tests for all/upgrade categories, exact ID, broad searches, selection restoration, and late-stage on-demand materialization.
- No changes to research rules, saves or combat in this stage.

## Stage 2 — partitioned technology data, behind the existing API

- Design a compact all-faction index of canonical identifiers, rank, faction, parent chain, and data-source/version. Prefer deduplicated immutable templates or compact records over duplicating 11 faction data trees.
- Split **materialization**, not authorization, into faction and three rank bands: I–III, IV–VI, VII–IX. Use a bounded LRU for expanded descriptions/stats and load requested neighbors when affordable.
- The existing lazy VII–IX/virtual survival/network variants must remain derived from the exact same stage V/VI ancestors. Prepackaged immutable indexes must be exhaustively cross-checked against authoritative `loadDefault()` on every catalog rebuild.
- Never change `TechnologyCatalog` public ID behavior or write new save formats in the initial rollout. Do not evict a record in use by combat, ship fitting or multiplayer verification.
- Land a read-only reference/parity test before switching any caller to staged materialization. Include all ~73k IDs, all 11 factions, stage/branch/prerequisite and exact numeric modifier parity, load/unload/reload and corrupted resource rejection.
- Ship only after API29 16 MiB startup and all campaigns verify. Keep existing eager parser as fallback until indexed mode passes.

## Stage 3 — graphics residency, mipmapping, LOD

- Pad/extrude turret atlas tiles before mipmap generation; verify region UVs and edge samples. Use minification filters only where supported and worth the ~1/3 extra mip-chain memory.
- Mipmap original hull albedo and source-normal/emissive maps already use `ShipTextureSampling`; extend fighter assets. Maintain GLES2/non-power-of-two and tiny-heap fallbacks. Never eagerly load the capital turret atlas at menu startup on a 16 MiB VM.
- Profile per-asset uploads, texture residency/evictions, shader binds and draw calls in fixed camera configurations. Compare zoom-out and close-up screenshots diagnostically, not as build blockers.

## Stage 4 — authority-preserving combat hot paths

- Profile first; choose a single shared fixed-tick spatial index for **candidate** ship/fighter/ordnance queries. Sensors, seekers and point defense still apply their own distinct physical acquisition/track rules and light-delay limitations.
- Preserve stable candidate iteration order to maintain deterministic outcomes and multiplayer replay. Compare seeded 200v200 battle traces, target IDs, intercept events, damage, fuel, retirements and saves before and after; use baseline feature switch for readback.
- Cache immutable fitted-module derived values with explicit invalidation on damage/refit/research change. No phantom upgrades or lost hardware.

## Stage 5 — full acceptance and release

- Complete focused functionality gates before any full release request. Then confirm real installed Windows MSI, Linux launcher, signed Android APK/update continuity, Android 14 400-ship run and all 11 Android 10 campaign/save shards.
- Review retained heap, GC, frame times, GPU residency and correctness diffs together. Report unknown/untested results explicitly.
- Publish only with working GitHub release permissions and successful public tag+asset readback. Preserve previous releases until an actually verified new prerelease exists.

## Rollback/recovery

All stages are separate, reversible `main` commits. If a stage breaks required functionality, revert **that stage only** by a normal forward commit; never force-push/retag. Do not replace a broken path with placeholders, reduced technology counts or disabled tests. Stop broadening scope until the last objective regression is understood.
