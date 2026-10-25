package diplomacy.sim;

import java.math.BigInteger;

/**
 * Deterministic Level System I definitions and survivor/underdog rewards.
 * Wave spawning, player bank and research purchase state remain separate tasks.
 */
public final class LevelEconomy {
    public static final int LEVEL_COUNT=500;
    public static final int SECTOR_SIZE=50;
    private LevelEconomy() { }
    public record Level(int index,int sector,int inSector,int maximumFleet,
                        int waves,long baseCredits,long seed) { }
    public static Level level(int index) {
        if(index<1 || index>LEVEL_COUNT)throw new IllegalArgumentException("Level out of range");
        int sector=(index-1)/SECTOR_SIZE+1;
        int count=20+(index-1)/3;
        int fleetCap=StrictMath.min(200,count);
        int waves=3+(index-1)/50;
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
    /** Fixed six-stage research ladder pending technology catalogue purchase binding. */
    public static long draftResearchCost(int stage) {
        return switch(stage) {
            case 1 -> 25000L;
            case 2 -> 75000L;
            case 3 -> 250000L;
            case 4 -> 900000L;
            case 5 -> 3500000L;
            case 6 -> 15000000L;
            default -> throw new IllegalArgumentException("Research stage must be 1 through 6");
        };
    }
}
