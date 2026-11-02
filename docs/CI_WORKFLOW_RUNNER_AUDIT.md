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

## Immediate runner-queue reduction — 2026-11-02

This supersedes the earlier policy table **for automatic scheduling**.
The original trigger arrangement was still scheduling complete Windows MSI,
Linux application, signed Android APK, Android emulators, and a release
publication attempt for **every push to main**, even when only weapon visuals
or documentation changed. A live REST audit found **19 queued** and **6
reported in-progress** runs; queued included **12 obsolete releases** and
three SHA-specific seven-seed 200v200 battle matrices.

### Automatic workflows after this change

1. `armor-damage-visual-diagnostic.yml`: **push only on its narrow
   armor renderer/test/file changes**, plus manual dispatch. This is the
   current visual iteration workstream; 33 scenarios remain in the existing
   capture until the fast subset has been verified.
2. `rocket-pd-balance-diagnostic.yml`: **push only on its point-defense,
   ordnance and diagnostic source changes**, plus manual dispatch. Current
   PD-load work is actively exercised here.
3. `fighter-wings.yml`: **pull_request only**, retaining objective
   functional gates before merge; no `main` push trigger.

### On-demand / dormant workflows (no automatic push)

- `continuous-functional-release.yml` — **manual only**. All complete
  Windows/Linux/Android installs, Android 10 faction regressions, signing,
  updater checks and the draft-before-publish safety gate are preserved
  verbatim. Ordinary source commits no longer build release packages.
- `randomized-combat-endurance.yml` — **manual only**, existing seven
  seeds and terminal replay evidence retained. This changes scheduling
  for NEW pushes; it does not cancel any previous in-progress work.
- `armor-live-combat-diagnostic.yml`
- `audio-pitch-diagnostics.yml`
- `fleet-logistics-diagnostics.yml`
- `fleet-tender-logistics.yml`
- `hull-coverage-diagnostics.yml`
- `technology-memory-diagnostic.yml`
- `weapon-art-diagnostics.yml`

The last seven are also **manual-only**; no test code, assertions or
artifact outputs have been deleted. Dormant diagnostic suites can be
manually dispatched when their area of development becomes active again.
All 12 definitions stay in version control for reproducibility.

### Legacy queued jobs

Editing triggers **does not retroactively cancel runs** already queued
by GitHub. Older SHA-based release groups also do not participate in
newer release concurrency. The GitHub connector currently supports
workflow read, rerun and artifact actions but does **not** expose a
cancel-run action. To free those runners immediately, the obsolete
legacy releases must be cancelled in GitHub Actions; do not cancel the
current armor diagnostic, current PD diagnostics, or actively executing
user-requested endurance runs.

### Release policy

Full-release checks remain a prerequisite to **publishing** a manual
release, not a prerequisite for every individual artwork iteration.
PR functional validation remains automatic. Test failures require repair
rather than being converted into silent passes or art-style release gates.
