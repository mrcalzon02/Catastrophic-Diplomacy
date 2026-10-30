package diplomacy.update;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Published releases, INCLUDING prereleases, can update any desktop platform. */
public final class DesktopUpdatePolicy {
    private DesktopUpdatePolicy() {}
    public static final String WINDOWS_INSTALLER = "Catastrophic-Diplomacy-Windows-x64.msi";
    public static final String LINUX_PACKAGE = "Catastrophic-Diplomacy-Linux-x64.zip";
    public static final long MAX_DOWNLOAD_BYTES = 750L * 1024 * 1024;

    public static String expectedAsset(String osName) {
        String value=osName.toLowerCase(Locale.ROOT);
        if(value.contains("win"))return WINDOWS_INSTALLER;
        if(value.contains("linux"))return LINUX_PACKAGE;
        throw new IllegalArgumentException("No desktop updater package for "+osName);
    }

    /** GitHub releases/latest excludes prereleases. Do not replicate that behavior. */
    public static boolean eligibleRelease(boolean draft,boolean prerelease) {
        return !draft;
    }

    /** GitHub's publication order, not semver/stability labels or save compatibility. */
    public static boolean isPublishedAfter(String candidateTime,String installedTime) {
        try {
            return Instant.parse(candidateTime).isAfter(Instant.parse(installedTime));
        } catch (DateTimeParseException|NullPointerException invalid) {
            throw new IllegalArgumentException("Missing or invalid GitHub release publication timestamp", invalid);
        }
    }

    public static void requireAsset(String tag,String fileName,String url,long bytes) {
        if (tag==null || !tag.matches("[A-Za-z0-9][A-Za-z0-9._+/-]{0,199}") ||
            tag.contains("//")||tag.contains("/../")||tag.contains("/./"))
            throw new IllegalArgumentException("Unsafe update tag");
        if (!WINDOWS_INSTALLER.equals(fileName) && !LINUX_PACKAGE.equals(fileName))
            throw new IllegalArgumentException("Unsupported desktop update asset");
        if (bytes<4096 || bytes>MAX_DOWNLOAD_BYTES)
            throw new IllegalArgumentException("Update is outside the bounded download size");
        URI uri=UpdatePolicy.allowedUrl(url);
        String expected="/"+UpdatePolicy.REPOSITORY+"/releases/download/"+tag+"/"+fileName;
        if(!"github.com".equals(uri.getHost()) || uri.getRawQuery()!=null ||
           uri.getRawFragment()!=null || !expected.equals(uri.getPath()))
            throw new IllegalArgumentException("Update URL is not the requested repository release asset");
    }
}
