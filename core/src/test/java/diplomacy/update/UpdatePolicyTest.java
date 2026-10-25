package diplomacy.update;

public final class UpdatePolicyTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void reject(Runnable attempt, String message) {
        try { attempt.run(); throw new AssertionError("Allowed unsafe input: " + message); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        check(UpdatePolicy.compareVersions("0.1.1", "0.1.0") > 0, "patch upgrade");
        check(UpdatePolicy.compareVersions("1.0.0", "0.99.99") > 0, "major upgrade");
        check(UpdatePolicy.compareVersions("0.4.1", "0.4.1") == 0, "equality");
        check(UpdatePolicy.compareVersions("0.3.9", "0.4.0") < 0, "downgrade");
        for (String bad : new String[]{"1.0", "1.0.0-rc1", "01.0.0", "1.0.0+dev", "-1.0.0", "1000000.0.0", "1.0.0/"})
            reject(() -> UpdatePolicy.compareVersions(bad,"0.1.0"), "version " + bad);
        var valid = new UpdatePolicy.Candidate("cd-v0.1.1", "0.1.1", 2,
                UpdatePolicy.PACKAGE_ID, 1, "Catastrophic-Diplomacy-0.1.1-Android.apk", 500000,
                "abcd".repeat(16));
        UpdatePolicy.requireCandidate(valid, "0.1.0", 1);
        reject(() -> UpdatePolicy.requireCandidate(valid,"0.1.1",2), "replay");
        reject(() -> UpdatePolicy.requireCandidate(valid,"0.1.0",2), "versionCode not greater");
        reject(() -> UpdatePolicy.requireCandidate(new UpdatePolicy.Candidate("cd-v0.1.1","0.1.1",2,"other.app",1,valid.fileName(),500000,valid.sha256()), "0.1.0",1), "package");
        reject(() -> UpdatePolicy.requireCandidate(new UpdatePolicy.Candidate("cd-v0.1.1","0.1.1",2,valid.applicationId(),2,valid.fileName(),500000,valid.sha256()), "0.1.0",1), "save schema");
        reject(() -> UpdatePolicy.requireCandidate(new UpdatePolicy.Candidate("cd-v0.1.1","0.1.1",2,valid.applicationId(),1,"../../bad.apk",500000,valid.sha256()), "0.1.0",1), "filename traversal");
        reject(() -> UpdatePolicy.requireCandidate(new UpdatePolicy.Candidate("cd-v0.1.1","0.1.1",2,valid.applicationId(),1,valid.fileName(),UpdatePolicy.MAX_APK_BYTES+1,valid.sha256()), "0.1.0",1), "oversize");
        reject(() -> UpdatePolicy.requireCandidate(new UpdatePolicy.Candidate("cd-v0.1.1","0.1.1",2,valid.applicationId(),1,valid.fileName(),500000,"0".repeat(63)), "0.1.0",1), "checksum");
        String validUrl="https://github.com/mrcalzon02/Catastrophic-Diplomacy/releases/download/cd-v0.1.1/" + valid.fileName();
        UpdatePolicy.requireDownloadUrl(validUrl,valid.tag(),valid.fileName());
        for (String bad : new String[]{validUrl.replace("https:","http:"),
                validUrl.replace("github.com", "github.com.evil.org"),
                validUrl.replace("/mrcalzon02/", "/other-owner/"),
                validUrl + "?steal=yes", "https://username@github.com" + validUrl.substring("https://github.com".length()),
                "https://github.com:443" + validUrl.substring("https://github.com".length()),
                "https://evil.org/x", "file:///sdcard/test.apk"})
            reject(() -> UpdatePolicy.requireDownloadUrl(bad,valid.tag(),valid.fileName()), "URL " + bad);
        UpdatePolicy.allowedUrl("https://release-assets.githubusercontent.com/download/x?sig=x");
        reject(() -> UpdatePolicy.allowedUrl("https://release-assets.githubusercontent.com.evil.org/download"), "redirect domain");
        System.out.println("PASS update version, package, schema, file, size, checksum and URL policies");
    }
}