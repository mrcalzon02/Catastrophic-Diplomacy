# GitHub updater — all published releases, no channel restriction

**Canonical repository:** https://github.com/mrcalzon02/Catastrophic-Diplomacy

The updater does **not** use GitHub's `/releases/latest` endpoint, which would
skip prereleases. It reads the public `/releases` list and accepts **every
published release** (stable, beta, RC, and other experimental tags). Unpublished
drafts are not downloadable and cannot be offered.

On Android and Fire OS sideloads, the existing update menu and automatic
startup scan query all published GitHub releases, including prereleases.
The APK must have the same application ID, compatible Android signing identity,
and a greater `versionCode`: these restrictions are required by Android's
package installer, **not** a stable-channel or save-compatibility preference.
The update uses Android's standard installer after download and confirmation.

On Windows and Linux, `UPDATES` in the game and the startup check both use
`DesktopReleaseUpdater`. The build embeds a release tag generated from
Android's `versionName` to avoid comparing against the unrelated Gradle
`0.1.0-SNAPSHOT` project version. The updater discovers the currently installed
release's `published_at` date via `/releases/tags/<tag>`, then scans published
releases and selects the **newest more recently published** release containing
the correct platform package. Release *channel* and save format do not affect
this selection:

* Windows: `Catastrophic-Diplomacy-Windows-x64.msi`
* Linux: `Catastrophic-Diplomacy-Linux-x64.zip`

A candidate must be served as an HTTPS release asset by the correct
repository. Download size is checked; GitHub's SHA-256 digest is checked
where supplied. Files are downloaded only after the user approves, into
`~/Downloads/Catastrophic-Diplomacy/`; Windows offers to launch the MSI,
while Linux offers to open the downloaded ZIP folder. Neither automatically
deletes old installations or touches campaign saves.

**Important release boundary:** adding this code to `main` does not alter
binaries already published under `cd-v0.1.5-rc.1`. Windows/Linux will gain
these updater controls in the **next packaged GitHub release** containing this
commit, and the platform packages must follow the asset names above.

Tests: `:core:desktopUpdaterPolicySmoke`, `:core:updaterPolicySmoke`,
`:desktop:classes`. Native desktop update check and installation require
network/runtime smoke on Windows and Linux; pure Java tests cannot establish
live download success.
