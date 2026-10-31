package diplomacy.sim;

/** All six technology ranks and four lab upgrades obey a zero-duration floor.
 * This test is authoritative: instant paid research is allowed. */
public final class ResearchVictoryFloorTest {
    private static void require(boolean ok,String info){
        if(!ok)throw new AssertionError(info);
    }
    public static void main(String[] args) {
        int instant=0,timed=0;
        for(int bonus=0;bonus<=4;bonus++){
            for(int rank=0;rank<6;rank++){
                var wallet=new CampaignProgress.State(Battle.Faction.TERRAN,
                        401,100000000L,400,rank,400,bonus,CampaignProgress.Project.NONE,0);
                int expected=Math.max(0,LevelEconomy.researchMissionCost(rank+1)-bonus);
                require(wallet.nextResearchMissions()==expected,
                        "Wrong nonnegative research time for rank "+rank+" boost "+bonus);
                long startingBank=wallet.credits();
                var started=wallet.beginResearch(CampaignProgress.Project.LICENSE);
                require(started.credits()==startingBank-wallet.nextResearchCost(),
                        "Instant technology must still charge full research price");
                require(started.missionsUntilResearchComplete()==expected,
                        "Research time changed between quote and purchase");
                if(expected==0){
                    instant++;
                    require(started.researchRank()==rank+1&&!started.researchActive(),
                            "Zero-duration license was not awarded instantly");
                }else{
                    timed++;
                    require(started.researchRank()==rank&&started.researchActive(),
                            "Timed technology finished without a successful mission");
                    for(int turn=1;turn<=expected;turn++){
                        started=started.complete(1,1,1);
                        require(started.researchRank()==(turn==expected?rank+1:rank),
                                "Technology unlocked on wrong mission turn");
                    }
                }
                require(started.researchRank()==rank+1&&!started.researchActive(),
                        "Technology project never completed");
            }
        }
        for(int bonus=0;bonus<4;bonus++){
            var wallet=new CampaignProgress.State(Battle.Faction.TERRAN,
                    401,100000000L,400,0,400,bonus,CampaignProgress.Project.NONE,0);
            int expected=Math.max(0,(bonus+2)-bonus);
            require(wallet.nextAccelerationMissions()==expected,
                    "Accelerator quote should be nonnegative");
            var started=wallet.beginResearch(CampaignProgress.Project.ACCELERATOR);
            require(started.missionsUntilResearchComplete()==expected,
                    "Paid lab quote and project differ");
            for(int turn=0;turn<expected;turn++)
                started=started.complete(1,1,1);
            require(started.speedUpgrades()==bonus+1&&!started.researchActive(),
                    "Laboratory accelerator not completed after purchased mission time");
        }
        System.out.println("PASS "+instant+" instant and "+timed+
                " mission-timed technology combinations, plus 4 paid laboratory projects");
    }
}
