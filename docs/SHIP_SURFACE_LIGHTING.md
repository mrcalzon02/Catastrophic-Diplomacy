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
