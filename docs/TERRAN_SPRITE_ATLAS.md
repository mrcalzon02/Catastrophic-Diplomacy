# Terran placeholder ship sprites — first faction

Source: the first generated Catastrophic Diplomacy Terran fleet sheet, October 24, 2026. Original design family: white/gray Terran mass-production hulls, navy panels, blue-white drives and visible missile arrays. This art is **placeholder concept artwork** suitable for testing deployment/faction/hull readability, not final normal maps or physically authored sprite damage layers.

The ten ship cutouts are: `interceptor`, `corvette`, `missile_corvette`, `frigate`, `destroyer`, `light_cruiser`, `heavy_cruiser`, `battlecruiser`, `carrier`, `dreadnought`. These are independently cropped from the original transparent top-down atlas, with labels cut away. An atlas-manifest in `assets/ships/terran` records filename and original crop coordinates.

The integrated shipyard now supports **11 hull classes**: ten pictured Terran classes plus the original generic Cruiser, which uses the light-cruiser sprite alias. `TerranPlaceholderSprites` loads the 10 class PNGs through libGDX internal assets, and the GPU/software geometry paths suppress duplicate placeholder hull drawings where a matching sprite exists. The shipyard has a separate preview view for actual hull artwork; its normalized hardpoint schematic deliberately remains independent until real sprite-space mount anchors are approved. To install the images, extract `Catastrophic_Diplomacy_Runtime_Art_Stage1.zip` at the repository root, retaining `assets/ships/terran/`. The ten PNGs are **not yet committed as GitHub binaries**; the fallback renderer remains when images are missing. See [Stage 1 integration and import status](ART_INTEGRATION_STAGE1.md).

All images face upward. Game coordinate direction +X means 0° faces right, so the renderer rotates nose-up sprites by ship heading - 90°. The current sprite layer overlays the existing GPU/geometry path. A production pass should remove duplicate underlying hull draws when sprite art is enabled, add normals/emissive/structure layers and use UV-synchronous tile masks and instanced shaders to expose actual interiors.

Pending graphics verification: transparent edges, no label contamination, desired fleet scale, image filtering, transform orientation, asset packing, GL30 fallback, missing-art behavior, licensed source provenance, and Android loading.

## Damage compositing proof

The shared Terran drawing layer creates a procedural dark structural hull texture at startup. Undamaged sprites render as a single image. Ships with damaged tiles draw that underlayer, then render each affected RGBA exterior tile with armor-dependent alpha. This is a real localized transparency path for testing, not merely orange indicators. The internal hull texture is deliberately generic and must be replaced by authored structure and correct UV-aligned normal/emissive material layers in a later art pass. Run desktop/Android rendering tests before accepting device performance.
