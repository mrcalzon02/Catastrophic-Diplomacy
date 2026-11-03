package diplomacy.sim;

/** Bounded immutable physical target history; no future-time truth in telemetry. */
public final class SensorLightTimeHistory {
    public static final int CAPACITY=128;
    private final Sample[] samples=new Sample[CAPACITY];
    /** Physical/EM sensor signatures and actual installed receiver capabilities
     * at the same tick as each motion sample. Never substituted with current
     * target truth after an enemy changes cloak, heat or hull profile. */
    private final Profile[] profiles=new Profile[CAPACITY];
    public record Profile(long tick,double radarCrossSection,
            double thermalSignature,double opticalSignature,
            double maxHull,double maxArmor,double power,double ecmCondition,
            double coolingCondition,double sensorRange,double infraredRange,
            double xrayRange,double graviticRange,double sensorsCondition,
            double crewReadiness,double sensorAcuity,double resolutionGain,
            double commandAssist,SensorActiveScan.Mode scanMode,int focusId) { }
    public Profile profileAt(long tick){
        if(tick<0)return null;
        Profile profile=profiles[(int)(tick%CAPACITY)];
        return profile!=null&&profile.tick()==tick?profile:null;
    }
    public record Sample(long tick,double x,double y,double vx,double vy,
                         boolean alive,boolean sensorsPowered){
        /** Compatibility for deterministic geometry-only sample fixtures. */
        public Sample(long tick,double x,double y,double vx,double vy,
                      boolean alive){
            this(tick,x,y,vx,vy,alive,true);
        }
    }
    public record Observation(long emittedTick,long measuredTick,long receivedTick,
                              int channels,Sample sample){
        public Observation{
            if(emittedTick<0||emittedTick>measuredTick||
                    measuredTick>receivedTick||channels==0||sample==null||
                    sample.tick()!=measuredTick)
                throw new IllegalArgumentException("Impossible sensor chronology");
        }
    }
    public void capture(long tick,Battle.Ship ship){
        if(tick<0||ship==null)throw new IllegalArgumentException("Invalid sample");
        boolean activeReady=ship.alive()&&ship.power>3&&
                ship.sensorsCondition>.05&&ship.crewReadiness()>=.05;
        int slot=(int)(tick%CAPACITY);
        samples[slot]=new Sample(tick,ship.x,ship.y,ship.vx,
                ship.vy,ship.alive(),activeReady);
        profiles[slot]=new Profile(tick,ship.radarCrossSection,
                ship.thermalSignature,ship.opticalSignature,ship.maxHull,
                ship.maxArmor,ship.power,ship.ecmCondition,ship.coolingCondition,
                ship.sensorRange,ship.infraredRange,ship.xrayRange,
                ship.graviticRange,ship.sensorsCondition,ship.crewReadiness(),
                ship.sensorAcuity,ship.network.resolutionGain(),
                ship.commandAssist,ship.scan.mode(),ship.scan.focusTargetId());
    }
    public Sample at(long tick){
        if(tick<0)return null;
        Sample found=samples[(int)(tick%CAPACITY)];
        return found!=null&&found.tick()==tick?found:null;
    }
    public static int propagationTicks(double distance){
        return (int)StrictMath.ceil(WorldScale.signalDelayTicks(distance,false)-1e-10);
    }
    /** Each observation requires a specific historical photon arrival:
     * passive sensors receive one-way radiation; radar/X-ray require a paid
     * or STANDARD powered outbound emission and its reflected return.
     * The search is bounded by the one-light-minute arena diagonal.
     * A relay hop is additive; hardware signature eligibility is filtered
     * against the historical profile by the caller. */
    public static Observation receive(Battle.Ship source,Battle.Ship target,
            long receivedTick,int hardwareChannels,int linkTicks){
        if(source==null||target==null||receivedTick<0||linkTicks<0)
            throw new IllegalArgumentException("Invalid sensor route");
        if(hardwareChannels==0)return null;
        // The receiver knows its own physical position at reception; use the
        // timestamped sensor pose, never the distant hostile's current pose.
        long sensorTick=receivedTick-linkTicks;
        if(sensorTick<0)return null;
        Sample sensorState=source.sensorHistory.at(sensorTick);
        if(sensorState==null&&sensorTick>0)
            sensorState=source.sensorHistory.at(sensorTick-1);
        if(sensorState==null)return null;
        // Select samples by the time photons actually ARRIVE, rather than
        // seeding a delay with the target's latest hidden state. A sudden
        // maneuver cannot erase a radar echo already on its return flight.
        // The arena diagonal spans at most 30 ticks one-way; bounded scan.
        int passive=hardwareChannels&(Battle.CONTACT_OPTICAL|
                Battle.CONTACT_INFRARED|Battle.CONTACT_GRAVITIC);
        int active=hardwareChannels&(Battle.CONTACT_RADAR|Battle.CONTACT_XRAY);
        Sample passiveFix=null,activeFix=null;
        long activeEmission=-1;
        int oneWayLimit=StrictMath.min(CAPACITY-1,
                propagationTicks(WorldScale.DIAGONAL_WORLD_UNITS)+2);
        for(int age=0;age<=oneWayLimit&&age<=sensorTick;age++){
            long reflectedTick=sensorTick-age;
            Sample reflected=target.sensorHistory.at(reflectedTick);
            if(reflected==null||!reflected.alive())continue;
            int returnTicks=propagationTicks(StrictMath.hypot(
                    reflected.x()-sensorState.x(),
                    reflected.y()-sensorState.y()));
            // Each return is a specific immutable photon arrival, not a
            // continuously available old radar fix until it is overwritten.
            if(age!=returnTicks)continue;
            if(passive!=0&&passiveFix==null)passiveFix=reflected;
            if(active!=0&&activeFix==null){
                long emittedTick=reflectedTick-returnTicks;
                Sample sourceEmission=source.sensorHistory.at(emittedTick);
                if(sourceEmission!=null&&sourceEmission.sensorsPowered()&&
                        source.scan.emittedFor(emittedTick,target.id,active)){
                    activeFix=reflected;
                    activeEmission=emittedTick;
                }
            }
            if(passiveFix!=null&&(active==0||activeFix!=null))break;
        }
        // Active echo has an independently verified outbound emission.
        // Preserve passive corroboration only from the SAME measured sample:
        // conflating separate photon epochs would fabricate track precision.
        if(activeFix!=null){
            int channels=active;
            if(passiveFix!=null&&passiveFix.tick()==activeFix.tick())
                channels|=passive;
            return new Observation(activeEmission,activeFix.tick(),
                    receivedTick,channels,activeFix);
        }
        if(passiveFix==null)return null;
        return new Observation(passiveFix.tick(),passiveFix.tick(),
                receivedTick,passive,passiveFix);
    }
}
