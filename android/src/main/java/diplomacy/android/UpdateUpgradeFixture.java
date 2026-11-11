package diplomacy.android;

import android.content.Context;
import diplomacy.sim.Battle;
import diplomacy.sim.CampaignFleet;
import diplomacy.sim.CampaignLedger;
import diplomacy.sim.CampaignProgress;
import diplomacy.sim.CampaignTechnology;
import diplomacy.shipyard.Shipyard;
import diplomacy.tech.TechnologyCatalog;
import hbnet.LocalProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Non-shipping updaterTest state fixture. AndroidLauncher invokes this only
 * when the installed package is debuggable; production release APKs cannot
 * activate it. Every artifact is written through the same persistence APIs
 * used by normal gameplay/networking.
 */
final class UpdateUpgradeFixture {
    private static final String PREFS="catastrophic-diplomacy-performance";
    private static final String PIN=
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private UpdateUpgradeFixture(){}

    static void seed(Context context)throws Exception{
        Path root=context.getFilesDir().toPath();
        Path marker=root.resolve("updater-fixture.marker");
        if(Files.exists(marker))return;

        TechnologyCatalog catalog=TechnologyCatalog.loadDefault();
        Battle.Faction faction=Battle.Faction.TERRAN;
        CampaignProgress.State progress=new CampaignProgress.State(
                faction,11,123456L,10,3);
        Shipyard.Design starter=Shipyard.starter(faction,Shipyard.Hull.CORVETTE);
        CampaignFleet.Roster fleet=CampaignFleet.start(
                faction,starter,catalog,0x0CDA11EEDL);
        CampaignTechnology.State research=new CampaignTechnology.State(
                faction,Set.of(
                        "TECH-TER-LAS-OUTPUT-01",
                        "TECH-TER-LAS-OUTPUT-02",
                        "TECH-TER-LAS-OUTPUT-03"),
                null,0,0);
        research.validateCatalog(catalog);
        Path ledger=root.resolve("campaign/authoritative-ledger-v1.dat");
        Files.createDirectories(ledger.getParent());
        CampaignLedger.commit(ledger,
                new CampaignLedger.Snapshot(1,progress,fleet,research),catalog);

        Path network=root.resolve("network-profile");
        LocalProfile profile=new LocalProfile(network);
        profile.streamSafe(false);
        profile.server(null,"Updater Fixture","127.0.0.1",27817,PIN);

        boolean preferences=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit()
                .putFloat("requestedSpeed",2.5f)
                .putBoolean("hud",true)
                .putBoolean("capture",false)
                .putBoolean("fps",false)
                .putBoolean("adaptive",false)
                .commit();
        if(!preferences)throw new java.io.IOException("Updater fixture preferences were not committed");

        Files.write(marker,"schema=1\n".getBytes(StandardCharsets.US_ASCII));
    }
}
