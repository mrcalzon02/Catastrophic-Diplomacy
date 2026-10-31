package diplomacy.sim;

public final class LevelEconomyTest {
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public static void main(String[] args) {
        var first=LevelEconomy.level(1);
        check(first.sector()==1&&first.inSector()==1&&first.maximumFleet()==1,"first level specification");
        check(first.baseCredits()==10000,"initial example base credits");
        check(LevelEconomy.level(50).inSector()==50,"sector boundary");
        check(LevelEconomy.level(51).sector()==2&&LevelEconomy.level(51).inSector()==1,"next sector");
        check(LevelEconomy.level(500).sector()==10&&LevelEconomy.level(500).inSector()==50,"last level");
        check(LevelEconomy.level(1).waves()==3&&LevelEconomy.level(493).waves()==12,
                "Pre-finale reinforcements changed");
        int previous=0;
        for(int mission=1;mission<=500;mission++){
            int current=LevelEconomy.level(mission).maximumFleet();
            check(current>=previous&&current<=200,"Nonmonotonic fleet cap "+mission);
            previous=current;
            if(mission>=494)
                check(current==200&&LevelEconomy.level(mission).waves()==1,
                        "Final seven must each be a single 200-ship mass battle");
        }
        check(LevelEconomy.victoryCredits(first,1,1,true)==first.baseCredits(),
                "First solo mission should not create an underdog multiplier");
        check(LevelEconomy.victoryCredits(10000,20,20,20,true)==10000,"full fleet perfect victory");
        check(LevelEconomy.victoryCredits(10000,20,19,19,true)==11000,"one missing +0.1");
        check(LevelEconomy.victoryCredits(10000,20,16,16,true)==14000,"four missing +0.4");
        check(LevelEconomy.victoryCredits(10000,20,16,12,true)==10500,"survival fraction before multiplier");
        check(LevelEconomy.victoryCredits(10000,20,10,10,true)==20000,"ten missing 2.0x");
        check(LevelEconomy.victoryCredits(10000,20,1,1,true)==29000,"one-ship win 2.9x");
        check(LevelEconomy.victoryCredits(10000,20,1,0,true)==0,"lost one ship means no survivors");
        check(LevelEconomy.victoryCredits(10000,20,10,10,false)==0,"defeat cannot farm credits");
        check(LevelEconomy.victoryCredits(Long.MAX_VALUE,1,1,1,true)==Long.MAX_VALUE,
                "max signed 64-bit reward must remain exact");
        boolean rewardOverflow=false;
        try{LevelEconomy.victoryCredits(Long.MAX_VALUE,2,1,1,true);}
        catch(ArithmeticException expected){rewardOverflow=true;}
        check(rewardOverflow,"reward overflow must not silently wrap credits");
        check(LevelEconomy.victoryCredits(10000,20,16,16,true)==14000,
                "Raw first-clear formula is unchanged; replay reduction is applied by CampaignProgress");
        for(int stage=1;stage<=9;stage++)
            check(LevelEconomy.researchMissionCost(stage)==Math.min(5,stage),
                    "Research mission durations must range from one to five");
        for(int level=1;level<=4;level++){
            check(LevelEconomy.researchSpeedCost(level)>0,"Research acceleration cannot be free");
            check(LevelEconomy.researchSpeedMilestone(level)>0,"Accelerator must have milestone");
        }
        for(int stage=1;stage<=9;stage++)
            check(LevelEconomy.draftResearchCost(stage)>0,"research pricing stage "+stage);
        check(LevelEconomy.researchLicenseMilestone(6)==0,"Existing licenses unexpectedly milestone-gated");
        check(LevelEconomy.researchLicenseMilestone(7)==250,"Tech VII should follow Level 250");
        check(LevelEconomy.researchLicenseMilestone(8)==350,"Tech VIII should follow Level 350");
        check(LevelEconomy.researchLicenseMilestone(9)==425,"Tech IX should follow Level 425");
        boolean rejected=false;
        try{LevelEconomy.victoryCredits(10000,20,21,20,true);}
        catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"reject oversized deployment");
        check(LevelEconomy.level(1).seed()==first.seed(),"deterministic level seed");
        System.out.println("PASS 500 level definitions and exact survivor/underdog credit economics");
    }
}
