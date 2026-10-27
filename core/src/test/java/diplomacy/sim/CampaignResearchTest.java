package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public final class CampaignResearchTest {
    private static void check(boolean valid,String reason){if(!valid)throw new AssertionError(reason);}
    private static void rejects(Runnable r,String reason) {
        try{r.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError("Expected rejection: "+reason);
    }
    private static CampaignProgress.State funded(int high,int rank,int speed,long credits) {
        return new CampaignProgress.State(Battle.Faction.TERRAN,Math.min(500,high+1),
                credits,high,rank,speed,CampaignProgress.Project.NONE,0,false);
    }
    private static String oldSave(String magic,String fields) {
        String body=magic+"\n"+fields;
        CRC32 crc=new CRC32();crc.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(crc.getValue())+"\n";
    }
    public static void main(String[] args){
        var zero=CampaignProgress.start(Battle.Faction.TERRAN);
        rejects(()->zero.beginResearch(CampaignProgress.Project.LICENSE),"Research cannot be free");
        var original=funded(0,0,0,50000);
        var first=original.beginResearch(CampaignProgress.Project.LICENSE);
        check(first.researchRank()==0&&first.activeProject()==CampaignProgress.Project.LICENSE
                &&first.missionsUntilResearchComplete()==1,"Early research requires one mission");
        rejects(()->first.beginResearch(CampaignProgress.Project.ACCELERATOR),
                "One research project must be active at a time");
        var won=first.complete(1,20,20);
        check(won.researchRank()==1&&won.activeProject()==CampaignProgress.Project.NONE,
                "First win did not complete first research license");
        var second=won.beginResearch(CampaignProgress.Project.LICENSE);
        check(second.missionsUntilResearchComplete()==2,"Rank 2 requires two missions");
        var once=second.complete(1,20,20);
        check(once.projectProgress()==1&&once.missionsUntilResearchComplete()==1,
                "Replay victory advanced more than one mission tick");
        check(once.credits()-second.credits()==
                LevelEconomy.victoryCredits(LevelEconomy.level(1),20,20,true)/4,
                "Repeat win did not pay one-quarter");
        var twice=once.complete(1,20,20);
        check(twice.researchRank()==2&&twice.activeProject()==CampaignProgress.Project.NONE,
                "Two mission victories did not complete rank 2");
        var late=funded(399,4,0,80000000).beginResearch(CampaignProgress.Project.LICENSE);
        check(late.currentProjectMissions()==5,"Late tech must require five missions");
        for(int i=0;i<4;i++)late=late.complete(1,20,20);
        check(late.projectProgress()==4&&late.researchRank()==4,
                "Five-mission project completed early");
        late=late.complete(1,20,20);
        check(late.researchRank()==5,"Late project did not complete after five missions");
        rejects(()->funded(24,0,0,200000000).beginResearch(CampaignProgress.Project.ACCELERATOR),
                "Research accelerator milestone bypass");
        rejects(()->funded(25,0,0,0).beginResearch(CampaignProgress.Project.ACCELERATOR),
                "Research acceleration available for free");
        var boosted=funded(400,0,0,90000000);
        for(int upgrade=1;upgrade<=4;upgrade++){
            long previousCredits=boosted.credits();
            var started=boosted.beginResearch(CampaignProgress.Project.ACCELERATOR);
            check(started.credits()==previousCredits-LevelEconomy.researchSpeedCost(upgrade),
                    "Accelerator "+upgrade+" not fully paid");
            check(started.currentProjectMissions()>=0&&started.currentProjectMissions()<=5,
                    "Negative or excessive lab project time");
            while(started.activeProject()!=CampaignProgress.Project.NONE)
                started=started.complete(1,20,20);
            boosted=started;
            check(boosted.speedUpgrades()==upgrade,"Accelerator did not unlock");
        }
        var instant=boosted.beginResearch(CampaignProgress.Project.LICENSE);
        check(instant.researchRank()==1&&instant.activeProject()==CampaignProgress.Project.NONE
                &&instant.nextResearchMissions()==0,"Fast low-tier technology must unlock instantly");
        check(instant.missionsUntilResearchComplete()==0&&instant.currentProjectMissions()==0,
                "Instant unlock went below zero missions");
        check(instant.nextResearchMissions()>=0,"Negative research time after all four upgrades");
        rejects(()->boosted.beginResearch(CampaignProgress.Project.ACCELERATOR),
                "Fifth research accelerator exists");
        rejects(()->new CampaignProgress.State(Battle.Faction.TERRAN,1,0,0,
                0,0,CampaignProgress.Project.LICENSE,-1,false),
                "Negative saved research progress accepted");
        check(CampaignProgress.decode(CampaignProgress.encode(first)).equals(first),
                "Pending research not checksummed and persisted");
        check(CampaignProgress.decode(CampaignProgress.encode(boosted)).equals(boosted),
                "Accelerator investment not preserved");
        var v1=CampaignProgress.decode(oldSave("CD-CAMPAIGN-1",
                "TERRAN\n2\n75000\n1\n"));
        check(v1.researchRank()==0&&v1.credits()==75000&&v1.isReplay(1),
                "Legacy campaign migration changed credits/clear history");
        var v2=CampaignProgress.decode(oldSave("CD-CAMPAIGN-2",
                "TERRAN\n3\n100000\n2\n2\n"));
        check(v2.researchRank()==2&&v2.speedUpgrades()==0&&v2.isReplay(2),
                "Prior saved research licenses lost");
        rejects(()->CampaignProgress.decode(CampaignProgress.encode(first).replace("TERRAN","DRAXIAN")),
                "Corrupt save checksum not rejected");
        System.out.println("PASS mission research: zero floor, 1..5 turns, paid four accelerators, quarter replays, migrations");
    }
}
