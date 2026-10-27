package diplomacy.sim;

/** Permanent commissioned hull identity. 16 decimal digits, within signed
 * Java long, independent of ship name, hull class and tactical battle ship ID.
 * 8-digit campaign prefix + 8-digit never-recycled commission sequence.
 */
public final class VesselIdentifiers {
    public static final long MIN=1_000_000_000_000_000L;
    public static final long MAX=9_999_999_999_999_999L;
    public static final int MAX_SERIAL=99_999_999;
    private static final long SEQUENCE_BASE=100_000_000L;
    private VesselIdentifiers(){}
    public static boolean valid(long id){return id>=MIN&&id<=MAX;}
    public static long prefix(long seed){
        long v=seed+0x9e3779b97f4a7c15L;
        v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;
        v=(v^(v>>>27))*0x94d049bb133111ebL;
        v^=v>>>31;
        return 10_000_000L+Math.floorMod(v,90_000_000L);
    }
    public static long commissioned(long seed,int serial){
        if(serial<1||serial>MAX_SERIAL)
            throw new IllegalArgumentException("Exhausted unique 16-digit vessel IDs");
        return prefix(seed)*SEQUENCE_BASE+serial;
    }
    public static int serial(long id){
        if(!valid(id))throw new IllegalArgumentException("Invalid vessel ID");
        return (int)(id%SEQUENCE_BASE);
    }
}
