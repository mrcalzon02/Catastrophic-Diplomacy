# GitHub Actions — current workflow ownership

Reconciled on **8 November 2026** against production source `0159042e47a9271e5e3b077756f5c4d57763a688`. This documentation update changes no workflow, trigger, job or release request. See the [full audit](PROJECT_AUDIT_2026-11-08.md) and [development repair plan](DEVELOPMENT_REPAIR_PLAN.md).

## Actual activation

There are **22 workflow definitions**, with **one automatic main-push smoke job** for the explicitly listed source paths and **no scheduled workflow**.

| Workflow/category | Trigger | Purpose and job scope |
| --- | --- | --- |
| `release-blocker-smoke.yml` | Selected main source paths, or manual dispatch | One `objective-regressions` job; no APK/MSI packaging |
| `continuous-functional-release.yml` | Deliberate change to `.github/release-request.json` on main, or manual dispatch | Verified Linux, Windows and Android packages, runtime checks and publication |
| `fighter-wings.yml` | Relevant pull requests | Three jobs: desktop/server, Android compilation, advisory art diagnostics; no signed release |
| Eighteen specialist diagnostics | Manual dispatch only | Targeted mechanics, art, balance, audio and performance evidence |
| `publish-verified-artifacts.yml` | Manual dispatch only | Recover publication from an eligible already-tested source run |

Routine documentation changes and ordinary main commits outside the smoke path filter do not schedule a full release. The release workflow has six job definitions; its eleven faction-runtime matrix entries make the current explicit release **16 actual jobs**: 15 build/runtime jobs plus publication. The campaign matrix allows three concurrent emulators.

Release concurrency cancels an older same-ref explicit release run when a later release request starts. Ordinary development commits do not match the release request-file filter. The tested source remains pinned; publication checks that it is still an ancestor of main, so a subsequent documentation commit does not by itself invalidate the candidate.

## Manual diagnostic inventory

- Armor: `armor-damage-visual-diagnostic`, `armor-live-combat-diagnostic`, `armor-mask-48-ab`.
- Audio/timeline: `audio-pitch-diagnostics`, `combat-timeline-diagnostic`.
- Logistics/coverage: `fleet-logistics-diagnostics`, `fleet-tender-logistics`, `hull-coverage-diagnostics`.
- PD/ordnance: `particle-pd-diagnostic`, `pd-flak-wave-diagnostic`, `rail-pd-diagnostic`, `rocket-pd-balance-diagnostic`, `rotary-ciws-diagnostic`.
- Performance/endurance: `position-fidelity-diagnostic`, `randomized-combat-endurance`, `technology-efficiency-diagnostics`, `technology-memory-diagnostic`.
- Assets: `weapon-art-diagnostics`.

Names above omit the common `.yml` suffix. These jobs remain available; routine pushes do not launch them.

## Release contract

1. Build, launch/run and required multiplayer/updater functionality are release-blocking. Art, visual style, balance and numerical performance are diagnostics. A missing required resource that prevents launch remains a real functional failure.
2. Use the retained Android signing identity and package ID. Never rotate secrets as part of ordinary development or audit work.
3. Each release receives its own tag. Do not retag or overwrite a different source's published package. Published prereleases count as updates; drafts do not.
4. Check actual platform packages, source identity, hashes and published asset readback. Successful packaging is not physical-device certification.
5. Keep routine verification within the single intended main smoke job and existing platform release route. Do not add unrelated workflows or dispatch diagnostic jobs merely to generate activity.

## Audit findings requiring follow-up

- **F18: coverage holes.** The one smoke path filter misses server/network/hbnet, Android/desktop and much campaign/persistence source. Expand relevant coverage within the existing job.
- **F19: transitive convention checks.** Mandatory Gradle tasks still pull some source-spelling or visual convention assertions. Preserve actual runtime/resource safety tests while making artistic/implementation-convention checks advisory.
- **F24: recovery mismatch.** Main release permits `workflow_dispatch`, but recovery currently requires the source event to be `push`. Align supported entrypoints without weakening source/artifact/job verification.

These are planned repairs, not changes performed by this audit. The full register and acceptance criteria are in the development plan.

## Source-matched release evidence

[Release `cd-v0.1.5-dev.693`](https://github.com/mrcalzon02/Catastrophic-Diplomacy/releases/tag/cd-v0.1.5-dev.693) targets the audited source and includes Android APK, Windows MSI, Linux ZIP, full checksums, package identity, release manifest and signing evidence.

[Release run 42349197474](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/42349197474) has all 16 jobs successful. [Objective smoke 42349197600](https://github.com/mrcalzon02/Catastrophic-Diplomacy/actions/runs/42349197600) has its one job successful. These are exact-source hosted results; fresh local audit results are separately recorded in the [audit evidence](audits/2026-11-08/README.md). Historical PR #23 and old failed receipts must not replace this current-source evidence.
