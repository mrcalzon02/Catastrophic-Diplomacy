package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Headless campaign research and replay contract for desktop and Android. */
public final class CampaignResearchTest {
    private static void require(boolean yes,String explanation){
        if(!yes)throw new AssertionError(explanation);
    }
    private static void rejects(Runnable action,String detail){
        try{action.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(detail);
    }
    private static String legacy(String magic,int level,long credits,int victories,int rank) {
        String s=magic+"\nTERRAN\n"+level+"\n"+credits+"\n"+victories+"\n";
        if(!magic.equals("CD-CAMPAIGN-1"))s+=rank+"\n";
        return seal(s);
    }
    private static String seal(String body){
        CRC32 check=new CRC32();
        check.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(check.getValue())+"\n";
    }
    public static void main(String[] ignored){
        var start=CampaignProgress.start(Battle.Faction.TERRAN);
        require(!start.completedMission(1)&&start.nextResearchMissions()==1,
                "Mission one and earliest technology must be new and 1 unit");
        rejects(start::purchaseNextResearch,"No credits means no research");
        var funded=new CampaignProgress.State(Battle.Faction.TERRAN,1,25000,0,0);
        var queued=funded.purchaseNextResearch();
        require(queued.credits()==0&&queued.researchRank()==0
                        &&queued.activeProject()==CampaignProgress.Project.LICENSE
                        &&queued.currentProjectMissions()==1,
                "Technology costs real credits and requires a completed mission");
        rejects(queued::purchaseNextResearch,"Two active projects are illegal");
        rejects(()->queued.complete(2,4,4),"Locked mission cannot earn research time");
        rejects(()->queued.complete(1,4,0),"Defeat cannot earn research time");
        long raw1=LevelEconomy.victoryCredits(LevelEconomy.level(1),4,4,true);
        var won=queued.complete(1,4,4);
        require(won.researchRank()==1&&won.activeProject()==CampaignProgress.Project.NONE
                        &&won.completedMission(1)&&won.credits()==raw1,
                "First clear pays full and finishes early 1-mission research");
        var replay=won.complete(1,4,4);
        require(replay.credits()-won.credits()==raw1/4&&replay.unlocked()==won.unlocked()
                        &&replay.researchRank()==1,"Repeat must pay only one quarter");
        var tier2=new CampaignProgress.State(Battle.Faction.TERRAN,2,100000,1,1)
                .purchaseNextResearch();
        require(tier2.currentProjectMissions()==2,"Second technology research must take two units");
        var partial=tier2.complete(1,4,4);
        require(partial.projectProgress()==1&&partial.researchRank()==1
                        &&partial.missionsUntilResearchComplete()==1,
                "Replay must advance exactly one unfinished research unit");
        require(CampaignProgress.decode(CampaignProgress.encode(partial)).equals(partial),
                "Active research and progress not persisted");
        var complete=partial.complete(2,4,4);
        require(complete.researchRank()==2&&complete.activeProject()==CampaignProgress.Project.NONE,
                "Rank two only unlocks on the second successful mission");
        require(complete.credits()-partial.credits()==
                LevelEconomy.victoryCredits(LevelEconomy.level(2),4,4,true),
                "New mission after a replay must still pay full amount");

        int firstGate=LevelEconomy.researchSpeedMilestone(1);
        var blocked=new CampaignProgress.State(Battle.Faction.TERRAN,firstGate,1000000,4,1);
        rejects(()->blocked.beginResearch(CampaignProgress.Project.ACCELERATOR),
                "Cannot skip campaign milestone to unlock lab accelerator");
        var eligible=new CampaignProgress.State(Battle.Faction.TERRAN,firstGate+1,
                1000000,5,1);
        var lab=eligible.beginResearch(CampaignProgress.Project.ACCELERATOR);
        require(lab.speedUpgrades()==0&&lab.projectProgress()==0
                        &&lab.credits()==1000000-LevelEconomy.researchSpeedCost(1)
                        &&lab.nextAccelerationMissions()==2,
                "Lab upgrade must have a real expensive purchase and completion time");
        lab=lab.complete(1,4,4);
        require(lab.speedUpgrades()==0&&lab.projectProgress()==1,
                "Paying for a lab speed upgrade must not immediately grant it");
        lab=lab.complete(1,4,4);
        require(lab.speedUpgrades()==1&&lab.researchPerVictory()==2,
                "Second victory completes first research speed increase");

        int fourthGate=LevelEconomy.researchSpeedMilestone(4);
        var late=new CampaignProgress.State(Battle.Faction.TERRAN,fourthGate+1,
                100000000L,500,5,fourthGate,3,CampaignProgress.Project.NONE,0);
        var fourth=late.purchaseResearchSpeed();
        require(fourth.currentProjectMissions()==5&&fourth.researchPerVictory()==4
                        &&fourth.missionsUntilResearchComplete()==2,
                "Fourth lab needs five work units with a four-work rate");
        var fourthPartial=fourth.complete(1,4,4);
        require(fourthPartial.projectProgress()==4&&fourthPartial.speedUpgrades()==3,
                "Fourth lab should not be active before its completion");
        var faster=fourthPartial.complete(1,4,4);
        require(faster.speedUpgrades()==4&&faster.researchPerVictory()==5,
                "Fourth lab completion raises the research rate to five");
        rejects(faster::purchaseResearchSpeed,"The research tree permits at most four accelerators");
        var apex=new CampaignProgress.State(Battle.Faction.TERRAN,fourthGate+1,
                100000000L,500,5,fourthGate,4,CampaignProgress.Project.NONE,0)
                .purchaseNextResearch();
        require(apex.currentProjectMissions()==5&&apex.missionsUntilResearchComplete()==1,
                "Late technology should require five base units but one accelerated victory");
        require(apex.complete(1,4,4).researchRank()==6,
                "Maximum acceleration cannot unlock without a successful mission");

        var last=new CampaignProgress.State(Battle.Faction.TERRAN,500,10000L,499,
                6,499,0,CampaignProgress.Project.NONE,0);
        require(!last.completedMission(500),"Mission 500 first clear must be tracked separately");
        long raw500=LevelEconomy.victoryCredits(LevelEconomy.level(500),4,4,true);
        var cleared=last.complete(500,4,4);
        require(cleared.highestCompleted()==500&&cleared.credits()-last.credits()==raw500,
                "First mission 500 victory must pay full reward");
        var repeated=cleared.complete(500,4,4);
        require(repeated.credits()-cleared.credits()==raw500/4,
                "The final campaign mission repeat must pay one quarter");
        require(CampaignProgress.decode(CampaignProgress.encode(repeated)).equals(repeated),
                "High-water clear ledger did not persist");

        var v1=CampaignProgress.decode(legacy("CD-CAMPAIGN-1",4,75000,3,0));
        var v2=CampaignProgress.decode(legacy("CD-CAMPAIGN-2",4,80000,3,2));
        require(v1.highestCompleted()==3&&v1.researchRank()==0&&v1.credits()==75000,
                "v1 progress and credits must migrate intact");
        require(v2.highestCompleted()==3&&v2.researchRank()==2&&v2.credits()==80000,
                "v2 already purchased licenses must migrate intact");
        String old3=seal("CD-CAMPAIGN-3\nTERRAN\n4\n90000\n3\n1\n3\n2\n1\n0\n");
        var v3=CampaignProgress.decode(old3);
        require(v3.researchRank()==1&&v3.researchTarget()==2&&v3.projectProgress()==1,
                "Prior mission-timed v3 research must migrate intact");
        String namedV3=seal("CD-CAMPAIGN-3\nTERRAN\n500\n500000\n500\n2\n1\nLICENSE\n1\ntrue\n");
        var named=CampaignProgress.decode(namedV3);
        require(named.highestCompleted()==500&&named.researchRank()==2
                        &&named.activeProject()==CampaignProgress.Project.LICENSE,
                "Named legacy v3 project should preserve progress without free completion");
        rejects(()->CampaignProgress.decode(CampaignProgress.encode(faster)
                .replace("ACCELERATOR","LICENSE")),"Checksum must reject forged technology");
        System.out.println("PASS: quarter replays, 1-5 research, 4 milestone labs, last level, v1-v4 save migration");
    }
}
