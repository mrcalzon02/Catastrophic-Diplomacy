package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Research durations are bounded below by zero; immediate unlocks are valid. */
public final class CampaignResearchTest {
    private static void check(boolean pass,String why){if(!pass)throw new AssertionError(why);}
    private static void reject(Runnable task,String why){
        try{task.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError(why);
    }
    private static CampaignProgress.State funded(int frontier,int rank,int speed,long credits){
        return new CampaignProgress.State(Battle.Faction.TERRAN,Math.min(500,frontier+1),
                credits,frontier,rank,frontier,speed,CampaignProgress.Project.NONE,0);
    }
    private static String seal(String magic,String data){
        String body=magic+"\n"+data;
        CRC32 crc=new CRC32();crc.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(crc.getValue())+"\n";
    }
    public static void main(String[] args){
        reject(CampaignProgress.start(Battle.Faction.TERRAN)::purchaseNextResearch,
                "Starting technology must be purchased");
        var first=funded(0,0,0,300000).purchaseNextResearch();
        check(first.activeProject()==CampaignProgress.Project.LICENSE
                &&first.missionsUntilResearchComplete()==1,"First technology needs one mission");
        reject(()->first.beginResearch(CampaignProgress.Project.ACCELERATOR),
                "No simultaneous projects");
        var winner=first.complete(1,20,20);
        check(winner.researchRank()==1&&winner.activeProject()==CampaignProgress.Project.NONE,
                "One successful mission did not complete early research");
        var second=winner.purchaseNextResearch();
        check(second.missionsUntilResearchComplete()==2,"Second technology should take two missions");
        var one=second.complete(1,20,20);
        long full=LevelEconomy.victoryCredits(LevelEconomy.level(1),20,20,true);
        check(one.credits()-second.credits()==full/4,
                "Repeated mission must pay exactly one quarter");
        check(one.projectProgress()==1&&one.missionsUntilResearchComplete()==1,
                "Replay must advance research by one mission tick only");
        var two=one.complete(1,20,20);
        check(two.researchRank()==2,"Second research project failed after two wins");
        var fifth=funded(399,4,0,80000000).purchaseNextResearch();
        check(fifth.missionsUntilResearchComplete()==5,"Late projects must need five missions");
        for(int i=0;i<4;i++)fifth=fifth.complete(1,20,20);
        check(fifth.researchRank()==4&&fifth.projectProgress()==4,
                "Five-turn project prematurely completed");
        fifth=fifth.complete(1,20,20);
        check(fifth.researchRank()==5,"Five-turn project did not complete");
        reject(()->funded(24,0,0,90000000).purchaseResearchSpeed(),
                "Lab milestone 25 bypassed");
        reject(()->funded(25,0,0,0).purchaseResearchSpeed(),
                "Accelerators cannot be free");
        var fleet=funded(400,0,0,90000000);
        for(int tier=1;tier<=4;tier++){
            long balance=fleet.credits();
            var queued=fleet.purchaseResearchSpeed();
            check(queued.credits()==balance-LevelEconomy.researchSpeedCost(tier),
                    "Lab "+tier+" failed to debit credits");
            check(queued.missionsUntilResearchComplete()>=0,
                    "A laboratory has negative work duration");
            while(queued.activeProject()!=CampaignProgress.Project.NONE)
                queued=queued.complete(1,20,20);
            fleet=queued;
            check(fleet.speedUpgrades()==tier,"Research accelerator not earned");
        }
        check(fleet.researchPerVictory()==1,
                "A completed mission must always provide one research tick");
        var acceleratedLicense=fleet.purchaseNextResearch();
        check(acceleratedLicense.researchRank()==0&&
                        acceleratedLicense.activeProject()==CampaignProgress.Project.LICENSE,
                "Even at maximum acceleration a newly paid technology must require victory");
        check(acceleratedLicense.missionsUntilResearchComplete()==1,
                "Four speed upgrades should reduce research to one mission, not zero");
        check(acceleratedLicense.complete(1,20,20).researchRank()==1,
                "Winning the required research mission must unlock the technology");
        final var maxedFleet=fleet;
        reject(maxedFleet::purchaseResearchSpeed,"No fifth accelerator");
        check(CampaignProgress.decode(CampaignProgress.encode(first)).equals(first),
                "Active project failed to survive checksum save");
        check(CampaignProgress.decode(CampaignProgress.encode(maxedFleet)).equals(maxedFleet),
                "Speed upgrade purchase not saved");
        reject(()->new CampaignProgress.State(Battle.Faction.TERRAN,1,0,0,
                0,0,0,CampaignProgress.Project.LICENSE,-1),
                "Negative stored research progress accepted");
        var v1=CampaignProgress.decode(seal("CD-CAMPAIGN-1",
                "TERRAN\n2\n75000\n1\n"));
        check(v1.credits()==75000&&v1.isReplay(1),"Legacy first-clear lost");
        var v2=CampaignProgress.decode(seal("CD-CAMPAIGN-2",
                "TERRAN\n3\n100000\n2\n2\n"));
        check(v2.researchRank()==2&&v2.isReplay(2),"Prior technology license lost");
        var namedV3=CampaignProgress.decode(seal("CD-CAMPAIGN-3",
                "TERRAN\n500\n500000\n500\n2\n1\nLICENSE\n1\ntrue\n"));
        check(namedV3.highestCompleted()==500&&namedV3.researchRank()==2
                &&namedV3.activeProject()==CampaignProgress.Project.LICENSE,
                "Named v3 project lost or wrongly autocompleted");
        var numericV3=CampaignProgress.decode(seal("CD-CAMPAIGN-3",
                "TERRAN\n401\n500000\n400\n0\n400\n1\n0\n4\n"));
        check(numericV3.researchRank()==1&&!numericV3.researchActive(),
                "Zero-duration numeric v3 research was not restored");
        var almost=funded(499,0,0,0);
        var finalWin=almost.complete(500,20,20);
        long first500=LevelEconomy.victoryCredits(LevelEconomy.level(500),20,20,true);
        check(finalWin.highestCompleted()==500&&finalWin.credits()==first500,
                "Campaign stage 500 first clear payout wrong");
        check(finalWin.complete(500,20,20).credits()-finalWin.credits()==first500/4,
                "Final stage replay paid full reward twice");
        reject(()->CampaignProgress.decode(CampaignProgress.encode(first).replace("TERRAN","DRAXIAN")),
                "Corrupted research save checksum accepted");
        System.out.println("PASS research: instant zero-floor, paid licenses/labs, four accelerators, quarter replays, v1-v4 saves");
    }
}
