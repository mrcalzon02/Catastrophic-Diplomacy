# Shield overlay sprite — runtime integration and visual audit

## Source of the problem

The actual original game texture `assets/materials/shield_energy_blue.png` is a **384×384 alpha-bearing blue hexagonal field sprite** (47,790 bytes). It is also archived in Google Drive under **Combat Textures — Shields and Structural Damage**.

Previously the renderer loaded this original image but sampled it **only within each hull silhouette**, then blended the resulting material with a base opacity of roughly 0.4. The material's own average alpha is already low, making the field barely visible, and no external shell was drawn. The distinct localized shield hit/collapse/recharge arcs from `ShieldFxRenderer` were working, but they could not substitute for the original shield-overlay sprite.

## Change

The original image is unchanged. `TerranPlaceholderSprites.prepareShieldShell()` now creates **four cached, textured transparent ellipse sectors** from the original hex material, using the existing `DirectionalDefense.facingFor` mapping. The ellipse is purely an alpha mask around the real authored texture, not a generated geometric replacement for the hexagon art. These four textures are shared across ships and created only once, never each frame or each shield tick.

Each ship draws the original energy texture as a distinct **1.31×-wide, 1.27×-tall shell** enclosing its actual sprite, with a brighter soft perimeter. The existing hull-silhouette texture layer remains available for localized contact and charge response. The shell and hull layer share **the real per-facing shield energy opacity**, `ShieldFxTimeline.opacity`, interpolation pose, rotation, on-screen culling and existing 5.5-pixel LOD cutoff.

- Zero-energy or collapsed facing: no overlay for that facing.
- Powered facing: visible blue-hex material and soft off-hull border, pulse tied to shield fraction.
- Hit, strain, recharge, restart: existing timelines and geometry effects still execute on top of the material.
- Shield death: powered sectors vanish. The code does **not** fake a shield by drawing a full solid oval around unshielded vessels.
- All factions and original hull art use the existing shared combat sprite renderer.

Runtime GPU cost: at close zoom, at most four additional cached textured quads per visible protected ship. No 384px Pixmap rebuild per ship or per frame. Zoom LOD and preexisting view frustum culling still apply.

## Verification

`ShieldSurfaceModelTest` checks zero outside radius, original transparency preservation, interior visible pixels and stronger rim opacity. It is a **nonblocking visual diagnostic**, in accordance with the build policy that artwork, cosmetic readability and balance metrics cannot block releases. The existing shield timeline visual smoke remains diagnostic as well. Failure to launch, missing required assets and functional gameplay still remain legitimate release gates.

```sh
gradle :game:shieldSurfaceSmoke :game:shieldFxSmoke
gradle :game:menuFlowSmoke :android:assembleDebug :desktop:installDist
```

**Outstanding validation:** A real Android and desktop screenshot of shields at close zoom (fully charged, one collapsed face, impact/recharge) is still needed before calling this visually approved. Source-code and unit-test success alone do not prove that the field texture is visually compelling on a GPU.
