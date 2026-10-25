# Tactical HUD UI art — curated dual-sheet pack (2026-10-25)

## Status and source of truth

Two original concept sheets are preserved without edits in an approved 5.88 MB transfer archive stored in the **Catastrophic Diplomacy** Google Drive project folder:
- [Dual-sheet source + curated runtime PNG ZIP](https://drive.google.com/file/d/1rleH-c_io4r0ndnLBdbttDe7uuQBnkpM/view?usp=drivesdk)
- [QA contact sheet](https://drive.google.com/file/d/1o_yYeomCH1PJ3geE9rjccW2bPTbl4N2Q/view?usp=drivesdk)

The ZIP contains the original full 1536×1024 PNG sheets, a 10-column × 9-row (80-pixel cells) curated PNG atlas, `icons.tsv` / JSON crop manifest, ten text-free ornamental button backgrounds, and five decorated status-bar references.

**This GitHub commit records staging instructions, hashes, an approved icon registry and a checked importer, not actual PNG binary contents.** Remote graphics are not runtime-ready until the binary asset ZIP is imported and the resulting files are committed to GitHub. Do not report an APK with this GUI as shipped or tested before that separate binary commit and render smoke test.

## Which art is selected

- **Sheet 01 / systems:** compact back/forward, restart, display/audio/settings, target and IFF warnings, shields/repair/power indicators, shipyard controls, starmap markers and alerts.
- **Sheet 02 / combat:** ship/fleet/weapons/shields/armor/engine, drones/turrets/missiles/lasers/EMP, repair and electronic detection, refinery/research/warp, and space hazards (nebulae, asteroids, stellar events, black holes).
- **Excluded from runtime:** storefront/loot boxes, player cosmetics, casual minigame art, unrelated RPG skill trees, invented faction emblems. Both whole original sheets stay safely in `art_source/ui/`, outside Android assets, for future review.

## Installing and verifying the exact source bytes

Place the ZIP on a trusted development checkout and run:

```bash
python tools/import_ui_kit.py --zip /path/to/Catastrophic_Diplomacy_UI_Kit_v1.zip --check
python tools/import_ui_kit.py --zip /path/to/Catastrophic_Diplomacy_UI_Kit_v1.zip --import
```

The importer verifies the full pack before any write, blocks unknown/corrupt sources and differing destination files, and skips byte-identical existing files. It imports the 2 originals to `art_source/ui/`; only selected assets enter `assets/ui/tactical/`. After import, review every source and sprite, then commit the imported binaries on the verified branch.

ZIP SHA-256: `8220b70e36e7133f8bbb5d1d89dacc0a1b14ee12671c481d822454901f80a170`.

Sheet 01 SHA-256: `3b74777d6c4a6dc8448fa0958f161b1b44967ef5a159a768bd36ca223e36417c`.

Sheet 02 SHA-256: `7bca86a5ba6cb908f5985e34293157ccad0cb4fb23e4700112afc2f0b2622d26`.

Curated atlas SHA-256: `1bc99703f65496727ef3c08db82ea660db22f0581f811c1cf9c20c6fc14dc293`.

## Renderer integration contract

Atlas logical tile is 80×80; `assets/ui/tactical/icons.tsv` maps a semantic icon ID to integer `column,row`. The image is in `assets/ui/tactical/icons.png`. Icons are framed, **not** clean transparent silhouettes; keep the dark interior rather than alpha-keying black pixels. Load lazily and fall back to existing geometry/text when missing. Bind visual selection to the **existing** hit regions, rather than rewriting battle/shipyard input handling.

Use ornate blank styles from `assets/ui/tactical/buttons/` for actual interactive buttons, with separately rendered text and standard disabled/pressed/hover feedback. Use matching bars from `assets/ui/tactical/bars/` for visual prototypes; actual values and status must remain data-driven. Confirm legibility at 24 px and 32 px on device, color-blind/IFF distinction, frame continuity, atlas offsets, texture disposal, and Android packaging. Never present decorative art as implemented new gameplay.

## QA performed

The curated crop positions were visually reviewed and corrected after the second sheet's 16-column icon rows were identified. A dry-run `--check`, fresh install `--import`, and repeat `--check` succeeded locally on the exact ZIP; this is *not* a desktop or Android runtime test.
