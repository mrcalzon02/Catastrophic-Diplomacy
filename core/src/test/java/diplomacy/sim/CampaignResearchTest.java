package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Verify research cannot take negative time, even at maximum acceleration. */
public final class CampaignResearchTest {
    private static void check(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    private static void reject(Runnable r,String reason){
        try{r.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(reason);
    }
    private static CampaignProgress.State funded(int completed,int rank,int speed,long credits){
        return new CampaignProgress.State(Battle.Faction.TERRAN,Math.min(500,completed+1),
                credits,completed,rank,completed,speed,CampaignProgress.Project.NONE,0);
    }
    private static String legacy(String version,String fields){
        String body=version+"\n"+fields;CRC32 crc=new CRC32();
        crc.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(crc.getValue())+"\n";
    }
    public static void main(String[] args){
        var start=CampaignProgress.start(Battle.Faction.TERRAN);
        reject(start::purchaseNextResearch,"Research must be paid");
        var license=funded(0,0,0,50000).purchaseNextResearch();
        check(license.activeProject()==CampaignProgress.Project.LICENSE
                &&license.missionsUntilResearchComplete()==1,
                "Basic licensed research should need one victory");
        reject(()->license.beginResearch(CampaignProgress.Project.ACCELERATOR),
                "Two concurrent projects permitted");
        var first=license.complete(1,20,20);
        check(first.researchRank()==1&&first.activeProject()==CampaignProgress.Project.NONE,
                "Mission failed to unlock license");
        var later=first.purchaseNextResearch();
        check(later.missionsUntilResearchComplete()==2,"Second research tier costs two missions");
        var one=later.complete(1,20,20);
        long full=LevelEconomy.victoryCredits(LevelEconomy.level(1),20,20,true);
        check(one.credits()-later.credits()==full/4,
                "Repeat mission must pay quarter credits");
        check(one.projectProgress()==1&&one.missionsUntilResearchComplete()==1,
                "Replay granted more than one research mission");
        var done=one.complete(1,20,20);
        check(done.researchRank()==2,"Second stage failed to unlock in two victories");
        var high=funded(399,4,0,80000000).purchaseNextResearch();
        for(int i=0;i<4;i++)high=high.complete(1,20,20);
        check(high.projectProgress()==4&&high.researchRank()==4,
                "Fifth research stage must take five victories without upgrades");
        high=high.complete(1,20,20);
        check(high.researchRank()==5,"Fifth research stage not complete after five victories");
        reject(()->funded(24,0,0,90000000)
                .beginResearch(CampaignProgress.Project.ACCELERATOR),
                "Early acceleration bypasses campaign milestone");
        reject(()->funded(25,0,0,0)
                .beginResearch(CampaignProgress.Project.ACCELERATOR),
                "Acceleration must not be free");
        var accelerated=funded(400,0,0,90000000);
        for(int tier=1;tier<=4;tier++){
            long bank=accelerated.credits();
            var lab=accelerated.beginResearch(CampaignProgress.Project.ACCELERATOR);
            check(lab.credits()==bank-LevelEconomy.researchSpeedCost(tier),
                    "Lab "+tier+" failed to charge exact credits");
            check(lab.currentProjectMissions()>=0,"Negative laboratory duration");
            while(lab.activeProject()!=CampaignProgress.Project.NONE)
                lab=lab.complete(1,20,20);
            accelerated=lab;
            check(accelerated.speedUpgrades()==tier,
                    "Lab "+tier+" did not install its speed upgrade");
        }
        check(accelerated.researchPerVictory()==1,
                "A successful mission must advance by exactly one research tick");
        var instant=accelerated.purchaseNextResearch();
        check(instant.researchRank()==1&&instant.activeProject()==CampaignProgress.Project.NONE,
                "Accelerated early technology should instantly unlock at duration zero");
        check(instant.currentProjectMissions()==0&&instant.missionsUntilResearchComplete()==0
                &&instant.nextResearchMissions()>=0,"Research duration became negative");
        reject(()->accelerated.beginResearch(CampaignProgress.Project.ACCELERATOR),
                "No fifth accelerator should exist");
        reject(()->new CampaignProgress.State(Battle.Faction.TERRAN,1,0,0,
                0,0,0,CampaignProgress.Project.LICENSE,-1),
                "Negative saved project work accepted");
        check(CampaignProgress.decode(CampaignProgress.encode(license)).equals(license),
                "Active research was not persisted");
        check(CampaignProgress.decode(CampaignProgress.encode(accelerated)).equals(accelerated),
                "Four acceleration upgrades not persisted");
        var old1=CampaignProgress.decode(legacy("CD-CAMPAIGN-1","TERRAN\n2\n75000\n1\n"));
        check(old1.credits()==75000&&old1.isReplay(1),"v1 migration lost bank or mission history");
        var old2=CampaignProgress.decode(legacy("CD-CAMPAIGN-2","TERRAN\n3\n100000\n2\n2\n"));
        check(old2.researchRank()==2&&old2.isReplay(2),"v2 migration lost prior licenses");
        // Prior v3 used a named project and separate level-500 completion bit.
        var old3=CampaignProgress.decode(legacy("CD-CAMPAIGN-3",
                "TERRAN\n500\n500000\n500\n2\n1\nLICENSE\n1\ntrue\n"));
        check(old3.highestCompleted()==500&&old3.researchRank()==3,
                "Named v3 project and final mission flag lost on migration");
        var compact3=CampaignProgress.decode(legacy("CD-CAMPAIGN-3",
                "TERRAN\n401\n500000\n400\n0\n400\n1\n0\n4\n"));
        check(compact3.researchRank()==1&&!compact3.researchActive(),
                "Previously instant research did not migrate safely");
        var top=funded(499,0,0,0).complete(500,20,20);
        long first500=LevelEconomy.victoryCredits(LevelEconomy.level(500),20,20,true);
        check(top.credits()==first500&&top.highestCompleted()==500,
                "Final campaign mission first win was not credited once");
        check(top.complete(500,20,20).credits()-top.credits()==first500/4,
                "Final campaign mission replay paid first-clear reward");
        reject(()->CampaignProgress.decode(CampaignProgress.encode(license).replace("TERRAN","DRAXIAN")),
                "Changed campaign save was accepted despite checksum");
        System.out.println("PASS timed research, zero-floor instant unlocks, paid labs, replay quarter rewards and v1-v4 migrations");
    }
}
