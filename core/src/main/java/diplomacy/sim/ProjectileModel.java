package diplomacy.sim;

import java.util.List;

/**
 * Module-derived physical ordnance data. These are SIMULATION properties, not
 * sprite dimensions: a small cross section is difficult to acquire and hit even
 * when an artist draws a large glow around the projectile.
 *
 * Existing CD-SHIP-1 upgrade stages feed the model without changing save files.
 * Per-faction tuning is a first-pass balance table, not a locked final roster.
 */
public final class ProjectileModel {
    private ProjectileModel() { }
    public enum Kind { KINETIC, MISSILE, TORPEDO }
    /** speed in world units/s; health in PD damage; radar cross-section and
     * emission signature are positive, dimensionless relative detection units. */
    public record Spec(double speed,double health,double crossSection,double signature) {
        public Spec {
            if(!finite(speed,10,1500)||!finite(health,0.1,200)
                    ||!finite(crossSection,0.04,8)||!finite(signature,0.04,8))
                throw new IllegalArgumentException("Invalid physical projectile properties");
        }
    }
    /** Module-supplied PD scan reach, tracking fire control, and per-hit damage.
     * Actual acquisition also uses the launching ship's sensor condition/weather. */
    public record Defense(double detectionMultiplier,double tracking,double damage) {
        public Defense {
            if(!finite(detectionMultiplier,0.4,3)||!finite(tracking,0.3,4)
                    ||!finite(damage,0.1,40))
                throw new IllegalArgumentException("Invalid point defense equipment");
        }
    }
    private static boolean finite(double number,double low,double high){
        return Double.isFinite(number)&&number>=low&&number<=high;
    }
    private static double clamp(double x,double low,double high){
        return StrictMath.max(low,StrictMath.min(high,x));
    }
    public static final Defense STANDARD_PD=new Defense(1,1,2.7);
    public static Spec standard(Kind type) {
        return switch(type){
            case KINETIC -> new Spec(285*WorldScale.PROJECTILE_SPEED_SCALE,1.60,0.24,0.54);
            case MISSILE -> new Spec(220*WorldScale.PROJECTILE_SPEED_SCALE,2.50,0.82,1.25);
            case TORPEDO -> new Spec(135*WorldScale.PROJECTILE_SPEED_SCALE,5.10,1.35,1.65);
        };
    }
    /** All six factions get different propulsive/stealth/armor trade-offs. */
    public static Spec module(Battle.Faction faction,Kind kind,
                              double outputRatio,double accuracyRatio,double integrityRatio){
        if(faction==null||kind==null||!finite(outputRatio,0.1,5)
                ||!finite(accuracyRatio,0.1,5)||!finite(integrityRatio,0.1,5))
            throw new IllegalArgumentException("Invalid fitted weapon modifiers");
        Spec base=standard(kind);
        double speed=1,armor=1,area=1,emissions=1;
        switch(faction){
            case TERRAN -> { }
            case DRAXIAN, VAERITH_PRISM -> {armor=1.10;area=1.12;emissions=1.08;}
            case CARNELIAN, NYX_BIOLITH -> {speed=0.88;armor=1.28;area=1.24;emissions=1.12;}
            case ZIPPLEDROW, UMBRA_FOUNDRY -> {speed=1.20;armor=0.85;area=0.80;emissions=0.92;}
            case UZIPT, ZELTHAR_LUMINARI -> {speed=1.08;armor=0.95;area=0.88;emissions=0.82;}
            case MANDRALLY, AUREATE_SYNOD -> {speed=0.94;armor=1.14;area=1.10;emissions=0.78;}
        }
        // Specialist improves guidance/propulsion; resilience armor slightly
        // increases physical cross section; high output makes warmer projectiles.
        double upgradedSpeed=clamp(1+0.18*(accuracyRatio-1)+0.08*(outputRatio-1),0.75,1.55);
        return new Spec(
            clamp(base.speed*speed*upgradedSpeed,20,1400),
            clamp(base.health*armor*integrityRatio,0.15,190),
            clamp(base.crossSection*area*(1+0.10*(integrityRatio-1))
                    /StrictMath.sqrt(clamp(accuracyRatio,0.5,3)),0.05,7),
            clamp(base.signature*emissions*(1+0.15*(outputRatio-1))
                    /StrictMath.sqrt(clamp(accuracyRatio,0.5,3)),0.05,7));
    }
    public static Spec average(List<Spec> parts,Kind kind){
        if(parts.isEmpty())return standard(kind);
        double speed=0,health=0,cross=0,signature=0;
        for(Spec part:parts){
            speed+=part.speed;health+=part.health;
            cross+=part.crossSection;signature+=part.signature;
        }
        int n=parts.size();
        return new Spec(speed/n,health/n,cross/n,signature/n);
    }
    public static Defense defense(double outputRatio,double trackingRatio,
                                  double sensorRatio){
        if(!finite(outputRatio,0.1,5)||!finite(trackingRatio,0.1,5)
                ||!finite(sensorRatio,0.1,5))
            throw new IllegalArgumentException("Invalid fitted defense modifiers");
        return new Defense(
            clamp(sensorRatio,0.45,2.9),
            clamp(trackingRatio,0.35,3.7),
            clamp(STANDARD_PD.damage*outputRatio,0.2,35));
    }
    /** Radar detection varies with the fourth root of effective target area.
     * This avoids a physically tiny projectile becoming completely invisible. */
    public static double acquisitionRange(double baseRange,double sensorCondition,
                                          double weatherFactor,Defense system,Spec target){
        if(target==null||system==null)return 0;
        return acquisitionRange(baseRange,sensorCondition,weatherFactor,system,target,1);
    }
    /** Sensor acquisition consumes the *current* motor signature, without per-PD allocations. */
    public static double acquisitionRange(double baseRange,double sensorCondition,
                                          double weatherFactor,Defense system,Spec target,
                                          double phaseEmission){
        if(target==null||system==null)return 0;
        double effective=clamp(target.crossSection*target.signature*
                clamp(phaseEmission,0.05,4),0.005,64);
        return StrictMath.max(0,baseRange)*clamp(sensorCondition,0,1)
                *clamp(weatherFactor,0,2.5)*system.detectionMultiplier
                *clamp(StrictMath.sqrt(StrictMath.sqrt(effective)),0.30,2.2);
    }
    /**
     * A conservative bound for excluding a distant physical point-defense
     * contact before doing expensive exact distance/signature calculations.
     * The final acquisitionRange(...) still makes ALL decisions.
     *
     * The exact formula bounds its signature fourth-root multiplier to 2.2,
     * for every legal fitted Spec, motor phase, and defense system. Its other
     * factors are identical here, plus a one-micron/roundoff guard so that
     * tangencies and equality always fall through to the original solver.
     * Non-finite diagnostics also fall through rather than incorrectly
     * rejecting a real body.
     */
    public static double maxPossibleAcquisitionDistanceSquared(
            double baseRange,double sensorCondition,double weatherFactor,
            Defense system){
        if(system==null)return Double.POSITIVE_INFINITY;
        double max=StrictMath.max(0,baseRange)*clamp(sensorCondition,0,1)
                *clamp(weatherFactor,0,2.5)*system.detectionMultiplier()*2.2;
        if(!Double.isFinite(max)||max<0)
            return Double.POSITIVE_INFINITY;
        double upper=(max+1e-6)*(1+1e-12);
        return upper*upper;
    }
    /** Exclusion ONLY. NaN/overflow geometry uses the exact old path. */
    public static boolean definitelyOutsideAcquisition(
            double dx,double dy,double safeMaxSquared){
        double squared=dx*dx+dy*dy;
        return Double.isFinite(squared)&&Double.isFinite(safeMaxSquared)
                &&squared>safeMaxSquared;
    }

    /** Hardening limits laser ablation per precise hit. This is a physical
     * durability response, not a fixed chance-to-miss or immunity flag.
     * The lightest rockets/micro ordnance retain full laser vulnerability. */
    public static double precisionPdDamage(Spec target,Defense system){
        if(target==null||system==null)return 0;
        double hardened=StrictMath.max(0,target.health()-2.1);
        return system.damage()/(1+1.2*hardened);
    }

    /** Flak fragmentation has much weaker penetration through thick intact
     * warhead casing. A live frag cloud can still damage multiple soft bodies,
     * but must hit a hardened torpedo repeatedly to destroy it. */
    public static double fragmentationPdDamage(Spec target,double baseDamage){
        if(target==null||!Double.isFinite(baseDamage)||baseDamage<0)return 0;
        double hardened=StrictMath.max(0,target.health()-2.0);
        return baseDamage/(1+1.5*hardened*hardened);
    }

    /** Speed, aspect/size and dedicated tracking quality determine PD accuracy.
     * The output is the actual chance used by the combat RNG, not a visual guess. */
    public static double interceptionChance(Spec target,Defense system,
                                            double crewCondition,double weatherAccuracy){
        if(target==null||system==null)return 0;
        return interceptionChance(target,system,crewCondition,weatherAccuracy,target.speed);
    }
    /** A real moving projectile uses its current world speed in the PD tracking solution. */
    public static double interceptionChance(Spec target,Defense system,
                                            double crewCondition,double weatherAccuracy,
                                            double actualSpeed){
        if(target==null||system==null)return 0;
        double area=clamp(StrictMath.sqrt(target.crossSection),0.32,1.8);
        // Preserve the previous tracking balance while every physical body is
        // deliberately slowed by the shared cinematic/tactical scale factor.
        double speedFactor=clamp((220.0*WorldScale.PROJECTILE_SPEED_SCALE)/
                StrictMath.max(10,actualSpeed),0.42,1.60);
        return clamp(0.68*clamp(crewCondition,0,1)
                *clamp(weatherAccuracy,0,1.5)*system.tracking
                *area*speedFactor,0,0.96);
    }
}
