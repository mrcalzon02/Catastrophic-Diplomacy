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

    /**
     * Finite per-body seeker error. This is an ESTIMATE from an already arrived
     * photon sample, never a modification of target history or physical hits.
     * The sensor-noise seed is stable over six measured ticks, modeling a
     * bounded integration/dwell window without using the battle RNG stream.
     */
    public record Fix(double x,double y,double vx,double vy,
                      double uncertaintyRadius,long measuredTick) {
        public Fix {
            if(!Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(vx)||!Double.isFinite(vy)||
                    !Double.isFinite(uncertaintyRadius)||uncertaintyRadius<0||
                    measuredTick<0)
                throw new IllegalArgumentException("Invalid seeker estimate");
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
    public static Fix estimate(Return observed,int emitterId,long seekerId,
                               double guidanceQuality,int phantom,double hullScale){
        if(observed==null||emitterId<=0||seekerId<=0||phantom< -1||
                !Double.isFinite(guidanceQuality)||guidanceQuality<=0||
                !Double.isFinite(hullScale)||hullScale<=0)
            throw new IllegalArgumentException("Invalid photon seeker estimate");
        if(phantom>=DeceptionProjection.falseContacts(observed.profile()))
            throw new IllegalArgumentException("Phantom was not emitted at this tick");
        SensorLightTimeHistory.Sample s=observed.sample();
        double x=phantom>=0?DeceptionProjection.ghostX(
                emitterId,s,hullScale,phantom):s.x();
        double y=phantom>=0?DeceptionProjection.ghostY(
                emitterId,s,hullScale,phantom):s.y();
        SensorLightTimeHistory.Profile profile=observed.profile();
        double interference=StrictMath.min(.95,StrictMath.max(0,
                profile.ecmStrength()*profile.ecmCondition()+
                (profile.power()>4&&profile.ecmCondition()>.15?
                        .45*StrictMath.max(0,profile.hologramNoise()):0)));
        double quality=StrictMath.max(.45,StrictMath.min(2.5,guidanceQuality));
        double area=StrictMath.max(.5,StrictMath.min(1.6,
                StrictMath.sqrt(StrictMath.max(.001,profile.radarCrossSection()))));
        double age=(observed.receivedTick()-observed.measuredTick())*Battle.DT;
        double measuredSpeed=StrictMath.hypot(s.vx(),s.vy());
        double sigma=(1.25+observed.distance()*.005+
                age*(.8+measuredSpeed*.045))*
                (1+1.7*interference)/(quality*StrictMath.sqrt(area));
        sigma=StrictMath.max(.25,StrictMath.min(90,sigma));
        // Independent stable per-seeker noise, not a universal accuracy roll.
        // A marginally wrong target solution can still hit a real hull.
        long seed=mix(seekerId*0x9e3779b97f4a7c15L ^
                (long)emitterId*0xbf58476d1ce4e5b9L ^
                (s.tick()/6L)*0x94d049bb133111ebL);
        double angle=2*StrictMath.PI*unit(seed);
        double radial=sigma*StrictMath.sqrt(unit(mix(seed^0x632be59bd9b4e019L)));
        double velocityAngle=2*StrictMath.PI*unit(mix(seed^0x2545f4914f6cdd1dL));
        double velocityError=StrictMath.min(2.5,sigma*.028);
        return new Fix(x+radial*StrictMath.cos(angle),
                y+radial*StrictMath.sin(angle),
                s.vx()+velocityError*StrictMath.cos(velocityAngle),
                s.vy()+velocityError*StrictMath.sin(velocityAngle),
                sigma,s.tick());
    }

    /** Stable staggered scans avoid a per-frame reacquisition oracle. */
    public static boolean scanDue(int flightTicks,long visualId) {
        if(flightTicks<0||visualId<0)
            throw new IllegalArgumentException("Invalid seeker scan clock");
        return (flightTicks+visualId%6)%6==0;
    }
}
