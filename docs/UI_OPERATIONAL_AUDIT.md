# UI and operational audit — 2026-11-06

Scope: current main shared client menus, Options, encyclopedia controls, fleet-order navigation, Android/desktop updater flows, and Actions triggers/publication.

| Finding | Implemented change |
| --- | --- |
| Options voice volume used a separate drag-bar interaction | Shared painted decrement/increment buttons, 5 percentage point steps, visible percent, unchanged keyboard arrows and saved preference; readout/gutters are inert |
| Fixed text offsets across Options, encyclopedia and some navigation controls | Shared measured, centered, bounded button label renderer; font scale/color restored after drawing; long labels fitted/truncated inside their buttons |
| Default libGDX font across shared client | Bundled Atkinson Hyperlegible Regular (SIL OFL 1.1), baked atlas and vendored source/license, linear filtering, no runtime font dependency |
| Android check status vanished; repeated requests were silently ignored | Persistent hideable status, percent download progress; manual requests join an ongoing startup check and receive its result |
| Manual update check stopped at an offer | Newer APK/package downloads after a manual check; Android verifies size, optional SHA, application ID, newer versionCode and retained signer before the system installer; desktop verifies size, optional SHA and MSI header |
| Same manifestless Android release could download again | Exact installed release tag reports already current; verified pending APK can reopen installation after enabling unknown-source permission |
| Desktop startup check could display a modal offer | Background availability is logged; manual checks provide status, explicit current/failure feedback and download results |
| Ten specialist diagnostics ran automatically on development pushes | Converted to manual-only while retaining their runnable jobs and artifacts |
| Last release passed all 15 build/runtime jobs then failed publishing with HTTP 500 | Bounded retries of publishing the existing draft, with independent public-state readback; no retagging, asset overwrite or skipped functional gate |

## Action hierarchy

- Automatic main-branch checks: `release-blocker-smoke.yml` (functional regressions, path filtered).
- Pull requests: `fighter-wings.yml` (functional verification).
- Explicit release request file/manual dispatch: `continuous-functional-release.yml` (signed Android plus tested Windows/Linux packages).
- Manual recovery only: `publish-verified-artifacts.yml`.
- Eighteen specialist diagnostics remain manual-only; none are scheduled or launched by routine pushes.

This preserves Android signer continuity, real device-API emulator campaign/upgrade/menu tests, required multiplayer/updater checks, and install/launch tests. It reduces routine workflow fan-out without removing those capabilities.

## Verification

Java 17 compilation of core/shared client and desktop updater; existing menu routing and volume tests; Android API compatibility scan; six update-manifest tests; publication retry tests; workflow YAML parsing. The signed-APK runtime workflow also taps both real volume buttons and captures Options/Dev screenshots. Release jobs remain the authority for packaging, signer checks and Android runtime behavior. No physical-device performance claim is made.

## Remaining review

This is a focused pass, not certification that every legacy engineering/subsystem screen has identical chrome. Some dense shipyard technology controls still have dedicated layouts; keep their module selection and information hierarchy in a separate visual review. The release pipeline still runs eleven Android-10 faction shards because those detect genuine campaign launch failures. They are constrained to three concurrent emulators. Historical Actions run records are retained.
