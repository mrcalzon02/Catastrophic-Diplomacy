# Textless gritty UI / eight-button runtime candidate

These eight original textless button bodies were selected from the `182` technical edge/atlas/hash-pass candidates of `CD_Gritty_UI_Button_Sheets_v2`. They are grouped into a single 448×288 RGBA atlas under `buttons_v1.png`, with a 2×4 grid of 224×72 tiles (each inset to 212×64 with 6px horizontal and 4px vertical margins). Texture regions are nine-patch scaled and labels remain live BitmapFont glyphs.

Order / semantic intent / original candidate:
- 0 normal: `A_009`
- 1 selected: `A_025`
- 2 primary: `A_026`
- 3 confirm: `A_027`
- 4 warning: `A_017`
- 5 danger: `B_003`
- 6 telemetry: `B_016`
- 7 secondary: `A_018`

All eight are **technical-pass candidates, not artistically certified production art**. The original source bytes are retained in the two ZIPs in `asset_packs/gritty-ui/`; source digests are registered there, with the per-sprite manifest. They have complete independent button silhouettes and no lettering. The 48 held source pieces are **not** in either ZIP. GUI input regions remain unchanged; missing/invalid texture falls back to procedural chrome. The entire atlas consumes one GPU texture on both desktop and mobile.

The separate `background-panels-v1.zip` preserves nine opaque, no-label design candidates. They must not be applied blindly to gameplay screens: crop/fit within dedicated panels, do not nine-patch stretch photographic images or obscure live HUD labels. They are not loaded automatically until visual placement checks.

Acceptance: run `gradle verifyGrittyUiAssets :game:menuFlowSmoke :desktop:jar :android:assembleDebug` in GitHub Actions, and then review screenshots at native 1280×720 and mobile viewport; do not infer image quality from source-level or headless checks.
