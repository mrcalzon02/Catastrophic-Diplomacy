# Desktop and Android acceptance gates — not yet certified

The repository shares one Java 17 `:core` combat/shipyard implementation and one libGDX `:game` client. A headless Java 17 `javac --release 17` run proves source-level simulation compatibility; it does **not** certify libGDX rendering, desktop packaging, APK signing, touch devices or persisted Android files.

## Local build checklist (persistent trusted machine only)

- Check JDK 17, Gradle 8.11.1-compatible toolchain, Android SDK platform 35/build tools, and any local Android SDK configuration. There is currently no committed `gradlew` wrapper.
- Run `gradle :core:smoke :desktop:classes :android:assembleDebug` and read errors; `:core:smoke` chains the catalog, shipyard, platform persistence, combat-machinery and scale tests.
- Launch `gradle :desktop:run`, open shipyard, fit a hangar/controller/magazine or ECM, apply, and visually confirm fighters/drones/torpedoes and HUD counters during combat. Verify shipyard controls with mouse and touch emulation.
- Run `gradle :android:assembleRelease` **only on the trusted local machine with the existing `.local-secrets` identity intact**. Do not re-run key creation on ephemeral workers. Record the APK file hash, and use the Android SDK's `apksigner verify --verbose --print-certs <apk>` without dumping secrets. Verify the SHA-256 signing certificate fingerprint against the retained release identity and a previously installed APK when upgrading. No unsigned release may be published.
- On Android API 26+ emulator and actual handset, install debug and separately test a signed release upgrade (same package identity and signing certificate). In the shipyard: tap a slot, scroll through a long dreadnought list, change parts and upgrade stages, apply, close/reopen, restart the process and confirm both fleets survive. Simulate a corrupt primary fleet file and confirm backup recovery without overwriting the backup. Test app background/restore, aspect ratio, button hit targets and denied/low-storage write behavior.
- Confirm updates preserve Android app data (`games.catastrophicdiplomacy`) and reject incompatible signers. Verify the same core blueprint file round-trips on desktop and Android, without attempting to sync private paths between machines.

## Locally verified here

`javac --release 17` compiled core plus all test classes on OpenJDK 21. Six headless suites passed: battle determinism, technology catalog (120 components, 2,880 upgrades), shipyard fitting, versioned checksummed fleet saves/pointer gestures, distinct machinery combat, and a deterministic 200-ship/300-tick scale test. Scale elapsed time depends on CPU and does not imply GPU frame-rate or Android performance.

## Outstanding

Physical component/compartment damage, carrier recovery/refuel logistics, missile warhead variants and long-fleet GPU optimization remain additional implementation tasks. No end-to-end Java17 desktop libGDX or Android toolchain build was run in this environment. No signing keys or GitHub Actions workflow files were created, rotated or read for this development slice.
