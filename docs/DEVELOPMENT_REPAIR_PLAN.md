# Catastrophic Diplomacy — Development Repair Plan

**Prepared:** 8 November 2026. **Audited game code:** `0159042e47a9271e5e3b077756f5c4d57763a688`. **Evidence checkpoint:** `26f852a005750e0fd06588f4b93a7ea157f22a13`. **Status:** first implementation batch is underway; B0 coverage and B1/B2 campaign repairs are recorded in the [repair checkpoints](repairs/2026-11-08-campaign/CHECKPOINTS.md). The original audit remains a historical baseline.

## 1. Starting position and operating rules

Fresh Java 17 compilation of core/game/server/desktop passed, together with the executed 106 named Java Smoke tasks and 22 Python tests. The supplementary combined-arms, fighter, hardpoint and timeline checks also passed. Separate probes reproduce campaign-controller, combat chronology and multiplayer integration defects; a successful reproduction process means the defect was observed. The baseline includes working campaign ownership, atomic combined saves, nine-tier research, physical fighter recovery/rearm, multiple defense families and authenticated authoritative networking. Repairs should preserve those systems. [E1][E2][E3][E4][E6]

Finding IDs follow the accompanying [project audit](PROJECT_AUDIT_2026-11-08.md); the evidence records below pin observations to their source checkpoint.

Work remains on the single authoritative `main`, with small, reviewable commits and durable evidence. Extend the existing single automatic smoke job; do not create another workflow or restore automatic specialist diagnostics. Keep Java 17, shared deterministic combat, host authority, package/signing identity and existing saves intact. Preserve nine research tiers, all eleven base hull sizes accessible by Tech III, specialist chassis progression and all 48 survival variants. No branch may silently lose its funded research during migration.

Repair priority is not a new release gate. Build, launch/run and required multiplayer/updater failures retain their established blocking status. Artistic preference, visual polish, balance targets and optional performance measurements remain advisory. A verified crash or unusable required control is a runtime defect even when it occurs in a visual subsystem. Classify individual assertions by consequence instead of broadly deleting rendering/audio checks or treating every diagnostic as mandatory.

## 2. Dependency and responsibility map

Batch numbers identify work packages, not a requirement to serialize independent work. Establish B0 runtime coverage before integrating the functional repairs; remaining advisory-classification cleanup can continue alongside them. Campaign recovery precedes campaign retry changes. Network decoding/session identity precedes expanded presentation and lobby lifecycle. Combat repairs, wiki repair and updater acceptance can proceed independently once evidence discipline is established. Coordinate edits touching `Battle` or `CatastrophicGame` through one integrator per file.

| Batch | Findings / responsibility | Prerequisite | Concrete completion evidence |
|---|---|---|---|
| B0 | F18, F19, F22 — verification and documentation | Current audit | One smoke job covers runtime paths; assertions classified; status ledger current |
| B1 | F01, F02 — campaign persistence | B0 | Failed settlements recover exactly once; damaged authority has an explicit recovery route |
| B2 | F03–F05 — campaign controllers | B1 for result navigation | Pointer/keyboard parity, branching caps and matching nine-tier quotes |
| B3 | F12–F14 — combat/sensing | B0 | Causal steering, defensive withdrawal and delayed finite command sharing |
| B4 | F06–F09 — network state and client | B0 | Typed validated snapshots, explicit session resets, complete presentation and visible exits |
| B5 | F10, F11 — match lifecycle | B4 protocol boundary | Ready/unready/host start and custom fleets through supported pinned TLS |
| B6 | F15 — encyclopedia | B0 | Arbitrary search survives constrained heap and remains interruptible |
| B7 | F23–F26 — updater/release | B0; B1 for save-upgrade fixtures | Production updater path, saved-state upgrade, recovery dispatch and resilient discovery |
| B8 | F16, F17 — advanced integration | B1/B3; B4/B5 for network parity | Persisted scan doctrine and one paid mixed-wing lifecycle |
| B9 | F20–F22 — devices, exchange and content | Relevant repaired feature | Readable reports, ordinary Android file exchange and current acceptance receipts |

## 3. B0 — Make evidence follow actual runtime paths

Expand the existing smoke trigger to cover server, `hbnet`, network codecs, Android/desktop launch/updater code and campaign/persistence changes. Select relevant regressions within the same lightweight job. Audit transitive Gradle dependencies: retain compilation, finite-state, required-resource and functioning-control checks; classify shader spelling, stylistic thresholds and presentation taste as advisory diagnostics. This addresses F18/F19 without multiplying pipelines.

Promote each decisive probe into a regression at the lowest boundary that exercises the real failure: actual controller dispatch, ordinary `Battle.step()`, or framed network receipt. Keep the audit probes as historical evidence. Give the repaired regression assertions their intended successful behavior; do not count the original defect-asserting probe as a repair pass.

**Acceptance:** a runtime-only change in each formerly omitted module triggers the same job; documentation-only checkpoints do not start full releases; required tests reach the affected caller. A harmless shader refactor is not rejected solely for source spelling. Update F22’s status ledger with source SHA, evidence class and pending device proof. Preserve historical receipts while marking superseded claims.

**Checkpoint/rollback:** record the task graph and trigger inventory before/after. Revert an overbroad trigger or misclassified dependency through a normal commit; retain the new regression and explain any temporarily uncovered route.

## 4. B1 — Recover campaign transactions and damaged saves

F01 requires a pending-settlement state around the existing atomic ledger. Compute returned vessels, losses, rewards, salvage and research advancement once, with the source generation and after-action report. Represent write outcomes explicitly. A pre-write failure exposes Retry Save; an uncertain readback reconciles stored authority against the proposed snapshot before attempting another generation. Results must visibly distinguish committed and pending outcomes. Navigation must not silently discard a pending settlement. [E2]

For F02, preserve the correct refusal to mint resources from corrupted authority. Add visible recovery actions: recover a valid backup, preserve/export damaged records, and explicitly start a replacement campaign after the player chooses reset. Distinguish corruption, unavailable storage, insufficient space and unsupported atomic replacement. Setup/research/result screens must display their own transaction failures rather than writing only to a hidden dock notice.

**Acceptance:** execute successful settlement, write failure followed by repair/retry, repeated Retry Save, uncertain write outcome, win/loss/withdrawal/draw, and process reopen. Assert one exact wallet/fleet/research generation, stable vessel IDs and no duplicated rewards. Test valid/invalid/missing/oversized primary and backup combinations. Explicit reset retains recoverable original bytes and creates the approved single-starter, zero-credit campaign.

**Checkpoint/rollback:** retain bounded pre-migration save fixtures and a format compatibility table. Do not issue a new schema until old saves and forward-produced saves have a supported read/recovery policy. A code revert must not overwrite the newest valid player authority. If storage cannot persist a journal, disclose that limitation instead of claiming crash-proof pending recovery.

## 5. B2 — Repair campaign actions and authorization presentation

Give F03 one Results action used by pointer, touch and keyboard. A new campaign sortie must pass through preparation of the committed surviving fleet, with the chosen mission and advisory service warnings. Keep the existing launch-screen and roster validation. Resolve pending settlement before permitting another sortie; do not make the inert button work by deleting its guard.

For F04, derive a mission context from `campaign.available()` and the selected playable mission. Use it consistently for dock activation, squadron activation, deployment, preview and reward inputs. The numeric highest-completed frontier is not the branching mission authority. For F05, replace six-tier clamps and stale labels, and render selected tier, pending authorization, price and post-spend balance from one quote model. [E2]

**Acceptance:** actual pointer/touch/keyboard dispatch after win, loss and withdrawal; an empty active roster returns to management. After mission 1, fronts 11, 21 and 31 obey their actual caps; early-mission replays and locked-map inspection behave consistently. License transitions I–IX show exactly the amount authorized, including VII’s 28 million CR quote rather than VI’s 15 million CR card. The existing core quote and confirmation notice are correct; this repair resolves the conflicting selected card, not an observed wrong debit. Exercise cancelled confirmation, insufficient funds, unmet milestones and save failure. No transaction bypasses funded-discovery checks.

**Checkpoint/rollback:** keep these controller changes separate from balancing or UI redesign. Revert the affected action/model if ownership or debit parity regresses; preserve B1’s recovered authority and data format.

## 6. B3 — Repair the three causal combat failures

For F12, carry an authorized observation through target selection into FREE/HOLD_RANGE navigation. Derive bearing, distance and extrapolation from observed position and age. Keep present body geometry for physical collisions. Audit readiness/health ranking shortcuts separately rather than retaining unobserved enemy truth in a different decision stage.

For F13, align particle point-defense withdrawal eligibility with the defensive contract used by the other families. Maintain power, heat, damage, HOLD_FIRE and escaped-state restrictions. For F14, preserve real reception identity/time and implement a bounded request/returned-solution path, or use a causally mature retained report. Fresh reports must not continually restart command eligibility. Do not remove reciprocal transit checks to make the sharing counter increase. [E3]

**Acceptance:** equal observed histories produce equal normal-step navigation before new information arrives and diverge only afterward. Test contact loss and replay fingerprints. A physically withdrawing PPD ship performs paid defensive dwell against real incoming launches, while invalid resource/order states prevent it. Two spatially distinct recipients exhaust local compute, wait for real transit, then debit one finite fitted donor budget through ordinary firing. No donation appears early or survives loss of donor authority/power.

**Checkpoint/rollback:** one narrow change per defect, with fixed seeds and resource/chronology observations. Preserve a versioned expected behavior change; do not treat an old fingerprint as an unconditional requirement after correcting faulty behavior. Revert the isolated behavior change if determinism or resource accounting fails. Multi-source command policy remains a separate integration decision.

## 7. B4 — Stabilize multiplayer state, presentation and input

First address F09 with typed snapshot decoding before publication to the renderer: version, enums, teams, IDs, duplicates, lengths/counts, finite values and sensible ranges. Reject an incompatible or malformed snapshot visibly while retaining the last valid view. Framing and checksum validation remain necessary but insufficient.

Carry connection/session identity for F08. Reset interpolation, mirrors and transient entity state on a genuinely new session, including equal/higher tick starts and reused entity IDs. Preserve continuity only under an explicit same-session reconnect contract. Then extend F06’s schema to carry actual hull/variant/fitting identity and supported missile, ballistic and craft entities, including births/removals. Clients present host state; they do not simulate authoritative damage locally. [E4]

F07 requires screen-specific input ownership. Detach/disable hidden directory actors during battle, provide visible touch/keyboard return controls, and render terminal/disconnect status above the preserved final battlefield. Distinguish victory, draw/time limit and link failure.

**Acceptance:** pass malformed checksummed rows through the real reader without render-thread failure; verify last-good state remains. Reconnect at lower/equal/higher ticks and to another server using the same IDs. Two real peers display a mixed carrier/dreadnought fleet and its live ordnance/craft correctly. Battle taps never invoke invisible directory actions; disconnect and terminal results have accessible exits.

**Checkpoint/rollback:** version the wire contract and define mismatch handling before changing producers. Deploy compatible server/client builds together. Revert the schema/client pair together if necessary; never fall back to unchecked parsing or local simulation to conceal missing host data.

## 8. B5 — Complete readiness and supported secure lobbies

Separate accepted fleet, ready/unready and match start for F10. Replacement/edit/disconnect invalidates readiness; an authenticated host-start action requires both valid ready seats. No battle ticks occur before that transition. Handle duplicates and late messages idempotently.

For F11, expose the existing TLS lobby factory through the supported launcher/configuration path. The core factory exists; implement its missing entrypoint and test submitted custom fleets over pinned TLS. Preserve signed seats, sequence watermarks, bounded uploads, host-side tier/fitting validation and explicit trusted-LAN mode. Do not add an automatic TLS downgrade.

**Acceptance:** two custom-fleet clients submit, inspect, ready, unready, replace fleets, start and receive terminal results over actual encrypted transport. Reject wrong pins, invalid fleets, spectators’ commands and unauthorized start. Exercise disconnect before/after readiness and authenticated same-session reconnect. Existing 2-, 32- and 400-ship transport tests remain useful, but do not substitute for this lobby path. Include desktop and Android input/certificate UX before claiming platform completion.

**Checkpoint/rollback:** publish a transition table and protocol version receipt. Roll back compatible client/server changes together. Record deliberate seat-release/expiry policy before enabling replacement identities; temporary disconnect must not automatically surrender a signed seat.

## 9. B6 — Bound encyclopedia search memory and work

F15 is a reproduced arbitrary-search OOM at 16 MiB; its 32 MiB fallback also performs substantial synchronous query work. Baseline catalog construction passing at 16 MiB does not close arbitrary search. [E5]

Use a compact searchable token/ID representation, bounded result windows and lazy article detail. Move expensive work away from immediate input/render callbacks, with cancellation or coalescing for superseded queries. Preserve all nine-tier content and 48 survival variants. Reducing the catalog or raising heap alone does not establish a complete repair.

**Acceptance:** run the recorded `plasma`, `power x1.00` and broad `tech` cases at the reproduced heap limit, plus category/filter changes, empty/no-result text, typing/backspace bursts and selected-article opening. Check bounded live allocations and correct matches. Actual supported Android/desktop execution must remain responsive and permit leaving the wiki. Report measured timings and device specifications; do not invent FPS/latency acceptance from this host benchmark.

**Checkpoint/rollback:** retain query corpus, heap/output receipts and content counts. Prefer a bounded paged fallback with visible progress if an optimization fails. Keep the OOM regression functional; comparative speed and perceived smoothness remain diagnostic unless a required interaction fails.

## 10. B7 — Exercise real updates and repair discovery/recovery edges

F23 needs production-path acceptance: actual Update action → metadata check → download/verification → permission or installer handoff → reopened application. The debug dialog is not that path. Create an old-version campaign, researched technology, owned fleet/stores, settings and network identity before upgrading. Fingerprint them, perform a real supported upgrade and continue play afterward.

For F24, permit every supported authorized primary-release event, including manual dispatch, in publication recovery. Preserve exact-source, job/result and artifact validation; select the intended run explicitly. F25 requires a safe desktop discovery policy when the old installed tag is unavailable, without assuming a downgrade is newer. F26 adds conditional metadata caching/backoff and bounded work while preserving newest-published, draft-excluding, prerelease-inclusive selection. Do not assume creation-order page 1 contains the newest published release.

**Acceptance:** current/no-update/offline/rate-limited metadata, malformed manifest, wrong digest/package/signer, cancelled permission, resumed install and successful upgrade; verify identity and save retention. Test removed old tag, newer publication on a later page, cache invalidation and stale/offline state. Recovery accepts an otherwise eligible manually dispatched run and rejects wrong source, missing artifacts or failed required jobs. Linux’s manual extraction and Windows’s installer paths receive their own state-retention evidence.

**Checkpoint/rollback:** retain prior signed installers and save fixtures. Do not rotate signing identity or republish/retag history as a repair shortcut. Roll back updater code independently of player data; validate schema compatibility before offering an older binary. No release is dispatched merely to complete this planning document.

## 11. B8 — Connect scan doctrines and mixed flight wings

For F16, persist STANDARD/PASSIVE/BOOSTED_RADAR/FOCUSED_RADAR/XRAY_BURST in the authoritative standing/deployment state, with focused target reference, eligibility and disabled reasons. Apply before tick 1 because the existing configuration boundary is tick 0. Mid-battle switching requires a separately defined command/state transition. Defaults and old saves must remain valid; preserve the existing standing-order pages, fuel reserve and automatic-withdrawal behavior.

For F17, make existing manifest/bay identities participate in the single campaign inventory authority. Connect purchase, fitting, per-bay damage/throughput, launch, loss, recovery, paid rearm, refit and save/load. Migrate the existing single-profile reserve and returned craft explicitly. Do not create a parallel ledger or assume the standalone wing codec is already live launch authority. Preserve working landing, service delay, ammunition/energy debit and saved fighter seeker selection.

**Acceptance:** a chosen scan doctrine survives save/reopen/deployment, changes real pulse/resource behavior and reaches equivalent authorized server setup. Two distinct paid wing types on real bays launch their configured profiles, consume their own stock, recover into eligible bays and preserve identity through service/refit/reopen, including a damaged or unavailable bay. Destroyed craft stay lost. Check old-save migration and research references across all 48 survival variants and nine tiers when the authority format changes.

**Checkpoint/rollback:** retain versioned migration fixtures and resource conservation reports. Introduce features only after the inventory/scan contract is reviewable. Revert UI exposure independently if needed while keeping migrated data readable. Decide multi-source command fusion against B3’s working causal sharing model before adding arbitration; it is not the fix for F14 itself.

## 12. B9 — Finish device paths, readability and content receipts

F20 needs measured report rows, not overlapping baselines: retain readable ship identity and hull/crew/ammo/fuel details, and derive pagination from available height. Verify long names, 16-digit IDs, large values and multiple pages on actual intended viewports.

For F21, connect checked fleet/challenge codecs to ordinary Android picker/share destinations. Import both sides atomically after faction/count/technology validation, handle cancelled/denied provider access and present actionable export locations. Explain the distinction between saved default blueprints, transient per-ship setup and exported complete fleets.

Complete F22 continuously: current implemented/tested/integration-pending/design-decision entries, exact artifact/source evidence and withdrawn historical claims. Check normal home→campaign→dock/research→deployment→battle→results→save/reopen and skirmish/network equivalents after the affected batches.

**Acceptance:** readable touch targets and exits, functional file exchange, and exact-source device receipts. Measure matched-seed 200v200 CPU/render/allocation behavior at displayed speeds 0.5, 1, 2 and 3 with pause/camera transitions. Keep balance and appearance advisory. Retain all 48 survival variants, physical effects and independent research; record the 231 missing unique voice cues from the current atlas as content work without inventing recordings or blocking release solely for absent narration.

**Checkpoint/rollback:** separate asset/layout work from simulation edits. Retain prior valid assets and codecs; verify required rendering/audio paths still run after reverting optional presentation changes.

## 13. Explicit design queue

Resolve these in written, reviewable contracts before dependent feature expansion; they do not erase the confirmed repairs above.

| Decision | Required contract and proof |
|---|---|
| Player versus spectator network visibility | Define omniscient spectator and sensor-limited player payloads. If player sensing is intended, filter before serialization; hidden truth must be absent from the payload, not merely hidden on screen. Resolve before claiming competitive sensor parity. |
| Campaign abandon, restart, resume and total loss | State whether uncompleted sorties can restart freely, whether process death resumes, and what happens when no hull is affordable. Add the chosen checkpoint/penalty/defeat flow; never silently grant replacement resources. |
| Multi-source FCM | Define donor arbitration, finite budgets, information provenance, measured-track covariance/hypothesis reconciliation and returned-solution timing after F14 works for one donor. Test causal benefit across multiple sources without duplicated compute. |
| Replay and wider local-first multiplayer | A seed/order log is not a complete custom-fleet replay. Specify initial fleets/rules/content version and prove final-fingerprint restoration. Separately define invitations/presence/ACLs, identity backup, signed-seat expiry, full tactical-order parity and legal-fleet capacity/WAN evidence. |

## 14. Checkpoint and closure rule

For every batch, record five stages: **CP0** baseline/scope/dependencies; **CP1** reproduced behavior and decisive regression; **CP2** minimal change and affected-path proof; **CP3** applicable existing suites plus actual platform acceptance; **CP4** single-main commit, exact artifact/source identity and updated open findings. Save a checkpoint before switching domains or ending a work session. A blocked device environment remains an explicit evidence gap.

Each receipt includes finding IDs, changed files, command/input path, environment, expected/observed result, preserved invariants, rollback compatibility and next dependency. Close a finding only at its required boundary: core math, controller interaction, transport, rendered flow, installer or physical device. Passing one boundary does not certify the others. Repeat broader testing only for affected contracts or a required gate; carry remaining advisory measurements transparently.

## Evidence references

Preserved records: [checkpoint ledger][E1], [campaign controller probe][E2], [combat probes][E3], [network probes][E4], [wiki profiles][E5], and [functional results][E6]. Detailed source anchors, classifications and platform limitations are in the accompanying project audit.

[E1]: https://github.com/mrcalzon02/Catastrophic-Diplomacy/blob/26f852a005750e0fd06588f4b93a7ea157f22a13/docs/audits/2026-11-08/CHECKPOINTS.md
[E2]: https://github.com/mrcalzon02/Catastrophic-Diplomacy/blob/26f852a005750e0fd06588f4b93a7ea157f22a13/docs/audits/2026-11-08/campaign-probe.log
[E3]: https://github.com/mrcalzon02/Catastrophic-Diplomacy/blob/26f852a005750e0fd06588f4b93a7ea157f22a13/docs/audits/2026-11-08/combat-probe.log
[E4]: https://github.com/mrcalzon02/Catastrophic-Diplomacy/blob/26f852a005750e0fd06588f4b93a7ea157f22a13/docs/audits/2026-11-08/network-mirror-audit-probe.json
[E5]: https://github.com/mrcalzon02/Catastrophic-Diplomacy/blob/26f852a005750e0fd06588f4b93a7ea157f22a13/docs/audits/2026-11-08/wiki-results.json
[E6]: https://github.com/mrcalzon02/Catastrophic-Diplomacy/blob/26f852a005750e0fd06588f4b93a7ea157f22a13/docs/audits/2026-11-08/functional-result.json
