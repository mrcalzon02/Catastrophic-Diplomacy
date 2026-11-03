package diplomacy.sim;

/** Bounded immutable physical target history; no future-time truth in telemetry. */
public final class SensorLightTimeHistory {
    public static final int CAPACITY=128;
    private final Sample[] samples=new Sample[CAPACITY];
    public record Sample(long tick,double x,double y,double vx,double vy,
                         boolean alive){}
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
        samples[(int)(tick%CAPACITY)]=new Sample(tick,ship.x,ship.y,ship.vx,
                ship.vy,ship.alive());
    }
    public Sample at(long tick){
        if(tick<0)return null;
        Sample found=samples[(int)(tick%CAPACITY)];
        return found!=null&&found.tick()==tick?found:null;
    }
    public static int propagationTicks(double distance){
        return (int)StrictMath.ceil(WorldScale.signalDelayTicks(distance,false)-1e-10);
    }
    /** Active channels require a round-trip echo before the reflected
     * target state can be received. A relay adds an extra one-way hop.
     * This does NOT yet authorize a fire-control lock or spend scan energy. */
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
        Sample lastRecorded=target.sensorHistory.at(sensorTick);
        if(lastRecorded==null&&sensorTick>0)
            lastRecorded=target.sensorHistory.at(sensorTick-1);
        if(lastRecorded==null)return null;
        // A bounded conservative light-cone solver. The latest available
        // candidate is ONLY a seed for travel time: the actual measurement
        // is always from an arrived, older historical sample. If a moving
        // target had been farther away, increase age rather than acquiring
        // it too early. Never read target.x/y/vx/vy here.
        int outward=propagationTicks(StrictMath.hypot(
                lastRecorded.x()-sensorState.x(),
                lastRecorded.y()-sensorState.y()));
        for(int pass=0;pass<4;pass++){
            long candidateTick=sensorTick-outward;
            Sample candidate=target.sensorHistory.at(candidateTick);
            if(candidate==null)return null;
            int required=propagationTicks(StrictMath.hypot(
                    candidate.x()-sensorState.x(),
                    candidate.y()-sensorState.y()));
            if(required<=outward)break;
            outward=required;
        }
        long measured=sensorTick-outward;
        Sample sample=target.sensorHistory.at(measured);
        if(sample==null||!sample.alive())return null;
        // Quantized outward/return photons each require their own full
        // causal interval; a rounded 2*d can be a tick too early.
        int passive=hardwareChannels&(Battle.CONTACT_OPTICAL|
                Battle.CONTACT_INFRARED|Battle.CONTACT_GRAVITIC);
        int active=hardwareChannels&(Battle.CONTACT_RADAR|Battle.CONTACT_XRAY);
        if(sensorTick<(long)outward*2)active=0;
        int arrived=passive|active;
        if(arrived==0)return null;
        long emitted=active!=0?measured-outward:measured;
        return new Observation(emitted,measured,receivedTick,arrived,sample);
    }
}
