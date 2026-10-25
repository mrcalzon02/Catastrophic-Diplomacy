package diplomacy.update;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java fail-closed metadata and URL policy shared by update clients and headless tests. */
public final class UpdatePolicy {
    private UpdatePolicy() { }
    public static final String PACKAGE_ID = "games.catastrophicdiplomacy";
    public static final String REPOSITORY = "mrcalzon02/Catastrophic-Diplomacy";
    public static final long MAX_APK_BYTES = 400L * 1024 * 1024;
    public static final int SUPPORTED_DATA_SCHEMA = 1;
    private static final Pattern VERSION = Pattern.compile("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})");
    private static final Pattern SHA = Pattern.compile("[a-fA-F0-9]{64}");
    private static final List<String> REDIRECT_HOSTS = List.of("github.com", "api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com");

    public record Candidate(String tag, String version, long versionCode, String applicationId,
                            int minDataSchema, String fileName, long bytes, String sha256) { }
    public static int compareVersions(String candidate, String installed) {
        int[] a = parse(candidate), b = parse(installed);
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        return 0;
    }
    private static int[] parse(String s) {
        if (s == null) throw new IllegalArgumentException("Missing application version");
        Matcher match = VERSION.matcher(s);
        if (!match.matches()) throw new IllegalArgumentException("Expected stable MAJOR.MINOR.PATCH version");
        return new int[]{Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3))};
    }
    public static void requireCandidate(Candidate candidate, String installedVersion, long installedCode) {
        if (candidate == null) throw new IllegalArgumentException("No update candidate");
        if (!candidate.applicationId().equals(PACKAGE_ID)) throw new IllegalArgumentException("Unexpected Android package ID");
        if (!candidate.tag().equals("cd-v" + candidate.version())) throw new IllegalArgumentException("Release tag/version mismatch");
        if (compareVersions(candidate.version(), installedVersion) <= 0 || candidate.versionCode() <= installedCode)
            throw new IllegalArgumentException("Not a newer version and build code");
        if (candidate.minDataSchema() < 1 || candidate.minDataSchema() > SUPPORTED_DATA_SCHEMA)
            throw new IllegalArgumentException("Unsupported local save schema");
        String expected = "Catastrophic-Diplomacy-" + candidate.version() + "-Android.apk";
        if (!candidate.fileName().equals(expected)) throw new IllegalArgumentException("Unexpected APK filename");
        if (candidate.bytes() < 4096 || candidate.bytes() > MAX_APK_BYTES)
            throw new IllegalArgumentException("APK outside bounded size limit");
        if (candidate.sha256() == null || !SHA.matcher(candidate.sha256()).matches())
            throw new IllegalArgumentException("Invalid SHA256 checksum");
    }
    public static void requireDownloadUrl(String url, String tag, String name) {
        URI uri = allowedUrl(url);
        if (!"github.com".equals(uri.getHost()) || uri.getRawQuery() != null || uri.getRawFragment() != null ||
                !uri.getRawPath().equals("/" + REPOSITORY + "/releases/download/" + tag + "/" + name))
            throw new IllegalArgumentException("Release asset must originate from this repository and tag");
    }
    /** Every hop is validated separately; redirect targets may be temporary GitHub asset URLs. */
    public static URI allowedUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null ||
                    uri.getHost() == null || !REDIRECT_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT)) ||
                    uri.getPort() != -1 || uri.getRawFragment() != null)
                throw new IllegalArgumentException("Untrusted update URL");
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Untrusted update URL", ex);
        }
    }
}