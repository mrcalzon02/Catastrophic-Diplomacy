package diplomacy.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Deterministic space-weather controller, independent of rendering and weapon RNG.
 * Asteroids are timed off-plane impacts, never collidable map entities.
 * All times use simulation ticks at 30 Hz.
 */
public final class SpaceWeather {
    public enum Kind { CLEAR, ASTEROIDS, NEBULA, SOLAR_FLARE, NEUTRON_SWEEP, RADIATION, ION_STORM }
    public enum Damage { KINETIC, PARTICLE, PLASMA }

    public interface Target {
        int weatherId();
        double weatherX();
        double weatherY();
        boolean alive();
    }
    public record Preset(int level, long seed, Kind primary, Kind secondary, double severity) {
        public Preset {
            if (primary == null || secondary == null || !Double.isFinite(severity)
                    || severity < 0 || severity > 2) throw new IllegalArgumentException("Invalid weather preset");
        }
        public boolean includes(Kind type) { return primary == type || secondary == type; }
        public boolean enabled() { return primary != Kind.CLEAR || secondary != Kind.CLEAR; }
    }
    /** Visible asteroid: rise/fade toward one point, then hit a designated ship or empty space. */
    public record Meteor(double x, double y, int targetId, long arrivalTick,
                         long strikeTick, long expiryTick, double radius,
                         boolean resolved, boolean hit) { }
    public record Impact(int targetId, double damage, Damage type, double fromX, double fromY) { }

    private static final long SALT = 0x5745415448455221L;
    public static final double CENTER_X = 640, CENTER_Y = 335;
    private final Preset preset;
    private final Random random;
    private final List<Meteor> meteors = new ArrayList<>();
    private final List<Meteor> visibleMeteors = Collections.unmodifiableList(meteors);
    private final List<Impact> impacts = new ArrayList<>();
    private final List<Impact> visibleImpacts = Collections.unmodifiableList(impacts);
    private long nextMeteorTick;
    private long tick;
    private int meteorHits, meteorMisses, irradiationPulses, neutronHits;

    public static Preset clear() { return new Preset(0, 0L, Kind.CLEAR, Kind.CLEAR, 0); }
    public static Preset forLevel(int index) {
        var level = LevelEconomy.level(index);
        long seed = mix(level.seed() ^ SALT);
        Kind[] hazards = {Kind.ASTEROIDS, Kind.NEBULA, Kind.SOLAR_FLARE,
                Kind.NEUTRON_SWEEP, Kind.RADIATION, Kind.ION_STORM};
        int ordinal = (index - 1) % hazards.length;
        Kind primary = hazards[ordinal];
        Kind secondary = index % 11 == 0 ? hazards[(ordinal + 2) % hazards.length] : Kind.CLEAR;
        double severity = 0.65 + 0.70 * unit(mix(seed + 7));
        return new Preset(index, seed, primary, secondary, severity);
    }
    public SpaceWeather(Preset preset) {
        if (preset == null) throw new IllegalArgumentException("Weather preset required");
        this.preset = preset;
        random = new Random(preset.seed());
        nextMeteorTick = 28 + random.nextInt(45);
    }
    public Preset preset() { return preset; }
    public long tick() { return tick; }
    public List<Meteor> meteors() { return visibleMeteors; }
    public List<Impact> impacts() { return visibleImpacts; }
    public int meteorHits() { return meteorHits; }
    public int meteorMisses() { return meteorMisses; }
    public int neutronHits() { return neutronHits; }
    public int irradiationPulses() { return irradiationPulses; }
    public double sensorFactor() {
        double factor=1;
        if(preset.includes(Kind.NEBULA))factor*=0.47;
        if(preset.includes(Kind.ION_STORM))factor*=0.67;
        if(solarActive())factor*=0.80;
        if(preset.includes(Kind.RADIATION))factor*=0.91;
        return factor;
    }
    public double accuracyFactor() {
        double factor=1;
        if(preset.includes(Kind.NEBULA))factor*=0.68;
        if(preset.includes(Kind.ION_STORM))factor*=0.78;
        if(solarActive())factor*=0.88;
        return factor;
    }
    public double guidanceFactor() { return Math.max(0.35,sensorFactor()); }
    public double coolingFactor() {
        return solarActive()?0.70:preset.includes(Kind.NEBULA)?0.89:1.0;
    }
    public double heatPerSecond() {
        double heat=0;
        if(solarActive())heat+=6.0*preset.severity();
        if(preset.includes(Kind.ION_STORM))heat+=1.1*preset.severity();
        return heat;
    }
    public double powerDrainPerSecond() {
        return preset.includes(Kind.ION_STORM)?1.6*preset.severity():0;
    }
    /** Solar activity: three seconds of flare per eighteen-second cycle. */
    public boolean solarActive() {
        return preset.includes(Kind.SOLAR_FLARE) && tick%540>=300 && tick%540<390;
    }
    /** Nine seconds of neutron sweep per twenty-four-second cycle. */
    public boolean neutronActive() {
        return preset.includes(Kind.NEUTRON_SWEEP) && tick%720>=150 && tick%720<420;
    }
    public double neutronAngle() {
        return (preset.seed()&255L)/255.0*StrictMath.PI
                +(tick%720-150)*StrictMath.PI/270.0;
    }
    public double neutronDistance(double x,double y) {
        double angle=neutronAngle();
        return StrictMath.abs((x-CENTER_X)*StrictMath.sin(angle)
                -(y-CENTER_Y)*StrictMath.cos(angle));
    }
    /** Advance once after movement; impacts are consumed immediately by Battle. */
    public List<Impact> advance(long simulationTick,List<? extends Target> vessels) {
        if(simulationTick!=tick+1)throw new IllegalArgumentException("Weather must advance exactly one tick");
        if(vessels==null)throw new IllegalArgumentException("Vessels required");
        tick=simulationTick;
        impacts.clear();
        if(!preset.enabled())return visibleImpacts;
        for(int i=meteors.size()-1;i>=0;i--) {
            Meteor m=meteors.get(i);
            if(tick>=m.expiryTick()){meteors.remove(i);continue;}
            if(tick>=m.strikeTick()&&!m.resolved()){
                Target victim=find(vessels,m.targetId());
                boolean hit=victim!=null&&victim.alive()
                        &&StrictMath.hypot(victim.weatherX()-m.x(),victim.weatherY()-m.y())<=55
                        &&random.nextDouble()<0.73;
                if(hit){
                    double angle=random.nextDouble()*StrictMath.PI*2;
                    impacts.add(new Impact(victim.weatherId(),(10+random.nextDouble()*22)*preset.severity(),
                            Damage.KINETIC,StrictMath.cos(angle),StrictMath.sin(angle)));
                    meteorHits++;
                }else meteorMisses++;
                meteors.set(i,new Meteor(m.x(),m.y(),m.targetId(),m.arrivalTick(),
                        m.strikeTick(),m.expiryTick(),m.radius(),true,hit));
            }
        }
        if(preset.includes(Kind.ASTEROIDS)&&tick>=nextMeteorTick){
            spawnMeteor(vessels);
            nextMeteorTick=tick+(long)((34+random.nextInt(60))/Math.max(0.45,preset.severity()));
        }
        if(tick%15==0&&solarActive())
            for(Target t:vessels)if(t.alive())
                impacts.add(new Impact(t.weatherId(),2.1*preset.severity(),Damage.PARTICLE,1,0));
        if(tick%45==0&&preset.includes(Kind.RADIATION)){
            irradiationPulses++;
            for(Target t:vessels)if(t.alive())
                impacts.add(new Impact(t.weatherId(),3.2*preset.severity(),Damage.PARTICLE,-1,0));
        }
        if(tick%15==0&&neutronActive()){
            double angle=neutronAngle();
            for(Target t:vessels)if(t.alive()&&neutronDistance(t.weatherX(),t.weatherY())<38){
                impacts.add(new Impact(t.weatherId(),6.0*preset.severity(),Damage.PARTICLE,
                        -StrictMath.sin(angle),StrictMath.cos(angle)));
                neutronHits++;
            }
        }
        return visibleImpacts;
    }
    private void spawnMeteor(List<? extends Target> vessels){
        Target candidate=null;
        int eligible=0;
        for(Target t:vessels)if(t.alive()&&random.nextDouble()<1.0/++eligible)candidate=t;
        boolean aimed=candidate!=null&&random.nextDouble()<0.68;
        double x=aimed?candidate.weatherX()+(random.nextDouble()-0.5)*24
                :60+random.nextDouble()*1160;
        double y=aimed?candidate.weatherY()+(random.nextDouble()-0.5)*24
                :50+random.nextDouble()*585;
        long strike=tick+24;
        if(meteors.size()>=28)meteors.remove(0);
        meteors.add(new Meteor(x,y,aimed?candidate.weatherId():-1,tick,strike,
                strike+23,5+random.nextDouble()*9,false,false));
    }
    private static Target find(List<? extends Target> vessels,int id){
        if(id<0)return null;
        for(Target t:vessels)if(t.weatherId()==id)return t;
        return null;
    }
    private static long mix(long x){
        x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;
        x=(x^(x>>>27))*0x94d049bb133111ebL;
        return x^(x>>>31);
    }
    private static double unit(long bits){return(bits>>>11)*0x1.0p-53;}
}
