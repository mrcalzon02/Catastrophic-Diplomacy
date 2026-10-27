package diplomacy.sim;

/** Independent acceptance: every paid project needs at least one victory. */
public final class ResearchVictoryFloorTest {
    private static void require(boolean ok,String info){
        if(!ok)throw new AssertionError(info);
    }
    public static void main(String[] args) {
        for(int bonus=0;bonus<=4;bonus++){
            for(int rank=0;rank<6;rank++){
                var wallet=new CampaignProgress.State(Battle.Faction.TERRAN,
                        401,100000000L,400,rank,400,bonus,CampaignProgress.Project.NONE,0);
                var started=wallet.beginResearch(CampaignProgress.Project.LICENSE);
                require(started.researchRank()==rank&&started.researchActive(),
                        "Technology unlocked without mission at rank "+rank+" boost "+bonus);
                require(started.missionsUntilResearchComplete()>=1&&
                        started.researchPerVictory()==1,"Victory research-clock invariant failed");
                int turns=0;
                while(started.researchActive()){
                    started=started.complete(1,20,20);
                    require(++turns<=5,"Research took more than five missions");
                }
                require(started.researchRank()==rank+1,"Research result missing");
            }
        }
        for(int bonus=0;bonus<4;bonus++){
            var wallet=new CampaignProgress.State(Battle.Faction.TERRAN,
                    401,100000000L,400,0,400,bonus,CampaignProgress.Project.NONE,0);
            var started=wallet.beginResearch(CampaignProgress.Project.ACCELERATOR);
            require(started.speedUpgrades()==bonus&&started.researchActive(),
                    "Accelerator unlocked without victory");
            int turns=0;
            while(started.researchActive()){
                started=started.complete(1,20,20);
                require(++turns<=5,"Accelerator research time unbounded");
            }
            require(started.speedUpgrades()==bonus+1,"Completed lab upgrade missing");
        }
        System.out.println("PASS: 30 technology + 4 lab projects never unlock instantly");
    }
}
