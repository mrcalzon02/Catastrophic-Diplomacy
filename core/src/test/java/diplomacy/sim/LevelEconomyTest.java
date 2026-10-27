package diplomacy.sim;

public final class LevelEconomyTest {
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public static void main(String[] args) {
        var first=LevelEconomy.level(1);
        check(first.sector()==1&&first.inSector()==1&&first.maximumFleet()==20,"first level specification");
        check(first.baseCredits()==10000,"initial example base credits");
        check(LevelEconomy.level(50).inSector()==50,"sector boundary");
        check(LevelEconomy.level(51).sector()==2&&LevelEconomy.level(51).inSector()==1,"next sector");
        check(LevelEconomy.level(500).sector()==10&&LevelEconomy.level(500).inSector()==50,"last level");
        check(LevelEconomy.victoryCredits(first,20,20,true)==10000,"full fleet perfect victory");
        check(LevelEconomy.victoryCredits(first,19,19,true)==11000,"one missing +0.1");
        check(LevelEconomy.victoryCredits(first,16,16,true)==14000,"four missing +0.4");
        check(LevelEconomy.victoryCredits(first,16,12,true)==10500,"survival fraction before multiplier");
        check(LevelEconomy.victoryCredits(first,10,10,true)==20000,"ten missing 2.0x");
        check(LevelEconomy.victoryCredits(first,1,1,true)==29000,"one-ship win 2.9x");
        check(LevelEconomy.victoryCredits(first,1,0,true)==0,"lost one ship means no survivors");
        check(LevelEconomy.victoryCredits(first,10,10,false)==0,"defeat cannot farm credits");
        check(LevelEconomy.victoryCredits(first,16,16,true)==14000,"full reward on replay");
        for(int stage=1;stage<=6;stage++)
            check(LevelEconomy.researchMissionCost(stage)==Math.min(5,stage),
                    "Research mission durations must range from one to five");
        for(int level=1;level<=4;level++){
            check(LevelEconomy.researchSpeedCost(level)>0,"Research acceleration cannot be free");
            check(LevelEconomy.researchSpeedMilestone(level)>0,"Accelerator must have milestone");
        }
        for(int stage=1;stage<=6;stage++)
            check(LevelEconomy.draftResearchCost(stage)>0,"research pricing stage "+stage);
        boolean rejected=false;
        try{LevelEconomy.victoryCredits(first,21,20,true);}
        catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"reject oversized deployment");
        check(LevelEconomy.level(1).seed()==first.seed(),"deterministic level seed");
        System.out.println("PASS 500 level definitions and exact survivor/underdog credit economics");
    }
}
