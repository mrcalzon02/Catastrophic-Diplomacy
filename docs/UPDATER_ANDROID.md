# Catastrophic Diplomacy — Android sideload updater (Stage I)

## Channel boundary

The `main` channel uses GitHub-signed-infrastructure HTTPS releases and **Android's system installer**, not silent or root installation. Updates are always user-initiated through `UPDATE` (lower-right battlefield overlay) or keyboard `U`. Android 8+ can require one-time **Allow from this source** authorization. Apps originally installed through Play (`com.android.vending`) must update through Play Store instead; this downloader does not handle Play builds. Release installation preserves app-private fleet saves only when Android recognizes the same package ID and compatible signing certificate. Downgrade/rollback is **not** automated; a safe rollback release must use a higher versionCode and compatible save schema.

## Required GitHub release contents

A published non-prerelease/non-draft tag `cd-vMAJOR.MINOR.PATCH`, with exactly named assets:

- `Catastrophic-Diplomacy-MAJOR.MINOR.PATCH-Android.apk` — release-signed *sideload* APK, package ID `games.catastrophicdiplomacy`, strictly incrementing Android `versionCode`, consistent `versionName` and preserved signing certificate.
- `release-manifest.json` — small JSON document with the following schema (illustrative placeholders, **not an actual release**):

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

The manifest must be produced from the **already signed** APK, not from an unsigned intermediate. An optional `SHA256SUMS.txt` can accompany releases. The installer finds up to 20 newest GitHub releases, ignores unrelated tags, drafts and prereleases, requires a matching manifest/APK, and chooses the highest eligible version. Use actual artifact size/digest, not the example. Starting updater-capable source version is `0.1.1` with Android `versionCode 2` (source configuration only; it is not a published APK). The next release must have a larger `versionCode`.

## Network and security guarantees

- Opt-in HTTPS connection to `api.github.com/repos/mrcalzon02/Catastrophic-Diplomacy/releases`, exact asset names and release-scoped links. Redirects are capped and every URL remains HTTPS on GitHub's restricted release domains.
- Strict three-component stable version parsing, monotonically greater **both** versionName and versionCode, exact package ID, save schema gate, asset byte limit (400 MiB), exact byte count, SHA256 of streamed APK, cross-check with GitHub asset digest if available.
- APK is downloaded only after first user approval into private `cache/updater/candidate.apk.part`; on mismatch the partial file is deleted. Android archive metadata is checked for package name, version and exact signer equality with installed app **before** staging/installer offer. Verified files go to `cache/updater/verified-update.apk`; FileProvider grants the system installer read access without broad storage permissions.
- A second user prompt launches Android's ordinary installer, which independently applies platform signing/upgrade restrictions. Neither the app nor update process clears app data, edits save files, bypasses installer consent, or asks for root access.
- Release metadata and hash have **transport/repository integrity through HTTPS**, not an independent cryptographic publisher signature. APK signing continuity is additionally checked. The manifest is not Ed25519-signed; do not claim it is. Preserve the signing identity outside the repository.

## Tests and release readiness

Run pure Java: `javac --release 17 -d out core/src/main/java/diplomacy/update/UpdatePolicy.java core/src/test/java/diplomacy/update/UpdatePolicyTest.java && java -cp out diplomacy.update.UpdatePolicyTest`. Gradle task: `gradle :core:updaterPolicySmoke`. This verifies reject/accept policy, **not** Android networking or installation.

A real end-to-end acceptance requires **two distinct release-signed APKs from the same certificate**, a published GitHub release with a valid manifest, and a device/emulator that has the older build installed:

1. Install 0.1.1/code 2, create and save custom fleets. Publish 0.1.2/code 3 with a real matching manifest.
2. Tap UPDATE; approve download; verify the SHA/size, APK identity and signer; approve Android's installer. Check 0.1.2/code 3 installed and fleet saves survive reopening.
3. Reject wrong package ID, old or same versionCode, invalid manifest schema, tampered APK, wrong digest, expired/invalid TLS, oversized download and forged redirect.
4. Exercise offline startup, denied install-source permission, user cancellation, low storage, Wi-Fi interruption/retry, missing release assets, malformed JSON, source mismatch, and signing-certificate mismatch.
5. For Play distribution, remove `REQUEST_INSTALL_PACKAGES` and use Play update delivery; do not reuse this sideload release flow for Play compliance.

**Not completed:** No published GitHub release, no built/signed updater APK, and no verified Android-device upgrade. No GitHub Actions workflows or signing-secret mutations were introduced.