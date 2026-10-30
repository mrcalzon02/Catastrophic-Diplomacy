# Capital wreckage retained in battle

Cruiser-class ships and larger (including all fitted capital variants) retain three of their actual textured hull fragments after the initial 2.55-second breakup. They continue drifting slowly and darken/fade over 18 seconds, while the initial 3.75-second destruction smoke clears. Smaller interceptor, corvette, frigate and destroyer kills do not add retained wreckage. Missing source artwork uses actual faction hull silhouette fragments, not fabricated box ships.

The entire feature is render-only: wrecks are **not obstacles** and cannot be targeted, repaired, salvaged or collided with. A separate 16-wreck maximum plus the preexisting 24-burst maximum prevents accumulation in 200v200 battles. The renderer retains only polygon UV metadata, not per-wreck GPU textures; both queues reset on new battle, expire by presentation time, and are camera-culled. The same rendering applies to campaign, skirmish and screensaver modes.

`:game:shipDeathSmoke` regression now covers cruiser/capital classification, cap limits, smoke clearing first, final wreck expiration and cross-battle reset. GPU/device visual quality must be judged separately from automated Java tests.
