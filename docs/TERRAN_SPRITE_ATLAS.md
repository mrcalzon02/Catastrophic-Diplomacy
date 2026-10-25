# Terran placeholder ship sprites — first faction

Source: the first generated Catastrophic Diplomacy Terran fleet sheet, October 24, 2026. Original design family: white/gray Terran mass-production hulls, navy panels, blue-white drives and visible missile arrays. This art is **placeholder concept artwork** suitable for testing deployment/faction/hull readability, not final normal maps or physically authored sprite damage layers.

The ten ship cutouts are: `interceptor`, `corvette`, `missile_corvette`, `frigate`, `destroyer`, `light_cruiser`, `heavy_cruiser`, `battlecruiser`, `carrier`, `dreadnought`. These are independently cropped from the original transparent top-down atlas, with labels cut away. An atlas-manifest in `assets/ships/terran` records filename and original crop coordinates.

The current code supports only four fitted hull classes; temporary mapping is **corvette → corvette; frigate → frigate; cruiser → light_cruiser; dreadnought → dreadnought**. The other six sprites are reserved for additional hull and specialized variant implementation, not falsely presented as buildable. The sprite importer in `TerranPlaceholderSprites` uses libGDX internal assets. To install, copy the ten PNG files from the exported `catastrophic_diplomacy_terran_placeholders.zip` to `assets/ships/terran/`. The binary PNGs are **not yet in the GitHub source tree**; the fallback polygon renderer remains active when assets are missing. Do not claim the first-faction image art is shipped as a GitHub release.

All images face upward. Game coordinate direction +X means 0° faces right, so the renderer rotates nose-up sprites by ship heading - 90°. The current sprite layer overlays the existing GPU/geometry path. A production pass should remove duplicate underlying hull draws when sprite art is enabled, add normals/emissive/structure layers and use UV-synchronous tile masks and instanced shaders to expose actual interiors.

Pending graphics verification: transparent edges, no label contamination, desired fleet scale, image filtering, transform orientation, asset packing, GL30 fallback, missing-art behavior, licensed source provenance, and Android loading.

## Damage compositing proof

The shared Terran drawing layer creates a procedural dark structural hull texture at startup. Undamaged sprites render as a single image. Ships with damaged tiles draw that underlayer, then render each affected RGBA exterior tile with armor-dependent alpha. This is a real localized transparency path for testing, not merely orange indicators. The internal hull texture is deliberately generic and must be replaced by authored structure and correct UV-aligned normal/emissive material layers in a later art pass. Run desktop/Android rendering tests before accepting device performance.
