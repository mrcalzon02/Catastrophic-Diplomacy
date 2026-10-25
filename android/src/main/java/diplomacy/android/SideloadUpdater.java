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
            UpdatePolicy.REPOSITORY + "/releases?per_page=20";
    private static final String MANIFEST_NAME = "release-manifest.json";
    private static final int MAX_RELEASE_JSON = 512 * 1024;
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

    void checkForUpdates() {
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
            Toast.makeText(activity, "Checking GitHub releases…", Toast.LENGTH_SHORT).show();
            io.execute(this::findUpdate);
        });
    }

    private boolean isPlayInstall() {
        String installer = activity.getPackageManager().getInstallerPackageName(activity.getPackageName());
        return "com.android.vending".equals(installer);
    }

    private void findUpdate() {
        try {
            PackageInfo installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            String installedVersion = installed.versionName;
            long installedCode = code(installed);
            JSONArray releases = new JSONArray(new String(downloadBytes(RELEASE_API, MAX_RELEASE_JSON), java.nio.charset.StandardCharsets.UTF_8));
            Release best = null;
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.getJSONObject(i);
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue;
                String tag = release.optString("tag_name");
                if (!tag.matches("cd-v(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})")) continue;
                String version = tag.substring(4);
                if (UpdatePolicy.compareVersions(version, installedVersion) <= 0) continue;
                JSONArray assets = release.getJSONArray("assets");
                JSONObject manifestAsset = findAsset(assets, MANIFEST_NAME);
                String apkName = "Catastrophic-Diplomacy-" + version + "-Android.apk";
                JSONObject apkAsset = findAsset(assets, apkName);
                if (manifestAsset == null || apkAsset == null) continue;
                String manifestUrl = manifestAsset.getString("browser_download_url");
                String apkUrl = apkAsset.getString("browser_download_url");
                UpdatePolicy.requireDownloadUrl(manifestUrl, tag, MANIFEST_NAME);
                UpdatePolicy.requireDownloadUrl(apkUrl, tag, apkName);
                if (manifestAsset.getLong("size") > MAX_MANIFEST_JSON) continue;
                JSONObject manifest = new JSONObject(new String(downloadBytes(manifestUrl, MAX_MANIFEST_JSON), java.nio.charset.StandardCharsets.UTF_8));
                if (manifest.getInt("schema") != 1) continue;
                JSONObject item = manifest.getJSONObject("apk");
                var candidate = new UpdatePolicy.Candidate(tag, manifest.getString("version"),
                        manifest.getLong("versionCode"), manifest.getString("applicationId"),
                        manifest.getInt("minDataSchema"), item.getString("name"),
                        item.getLong("bytes"), item.getString("sha256"));
                UpdatePolicy.requireCandidate(candidate, installedVersion, installedCode);
                if (apkAsset.getLong("size") != candidate.bytes())
                    throw new IOException("Release APK size differs from manifest");
                String githubDigest = apkAsset.optString("digest");
                if (githubDigest.startsWith("sha256:") &&
                        !githubDigest.substring(7).equalsIgnoreCase(candidate.sha256()))
                    throw new IOException("GitHub asset digest disagrees with release manifest");
                if (best == null || UpdatePolicy.compareVersions(candidate.version(), best.candidate.version()) > 0)
                    best = new Release(candidate, apkUrl);
            }
            Release found = best;
            if (found == null) {
                dialog("No new release", "There is no newer published Catastrophic Diplomacy Android update available.", null);
            } else {
                activity.runOnUiThread(() -> {
                    busy.set(false);
                    if (closed || activity.isFinishing()) return;
                    new AlertDialog.Builder(activity)
                            .setTitle("Update available: " + found.candidate.version())
                            .setMessage("Download verified Android update (" + (found.candidate.bytes() / 1048576) + " MiB)? Your installed fleets and saves remain in Android's application data. Installation requires your consent.")
                            .setNegativeButton("Not now", null)
                            .setPositiveButton("Download", (d, w) -> downloadUpdate(found)).show();
                });
            }
        } catch (Exception failure) {
            dialog("Update check failed", safeError(failure), null);
        } finally {
            // While showing an available-update dialog, busy is reset by the UI handler.
            // Reset here also covers empty results and failures.
            busy.set(false);
        }
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
                if (read != release.candidate.bytes() || !hex(sha.digest()).equalsIgnoreCase(release.candidate.sha256()))
                    throw new IOException("Downloaded APK failed checksum or length verification");
                validateArchive(part, release.candidate);
                if (!part.renameTo(target)) throw new IOException("Unable to stage verified APK");
                pendingApk = target;
                activity.runOnUiThread(() -> {
                    if (!closed && !activity.isFinishing())
                        new AlertDialog.Builder(activity).setTitle("Update verified")
                                .setMessage("Version " + release.candidate.version() + " passed file integrity, package ID, version code and signing certificate checks. Open Android's system installer?")
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
        if (code(archive) != candidate.versionCode() || code(archive) <= code(installed) ||
                !candidate.version().equals(archive.versionName))
            throw new IOException("APK version name or code mismatch");
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
    private record Release(UpdatePolicy.Candidate candidate, String apkUrl) { }
}