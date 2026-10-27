package diplomacy.sim;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Timed paid research, one-quarter repeat rewards and four acceleration tiers. */
public final class CampaignResearchTest {
    private static void check(boolean valid,String reason){if(!valid)throw new AssertionError(reason);}
    private static void rejects(Runnable task,String reason){
        try{task.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError("Expected rejection: "+reason);
    }
    private static CampaignProgress.State funded(int high,int rank,int speed,long credits){
        return new CampaignProgress.State(Battle.Faction.TERRAN,Math.min(500,high+1),
                credits,high,rank,high,0,0,speed);
    }
    private static String oldSave(String version,String fields){
        String body=version+"\n"+fields;
        CRC32 crc=new CRC32();crc.update(body.getBytes(StandardCharsets.UTF_8));
        return body+Long.toHexString(crc.getValue())+"\n";
    }
    public static void main(String[] args){
        var initial=CampaignProgress.start(Battle.Faction.TERRAN);
        rejects(initial::purchaseNextResearch,"No starting credits");
        var funding=funded(0,0,0,50000);
        var started=funding.purchaseNextResearch();
        check(started.researchRank()==0&&started.researchTarget()==1&&started.researchRemaining()==1,
                "Stage one should not unlock before the first mission");
        rejects(started::purchaseNextResearch,"Cannot start two paid projects");
        var winner=started.complete(1,20,20);
        check(winner.researchRank()==1&&!winner.researchActive()&&winner.researchRemaining()==0,
                "Exactly one successful mission must finish one-turn research");
        var second=winner.purchaseNextResearch();
        check(second.researchTarget()==2&&second.researchRemaining()==2,"Second stage must require two missions");
        var once=second.complete(1,20,20);
        check(once.researchRemaining()==1&&once.researchRank()==1,
                "Replaying an already-completed stage advances one tick, not two");
        var twice=once.complete(1,20,20);
        check(twice.researchRank()==2&&!twice.researchActive(),
                "Replay mission did not finish second technology project");
        check(second.complete(1,20,20).credits()-second.credits()==
                LevelEconomy.victoryCredits(LevelEconomy.level(1),20,20,true)/4,
                "Mission replay must award precisely 25 percent");
        var mid=funded(399,4,0,50000000).purchaseNextResearch();
        check(mid.researchRemaining()==5,"Late project must require five mission victories");
        for(int i=0;i<4;i++)mid=mid.complete(1,20,20);
        check(mid.researchRemaining()==1&&mid.researchRank()==4,
                "Four successful missions must not immediately finish five-turn project");
        mid=mid.complete(1,20,20);
        check(mid.researchRank()==5,"Five mission ticks did not complete high-tier project");
        var boosted=funded(400,0,0,50000000);
        for(int i=1;i<=4;i++){
            long oldCredits=boosted.credits();
            boosted=boosted.purchaseResearchSpeed();
            check(boosted.researchSpeed()==i&&
                    boosted.credits()==oldCredits-LevelEconomy.researchSpeedCost(i),
                    "Research upgrade "+i+" failed to charge correct credits");
        }
        check(boosted.researchSpeed()==4,"All four optional accelerators not applied");
        var instant=boosted.purchaseNextResearch();
        check(instant.researchRank()==1&&!instant.researchActive()&&instant.researchRemaining()==0,
                "Speed upgrades must allow zero-turn INSTANT technology");
        check(instant.researchDuration()>=0&&instant.researchRemaining()>=0,
                "Instant research went negative");
        rejects(instant::purchaseResearchSpeed,"No fifth acceleration tier");
        rejects(()->funded(24,0,0,500000).purchaseResearchSpeed(),
                "Milestone 25 cannot be bypassed");
        rejects(()->funded(25,0,0,0).purchaseResearchSpeed(),
                "Milestone acceleration cannot be free");
        var almost=funded(25,0,0,500000).purchaseNextResearch();
        var accelerated=almost.purchaseResearchSpeed();
        check(accelerated.researchRank()==1&&!accelerated.researchActive(),
                "Purchasing booster during one-turn project should instantly finish it");
        rejects(()->new CampaignProgress.State(Battle.Faction.TERRAN,1,0,0,0,0,1,-1,0),
                "Negative research progress accepted");
        check(CampaignProgress.decode(CampaignProgress.encode(accelerated)).equals(accelerated),
                "Pending research or booster save did not round-trip");
        check(CampaignProgress.decode(CampaignProgress.encode(second)).equals(second),
                "Active mission-time project failed save/reload");
        var legacy=CampaignProgress.decode(oldSave("CD-CAMPAIGN-1","TERRAN\n2\n75000\n1\n"));
        check(legacy.highestCompleted()==1&&legacy.researchRank()==0,
                "Legacy v1 campaign should retain money and previous clears");
        var previous=CampaignProgress.decode(oldSave("CD-CAMPAIGN-2","TERRAN\n3\n100000\n2\n2\n"));
        check(previous.researchRank()==2&&previous.highestCompleted()==2
                &&previous.researchSpeed()==0,"Version 2 research licenses must migrate");
        rejects(()->CampaignProgress.decode(CampaignProgress.encode(second).replace("TERRAN","DRAXIAN")),
                "Modified mission-time research record escaped checksum");
        System.out.println("PASS campaign research: zero-floor, 1..5 mission projects, four paid milestone boosters, save migrations, quarter replays");
    }
}
