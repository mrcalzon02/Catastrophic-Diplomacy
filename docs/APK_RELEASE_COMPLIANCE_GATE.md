# Mandatory Android APK Release Compliance Checklist

**Applies to:** every APK build reviewed for internal device testing, signed GitHub/sideload delivery, and Google Play submission for this product. **Gate state:** NOT CERTIFIED until a build-specific evidence record has passed the verifier and the real-device checks below have been completed.

**Canonical master (38 controls; check the latest version):** https://docs.google.com/document/d/13ITYsIxm-LV6xGN3FLlMfDQqOBCZwC8LnMvyBiqm0Ic/edit

This repository-local checklist is a release-test entry point, not permission to assert that a build complies with Google Play. Each release must be checked against current Google Play policies and the actual Play Console account, using the master as a source index. Where distribution is **sideload**, Play-only items may be marked NOT_APPLICABLE with a specific reason. Where distribution is **play**, Google Play requirements are in force. An internal or GitHub debug APK is not evidence of a Play-approved artifact.

## Major checklist (all controls have an explicit disposition)

### A — App identity, distribution and target API [P0]
- [ ] GP-ID-01: package ID, track/channel and Play Console state inventoried.
- [ ] GP-ID-02: app-registration status and any applicable registration deadline verified.
- [ ] GP-ID-03: signer, versionCode and same-identity upgrade continuity verified.
- [ ] GP-ID-04: target SDK/API policy and any granted extension checked for the build date and supported form factor.
- [ ] GP-ID-05: platform and Android XR/VR eligibility verified, not assumed.

### B — User data, privacy and SDK disclosures [P0]
- [ ] GP-DS-01: Data safety applicability and complete per-package declaration established.
- [ ] GP-DS-02: actual SDK, network and off-device data inventory captured.
- [ ] GP-DS-03: data-type, purpose, sharing, required/optional and vendor classification reconciled.
- [ ] GP-DS-04: all distributed versions, markets and audiences reflected.
- [ ] GP-DS-05: reachable, accurate privacy-policy URL verified where required.
- [ ] GP-DS-06: encryption-in-transit and data deletion disclosures independently tested.
- [ ] GP-DS-07: account/data deletion user flow tested, if relevant.
- [ ] GP-DS-08: Play Console Data safety review/storefront preview retained, if applicable.
- [ ] GP-DS-09: versioned declarations and safe CSV/diff audit retained.
- [ ] GP-DS-10: SDK, permissions, analytics, cloud and update data-flow changes retriggered review.

### C — APK updater and native-module distribution [P0]
- [ ] GP-MOD-01: each downloaded item classified as data, asset, APK, executable/native library or interpreted code.
- [ ] GP-MOD-02: Play builds do not bypass Play update/code-download restrictions; GitHub/sideload paths reviewed separately.
- [ ] GP-MOD-03: assets checked for provenance, integrity, version binding, rollback and failed downloads.
- [ ] GP-MOD-04: sideload installer consent, signing continuity and recovery tested, where relevant.

### D — Play Integrity and SDK-provider conditional controls [P1]
- [ ] GP-PI-01: risk-based Play Integrity implementation decision documented.
- [ ] GP-PI-02: client and server validation proved if Play Integrity is integrated.
- [ ] GP-PI-03: SDK-provider-specific Play SDK Console obligations reviewed only if publishing an SDK.
- [ ] GP-PI-04: quotas, limitations and failure modes accounted for if Play Integrity is integrated.
- [ ] GP-PI-05: false positives, offline and non-Play-device behavior tested if integrated.

### E — Policy deadlines [P0]
- [ ] GP-POL-01: current deadlines and Play Console Policy status reviewed near the submission date.
- [ ] GP-POL-02: contacts, location, call logs, geofencing and child-directed-content applicability evaluated.

### F — Review, approval and rollout [P0]
- [ ] GP-PUB-01: signing, listing, policies and exact submission artifact prepared.
- [ ] GP-PUB-02: review submission and any saved changes explicitly checked.
- [ ] GP-PUB-03: managed publishing eligibility/configuration verified if used.
- [ ] GP-PUB-04: review lead time and launch schedule assessed.
- [ ] GP-PUB-05: approved versus published state independently distinguished.
- [ ] GP-PUB-06: managed-publishing exclusions identified if relevant.
- [ ] GP-PUB-07: installed/downloaded released artifact and staged rollout behavior validated.

### G — Device, evidence and response [P1]
- [ ] GP-OPS-01: commit, signed APK, SHA-256, version, signer and release location match.
- [ ] GP-OPS-02: fresh-install, same-signer upgrade, data persistence, offline and failure paths tested on actual Android hardware.
- [ ] GP-OPS-03: crash/ANR visibility, update failures and rollback/halt ownership documented.
- [ ] GP-OPS-04: relevant SDK/permission/module changes re-trigger all affected gates.
- [ ] GP-OPS-05: signing secrets and credentials remain outside source and public artifacts.

## Mandatory evidence contract

Run `python3 tools/verify_apk_release_gate.py --checklist-only` as a lightweight source/checklist regression. It only verifies coverage and **cannot** certify an APK.

For a signed release candidate, create an untracked **per-artifact** JSON evidence record (example location `release/apk-compliance-evidence.json`), with `application_id`, `release_version`, `git_commit`, `artifact_sha256`, `distribution` (`play`, `sideload` or `internal`), `verified_at`, `verifier`, and `controls`. Each control ID above requires `{ "status": "PASS", "evidence": "specific test report or Play Console reference" }` or `{ "status": "NOT_APPLICABLE", "reason": "why this control cannot apply", "evidence": "manifest/source/console proof" }`. No default PASS, inferred pass, silent omission or unexplained exception.

Run `python3 tools/verify_apk_release_gate.py --evidence release/apk-compliance-evidence.json --apk path/to/signed.apk`. A PASS means the **evidence manifest is complete and matches the APK hash**, NOT that its manual assertions have been independently proven. A release tester must additionally verify the listed evidence, Android manifest, certificate fingerprint, target API, actual installation and Play Console status (if Play). Only then may the release owner mark the release accepted.

**Hard HOLD:** unresolved P0 control, missing evidence, mismatched artifact/signature, Play-update/download-policy concern, untested actual device upgrade, or pending Console review. Never mark committed/pushed/published/live from a local code check.

**Cross-project scope:** this gate covers this repository only. Do not copy signing keys, package identities or pass status between products. Do not modify GitHub Actions or release signing solely to add this checklist.
