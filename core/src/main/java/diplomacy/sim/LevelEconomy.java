package diplomacy.sim;

import java.math.BigInteger;

/**
 * Deterministic Level System I definitions and survivor/underdog rewards.
 * Wave spawning, player bank and research purchase state remain separate tasks.
 */
public final class LevelEconomy {
    public static final int LEVEL_COUNT=500;
    public static final int SECTOR_SIZE=50;
    public static final int FINALE_START=494;
    // Inclusive fleet-cap milestones. The final seven sorties are true
    // 200-on-200 deployments, not hundreds of waves against a token escort.
    private static final int[][] FLEET_CURVE={
            {1,1},{10,2},{25,4},{50,8},{75,12},{100,18},
            {125,26},{150,36},{175,47},{200,60},{225,74},
            {250,88},{275,102},{300,116},{325,129},
            {350,142},{375,153},{400,164},{425,175},
            {450,184},{475,193},{490,198},{493,199},
            {494,200},{500,200}
    };
    private LevelEconomy() { }
    /** Monotonic mission-specific cap, independent of the selected deployment. */
    public static int fleetCap(int mission){
        if(mission<1||mission>LEVEL_COUNT)
            throw new IllegalArgumentException("Level out of range");
        for(int i=1;i<FLEET_CURVE.length;i++){
            int[] previous=FLEET_CURVE[i-1],next=FLEET_CURVE[i];
            if(mission>next[0])continue;
            int span=next[0]-previous[0],offset=mission-previous[0];
            return previous[1]+(int)(((long)offset*(next[1]-previous[1])+span/2)/span);
        }
        return 200;
    }
    public record Level(int index,int sector,int inSector,int maximumFleet,
                        int waves,long baseCredits,long seed) { }
    public static Level level(int index) {
        if(index<1 || index>LEVEL_COUNT)throw new IllegalArgumentException("Level out of range");
        int sector=(index-1)/SECTOR_SIZE+1;
        int fleetCap=fleetCap(index);
        // The last seven missions commit both navies at full strength at once.
        // Keep earlier sector reinforcement waves unchanged.
        int waves=index>=FINALE_START?1:3+(index-1)/50;
        long n=index-1;
        long base=10000L+1200L*n+6L*n*n;
        long seed=0x43442d4c4556454cL ^ (index*0x9e3779b97f4a7c15L);
        return new Level(index,sector,(index-1)%SECTOR_SIZE+1,fleetCap,waves,base,seed);
    }
    /**
     * floor(B * (survivors/deployed) * (1+0.1*(cap-deployed))).
     * The integer rational form avoids platform floating-point rounding drift.
     * An incomplete or lost battle always pays zero.
     */
    public static long victoryCredits(long base,int maximumFleet,
                                      int deployed,int survivors,boolean completed) {
        if(base<0||maximumFleet<1||deployed<1||deployed>maximumFleet||
                survivors<0||survivors>deployed)
            throw new IllegalArgumentException("Invalid level reward inputs");
        if(!completed)return 0;
        BigInteger numerator=BigInteger.valueOf(base)
                .multiply(BigInteger.valueOf(survivors))
                .multiply(BigInteger.valueOf(10L+maximumFleet-deployed));
        BigInteger denominator=BigInteger.valueOf(deployed).multiply(BigInteger.TEN);
        return numerator.divide(denominator).longValueExact();
    }
    public static long victoryCredits(Level level,int deployed,int survivors,boolean completed) {
        if(level==null)throw new IllegalArgumentException("Level required");
        return victoryCredits(level.baseCredits(),level.maximumFleet(),deployed,survivors,completed);
    }
    /** Mission turns required for each of the nine research generations (1..5). */
    public static int researchMissionCost(int rank){
        if(rank<1||rank>9)throw new IllegalArgumentException("Research rank must be 1..9");
        return Math.min(5,rank);
    }
    /** Hold the three late-game engineering licenses for sectors 5, 7 and 9. */
    public static int researchLicenseMilestone(int rank) {
        if(rank<1||rank>9)throw new IllegalArgumentException("Technology tier must be 1..9");
        return switch(rank) {
            case 7 -> 250;
            case 8 -> 350;
            case 9 -> 425;
            default -> 0; // Existing I-VI campaign authorization remains intact.
        };
    }
    /** Four optional, expensive acceleration upgrades with completed-stage prerequisites. */
    public static int researchSpeedMilestone(int level){
        return switch(level){
            case 1 -> 25;
            case 2 -> 100;
            case 3 -> 225;
            case 4 -> 400;
            default -> throw new IllegalArgumentException("Research accelerator must be 1..4");
        };
    }
    public static long researchSpeedCost(int level){
        return switch(level){
            case 1 -> 80000L;
            case 2 -> 500000L;
            case 3 -> 2800000L;
            case 4 -> 12000000L;
            default -> throw new IllegalArgumentException("Research accelerator must be 1..4");
        };
    }
    /** Nine-stage research ladder pending technology catalogue purchase binding. */
    public static long draftResearchCost(int stage) {
        return switch(stage) {
            case 1 -> 25000L;
            case 2 -> 75000L;
            case 3 -> 250000L;
            case 4 -> 900000L;
            case 5 -> 3500000L;
            case 6 -> 15000000L;
            case 7 -> 28000000L;
            case 8 -> 45000000L;
            case 9 -> 70000000L;
            default -> throw new IllegalArgumentException("Research stage must be 1 through 9");
        };
    }
}
