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
        double d=StrictMath.hypot(target.x-source.x,target.y-source.y);
        int outward=propagationTicks(d),roundtrip=propagationTicks(2*d);
        int passive=hardwareChannels&(Battle.CONTACT_OPTICAL|
                Battle.CONTACT_INFRARED|Battle.CONTACT_GRAVITIC);
        int active=hardwareChannels&(Battle.CONTACT_RADAR|Battle.CONTACT_XRAY);
        if(receivedTick<(long)outward+linkTicks)passive=0;
        if(receivedTick<(long)roundtrip+linkTicks)active=0;
        int arrived=passive|active;
        if(arrived==0)return null;
        long measured=receivedTick-outward-linkTicks;
        Sample sample=target.sensorHistory.at(measured);
        if(sample==null||!sample.alive())return null;
        long emitted=active!=0?receivedTick-roundtrip-linkTicks:measured;
        return new Observation(emitted,measured,receivedTick,arrived,sample);
    }
}
