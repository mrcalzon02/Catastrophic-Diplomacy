# Weather VFX starter pack: approved asset intake

The user accepted the **49-frame corrected VFX ZIP as provisional starter art**, not final visual quality. Never use the original concept posters or rejected chopped/cropped sprite atlas as runtime resources.

## Intake and reproducible installation

The corrected pack is supplied separately as `catastrophic_weather_fx_verified.zip`, with `manifest.json`, per-frame checksums and the visual QA preview.

From the repository root:

```sh
python tools/import_weather_vfx.py catastrophic_weather_fx_verified.zip --verify-only
python tools/import_weather_vfx.py catastrophic_weather_fx_verified.zip
```

Exactly 49 RGBA PNGs are installed under `assets/effects/weather/` with stable names, dimensions and 8px+ alpha margins. A missing/extra/mismatched frame fails verification before writing anything; the legacy flawed atlas format is explicitly rejected. Preserve the original manifest and all 49 sprites together.

The game-side `WeatherFxAssets` library auto-detects these files through LibGDX internal assets, loads each texture once, disposes correctly, and leaves `BattlefieldWeatherVfx`'s deterministic geometric fallback intact if files are absent. Rocks use normal alpha blending; luminous beams, particles, arcs and shockwave sprites use additive blending. The art does **not** control hit tests, projectile collision, or damage.

The 49 audited binaries are **not yet committed as binary assets**. They are provided in the verified ZIP. The runtime's procedural renderer remains fully functional without them, and Android packages source assets directly from the repository's `assets/` directory. The runtime will therefore use procedural fallbacks until the approved PNGs are imported and committed. Do not report art as shipped simply because the loader exists.

## Quality gates before production

- Review contact sheet and game-size comparison; reject clipped/flat-edge rocks, cut shockwaves, labels, black rectangles and stray panel borders.
- Test desktop + Android loading, start/pause/resume, context loss and blending.
- Profile 200v200 fleets to avoid unnecessarily changing GL textures for dozens of individual sprites per frame. An atlas/texture packing pass may be useful before final release.
- Keep the 49 verified sprites replaceable; the user considers them adequate placeholders rather than finished art.
