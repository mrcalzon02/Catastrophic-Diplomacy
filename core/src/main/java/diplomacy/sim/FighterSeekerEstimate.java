package diplomacy.sim;

/**
 * One finite, deterministic onboard seeker measurement from an ALREADY
 * arrived photon/echo. The head's spectrum, field of view and echo strength
 * determine resolution. It cannot inspect a live hostile craft or decoy.
 *
 * A marginal coordinate error can still strike a real moving fighter:
 * collision remains swept physical geometry, never a hit-probability roll.
 */
public final class FighterSeekerEstimate {
    private FighterSeekerEstimate(){}

    public record Fix(double x,double y,double vx,double vy,
                      double uncertaintyRadius){
        public Fix{
            if(!Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(vx)||!Double.isFinite(vy)||
                    !Double.isFinite(uncertaintyRadius)||uncertaintyRadius<=0)
                throw new IllegalArgumentException("Invalid measured fighter fix");
        }
    }
    private static long mix(long value){
        value^=value>>>33;value*=0xff51afd7ed558ccdL;
        value^=value>>>33;value*=0xc4ceb9fe1a85ec53L;
        return value^(value>>>33);
    }
    private static double unit(long value){
        return (value>>>11)*0x1.0p-53;
    }

    /** Exactly one installed head; no hybrid/remote hardware granted here.
     * A stale observation only increases uncertainty, never changes its
     * historical observed coordinate or derives a fresh one from the craft. */
    public static Fix from(FighterInfraredSeeker.Reading reading,
                           long missileId,int targetCraftId,
                           double guidanceQuality,
                           FighterInfraredSeeker.Head infrared,
                           FighterRadarSeeker.Head radar,
                           double receiverX,double receiverY){
        if(reading==null||missileId<=0||targetCraftId<=0||
                (infrared==null)==(radar==null)||
                !Double.isFinite(guidanceQuality)||guidanceQuality<=0||
                !Double.isFinite(receiverX)||!Double.isFinite(receiverY))
            throw new IllegalArgumentException("Invalid onboard seeker measurement");

        double distance=StrictMath.hypot(reading.x()-receiverX,
                                         reading.y()-receiverY);
        double age=(reading.receivedTick()-reading.emittedTick())*Battle.DT;
        // Narrow radar discriminates angular direction best; wide-area radar
        // trades range and measurement precision for easier acquisition.
        // IR retains its own, generally noisier, thermal contrast response.
        double angular=radar!=null
                ?(.85+radar.halfAngleRadians)
                :(1.15+.75*infrared.halfAngleRadians);
        double signal=StrictMath.max(.15,
                StrictMath.min(4.0,reading.strength()));
        double quality=StrictMath.max(.5,
                StrictMath.min(2.5,guidanceQuality));
        double sigma=(.7+.014*distance+1.2*age)*angular/
                StrictMath.sqrt(signal*quality);
        sigma=StrictMath.max(.4,StrictMath.min(50,sigma));

        // No battle RNG draws: six related photon frames share a bounded
        // integration window. Each independently launched missile differs.
        long seed=mix(missileId*0x9e3779b97f4a7c15L ^
                (long)targetCraftId*0xbf58476d1ce4e5b9L ^
                (reading.emittedTick()/6L)*0x94d049bb133111ebL ^
                reading.flareId()*0x632be59bd9b4e019L ^
                (radar==null?0x2545f4914f6cdd1dL:0x7cc4f3a74f33df61L));
        double angle=2*StrictMath.PI*unit(seed);
        double radial=sigma*StrictMath.sqrt(
                unit(mix(seed^0x4f1bbcdc676fc3d3L)));
        double velocityAngle=2*StrictMath.PI*
                unit(mix(seed^0x71d67fffeda6009bL));
        double velocityError=StrictMath.min(2,sigma*.04);
        return new Fix(reading.x()+radial*StrictMath.cos(angle),
                reading.y()+radial*StrictMath.sin(angle),
                reading.vx()+velocityError*StrictMath.cos(velocityAngle),
                reading.vy()+velocityError*StrictMath.sin(velocityAngle),
                sigma);
    }
}
