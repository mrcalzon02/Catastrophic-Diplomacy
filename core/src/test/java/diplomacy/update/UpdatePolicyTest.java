package diplomacy.update;

/** Regression gates: do not silently reintroduce a stable-only update filter. */
public final class UpdatePolicyTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void reject(Runnable task,String why){
        try{task.run();throw new AssertionError("Unsafe candidate permitted: "+why);}
        catch(IllegalArgumentException expected){ }
    }
    public static void main(String[] args){
        check(UpdatePolicy.eligibleRelease(false,false),"stable release");
        check(UpdatePolicy.eligibleRelease(false,true),"beta/prerelease must be discoverable");
        check(!UpdatePolicy.eligibleRelease(true,true),"unpublished draft is not publicly available");
        check(UpdatePolicy.newerAndroidBuild(53,52),"test-build version code increase");
        check(!UpdatePolicy.newerAndroidBuild(52,52),"same code must not install");
        var nightly = new UpdatePolicy.Candidate("nightly_2026.10.25","0.1.1-rc.3",53,
                UpdatePolicy.PACKAGE_ID,1,"unusual-Nightly-Build.apk",8192,"a".repeat(64));
        UpdatePolicy.requireCandidate(nightly,"0.1.1",52);
        var sameName = new UpdatePolicy.Candidate("build-99","0.1.1",54,
                UpdatePolicy.PACKAGE_ID,1,"new.apk",8192,"");
        UpdatePolicy.requireCandidate(sameName,"0.1.1",52);
        var lowerName = new UpdatePolicy.Candidate("test+hotfix","0.0.9-dev",55,
                UpdatePolicy.PACKAGE_ID,1,"signed.apk",8192,"");
        UpdatePolicy.requireCandidate(lowerName,"0.1.1",52);
        reject(()->UpdatePolicy.requireCandidate(nightly,"0.1.1",53),"same build code");
        reject(()->UpdatePolicy.requireCandidate(nightly,"0.1.1",54),"older build code");
        reject(()->UpdatePolicy.requireCandidate(new UpdatePolicy.Candidate(
                "any","0.1.2",53,"other.package",1,"app.apk",8192,""),"0.1.1",52),"wrong app");
        reject(()->UpdatePolicy.requireCandidate(new UpdatePolicy.Candidate(
                "any","0.1.2",53,UpdatePolicy.PACKAGE_ID,4,"app.apk",8192,""),"0.1.1",52),"schema");
        var noManifest=UpdatePolicy.provisional("rc_28","provisional.apk",8192,"");
        check(noManifest.versionCode()==-1,"unknown versionCode must be inspected on APK download");
        for(String bad: new String[]{"../../bad.apk","test.zip","bad/name.apk","fake.apk/"})
            reject(()->UpdatePolicy.provisional("rc1",bad,8192,""),"filename "+bad);
        reject(()->UpdatePolicy.provisional("../tag","ok.apk",8192,""),"unsafe tag");
        reject(()->UpdatePolicy.provisional("test","a.apk",8192,"1234"),"invalid digest");
        reject(()->UpdatePolicy.provisional("test","a.apk",10,""),"undersized APK");
        reject(()->UpdatePolicy.provisional("test","a.apk",UpdatePolicy.MAX_APK_BYTES+1,""),"oversize APK");
        String url="https://github.com/mrcalzon02/Catastrophic-Diplomacy/releases/download/rc_28/provisional.apk";
        UpdatePolicy.requireDownloadUrl(url,"rc_28","provisional.apk");
        for(String bad: new String[]{url.replace("https:","http:"),
                 url.replace("github.com","github.com.evil.com"),url.replace("/mrcalzon02/","/bad-owner/"),
                 url+"?token=evil","file:///tmp/bad.apk"})
            reject(()->UpdatePolicy.requireDownloadUrl(bad,"rc_28","provisional.apk"),"download host");
        UpdatePolicy.allowedUrl("https://release-assets.githubusercontent.com/anything?token=github");
        reject(()->UpdatePolicy.allowedUrl("https://evil.example.com"),"redirect host");
        System.out.println("PASS stable + prerelease + nightly + same-name incremented versionCode; manifest optional");
    }
}
