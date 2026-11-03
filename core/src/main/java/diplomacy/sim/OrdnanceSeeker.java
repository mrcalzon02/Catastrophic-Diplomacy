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

    /** Photon-limited passive seeker return from the original assigned hull.
     * Missile location is its local known receiver pose; target location,
     * velocity, RCS and ECM are from the SAME historical emission tick.
     * This returns a measured fix, not a guaranteed lock or omniscient target.
     * The moving-receiver's historical light cone will be refined separately. */
    public record Return(long measuredTick,long receivedTick,double distance,
                         SensorLightTimeHistory.Sample sample,
                         SensorLightTimeHistory.Profile profile){
        public Return {
            if(measuredTick<0||receivedTick<measuredTick||distance<0||
                    sample==null||profile==null||
                    sample.tick()!=measuredTick||profile.tick()!=measuredTick)
                throw new IllegalArgumentException("Invalid delayed seeker return");
        }
    }
    public static Return passiveReturn(Battle.Ship target,double receiverX,
                                       double receiverY,long now){
        if(target==null||now<0||!Double.isFinite(receiverX)||
                !Double.isFinite(receiverY))
            throw new IllegalArgumentException("Invalid seeker receiver");
        int horizon=StrictMath.min(SensorLightTimeHistory.CAPACITY-1,
                SensorLightTimeHistory.propagationTicks(
                        WorldScale.DIAGONAL_WORLD_UNITS)+2);
        for(int age=0;age<=horizon&&age<=now;age++){
            long reflectedTick=now-age;
            SensorLightTimeHistory.Sample measured=
                    target.sensorHistory.at(reflectedTick);
            SensorLightTimeHistory.Profile profile=
                    target.sensorHistory.profileAt(reflectedTick);
            if(measured==null||profile==null||!measured.alive())continue;
            double range=StrictMath.hypot(measured.x()-receiverX,
                    measured.y()-receiverY);
            if(SensorLightTimeHistory.propagationTicks(range)==age)
                return new Return(reflectedTick,now,range,measured,profile);
        }
        return null;
    }
    /** Historical ECM and radar area affect the acquisition solution.
     * No target object or live enemy state is consulted. */
    public static boolean canTrackReturn(Return observed,double guidanceQuality,
                                         boolean torpedo,boolean reacquiring){
        if(observed==null)return false;
        SensorLightTimeHistory.Profile p=observed.profile();
        double interference=StrictMath.min(.95,
                p.ecmStrength()*p.ecmCondition()+
                (p.power()>4&&p.ecmCondition()>.15?
                        .45*StrictMath.max(0,p.hologramNoise()):0));
        return canTrack(observed.distance(),p.radarCrossSection(),
                interference,guidanceQuality,torpedo,reacquiring);
    }

    /** Stable staggered scans avoid a per-frame reacquisition oracle. */
    public static boolean scanDue(int flightTicks,long visualId) {
        if(flightTicks<0||visualId<0)
            throw new IllegalArgumentException("Invalid seeker scan clock");
        return (flightTicks+visualId%6)%6==0;
    }
}
