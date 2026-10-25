# Procedural starfields — Stage I

The game generates its skies in Java/libGDX, **not** from a 500-image gallery. The reference starfield mosaics are style targets, not shipped textures or required runtime data.

## Runtime architecture

1. `LevelSky.forLevel(1..500)` derives a visual-only stable seed from `LevelEconomy.level(index).seed()`. The ten 50-level sectors have different two-color palettes; every level changes its formation, cloud orientation, density, and noise scale. Same level = same sky on replay, unaffected by AI/combat RNG.
2. `ProceduralStarfield` creates **one shared 256×256 tileable RGBA noise map** at startup, using three blended octaves. All 500 levels sample that one texture with different coordinates, colors and formations. `useLevel(index)` only changes the sky recipe: no PNG per level, Pixmap rebuild, GPU upload, or texture churn during stage changes.
3. A low-cost **single SpriteBatch fragment-shader pass** colors the map into gas clouds, dust lanes, inclined spiral galaxies with luminous cores, voids and ion formations. Color, angle, structure, opacity and noise position are level-specific uniforms. If compilation fails, star layers remain visible against the normal cleared backdrop.
4. Three camera-aware **parallax layers** move at factors `0.025`, `0.10`, `0.23`. Stars are deterministic by signed world chunk and layer; sparse glints and distant points remain behind ships and the HUD.
5. The render pass never mutates `Battle`, `LevelEconomy`, ship blueprints, economy or combat RNG.

## Current integration

- The sandbox is not yet a real 500-level campaign. Preview starts at **1**; keyboard **N**/**B** previews next/previous sky without restarting an encounter.
- When campaign selection is implemented, call `starfield.useLevel(level.index())` on entering a stage, not each frame. Replays reuse the same definition.
- Starfields render before ships, weapons, damage effects and HUD.

## Validation and remaining gates

- `LevelSkyTest` checks 500 distinct/reproducible recipes, 10 sector palettes, six formations and invalid bounds. It was compiled and run using the real `LevelEconomy` source with Java 17 target. A separate mock-libGDX lifecycle test exercised 500 stage switches and confirmed the renderer allocated three textures once, released three textures on disposal, and balanced batch calls; this is a **mock test**, not GPU validation.
- The shader targets ES2 SpriteBatch; failure falls back to star-only rendering.
- Still requires desktop/Android GPU acceptance and benchmark of 200v200 battles, viewport resizing and Android context loss on a physical device. Procedural results may need art tuning to resemble the photographic reference sheets.
