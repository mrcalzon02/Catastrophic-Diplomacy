# Release and update contract

The authoritative repository is `mrcalzon02/Catastrophic-Diplomacy` and the release namespace is `cd-vMAJOR.MINOR.PATCH`. Desktop and Android are built from exactly the same simulation commit. Each release must include a declared data-schema version, semantic application version and platform-specific asset names. Release archives and APKs are staged locally and verified before publication.

**Build artifacts:** `Catastrophic-Diplomacy-VERSION-Windows-x64.zip` containing a JRE-bundled runtime, `Catastrophic-Diplomacy-VERSION-Android.apk` signed with the per-project PKCS12 keystore, and `SHA256SUMS.txt`. Windows EXE/MSI via `jpackage` when tested on Windows; unsigned APKs are never a release. A release manifest must contain version, platform, minimum supported schema, exact filename, byte length and SHA-256. Stable releases exclude drafts/prereleases and are tagged only within the `cd-v` namespace.

**Application updater (planned, not implemented):** Optional HTTPS check against the tagged release list; strict allowlisted GitHub domain redirects; semver comparison and platform matching; bounded download into application data, SHA256 verification, explicit install consent, preservation of user saves and rollback. Android updates require the same signing certificate and compatible package identity. Android installed by Google Play may require a separate Play upload key/app-signing process. No actual automatic release updater or installers have been built yet.

**Secrets:** `.local-secrets/android-release.p12` and `.local-secrets/release.properties` never enter the repository. `tools/bootstrap_secrets.py` securely generates them and uploads `CD_ANDROID_KEYSTORE_BASE64`, `CD_ANDROID_KEYSTORE_PASSWORD`, `CD_ANDROID_KEY_ALIAS`, and `CD_ANDROID_KEY_PASSWORD` as GitHub Actions secrets using `gh secret set`. GitHub Actions CI workflows are not enabled by default. Secrets are prepared for future explicit automation. GitHub repository API access is delegated through the authenticated `gh` session; never commit PATs.

**Gates:** local compile, deterministic headless simulation, instrumented desktop rendering, Android emulator and actual Android hardware, release signing, checksum verification, installer upgrade/rollback and privacy/security audit. Do not claim a release before its artifacts, tags and installation test are independently verified.

## Android release policy acceptance

Android release candidates, whether locally side-loaded or intended for future Google Play distribution, are subject to the mandatory [38-control APK release gate](APK_RELEASE_COMPLIANCE_GATE.md) and the source/test + per-artifact evidence checker at `tools/verify_apk_release_gate.py`. The future `google-play` branch adds a full functional-test preflight and **must not** be used to publish until Play upload signing, API compliance, Play Console review, asset compliance and device upgrades are verified. `main` remains the authoritative sideload-development branch; no CI/Actions or release workflow is activated by this policy update.

## 0.1.5 RC1 four-platform prerelease
A full-platform release is assembled only from successful, exact-commit native
Windows/Linux, Android and real emulator 200v200 runs. Windows/Linux desktop ZIP
app images, retained-signer Android and byte-identical Fire OS APK, checksums and
test provenance share the one versioned release. Android versionCode 6 is above
the published RC1 code 5; publisher never overwrites occupied tags. Windows
Authenticode, Linux signing, physical device performance and Android upgrade
testing are not yet certified.
