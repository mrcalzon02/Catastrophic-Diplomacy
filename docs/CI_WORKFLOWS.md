# GitHub Actions — single release owner

The repository intentionally has **four** active workflow entry points.

| Workflow | Trigger | Purpose | Can block publication? |
| --- | --- | --- | --- |
| `continuous-functional-release.yml` | Every push to `main` or manual dispatch | Create exactly one source-matched Android APK, Windows MSI and Linux ZIP; test installed desktop apps, Android emulator gameplay and signed build; publish prerelease after exact SHA/checksum verification | **Yes — functional/build/installation/runtime checks only** |
| `fighter-wings.yml` | Relevant pull requests only | Cross-platform desktop compilation, gameplay, network, updater and 200v200 acceptance; debug Android compile; nonblocking art diagnostics | **No — does not publish** |
| `fleet-logistics-diagnostics.yml` | Narrow logistics source changes on `main`, or manual | Support/projector/drone simulation evidence | **No** |
| `hull-coverage-diagnostics.yml` | Narrow hull test/source changes on `main`, or manual | Hull damage and combat diagnostic evidence | **No** |

## Release policy

1. **One automatic publisher:** `continuous-functional-release.yml` owns releases for every push to `main`. No other workflow creates or replaces release assets. Release runs are never cancelled merely because another commit arrives.
2. **Preserve the full functional gate:** gameplay/combat (including 200v200), launch/menu, LAN/PvP authoritative server checks, updater version selection, actual Linux ZIP extraction and launcher execution, actual Windows MSI install/start/uninstall, Android signing verification plus emulator combat/menu, package checksums and published-asset readback.
3. **Non-functional observations are not release blockers:** sprite/texture inventories, artwork, appearance, design and balance checks are informational. A missing resource that objectively prevents a game from building or running remains a functional failure.
4. **No duplicate platform publishing:** PR checks never package or publish signed releases. Legacy fixed-tag RC1 workflows were retired; the original commits and historical Action runs remain accessible in Git.
5. **Compatibility:** Android retains the established signing identity. Fire tablet manifest/ABI preflight is optional and is not certification on physical Fire hardware.
6. **Versioning:** each release run has its own `cd-v0.1.5-dev.<run-number>` tag. Its Android versionCode and desktop update identity match that run. Pre-release status does not prevent discovery by the updater.

## Retired workflow entry points

Obsolete feature-branch or fixed-release workflows removed from current `main`:
`alien-faction-art.yml`, `authoritative-weapons.yml`,
`combined-200v200-apk.yml`, `crew-life-support.yml`,
`full-platform-release.yml`, `hull-foundation-parity.yml`,
`ordnance-doctrines.yml`, `research-ammunition.yml`,
`signed-android-rc.yml`, `updater-integration-qualification.yml`,
`windows-msi-release.yml`.

Their underlying tests and implementation are **not removed**. In particular,
the command `gradle --no-daemon :core:weaponBehaviorSmoke :core:ordnanceDoctrineSmoke :core:ammunitionResearchSmoke :core:ammunitionSelectionSmoke :core:crewAccommodationSmoke :core:hullFoundationSmoke :core:alienFactionSmoke :core:massBattleSmoke` remains available for detailed manual regression passes. Use the two retained diagnostic workflows for their specialized evidence. The previous RC1 releases and exact source history are preserved.

A passing release confirms the tested functions on hosted runners; it does not establish performance on a user's device, production WAN reliability or full visual quality.
