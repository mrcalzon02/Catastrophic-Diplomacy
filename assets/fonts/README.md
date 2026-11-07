# Shared UI font

Atkinson Hyperlegible Regular, Copyright 2020 Braille Institute of America, Inc.
Licensed under the SIL Open Font License 1.1; see OFL.txt.
Source: https://github.com/google/fonts/tree/main/ofl/atkinsonhyperlegible

The vendored TTF and baked atlas are included in both Android and desktop assets.
Regenerate with `python tools/bake_ui_font.py` (Pillow 13). No network fetch,
native font library, or additional runtime dependency is needed by either client.
The 17px base preserves existing compact layouts; mobile menus retain their larger scale.
