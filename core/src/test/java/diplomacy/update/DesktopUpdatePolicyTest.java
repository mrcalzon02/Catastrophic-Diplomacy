package diplomacy.update;

public final class DesktopUpdatePolicyTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void reject(Runnable r){try{r.run();throw new AssertionError("Invalid candidate accepted");}
        catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        check(DesktopUpdatePolicy.eligibleRelease(false,true),"Release candidates excluded");
        check(DesktopUpdatePolicy.eligibleRelease(false,false),"Stable releases excluded");
        check(!DesktopUpdatePolicy.eligibleRelease(true,true),"Draft is not public");
        check(DesktopUpdatePolicy.expectedAsset("Windows 11").endsWith(".msi"),"Windows must be MSI");
        check(DesktopUpdatePolicy.expectedAsset("Linux").endsWith(".zip"),"Linux package not recognized");
        check(DesktopUpdatePolicy.isPublishedAfter("2026-10-30T10:15:00Z","2026-10-29T21:00:00Z"),
             "Newer prerelease missed due to version labels");
        check(!DesktopUpdatePolicy.isPublishedAfter("2026-10-28T10:00:00Z","2026-10-29T21:00:00Z"),
             "Older release incorrectly offered");
        reject(()->DesktopUpdatePolicy.isPublishedAfter("not-a-date","2026-10-30T00:00:00Z"));
        String tag="experimental-2026.11.rc";
        String file=DesktopUpdatePolicy.WINDOWS_INSTALLER;
        String url="https://github.com/"+UpdatePolicy.REPOSITORY+"/releases/download/"+tag+"/"+file;
        DesktopUpdatePolicy.requireAsset(tag,file,url,100000);
        reject(()->DesktopUpdatePolicy.requireAsset(tag,file,url.replace("github.com/","bad.site/"),100000));
        reject(()->DesktopUpdatePolicy.requireAsset(tag,file,url.replace(".msi",".exe"),100000));
        reject(()->DesktopUpdatePolicy.requireAsset(tag,"game.exe",url,100000));
        reject(()->DesktopUpdatePolicy.requireAsset("../other",file,url,100000));
        reject(()->DesktopUpdatePolicy.requireAsset(tag,file,url,10));
        check(DesktopUpdatePolicy.canonicalBuildSequence("cd-v0.1.5-dev.695")==695,
                "Canonical dev sequence was not decoded");
        check(DesktopUpdatePolicy.canonicalNewerThan(
                        "cd-v0.1.5-dev.700","cd-v0.1.5-dev.695"),
                "Missing-tag fallback missed a genuinely newer canonical build");
        check(!DesktopUpdatePolicy.canonicalNewerThan(
                        "cd-v0.1.5-dev.690","cd-v0.1.5-dev.695"),
                "Missing-tag fallback allowed a downgrade");
        reject(()->DesktopUpdatePolicy.canonicalNewerThan(
                "experimental-2026.11.rc","cd-v0.1.5-dev.695"));
        reject(()->DesktopUpdatePolicy.canonicalNewerThan(
                "cd-v0.1.5-dev.700","legacy-install"));
        System.out.println("PASS: desktop prerelease updater detects newest published GitHub release; missing-tag fallback is monotonic and downgrade-safe; Windows MSI and Linux ZIP");
    }
}
