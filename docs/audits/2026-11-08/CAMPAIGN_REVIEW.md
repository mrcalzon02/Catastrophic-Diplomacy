# Campaign, player flows, research, shipyard and persistence

**Finding map:** CF-02 → F01; CF-04 → F02; CF-03 → F03; CF-01 → F04; CF-06 → F05; CF-05 → F20. The [master repair plan](../../DEVELOPMENT_REPAIR_PLAN.md) governs cross-domain order.


## Scope and evidence rules

Game-code baseline: **0159042e47a9271e5e3b077756f5c4d57763a688**. Later audit checkpoints are documentation-only; source claims here refer to the code baseline. No production source was changed. The common Gradle/functional suites and separate controller probe are recorded independently below. Existing feature tests and diagnostic outputs are evidence within their tested scope, not proof that every interactive route is correct.

**Fresh executable evidence:** [CampaignFlowAuditProbe.java](CampaignFlowAuditProbe.java), compiled into `audit-work/campaign-probe-classes`, executed on Temurin 17.0.21+9 against freshly compiled baseline code and the checksum-verified release resource tree. [campaign-probe.log](campaign-probe.log) records five reproduced defects. The probe calls actual `CatastrophicGame` private handlers through reflection, uses minimal `Gdx.app`/`Gdx.files` interfaces, stores only temporary test fixtures, and removes fixture data after the run. This proves controller behavior; it is not rendered desktop or Android touch verification. Its battle-settlement fixture marks an opposing hull defeated to isolate the transaction, rather than claiming a complete naturally played campaign victory.

**Fresh common evidence:** production compilation succeeded; 106 unique named Smoke tasks succeeded, including campaign and UI groups. The [functional log](functional-checks.log) preserves the full run. No fresh local GUI acceptance was obtained; hosted release evidence is identified separately in the master audit. These passing checks do not contradict the defects here: the existing core tests do not invoke the faulty frontend callbacks and the menu tests largely verify coordinates/affordances.

## Executive assessment

The intended campaign loop is substantially implemented: faction choice, a single owned starter hull, branching fronts, owned active/reserve vessels, paid services and refitting, nine engineering licenses, individual funded module research, per-vessel orders, mission-specific opposition and waves, final 200v200 deployments, after-action accounting, and a combined atomic progress/fleet/technology authority. Historical planning documents materially understate this implementation and must not be used as a current missing-feature list.

The main integration weakness is **the handoff between otherwise valid core systems and frontend state transitions**. Five faults are reproduced through actual handlers: branching missions receive a numeric-frontier fleet cap; campaign Retry does nothing when tapped; a failed settlement can be left without retry after the fault is removed; corrupt primary plus backup blocks New Campaign as well as Continue; and late license authorization switches its budget card to the wrong owned tier. One additional layout collision is directly established from rendering coordinates. Fix these with a small shared controller/action model and transaction result handling, not a broad game rewrite.

## Intended player paths and actual authority

| Player path | Current code path | Current behavior and evidence | Remaining qualification |
|---|---|---|---|
| Home → New Campaign → faction → Begin | `openCampaignSetup`, `newCampaign` | Creates `startSolo` corvette, starts progress and technology, commits one ledger before opening map. Explicit replacement warning exists when a campaign is loaded. | Creation failure notice is stored in the dock channel, which setup does not draw; corrupt authorities make replacement impossible (CF-04). |
| Launch → Continue | `restoreCampaign`, `restoreCampaignFleet`, `showCampaign` | Highest valid atomic primary/backup generation is authority. Legacy split saves migrate only when no ledger authority exists. Continue selects `recommendedMission`. | No in-game repair/reset flow when both authority copies fail. No active-sortie resume checkpoint was found. |
| Campaign map → selectable fronts → mission | `CampaignProgress.available`, `CampaignMap`, `openDeployment` | After first chapter mission, middle fronts can open out of numeric order; within each front three lanes and convergence conditions apply; completed missions remain replayable. | Dock and squadron cap code still assumes `unlocked` is an authorization ceiling (CF-01). |
| Map/dock → fleet management | `openDock`, `dockToggle`, `editSquadron`, services and refit handlers | Owned vessel IDs, reserves, active group, squadron naming/radio, paid repair/crew/ordnance/fuel/craft and reserve-only refit are wired. Transactions compute immutable proposed state before committing. | Branch cap mismatch; errors and selected-mission context need shared handling. Service warnings are intentionally advisory. |
| Dock → shipyard → blueprint/purchase/refit | `openCampaignShipyard`, `openCampaignRefit`, `purchaseCampaignHull`, `refitCampaignHull` | Campaign blueprint is distinct from owned hulls. Purchases debit full quote; refits preserve identity and compatible inventory; campaign discovery checks apply. | Human acceptance should exercise reserve/active restrictions, depleted stores, rejected affordability and persistence after reopen. Do not claim a blueprint Apply repairs a commissioned vessel. |
| Map/dock → Research → Technology Lab | `requestResearch`, `requestTechnology` | Licenses/labs and specific technologies use wallet quotes, second-action confirmation and shared ledger persistence. Each victory advances funded work. Nine tiers are present. | Authorization selects the wrong late-license budget card (CF-06); failure messages use a different channel from the one drawn. |
| Dock → Deployment → standing orders → Launch | `openDeployment`, `adjustStandingOrders`, `launchCampaignMission` | Deployment uses actual selected mission cap; rejects empty/over-cap owned active list; exposes service warnings; maps permanent campaign IDs to tactical slots; launch copies exact active IDs into sortie. | Campaign launch requires the DEPLOYMENT screen, which the Results Retry pointer path violates (CF-03). |
| Battle → subsequent wave or terminal result | render transition via `BattleAftermath`/`CampaignEncounter.advance` | Waves reuse the battle; terminal advance produces settlement; winner requires engaged friendly survivors and eliminated opposition. | Tactical Restart/Home and OS interruption have no durable active-sortie journal; desired abandon/restart policy needs an explicit decision. |
| Terminal battle → After Action → Dock → reopen | `finishEngagement`, `CampaignAfterAction.capture`, `CampaignFleet.afterBattle`, `commitCampaign`, `leaveResults` | Progress, returned vessels and research are normally one durable generation. Named ship report and per-vessel expenditure are integrated. | Failed settlement recovery is missing (CF-02), Retry pointer no-op (CF-03), report lines overlap (CF-05). |
| Skirmish preset/custom → deployment → battle → retry | `selectScenario`, `openSkirmishDeployment`, `launchSkirmish`, `reset` | Preset selection resets fleet overrides, validates challenge limits, permits per-ship fitting, deterministic demonstration presets bypass player overrides; skirmish Results retry launches. | Durable default template pair differs from transient individually fitted deployments; communicate scope and decide whether full setups should auto-save. |
| Skirmish fleet/challenge exchange | fleet and challenge codec imports/exports | Fixed local filenames, checked codecs, faction/count/technology validation; fleet import only installs both sides after both validate. | No ordinary Android picker/share route was found. Source explicitly describes local data as app-private. This is an exchange usability gap, not missing codecs. |

## Confirmed findings in repair order within this domain

### CF-02 — Failed battle settlement has no recovery transition

**Priority:** high, state continuity and reward integrity. **Evidence:** reproduced actual controller failure. This is not a claim that the normal atomic ledger is absent or nonfunctional.

**Sources:** `game/src/main/java/diplomacy/client/CatastrophicGame.java:1807–1864`, `2195–2212`, `2796–2815`; `game/src/main/java/diplomacy/client/GameMenuPanel.java:1244–1303`; `core/src/main/java/diplomacy/sim/CampaignLedger.java:139–175`.

`finishEngagement` calculates the after-action report, returned fleet, victory credit, salvage and research progression. If `commitCampaign` fails, it sets reward zero and an error in `dockNotice`, then unconditionally opens RESULTS. The results renderer does not draw that notice. `leaveResults` opens the normal dock using the still-old progress/fleet; it neither retries nor retains a settlement action. Its visible Retry is a sortie replay action, not a commit retry, and the campaign pointer version is itself broken (CF-03).

**Fresh reproduction:** begin from a valid generation1 authority and a winning campaign battle fixture. Make the primary `.tmp` pathname a directory to create a deterministic write failure. Invoke actual `finishEngagement`: victory Results appears, lastReward is0 and live progress stays at its old value. Remove the injected filesystem fault and invoke actual `leaveResults`: the dock opens and on-disk authority remains generation1. No earned mission/research advancement is committed even though storage is again usable. The existing fail-closed behavior correctly avoids spending credits or updating only part of the roster; the missing recovery handoff is the defect.

**Player impact:** a completed engagement can lose its outcome with no explicit save-failure status in the report and no way to retry the transaction. Previous hull/stores can reappear because the entire old authority remains intact. A future transient readback error after a successful write additionally needs reconciliation; blindly retrying a stale generation must not double-credit or strand the session.

**Repair:** represent computed settlement as an immutable pending transaction with its source generation and report. Attempt it once; return a typed result such as committed, rejected-before-write or outcome-unknown. On a failure, show an explicit pending-settlement state and Retry Save. On unknown outcome, reread authority and compare against the proposed snapshot before any new write. Share the displayed failure channel with the owning screen. Preserve ordinary advisory service warnings; do not make balance diagnostics into release gates. If storage cannot persist even a journal, say that leaving the process risks loss rather than claiming a durable checkpoint exists.

**Acceptance:** one actual controller test for successful settlement; injected failure before write followed by Retry Save; fault removed before ordinary navigation; interrupted or uncertain readback with matching stored generation; duplicate activation of Retry Save; loss/withdrawal/draw settlement; process reopen after committed settlement. Assert exact wallet, owned IDs, hull/stores, research progress and generation. An earned outcome is applied once or visibly remains pending; it is never silently discarded.

### CF-03 — Campaign “Retry” pointer action is a no-op

**Priority:** high, broken advertised player path. **Evidence:** reproduced actual pointer handler.

**Sources:** `CatastrophicGame.java:564–570`, `1780–1785`, `2065–2074`; `GameMenuPanel.java:1300–1302`.

The rendered Retry button calls `launchCampaignMission()` from RESULTS when clicked/tapped. That method immediately returns unless the current screen is DEPLOYMENT. In contrast, keyboard R in the same RESULTS screen calls `openDock()`. The Retry button is therefore inert for every campaign result, while skirmish retry works.

**Fresh reproduction:** initialize a valid campaign/owned starter, RESULTS screen, `playedCampaign=true`, active mission1; call actual `tapFrontEnd(480,145)` inside the displayed Retry region. Screen remains RESULTS and no battle is created. The guard is unconditional, so this is not a timing or asset issue.

**Repair:** define one Results action shared by pointer, touch and keyboard. For campaign, prepare a new sortie from the currently committed owned fleet and chosen mission, exposing services/formation before launch; for skirmish, retain intended replay behavior. Do not remove the launch screen guard to bypass ownership/setup validation. Resolve CF-02 first so Retry cannot discard a pending settlement.

**Acceptance:** win/loss/withdrawal results each accept pointer/touch/keyboard consistently; empty surviving active group routes to dock, surviving fleet prepares correctly, depleted stores remain depleted until paid services, and no invalid deployment or stale sortie IDs appear. Test actual action dispatch rather than hitboxes alone.

### CF-01 — Valid branching mission gets the wrong fleet activation cap

**Priority:** high, progression/force deployment integration. **Evidence:** reproduced actual dock handler; same source pattern in squadron handler.

**Sources:** `CatastrophicGame.java:1455–1464`, `1527–1536`, `1619–1635`; `core/src/main/java/diplomacy/sim/CampaignProgress.java:84–123`, `239–260`; `core/src/main/java/diplomacy/sim/LevelEconomy.java:15–34`; `core/src/main/java/diplomacy/sim/CampaignFleet.java:240–255`.

Campaign `unlocked` is `highestCompleted+1`, retained as a numeric frontier. It is not the set of playable missions. `available()` deliberately opens fronts11,21,31 after mission1. The dock and squadron activation paths nevertheless choose `min(selectedMission, campaign.unlocked())` before asking for the fleet cap. After first clear, mission31 is available and allows5 vessels, but the dock uses mission2 and cap1. Deployment and generated opposition use the real mission31 definition.

**Fresh reproduction:** first-clear mission1 state, two owned corvettes with one in reserve, selected mission31. `available(31)` is true, `unlocked` is2, real cap is5. Actual `dockToggle` refuses activation with “Mission fleet cap reached” and leaves one active ship. The core roster accepts the exact same second vessel when supplied the real cap5. This fixture establishes the frontend mismatch; a naturally earned second hull can be funded through normal replay earnings.

**Player impact:** players choosing legitimate branches cannot deploy the force the mission permits. Squadron activation inherits the same mismatch. Depending on chosen lane and existing roster, later branches also use a lower unrelated cap.

**Repair:** obtain the current mission through a single available-mission authority shared by map, dock, squadrons, deployment and preview. Validate using `campaign.available`, not numeric `unlocked`. If the map is merely inspecting a locked mission, resolve an explicit playable target or show that status consistently. Preserve the independently specified enemy force and underdog reward formula.

**Acceptance:** after first-clear1, active/reserve and squadron operations agree with deployment for available11/21/31; test cap reached exactly, removal from active, locked inspection, replay of an early mission after later progression, and same result across desktop/Android input. Assert the actual selected mission’s cap, not a copied frontend formula.

### CF-04 — Corrupt primary plus backup traps both Continue and New Campaign

**Priority:** high recovery/usability; conditional on real authority damage. **Evidence:** reproduced restore and creation controllers with corrupt fixture files.

**Sources:** `CampaignLedger.java:102–127`, `139–148`; `CatastrophicGame.java:1172–1187`, `2130–2146`, `2195–2212`; `GameMenuPanel.java:794–805`.

The ledger correctly refuses to mint replacement ships or silently migrate stale legacy data when an authority exists but no valid primary/backup can be read. `restoreCampaign` clears live campaign/fleet/discoveries in this condition. Continue therefore routes to the setup screen. However, Begin/New Campaign calls the normal generation commit, which first loads the same invalid authority and rejects. There is no explicit archive/quarantine/reset-recovery action in the audited frontend. The setup screen does not display the failure `dockNotice`, and the catch message suggests freeing space even when corrupt content caused the failure.

**Fresh reproduction:** create temporary invalid primary and backup, call actual `restoreCampaign`, then actual `newCampaign`. Campaign remains null; screen stays CAMPAIGN_SETUP; corrupt data is preserved. Message becomes “CAMPAIGN SAVE FAILED: credits and fleet unchanged; free space and retry.” Repeating Begin cannot solve this condition.

**Repair:** add an explicit recovery screen with separate recover-valid-backup, export/archive corrupt files, and deliberate start-new authority actions. Preserve the damaged files for recovery. The user’s explicit New Campaign replacement decision can authorize reset, but the ordinary loader must never silently issue new resources. Surface the actual class of error in the visible screen.

**Acceptance:** valid primary; corrupt primary with valid backup; missing primary with valid backup; both invalid; oversized authority; inaccessible storage; explicit new campaign after archive; cancelled reset. Assert no overwrite before explicit reset, original corrupt bytes retained, starter and zero-credit policy correct, new authority fully validates and reloads. Report unsupported atomic filesystem distinctly from content corruption and full disk.

### CF-06 — Late research card conflicts with the correct pending quote

**Priority:** medium, spending clarity and nine-tier completeness. **Evidence:** reproduced actual research handlers.

**Sources:** `CatastrophicGame.java:1249–1258`, `1328–1349`; `GameMenuPanel.java:848–890`, `907–910`; `CampaignBudgetQuotes.java:25–43`.

Opening research correctly selects `min(8,researchRank)` for the next of nine tiers. Authorizing the next license instead resets the selected card using `min(5,researchRank)`. At ranks6–8 it selects tier6, which is already owned, while quote/confirmation/transaction still refer to the actual next tier7–9. The budget card derives cost and balance from the selected tier; its display and the actual pending payment diverge.

**Fresh reproduction:** legal rank6, highest completed250, sufficient funds. `openResearch` selects node6 (tier7). Actual `requestResearch(LICENSE)` selects node5 (tier6) but arms the next-license quote. The card’s tier6 cost is **15,000,000 CR**, whereas actual tier7 quote is **28,000,000 CR**. The confirmation notice contains the true quote, so this is a conflicting UI authorization presentation rather than an incorrect debit calculation.

**Repair:** use the nine-tier range and derive the selected card, authorization notice, enabled state and pending quote from one model. Show next-tier context consistently after first/second confirmation; owned-tier inspection can remain informational but must not masquerade as the pending purchase. Also fix stale `maxStage + "/6"` text in the skirmish challenge notice at line400 while doing the tier-string cleanup.

**Acceptance:** ranks0–8 authorize the intended next tier; rank9 has no next purchase; ranks6/7/8 display tier7/8/9 and exact matching quote/wallet balance; denied milestones and insufficient funds remain consistent; confirmation cancellation and stale-wallet changes re-quote; no debit before confirmation or on save failure.

### CF-05 — After-action ship IDs overlap expenditure details

**Priority:** medium presentation/readability; not a build or balance gate. **Evidence:** source-established drawing collision, visual acceptance still required.

**Sources:** `GameMenuPanel.java:1264–1279`.

For each commissioned-ship row, identity text is rendered at `y-17`, and the hull/crew/ammo/fuel line is rendered at `y-20`, a separation of only3 world pixels. Both use the same normal font. These occupy the same line height; long vessel IDs are especially likely to obscure the detail line. Five rows use fixed48 spacing, so the repair must consider row/page capacity as well as merely shifting one baseline.

**Repair:** lay out row content with measured font line height and explicit bounds, or combine/remove a redundant line while retaining access to full stable vessel identity. Recompute rows per page and pagination from available height. Avoid shrinking the entire report until it is illegible on mobile.

**Acceptance:** real result renders on desktop and Android at intended viewport sizes, with long70-character names,16-digit IDs, maximum ammo/fuel figures,5+ vessel pagination and all outcome labels. No line collision, clipped buttons or unsupported scrolling; performance information remains optional diagnostics.

## Important implemented systems that old documents describe as absent

1. **Atomic campaign authority is present.** `CampaignLedger` v2 stores progress, fleet and researched technology together. It checksum-validates encoded records, protects generations, writes fsynced temporary content and uses atomic move, retains a valid previous snapshot and reads back. Source `CampaignLedger.java:1–178`, caller `CatastrophicGame.java:2191–2213`. The pending issue is client recovery around failures, not inventing another split-save implementation.
2. **Detailed fleet after-action reporting is present.** `CampaignAfterAction.capture` and Results show named owned hull outcomes and expenditure. `finishEngagement` wires this report before settlement. Report layout and durability/recovery need work; “no after-action system” is inaccurate.
3. **Paid services, ownership and refits are present.** `dockService`, `dockServiceAll`, purchase and refit handlers compute proposed immutable wallet and roster values and commit together. The shipyard blueprint is deliberately separate from existing owned ships. Refit requires reserve and structural readiness, retains identity and compatible stores, and does not create free resupply.
4. **Research is not just a catalog.** Both engineering licenses/labs and individual module discovery projects debit campaign credits and advance on victories. Production purchasing/refitting uses discoveries-aware `CampaignShipyardRules.requireAllowed`, not only the legacy license overload. All positive installed module stages must map to funded technology IDs. Survival/sensor/sensing physical hardware gates are in that common rule. The full48 survival lineages remain required scope and are covered by the [combat review](COMBAT_REVIEW.md); this report does not claim all48 runtime behaviors were verified.
5. **All-nine-tier UI exists.** The research screen renders9 license nodes, technology actions map36 branch-stage choices, core progression accepts ranks0–9, and late tier milestone checks exist. A residual six-tier frontend clamp/text is a defect within that implemented system.
6. **New campaign uses one starter.** Production `newCampaign` invokes `CampaignFleet.startSolo`; older multi-vessel `start` exists for compatibility and fixtures. Do not treat it as the production new-game economy.
7. **Readiness warnings are implemented and advisory.** `openDeployment` calls `activeServiceWarnings`; damaged or under-supplied ships can be risked intentionally. Do not convert this into mandatory full service without a user design decision.

Documentation requiring reconciliation includes `docs/CAMPAIGN_FLEET_CONTINUITY.md` (its remaining-work list still calls ledger, detailed report and readiness warnings incomplete), `docs/LEVEL_SYSTEM_I.md` (old six-license/minimum-one-mission/purchasing status), and README’s technology/research paragraph (120/2880/six ranks and no campaign wallet/progression claim). `CampaignProgress.requiredMissions` comments still say one-victory minimum while actual code uses `max(0,base-speed)` and current zero-duration tests expect instant completion. Record the currently approved zero-floor behavior and update the stale comments unless the user explicitly changes that design; do not silently impose the older comment as a new requirement.

## Scope decisions and unverified paths

### Campaign interruption and abandonment

The current `pause()` handles audio; it does not serialize an active sortie. Tactical Restart reconstructs the encounter from committed pre-sortie stores, and tactical Home leaves combat without settlement. Those are implementation facts (`CatastrophicGame.java:2283–2304`, `2691–2729`, `3909–3912`). They are not automatically violations: a replay-friendly campaign may intentionally allow restart/abandon without penalty. A persistent strategic game may instead require active-sortie checkpoints or an explicit abandonment cost. Decide the desired rule, disclose it in the UI, then test restart, Home, desktop close, Android background/kill and process recovery. Do not fabricate a midbattle-resume guarantee now.

### Extinction / insufficient-money recovery

A campaign can lose all active hulls; reserve activation and affordable replacement are implemented. If all owned hulls are lost and funds are below replacement cost, there is no visible bankruptcy/last-chance recovery path in this audit. Determine whether this is intended campaign defeat, optional emergency recovery, or a required route to New Campaign. Avoid silently granting free hulls. Test the existing defeat presentation before choosing a mechanic.

### Skirmish save and exchange scope

`restoreFleets/saveFleets` persist the left/right default blueprint pair. Individually engineered `SkirmishFleet` arrays persist in memory for the session and can be explicitly exported, but are not automatically restored through that pair codec (`CatastrophicGame.java:368–394`, `1055–1089`, `1094–1136`, `2436–2452`, `2488–2509`). Formation/queued commands are rebuilt on entering deployment. This may be intentional, but UI labels should explain the difference between a reusable default, a per-ship battle setup, and an exported fleet. Returning from an already prepared setup and changing the base blueprint should be manually checked for stale in-memory overrides; do not report a confirmed defect until expected replacement semantics are defined.

Fleet import uses `shipyard/import-left.cdfleet` and `shipyard/import-right.cdfleet`, while export writes `skirmish-left.cdfleet` and `skirmish-right.cdfleet`. Challenge import uses `shipyard/import.cdship`. Codecs validate data and expected factions/count/technology, but no ordinary Android storage picker/share integration is apparent. A portable file workflow needs provider selection/export handling rather than instructions to manually place files in private Android storage. Treat this as an incomplete user-facing exchange route, pending platform verification, not as a missing codec.

### True power-loss durability

The ledger fsyncs file content and uses atomic replacement but does not fsync the containing directory in the examined implementation. Its behavior under actual sudden power loss is not proven by happy-path roundtrips or injected application-level write failures. Document tested guarantees precisely and validate filesystem/platform assumptions if strict crash/power-loss durability is an explicit requirement. Do not claim that a source-level atomic move proves every hardware power-failure scenario.

### Multiplayer flow

Home constructs `MultiplayerPanel`; construction exceptions only produce a log (`CatastrophicGame.java:1898–1900`). Actual networking, lobby/ownership and platform retry feedback belong to the other audit. Root’s fresh signed-TLS tests pass2/32/400ships, which establishes those protocol scenarios, not all user-visible connection errors.

## Development work packages and checkpoints

| Package | Deliverable | Dependency / scope | Required checkpoint evidence |
|---|---|---|---|
| C1: result transaction recovery | Typed settlement outcome, pending report state, visible save errors, reconciliation and exactly-once Retry Save | First; preserve current ledger format where practical | Injected failure before write + unknown/readback outcome; success/reopen; wallet/fleet/research equality; no lost pending state on UI navigation |
| C2: campaign route repair | Shared Results action, consistent keyboard/pointer route through owned fleet preparation | C1 pending settlement resolved before a new sortie | Win/loss/withdrawal/empty roster actual controller tests; rendered click/tap evidence |
| C3: mission context authority | Same validated mission descriptor for map/dock/squadron/deployment/opposition preview | Independent of C1 but can land in same focused campaign repair batch | Actual branch31 cap reproduction resolved; fronts11/21; replay/locked mission cases |
| C4: save recovery screen | Corruption-specific visible state, preserve/export bad authorities, explicit new-authority reset | Reuse C1 error presentation/result model | Primary/backup corruption matrix and user-confirmed reset path; no free recovery on ordinary load |
| C5: nine-tier authorization UI | Remove six-tier clamps/labels; one quote model for selected/pending purchase | Existing core discovery authority retained | All tier transitions including7–9 exact displayed/debited prices and zero-fund/milestone/save-fault cases |
| C6: report layout + exchange clarity | Non-overlapping measured after-action rows; communicate skirmish persistence; platform file workflow design | Visual changes diagnostic until actual acceptance; exchange implementation requires verified platform provider | Desktop + Android renders, long-data pagination, explicit import/export success/failure walkthrough |
| C7: authoritative document reconciliation | Replace stale “missing” list with code/test/release/user-acceptance status per system | Use evidence from preceding packages | Every claimed implemented/tested feature has current source/test and date; retained design questions are not represented as finished |

Checkpoint convention for ongoing development: CP0 scope/baseline; CP1 reproduction and failing observable behavior; CP2 minimal fix and affected-path tests; CP3 full applicable existing suites plus actual platform path; CP4 commit/build artifact and update development ledger. A checkpoint must include changed behavior, source SHA, evidence file names, resolved/open finding IDs and next dependency. Do not turn balance, presentation style or optional performance profiles into a blanket release blocker. Build, launch/runtime and required updater/multiplayer failures retain their separate established gates.

## Evidence index

- [Checkpoint ledger](CHECKPOINTS.md).
- [Actual controller probe](CampaignFlowAuditProbe.java).
- [Probe output](campaign-probe.log): exit 0 means all five intended defects were reproduced.
- [Compile metadata](compile-result.json).
- [Functional test log](functional-checks.log).
- Source paths and line references above refer to the [immutable audited source](https://github.com/mrcalzon02/Catastrophic-Diplomacy/tree/0159042e47a9271e5e3b077756f5c4d57763a688).

No local fixture data, proposed progression state or injected error is represented as a real user save. No production fix is claimed complete.
