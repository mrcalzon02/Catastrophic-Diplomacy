# Catastrophic Diplomacy — Android sideload updater (Stage I)

## Channel boundary

The `main` channel uses GitHub-signed-infrastructure HTTPS releases and **Android's system installer**, not silent or root installation. The application checks releases at startup (offline failures are quiet). Users can also check manually with `CHECK UPDATE` in the battlefield HUD or keyboard `U`. Downloads and installation remain opt-in. Android 8+ can require one-time **Allow from this source** authorization. Apps originally installed through Play (`com.android.vending`) must update through Play Store instead; this downloader does not handle Play builds. Release installation preserves app-private fleet saves only when Android recognizes the same package ID and compatible signing certificate. Downgrade/rollback is **not** automated; a safe rollback release must use a higher versionCode and compatible save schema.

## Required GitHub release contents

**Any published GitHub release**, including prereleases, nightly builds, test builds and stable releases, with an Android APK. No `cd-v` tag pattern is required. GitHub's unauthenticated public API cannot show unpublished drafts.

- Any safe `.apk` asset filename — the actual package must be `games.catastrophicdiplomacy`, with the same signing certificate and a strictly greater Android `versionCode`. The `versionName` can be unchanged or include beta/rc/build suffixes.
- **Optional** `release-manifest.json` — when present and accurate, allows a prerelease's actual code and checksum to be known before download. If missing or stale, the APK remains selectable for verification. Illustrative example (not an actual release):

```json
{
  "schema": 1,
  "version": "0.1.2",
  "versionCode": 3,
  "applicationId": "games.catastrophicdiplomacy",
  "minDataSchema": 1,
  "apk": {
    "name": "Catastrophic-Diplomacy-0.1.2-Android.apk",
    "bytes": 12345678,
    "sha256": "REPLACE_WITH_LOWERCASE_64_HEX_SHA256_FROM_SIGNED_APK"
  }
}
```

A manifest, when supplied, must describe the **already signed** APK. The updater pages through all visible published releases (up to 2,000 entries; exceeding the scan limit is an explicit error, never a false 'no update'). It selects the most recently **published** release that contains a valid APK asset, including prerelease and nonstandard tags. Following the established ReverieVR interaction, manual checks show one **Update / Later / View release** dialog, not a long list of every historic APK. Only the selected release's optional manifest is downloaded during discovery. If a valid manifest reports a build code not higher than the installed code, the result is **Already up to date**. When metadata is absent or inconsistent, the actual APK code and signer are inspected after an approved download, *before* requesting installation. Source version `0.1.1`/code 2 is not a published update; the next installable APK must have a higher versionCode than the installed copy.

## Network and security guarantees

- Startup and manual HTTPS checks against the same repository's paginated public release list, no stable-only or exact artifact naming gate. Asset URLs must belong to that release and redirects remain confined to the allowlisted HTTPS GitHub domains.
- **Only the Android versionCode determines whether a build is newer**. No prerelease filtering and no mandatory versionName increment. Exact package ID and signing certificate continuity are required. Expected download size is enforced (max 400 MiB); provided GitHub/manifest SHA256 is checked where available. A checksum-less APK must still pass Android's signed-package checks and system installation validation.
- APK is downloaded only after first user approval into private `cache/updater/candidate.apk.part`; on mismatch the partial file is deleted. Android archive metadata is checked for package name, version and exact signer equality with installed app **before** staging/installer offer. Verified files go to `cache/updater/verified-update.apk`; FileProvider grants the system installer read access without broad storage permissions.
- A second user prompt launches Android's ordinary installer, which independently applies platform signing/upgrade restrictions. Neither the app nor update process clears app data, edits save files, bypasses installer consent, or asks for root access.
- Release metadata and hash have **transport/repository integrity through HTTPS**, not an independent cryptographic publisher signature. APK signing continuity is additionally checked. The manifest is not Ed25519-signed; do not claim it is. Preserve the signing identity outside the repository.

## Tests and release readiness

Run pure Java: `javac --release 17 -d out core/src/main/java/diplomacy/update/UpdatePolicy.java core/src/test/java/diplomacy/update/UpdatePolicyTest.java && java -cp out diplomacy.update.UpdatePolicyTest`. Gradle task: `gradle :core:updaterPolicySmoke`. This verifies reject/accept policy, **not** Android networking or installation.

A real end-to-end acceptance requires **two distinct release-signed APKs from the same certificate**, a published GitHub release with an APK (manifest optional), and a device/emulator that has the older build installed:

1. Install 0.1.1/code 2, create and save custom fleets. Publish 0.1.2/code 3 with a real matching manifest.
2. Tap CHECK UPDATE; choose the published release; approve download; verify its available SHA, size, APK identity, versionCode and signer; approve Android's installer. Check 0.1.2/code 3 installed and fleet saves survive reopening.
3. Reject wrong package ID, old or same versionCode, invalid manifest schema, tampered APK, wrong digest, expired/invalid TLS, oversized download and forged redirect.
4. Exercise offline startup, denied install-source permission, user cancellation, low storage, Wi-Fi interruption/retry, missing release assets, malformed JSON, source mismatch, and signing-certificate mismatch.
5. For Play distribution, remove `REQUEST_INSTALL_PACKAGES` and use Play update delivery; do not reuse this sideload release flow for Play compliance.

**Not completed:** No published GitHub release, no built/signed updater APK, and no verified Android-device upgrade. No GitHub Actions workflows or signing-secret mutations were introduced.