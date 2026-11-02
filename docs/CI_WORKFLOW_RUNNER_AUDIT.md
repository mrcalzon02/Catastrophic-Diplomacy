# GitHub Actions runner and release audit (2026-11-02)

Repository: `mrcalzon02/Catastrophic-Diplomacy`, `main`.

## Before: why new armor captures never started

An audit sample of 100 recent workflow runs contained 70 queued, 3 in progress,
and 27 completed. The release workflow used
`group: functional-release-${{ github.sha }}` with
`cancel-in-progress: false`: every commit had a distinct concurrency group,
so obsolete release matrices were never superseded. Nine optional diagnostics
had no run-level concurrency policies at all. Every release included eleven
separate Android 10 faction emulators plus API34 runtime and three platform
builds, creating a substantial backlog of hosted runners.

A historical release (run 41515547927) failed Windows and Linux on
`:core:shipTacticalDirectivesSmoke` with
`AssertionError: Missing new saved order fields`. That is a **genuine
functional regression on that particular historical commit**, not a runner
failure or cosmetic gate. This audit does not claim the current source fixes
that problem without a successful new test.

## Policies implemented

| Workflow | Obsolete push behavior | Independent manual / PR behavior |
| --- | --- | --- |
| Continuous functional release | One main-branch concurrency group, newest run cancels older | Explicit dispatch on main uses the same latest-only policy |
| Fighter wings PR functional checks | No duplicate main release | Latest PR revision cancels older PR revisions |
| Seven 200v200 randomized endurance runs | Preserve SHA-specific, non-cancelled complete test runs | Manual terminal seed replays preserved, max two simultaneous jobs per run |
| Armor OpenGL visual diagnostic | Supersede old push captures | Manual runs have separate unique concurrency keys |
| Armor live-fire engineering diagnostic | Supersede old push | Manual runs independent |
| Audio-pitch diagnostics | Supersede old push | Manual runs independent |
| Fleet-logistics diagnostics | Supersede old push | Manual runs independent |
| Fleet-tender diagnostics | Supersede old push | Manual runs independent |
| Hull-coverage diagnostic | Supersede old push | Manual runs independent |
| Rocket/PD threat-matrix diagnostic | Supersede old push | Manual runs independent |
| Technology-heap diagnostic | Supersede old push | Manual runs independent |
| Authored weapon-art diagnostic | Supersede old push | Manual runs independent |

**All existing push path filters, smoke test commands, manual-dispatch
options, and evidence uploads are retained.** Optional workflows do not
block release publication. The diagnostic concurrency group contains the
GitHub workflow name; a weapon-art push cannot cancel a rocket/PD run or
a release. Explicit `workflow_dispatch` runs have a unique `github.run_id`
group and are never auto-cancelled by a subsequent push.

### Functional release integrity

The release still requires successful Linux packaging and launch, real
Windows MSI install/launch/uninstall, signed Android APK and update
checks, Android 14 runtime, **all eleven Android 10 campaign faction
shards**, and the existing gameplay/network/200v200 functional checks.
Up to three Android 10 campaign emulator jobs run simultaneously to
reduce runner congestion without dropping a single faction.

The publish step checks current `main` before packaging exposure,
uploads files to a **draft GitHub Release**, verifies exact source SHA,
three assets, and prerelease identity, checks the branch SHA again, and
only then makes the release public. If a newer push cancels the run
during upload, incomplete draft assets remain unpublished. New updater
selection still uses newest **published** GitHub prerelease or release;
drafts are excluded. Release-number gaps caused by cancelled runs are
normal and must not be repaired by retagging.

### Important migration caveat

**Existing queued workflow runs created with the old SHA-specific
concurrency groups cannot be cancelled retroactively by these YAML
changes.** Clearing that historic backlog is a one-time Actions
management task; do not cancel the latest armor run, an important
manual diagnostic, or terminal seven-seed battle tests merely to free
the queue. A failed workflow must remain diagnosable and be fixed,
not disabled.

### Audit acceptance checks

1. New push releases share one branch group and old pending/in-progress
   latest-only releases are cancelled by newer pushes.
2. Full functional gates and all eleven Android 10 factions remain
   required for publication.
3. Manual standalone diagnostics are not cancelled by routine pushes;
   push-generated diagnostics run on the latest relevant revision only.
4. Seven terminal 200v200 seeds still run to completion and publish
   their failure/replay evidence.
5. Publish verification confirms exact SHA and complete Windows, Linux,
   Android binaries, with no obsolete partial public release.
6. Latest real OpenGL armor samples and PD threat comparisons remain
   available for direct review after hosted runners execute.

This is an execution audit; successful completion of the new workflows
must be read back from GitHub rather than inferred from committed YAML.
