package diplomacy.sim;

import java.util.EnumMap;
import java.util.Map;

/**
 * Module-compatible guided-weapon firing schematics. This is NOT a cosmetic
 * muzzle-flash label: a salvo consists of separate finite-ammo, interceptable
 * objects launched on separate 30 Hz ticks. The old SINGLE pattern is the
 * exact default for existing equipment, factions and recorded combat tests.
 *
 * This first increment uses one controller per fitted weapon family/ship.
 * Future individual mounts will each own one of these immutable schematics.
 */
public final class OrdnanceDoctrine {
    private OrdnanceDoctrine(){}
    public enum Mode {
        SINGLE, RIPPLE, SATURATION, MICRO_SWARM, HEAVY_STRIKE,
        COLD_LAUNCH, DECOY_CLOUD, CLUSTER_CARRIER, CASCADE_SWARM
    }
    public enum Payload { STANDARD, MICRO, HEAVY, DECOY, CLUSTER_CARRIER, MICRO_CLUSTER_CARRIER, ULTRA_MICRO }
    public record Pattern(Mode mode,int real,int decoys,int intervalTicks,
                          int ignitionDelayTicks,double reloadMultiplier,
                          double damageMultiplier,double velocityMultiplier,
                          double durabilityMultiplier,double crossSectionMultiplier,
                          double signatureMultiplier,Payload payload) {
        public Pattern {
            if(mode==null||payload==null||real<1||real>12||decoys<0||decoys>12
                ||real+decoys>16||intervalTicks<0||intervalTicks>90
                ||ignitionDelayTicks<0||ignitionDelayTicks>90
                ||!finite(reloadMultiplier,.5,15)
                ||!finite(damageMultiplier,.01,5)
                ||!finite(velocityMultiplier,.1,3)
                ||!finite(durabilityMultiplier,.1,5)
                ||!finite(crossSectionMultiplier,.1,5)
                ||!finite(signatureMultiplier,.1,5)
                ||(decoys>0&&mode!=Mode.DECOY_CLOUD)
                ||(payload==Payload.DECOY))
                throw new IllegalArgumentException("Invalid guided-ordnance firing pattern");
        }
        public int count(){return real+decoys;}
        public int ammoNeeded(){return count();} // decoys are real stored hardware
        public long spanTicks(){return (long)(count()-1)*intervalTicks;}
        public boolean isDecoy(int index){return index>=real;}
    }
    private static boolean finite(double d,double lo,double hi){
        return Double.isFinite(d)&&d>=lo&&d<=hi;
    }
    private static final Map<Mode,Pattern> MISSILES=build(false);
    private static final Map<Mode,Pattern> TORPEDOES=build(true);
    private static Map<Mode,Pattern> build(boolean heavy){
        EnumMap<Mode,Pattern> m=new EnumMap<>(Mode.class);
        insert(m,new Pattern(Mode.SINGLE,1,0,0,0,1,1,1,1,1,1,Payload.STANDARD));
        insert(m,new Pattern(Mode.RIPPLE,3,0,5,0,3.2,.78,1,1,1,1,Payload.STANDARD));
        insert(m,new Pattern(Mode.SATURATION,5,0,2,0,5.5,.60,.96,.9,1.02,1.1,Payload.STANDARD));
        insert(m,new Pattern(Mode.MICRO_SWARM,8,0,1,0,4.0,.23,1.32,.42,.38,.63,Payload.MICRO));
        insert(m,new Pattern(Mode.HEAVY_STRIKE,heavy?2:2,0,7,0,3.4,1.45,.78,1.85,1.52,1.55,Payload.HEAVY));
        insert(m,new Pattern(Mode.COLD_LAUNCH,1,0,0,15,1.22,1,1,1,.90,.56,Payload.STANDARD));
        insert(m,new Pattern(Mode.DECOY_CLOUD,1,4,2,0,2.7,1,.94,1,1,1.06,Payload.STANDARD));
        // The carrier itself has no contact warhead: it opens partway through
        // flight and releases six independently guided, damageable bodies.
        insert(m,new Pattern(Mode.CLUSTER_CARRIER,1,0,0,0,2.3,.90,.94,1.4,1.2,1.1,
                Payload.CLUSTER_CARRIER));
        // Four small carrier rounds, six ultra-micro seekers in each.
        // Damage is split across the children, not multiplied by the fan-out.
        insert(m,new Pattern(Mode.CASCADE_SWARM,4,0,2,0,4.2,.21,1.20,.53,.50,.70,
                Payload.MICRO_CLUSTER_CARRIER));
        return Map.copyOf(m);
    }
    private static void insert(EnumMap<Mode,Pattern> list,Pattern data){
        if(list.putIfAbsent(data.mode(),data)!=null)
            throw new IllegalArgumentException("Duplicate salvo mode");
    }
    public static Map<Mode,Pattern> patterns(Battle.DamageType family){
        return switch(family){
            case MISSILE -> MISSILES;
            case TORPEDO -> TORPEDOES;
            default -> throw new IllegalArgumentException("Not a guided ordnance family: "+family);
        };
    }
    public static Pattern forWeapon(Battle.DamageType family,Mode mode){
        if(mode==null)throw new IllegalArgumentException("Missing launch doctrine");
        Pattern pattern=patterns(family).get(mode);
        if(pattern==null)throw new IllegalArgumentException("Unsupported launch doctrine");
        return pattern;
    }
    /** Preserve physical detection and PD damage semantics with bounded subtypes. */
    public static ProjectileModel.Spec derived(ProjectileModel.Spec base,Pattern pattern,
                                                boolean isDecoy){
        if(base==null||pattern==null)throw new IllegalArgumentException("No physical payload");
        double v=isDecoy?1.06:pattern.velocityMultiplier();
        double hp=isDecoy?.28:pattern.durabilityMultiplier();
        double area=isDecoy?1.8:pattern.crossSectionMultiplier();
        double emission=isDecoy?2.4:pattern.signatureMultiplier();
        return new ProjectileModel.Spec(
            clamp(base.speed()*v,10,1500),
            clamp(base.health()*hp,.1,200),
            clamp(base.crossSection()*area,.04,8),
            clamp(base.signature()*emission,.04,8));
    }
    private static double clamp(double v,double lo,double hi){
        return StrictMath.max(lo,StrictMath.min(hi,v));
    }
}
