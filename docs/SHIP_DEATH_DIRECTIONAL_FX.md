# Directional, weapon-specific ship destruction

A fatal hit now uses the actual last IMPACT visual event addressed to that hull **in the same simulation tick**, including the real contact position. The fracture and debris vectors bias away from that point into the hull, and maintain existing ship momentum, instead of exploding uniformly in every direction. Missing impact records fall back safely to the hull heading without inventing new combat outcomes.

The render-only cause palette adjusts how violent and smoky the destruction is: laser cuts are relatively clean and cool cyan; kinetic hits scatter fast warm fragments; plasma leaves denser blue-violet clouds; particle impacts tint violet; missiles add heat and smoke; torpedoes produce the largest debris impulse and a brief, small-scale blast flare. None of these effects alters hit points, shields, splash damage, ammunition, AI or replay seed.

All seven cause variants preserve the existing eight jagged ship-texture wedges and 10-puff bounded cloud, never exceed the 24-active-burst safety budget, and expire with the existing destruction timelines. These rules apply identically to campaign, skirmish and the screensaver.

`gradle :game:shipDeathSmoke` is an existing release gating JVM test, expanded to cover direction and visual family profiles. Real APK screenshot review and frame profiling remain separate acceptance conditions.
