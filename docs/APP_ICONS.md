# Native installer and launcher identity

All platforms use the same Catastrophic Diplomacy original-art emblem, composed deterministically with the approved painted Umbra Foundry battlecruiser (`assets/ships/factions/umbra_foundry/battlecruiser.png`). The surrounding steel frame, orange command bars, and blue reticle follow `docs/AUTHORITATIVE_UI_DESIGN_LANGUAGE.md`.

Build: `python -m pip install Pillow==11.3.0 && python tools/generate_app_icons.py && python tools/generate_app_icons.py --check`.

- Android: standard 48/72/96/144/192 mipmaps, a 432px safe-area adaptive foreground, XML adaptive launcher icon, and application manifest `android:icon` and `android:roundIcon`. Android Gradle resource merges depend on generation, not on manual copying.
- Windows MSI: `jpackage --icon` consumes a genuine multi-resolution 16–256px ICO, embedding identity into the executable, installer and shortcuts.
- Linux: `jpackage --icon` consumes the 512px PNG; the distribution also retains `share/icons/hicolor/512x512/apps/catastrophic-diplomacy.png`.
- Missing original artwork, broken generated icons, or an absent icon cannot fall back to generic Java/Android artwork. All three release CI jobs explicitly install the exporter and check icon generation; Android APK resources and Linux ZIP copy are checked after packaging.

Icon source uses an existing committed painting and adds no invented faction craft or unapproved external images. CI checks prove file validity/packaging; human mobile home-screen and Windows shortcut appearance still require visual acceptance.
