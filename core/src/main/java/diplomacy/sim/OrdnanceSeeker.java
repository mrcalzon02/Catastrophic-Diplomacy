package diplomacy.sim;

/** Deterministic per-body seeker range and scan cadence; never retargets. */
public final class OrdnanceSeeker {
    private OrdnanceSeeker() {}

    /** A target's radar cross section remains nonzero under ECM.
     * Jamming reduces detection reach, not missile speed or PD visibility.
     * Reacquisition requires a tighter solution than maintaining an existing lock. */
    public static boolean canTrack(double distance,double crossSection,
                                   double ecm,double guidanceQuality,
                                   boolean torpedo,boolean reacquiring) {
        if(!Double.isFinite(distance)||distance<0
                ||!Double.isFinite(crossSection)||crossSection<=0
                ||!Double.isFinite(ecm)||ecm<0
                ||!Double.isFinite(guidanceQuality)||guidanceQuality<=0)
            throw new IllegalArgumentException("Invalid seeker detection state");
        double area=StrictMath.max(.5,StrictMath.min(1.6,StrictMath.sqrt(crossSection)));
        double quality=StrictMath.max(.5,StrictMath.min(2.0,guidanceQuality));
        double interference=StrictMath.min(1,ecm);
        double reach=(torpedo?1300:1400)*area*quality*(1-.8*interference);
        return distance<=reach*(reacquiring?.8:1);
    }

    /** Stable staggered scans avoid a per-frame reacquisition oracle. */
    public static boolean scanDue(int flightTicks,long visualId) {
        if(flightTicks<0||visualId<0)
            throw new IllegalArgumentException("Invalid seeker scan clock");
        return (flightTicks+visualId%6)%6==0;
    }
}
