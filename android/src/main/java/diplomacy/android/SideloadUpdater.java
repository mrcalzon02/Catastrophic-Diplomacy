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
            if (isPlayInstall()) {
                busy.set(false);
                new AlertDialog.Builder(activity).setTitle("Updates managed by Google Play")
                        .setMessage("This installation was obtained through Google Play. Update it using Play Store, not the GitHub sideload installer.")
                        .setNegativeButton("Close", null)
                        .setPositiveButton("Open Play Store", (dialog, which) -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=" + activity.getPackageName()));
                            try { activity.startActivity(intent); }
                            catch (RuntimeException ignored) { show("Could not open Play Store"); }
                        }).show();
                return;
            }
            if (manual) Toast.makeText(activity, "Checking all published GitHub releases…", Toast.LENGTH_SHORT).show();
            io.execute(() -> findUpdate(manual));
        });
    }

    private boolean isPlayInstall() {
        String installer = activity.getPackageManager().getInstallerPackageName(activity.getPackageName());
        return "com.android.vending".equals(installer);
    }

    private void findUpdate(boolean manual) {
        try {
            PackageInfo installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            long installedCode = code(installed);
            List<Release> available = new ArrayList<>();
            int rejected = 0;
            boolean complete = false;

            // GitHub's /releases/latest endpoint excludes prereleases. Never use it here.
            // Iterate all pages, including prereleases and test-build tags, until exhausted.
            for (int page = 1; page <= MAX_PAGES; page++) {
                JSONArray releases = new JSONArray(new String(
                        downloadBytes(RELEASE_API + page, MAX_RELEASE_JSON),
                        java.nio.charset.StandardCharsets.UTF_8));
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.optJSONObject(i);
                    if (release == null || release.optBoolean("draft")) continue;
                    // Public GitHub does not reveal unpublished drafts; prereleases ARE included.
                    String tag = release.optString("tag_name", "");
                    JSONArray assets = release.optJSONArray("assets");
                    if (assets == null) continue;

                    JSONObject manifest = null;
                    JSONObject manifestAsset;
                    try { manifestAsset = findAsset(assets, MANIFEST_NAME); }
                    catch (JSONException malformed) { manifestAsset = null; rejected++; }
                    if (manifestAsset != null && manifestAsset.optLong("size", 0) > 0
                            && manifestAsset.optLong("size") <= MAX_MANIFEST_JSON) {
                        try {
                            String manifestUrl = manifestAsset.getString("browser_download_url");
                            UpdatePolicy.requireDownloadUrl(manifestUrl, tag, MANIFEST_NAME);
                            manifest = new JSONObject(new String(downloadBytes(manifestUrl, MAX_MANIFEST_JSON),
                                    java.nio.charset.StandardCharsets.UTF_8));
                        } catch (Exception ignored) {
                            // The manifest is optional: the downloaded signed APK remains authoritative.
                            rejected++;
                        }
                    }

                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.optJSONObject(j);
                        if (asset == null) continue;
                        String name = asset.optString("name", "");
                        if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                        try {
                            long bytes = asset.getLong("size");
                            String apkUrl = asset.getString("browser_download_url");
                            UpdatePolicy.requireDownloadUrl(apkUrl, tag, name);
                            String githubDigest = asset.optString("digest", "");
                            if (!githubDigest.isEmpty() && !githubDigest.startsWith("sha256:"))
                                throw new IOException("Unsupported GitHub asset digest");
                            String sha = githubDigest.startsWith("sha256:") ? githubDigest.substring(7) : "";
                            UpdatePolicy.Candidate candidate = UpdatePolicy.provisional(tag, name, bytes, sha);
                            if (manifest != null && manifest.optInt("schema") == 1) {
                                JSONObject item = manifest.optJSONObject("apk");
                                if (item != null && name.equals(item.optString("name"))) {
                                    // Stale manifests cannot hide valid test APKs. Archive
                                    // identity, versionCode and signature are verified after download.
                                    try {
                                        UpdatePolicy.Candidate described = new UpdatePolicy.Candidate(tag,
                                                manifest.getString("version"), manifest.getLong("versionCode"),
                                                manifest.getString("applicationId"), manifest.getInt("minDataSchema"),
                                                name, item.getLong("bytes"), item.getString("sha256"));
                                        UpdatePolicy.requireCandidate(described, installed.versionName, installedCode);
                                        if (described.bytes() != bytes)
                                            throw new IOException("Manifest size differs from GitHub asset");
                                        if (!sha.isEmpty() && !sha.equalsIgnoreCase(described.sha256()))
                                            throw new IOException("Manifest checksum differs from GitHub digest");
                                        candidate = described;
                                    } catch (Exception invalidManifest) {
                                        rejected++; // Keep provisional candidate for APK inspection.
                                    }
                                }
                            }
                            available.add(new Release(candidate, apkUrl, release.optBoolean("prerelease")));
                        } catch (Exception invalid) {
                            // One malformed asset must not hide other published releases.
                            rejected++;
                        }
                    }
                }
                if (releases.length() < PER_PAGE) { complete = true; break; }
            }
            if (!complete) throw new IOException("Release history exceeds supported scan; cannot report no updates");
            int skipped = rejected;
            activity.runOnUiThread(() -> {
                busy.set(false);
                if (closed || activity.isFinishing()) return;
                if (available.isEmpty()) {
                    if (manual) new AlertDialog.Builder(activity).setTitle("No downloadable update")
                            .setMessage("Scanned all published stable and prerelease entries. No eligible Android APK was found."
                                    + (skipped > 0 ? " " + skipped + " malformed assets were skipped." : ""))
                            .setPositiveButton("OK", null).show();
                    return;
                }
                // Every possible published APK is selectable. Without a manifest, actual
                // versionCode is verified from the downloaded APK *before* installation.
                String[] descriptions = new String[available.size()];
                for (int i = 0; i < available.size(); i++) {
                    Release entry = available.get(i);
                    UpdatePolicy.Candidate c = entry.candidate;
                    descriptions[i] = (entry.prerelease ? "[TEST] " : "") + c.tag() + " • " +
                            c.fileName() + (c.versionCode() > 0
                                    ? " (code " + c.versionCode() + ")"
                                    : " (verify build code after download)");
                }
                new AlertDialog.Builder(activity).setTitle("Available GitHub APK releases")
                        .setMessage("Includes test, beta, nightly and stable releases. Only an APK with a higher Android build code and matching app signature can install.")
                        .setItems(descriptions, (d, which) -> offerDownload(available.get(which)))
                        .setNegativeButton("Later", null).show();
            });
        } catch (Exception failure) {
            if (manual) dialog("Update check failed", safeError(failure), null);
            else android.util.Log.w("CD-Updater", "Automatic release check unavailable", failure);
        } finally {
            busy.set(false);
        }
    }

    private void offerDownload(Release release) {
        UpdatePolicy.Candidate c = release.candidate();
        new AlertDialog.Builder(activity)
                .setTitle("Get " + c.tag())
                .setMessage("Download " + c.fileName() + " (" + (c.bytes() / 1048576) +
                        " MiB)? Android will verify that it is a newer signed build before offering installation. Your existing saves remain untouched.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Download", (d, w) -> downloadUpdate(release)).show();
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
        if (code(archive) <= code(installed))
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