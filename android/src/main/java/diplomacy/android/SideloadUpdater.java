package diplomacy.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import diplomacy.update.UpdatePolicy;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** User-initiated, HTTPS-only GitHub release installer for sideloaded Android builds. */
final class SideloadUpdater {
    private static final String RELEASE_API = "https://api.github.com/repos/" +
            UpdatePolicy.REPOSITORY + "/releases?per_page=100&page=";
    private static final int PER_PAGE = 100;
    private static final int MAX_PAGES = 20; // Never silently say "no update" if history is larger.
    private static final int MAX_RELEASE_JSON = 2 * 1024 * 1024;
    private static final String MANIFEST_NAME = "release-manifest.json";
    private static final int MAX_MANIFEST_JSON = 16 * 1024;
    private final Activity activity;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "CD-Sideload-Updater");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean closed;
    private File pendingApk;

    SideloadUpdater(Activity activity) { this.activity = activity; }

    void checkForUpdates() { checkForUpdates(true); }
    /** At startup, check quietly if offline; manual checks always provide feedback. */
    void checkForUpdates(boolean manual) {
        if (closed || !busy.compareAndSet(false, true)) return;
        activity.runOnUiThread(() -> {
            String managedStore=managedStore();
            if (managedStore != null) {
                busy.set(false);
                // Store-managed installations must not offer a competing GitHub
                // APK signer/update channel, particularly on Amazon Fire tablets.
                // A background check also must not display a startup modal.
                if (manual) {
                    if ("amazon".equals(managedStore) || "unknown".equals(managedStore)) {
                        new AlertDialog.Builder(activity).setTitle("Updates managed by Amazon Appstore")
                                .setMessage("This Fire tablet installation came from the Amazon Appstore. Update it through the Amazon Appstore, not the GitHub sideload installer.")
                                .setPositiveButton("Close", null).show();
                    } else {
                        new AlertDialog.Builder(activity).setTitle("Updates managed by Google Play")
                                .setMessage("This installation was obtained through Google Play. Update it using Play Store, not the GitHub sideload installer.")
                                .setNegativeButton("Close", null)
                                .setPositiveButton("Open Play Store", (dialog, which) -> {
                                    Intent intent = new Intent(Intent.ACTION_VIEW,
                                            Uri.parse("market://details?id=" + activity.getPackageName()));
                                    try { activity.startActivity(intent); }
                                    catch (RuntimeException ignored) { show("Could not open Play Store"); }
                                }).show();
                    }
                }
                return;
            }
            if (manual) Toast.makeText(activity, "Checking all published GitHub releases…", Toast.LENGTH_SHORT).show();
            io.execute(() -> findUpdate(manual));
        });
    }

    private String managedStore() {
        String installer;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                android.content.pm.InstallSourceInfo info =
                        activity.getPackageManager().getInstallSourceInfo(activity.getPackageName());
                installer=info.getInstallingPackageName();
            } else {
                installer=activity.getPackageManager().getInstallerPackageName(activity.getPackageName());
            }
        } catch (PackageManager.NameNotFoundException missing) {
            // Fail closed rather than offering an unverified cross-store upgrade.
            return "unknown";
        }
        if ("com.android.vending".equals(installer))return "google";
        if ("com.amazon.venezia".equals(installer))return "amazon";
        if (installer == null) return null;
        return null; // Sideloaded APKs retain the existing signed GitHub updater.
    }


    private void findUpdate(boolean manual) {
        try {
            PackageInfo installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            long installedCode = code(installed);
            JSONObject newest = null;
            JSONObject newestApk = null;
            String newestPublished = "";
            int rejected = 0;
            boolean complete = false;

            // Same user flow as ReverieVR: offer ONE newest published APK.
            // GitHub's /releases/latest excludes prereleases, so inspect the full
            // published feed and compare published_at rather than relying on
            // GitHub's creation-date ordering or on a stable-only endpoint.
            // Inspect release metadata here; download only the winner's manifest.
            for (int page = 1; page <= MAX_PAGES; page++) {
                JSONArray releases = new JSONArray(new String(
                        downloadBytes(RELEASE_API + page, MAX_RELEASE_JSON),
                        java.nio.charset.StandardCharsets.UTF_8));
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.optJSONObject(i);
                    if (release == null || !UpdatePolicy.eligibleRelease(
                            release.optBoolean("draft"), release.optBoolean("prerelease"))) continue;
                    String publishedAt = release.optString("published_at", "");
                    String tag = release.optString("tag_name", "");
                    JSONArray assets = release.optJSONArray("assets");
                    if (publishedAt.isEmpty() || assets == null) continue;

                    JSONObject selectedApk = null;
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.optJSONObject(j);
                        if (asset == null) continue;
                        String name = asset.optString("name", "");
                        if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                        try {
                            long bytes = asset.getLong("size");
                            String apkUrl = asset.getString("browser_download_url");
                            UpdatePolicy.requireDownloadUrl(apkUrl, tag, name);
                            String digest = asset.optString("digest", "");
                            String sha = digest.startsWith("sha256:") ? digest.substring(7) : "";
                            UpdatePolicy.provisional(tag, name, bytes, sha);
                            // Prefer the ordinary Android package when a release
                            // also contains a distinct Fire OS package.
                            if (selectedApk == null || name.equalsIgnoreCase("Catastrophic-Diplomacy-Android.apk"))
                                selectedApk = asset;
                        } catch (Exception invalid) {
                            rejected++;
                        }
                    }
                    if (selectedApk != null && (newest == null ||
                            publishedAt.compareTo(newestPublished) > 0)) {
                        newest = release;
                        newestApk = selectedApk;
                        newestPublished = publishedAt;
                    }
                }
                if (releases.length() < PER_PAGE) { complete = true; break; }
            }
            if (!complete) throw new IOException("Release history exceeds supported scan; cannot report no updates");

            Release selected = null;
            boolean alreadyCurrent = false;
            if (newest != null) {
                String tag = newest.getString("tag_name");
                String name = newestApk.getString("name");
                long bytes = newestApk.getLong("size");
                String apkUrl = newestApk.getString("browser_download_url");
                String githubDigest = newestApk.optString("digest", "");
                String sha = githubDigest.startsWith("sha256:") ? githubDigest.substring(7) : "";
                UpdatePolicy.Candidate candidate = UpdatePolicy.provisional(tag, name, bytes, sha);

                JSONArray assets = newest.getJSONArray("assets");
                JSONObject manifestAsset;
                try { manifestAsset = findAsset(assets, MANIFEST_NAME); }
                catch (JSONException malformed) { manifestAsset = null; rejected++; }
                if (manifestAsset != null && manifestAsset.optLong("size", 0) > 0 &&
                        manifestAsset.optLong("size") <= MAX_MANIFEST_JSON) {
                    try {
                        String manifestUrl = manifestAsset.getString("browser_download_url");
                        UpdatePolicy.requireDownloadUrl(manifestUrl, tag, MANIFEST_NAME);
                        JSONObject manifest = new JSONObject(new String(
                                downloadBytes(manifestUrl, MAX_MANIFEST_JSON),
                                java.nio.charset.StandardCharsets.UTF_8));
                        JSONObject item = manifest.optJSONObject("apk");
                        if (manifest.optInt("schema") == 1 && item != null &&
                                name.equals(item.optString("name"))) {
                            UpdatePolicy.Candidate described = new UpdatePolicy.Candidate(tag,
                                    manifest.getString("version"), manifest.getLong("versionCode"),
                                    manifest.getString("applicationId"), manifest.getInt("minDataSchema"),
                                    name, item.getLong("bytes"), item.getString("sha256"));
                            if (described.bytes() != bytes ||
                                    !UpdatePolicy.PACKAGE_ID.equals(described.applicationId()) ||
                                    !described.version().matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,99}") ||
                                    described.versionCode() < 1 ||
                                    (!sha.isEmpty() && !sha.equalsIgnoreCase(described.sha256())))
                                throw new IOException("Release manifest disagrees with the published APK");
                            UpdatePolicy.requireAsset(tag, name, described.bytes(), described.sha256());
                            if (!UpdatePolicy.newerAndroidBuild(described.versionCode(), installedCode)) {
                                alreadyCurrent = true;
                            } else {
                                UpdatePolicy.requireCandidate(described, installed.versionName, installedCode);
                                candidate = described;
                            }
                        }
                    } catch (Exception badManifest) {
                        // Missing/stale metadata cannot hide a real APK. The
                        // downloaded archive is still checked before installation.
                        rejected++;
                        android.util.Log.w("CD-Updater", "Release manifest unavailable or inconsistent", badManifest);
                    }
                }
                selected = new Release(candidate, apkUrl, newest.optBoolean("prerelease"));
            }

            Release latest = selected;
            boolean current = alreadyCurrent;
            int skipped = rejected;
            activity.runOnUiThread(() -> {
                if (closed || activity.isFinishing()) return;
                if (latest == null) {
                    if (manual) dialog("No Android releases",
                            "No downloadable APK exists in the published GitHub releases." +
                            (skipped > 0 ? " Rejected malformed assets: " + skipped : ""), null);
                    return;
                }
                if (current) {
                    if (manual) dialog("Already up to date",
                            "Installed: " + installed.versionName + " (build " + installedCode +
                            "). Latest published: " + latest.candidate().tag() + ".", null);
                    return;
                }
                // A background check never steals focus from the game.
                if (manual) showAvailableUpdate(latest, installed.versionName, installedCode);
                else android.util.Log.i("CD-Updater", "Newest published APK: " + latest.candidate().tag());
            });
        } catch (Exception failure) {
            if (manual) dialog("Update check failed", safeError(failure), null);
            else android.util.Log.w("CD-Updater", "Automatic release check unavailable", failure);
        } finally {
            busy.set(false);
        }
    }

    /** Port of ReverieVR's working single-update decision dialog. */
    private void showAvailableUpdate(Release release, String installedVersion, long installedCode) {
        UpdatePolicy.Candidate c = release.candidate();
        String newVersion = c.versionCode() > 0 ? c.version() + " (build " + c.versionCode() + ")"
                : c.tag() + " (APK build number verified after download)";
        new AlertDialog.Builder(activity)
                .setTitle("Update available")
                .setMessage("Installed: " + installedVersion + " (build " + installedCode + ")" +
                        "\nAvailable: " + newVersion +
                        "\n\n" + (release.prerelease() ? "Published test/prerelease. " : "") +
                        "Download " + c.fileName() + " (" + (c.bytes() / 1048576) +
                        " MiB)? Android checks the package ID, build number and signer before installing. Saves are preserved on a compatible upgrade.")
                .setPositiveButton("Update", (d, w) -> downloadUpdate(release))
                .setNegativeButton("Later", null)
                .setNeutralButton("View release", (d, w) -> {
                    Uri page = Uri.parse("https://github.com/" + UpdatePolicy.REPOSITORY +
                            "/releases/tag/" + Uri.encode(c.tag()));
                    try { activity.startActivity(new Intent(Intent.ACTION_VIEW, page)); }
                    catch (RuntimeException failure) { show("Cannot open release page"); }
                })
                .show();
    }

    /** Debug-only emulator acceptance: exercise the production dialog without a live download. */
    void showVerificationDialog() {
        UpdatePolicy.Candidate sample = new UpdatePolicy.Candidate("cd-v0.1.5-dev.9000",
                "0.1.5-dev.9000", 10000, UpdatePolicy.PACKAGE_ID, 1,
                "Catastrophic-Diplomacy-Android.apk", 1048576, "");
        showAvailableUpdate(new Release(sample, "https://github.com/" + UpdatePolicy.REPOSITORY +
                "/releases/download/cd-v0.1.5-dev.9000/Catastrophic-Diplomacy-Android.apk", true),
                "0.1.5-dev.8999", 9999);
    }

    private void downloadUpdate(Release release) {
        if (closed || !busy.compareAndSet(false, true)) return;
        show("Downloading verified update…");
        io.execute(() -> {
            File part = null;
            try {
                File dir = new File(activity.getCacheDir(), "updater");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create private updater cache");
                part = new File(dir, "candidate.apk.part");
                File target = new File(dir, "verified-update.apk");
                Files.deleteIfExists(part.toPath());
                Files.deleteIfExists(target.toPath());
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                long read = 0;
                HttpURLConnection conn = connect(release.apkUrl);
                try (InputStream input = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream output = new FileOutputStream(part)) {
                    byte[] buffer = new byte[32768];
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Download interrupted");
                        read += length;
                        if (read > release.candidate.bytes() || read > UpdatePolicy.MAX_APK_BYTES)
                            throw new IOException("APK exceeded verified size");
                        sha.update(buffer, 0, length);
                        output.write(buffer, 0, length);
                    }
                    output.getFD().sync();
                } finally { conn.disconnect(); }
                if (read != release.candidate.bytes() ||
                        (!release.candidate.sha256().isEmpty() &&
                         !hex(sha.digest()).equalsIgnoreCase(release.candidate.sha256())))
                    throw new IOException("Downloaded APK failed checksum or length verification");
                validateArchive(part, release.candidate);
                if (!part.renameTo(target)) throw new IOException("Unable to stage verified APK");
                pendingApk = target;
                activity.runOnUiThread(() -> {
                    if (!closed && !activity.isFinishing())
                        new AlertDialog.Builder(activity).setTitle("Update verified")
                                .setMessage("This APK has passed byte-length, package ID, newer Android versionCode and signing-certificate checks. Any supplied SHA256 was also verified. Open Android's system installer?")
                                .setNegativeButton("Later", null)
                                .setPositiveButton("Install", (d, w) -> openInstaller(target)).show();
                });
            } catch (Exception failure) {
                if (part != null) part.delete();
                dialog("Update download rejected", safeError(failure), null);
            } finally { busy.set(false); }
        });
    }

    private void validateArchive(File apk, UpdatePolicy.Candidate candidate) throws Exception {
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), flags);
        if (archive == null || !candidate.applicationId().equals(archive.packageName) ||
                !activity.getPackageName().equals(archive.packageName))
            throw new IOException("APK has an unexpected Android application ID");
        if (!UpdatePolicy.newerAndroidBuild(code(archive), code(installed)))
            throw new IOException("APK is not a newer Android versionCode");
        if (candidate.versionCode() > 0 &&
                (code(archive) != candidate.versionCode() || !candidate.version().equals(archive.versionName)))
            throw new IOException("APK version metadata disagrees with its release manifest");
        Signature[] from = signers(installed), to = signers(archive);
        if (from == null || to == null || from.length == 0 || !Arrays.equals(from, to))
            throw new IOException("APK signer differs from the installed app; refusing unsafe upgrade");
    }

    private static Signature[] signers(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        return info.signatures;
    }
    private static long code(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private void openInstaller(File verifiedApk) {
        if (closed || !verifiedApk.isFile()) return;
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity).setTitle("Allow installation from this source")
                    .setMessage("Android must explicitly permit Catastrophic Diplomacy to open the system installer. Return and press UPDATE again after enabling this permission.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open settings", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(intent);
                    }).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".updates", verifiedApk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { activity.startActivity(intent); }
        catch (RuntimeException failure) { dialog("Installer unavailable", safeError(failure), null); }
    }

    private static JSONObject findAsset(JSONArray assets, String name) throws JSONException {
        JSONObject found = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (name.equals(asset.optString("name"))) {
                if (found != null) throw new JSONException("Duplicate asset " + name);
                found = asset;
            }
        }
        return found;
    }
    private static HttpURLConnection connect(String url) throws IOException {
        URI current = UpdatePolicy.allowedUrl(url);
        for (int redirects = 0; redirects <= 5; redirects++) {
            HttpURLConnection conn = (HttpURLConnection) current.toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("User-Agent", "CatastrophicDiplomacy-AndroidUpdater/1");
            conn.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
            int response = conn.getResponseCode();
            if (response == 301 || response == 302 || response == 303 || response == 307 || response == 308) {
                String destination = conn.getHeaderField("Location");
                conn.disconnect();
                if (destination == null) throw new IOException("Missing download redirect destination");
                current = UpdatePolicy.allowedUrl(current.resolve(destination).toString());
                continue;
            }
            if (response != 200) {
                conn.disconnect();
                throw new IOException("GitHub update endpoint returned HTTP " + response);
            }
            return conn;
        }
        throw new IOException("Too many GitHub download redirects");
    }
    private static byte[] downloadBytes(String url, int limit) throws IOException {
        HttpURLConnection connection = connect(url);
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] data = new byte[8192];
            int read;
            while ((read = input.read(data)) != -1) {
                if (output.size() + read > limit) throw new IOException("Update metadata exceeded size limit");
                output.write(data, 0, read);
            }
            return output.toByteArray();
        } finally { connection.disconnect(); }
    }
    private static String hex(byte[] value) {
        StringBuilder s = new StringBuilder(value.length * 2);
        for (byte x : value) s.append(String.format(Locale.ROOT, "%02x", x & 255));
        return s.toString();
    }
    private static String safeError(Exception failure) {
        return failure instanceof IOException || failure instanceof IllegalArgumentException || failure instanceof JSONException
                ? String.valueOf(failure.getMessage()) : "An update could not be validated; no installation was attempted.";
    }
    private void show(String message) { activity.runOnUiThread(() -> {
        if (!closed && !activity.isFinishing()) Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }); }
    private void dialog(String title, String message, Runnable after) { activity.runOnUiThread(() -> {
        if (!closed && !activity.isFinishing()) new AlertDialog.Builder(activity).setTitle(title)
                .setMessage(message).setPositiveButton("OK", (d, w) -> { if (after != null) after.run(); }).show();
    }); }
    void close() { closed = true; io.shutdownNow(); }
    private record Release(UpdatePolicy.Candidate candidate, String apkUrl, boolean prerelease) { }
}