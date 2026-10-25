# GOOGLE PLAY LIVE RELEASE BRANCH — PARKED / NOT AUTHORIZED YET

**Repository:** `mrcalzon02/Catastrophic-Diplomacy`  
**Branch:** `google-play` — the prospective Google Play **live release** branch.  
**Active sideload/testing branch:** `main` (no automatic channel migration).  
**Status:** PARKED / NOT RELEASED / NOT PLAY-APPROVED. Creating this GitHub branch does NOT register, authorize, approve or publish the app in Google Play Console.

## Release-channel separation

- Continue all normal application development and signed GitHub APK / phone-test / manual sideload delivery on `main`, using existing signer, APK release cadence and save continuity. Preserve current release workflows and do not make Play publishing a dependency for development APKs.
- Use `google-play` only to stage and verify future Play-specific Gradle build variants, separate upload signing, store policy data and distribution changes. It is **not** a second automatic phone-test publisher.
- Future source promotions from `main` into `google-play` must preserve independent Play signing and Play-safe update/code-delivery behavior. A source merge or fast-forward is not approval or deployment. No GitHub Actions or automated Play deployment were added.
- Do NOT call the branch “Google authorized” in release notes until actual Play Console verification. Release state changes require direct evidence and explicit authorization for publication.

## Hard mandatory functional + policy release test gate

The Play candidate branch contains `tools/verify_play_release.py` as a fail-closed release-test entry point. It is **not** enabled in the sideloading workflow, nor is a missing command automatically treated as a pass.

1. Functional/source preflight: `python3 tools/verify_play_release.py --preflight`. Every required functional test command in the runner must return exit code 0; no report-only downgrade, no bypass/no-op or virtual “PASS.” A static success does not prove a released APK.
2. Signed artifact candidate: `python3 tools/verify_play_release.py --evidence <play-evidence.json> --apk <real-signed-test.apk> --aab <real-upload-signed.aab>`. Requires all **38** indexed canonical Google Play controls, checked statuses and evidence, APK hash and certificate, exact package identity, Play AAB hash/upload certificate, privacy/API/Console-registration and physical device upgrade evidence. It does not publish.
3. Post-approval publication authorization check: `python3 tools/verify_play_release.py --publish --evidence <play-evidence.json> --apk <real-signed-test.apk> --aab <real-upload-signed.aab>`. This additionally requires an actual approved Play Console submission referenced in the evidence. A green runner result is NOT proof the publisher action ran: read back Play Console and actual store availability independently.
4. Actual release acceptance: verify Play track submission, review decision, managed publishing decision, rollout, staged installs, downloaded APK signers, device upgrades, crash/ANR and rollback/halt authority. Record exact build hashes, commit SHA, versionCode, proof dates and reviewer.

**Source of policy truth:** [38-control shared checklist](APK_RELEASE_COMPLIANCE_GATE.md) and its [master Google Doc](https://docs.google.com/document/d/13ITYsIxm-LV6xGN3FLlMfDQqOBCZwC8LnMvyBiqm0Ic/edit). Play requirements are rechecked against current official policy and live Play Console state before every public submission.

## Application-specific readiness assessment

Current `android/build.gradle` uses targetSdk 35 and the existing local PKCS12 release signer. A separate Play upload identity and compliant AAB flow are not yet configured. `gradle :core:smoke :desktop:classes :android:assembleDebug` forms the required source preflight. Blockers: current Play target API check, Play signer and AAB work, Play Console registration/review, real Android handset acceptance, save/upgrade proof. DO NOT invoke `tools/bootstrap_secrets.py` as part of this branch scaffolding; it is reserved for an explicit signing handoff.

## Enforcement and evidence limitations

The release runner rejects failed unit/functional tests and incomplete/mismatched local artifacts when it is executed. GitHub branch protection / required-status rules, external Play Console authorization, secrets enrollment and physical-device installation are **not established by creating this branch or by a documentation commit**. Until those are actually configured and verified, the branch is *staged*, not live-release ready. No signed APK/AAB, local tests, Console submission or publication has been performed as part of this branch creation.
