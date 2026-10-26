package diplomacy.sim;

import java.util.EnumMap;
import java.util.Map;

/**
 * Warhead/ammunition identity is independent from gun family, launch cadence,
 * installed module engineering and three-stage FX. No ammo creates a weapon
 * on a bare hull. Numeric velocities are SIMULATION units, not fractions of c.
 */
public final class AmmunitionTable {
    private AmmunitionTable(){}
    public enum Kinetic { STANDARD, ARMOR_PIERCING, FRAGMENTATION, HIGH_EXPLOSIVE, RELATIVISTIC }
    public enum Warhead { STANDARD, ARMOR_PIERCING, FRAGMENTATION, HIGH_EXPLOSIVE, EMP, SHIELD_BREAKER }
    /** Primary raw damage, flight velocity, interception HP/size/emissions,
     * portion of post-shield hit bypassing armor, proximity blast, electronics.
     */
    public record Profile(double damage,double speed,double durability,double crossSection,
                          double emissions,double armorBypass,double blastRadius,
                          double blastFraction,double energyDrain,double shieldDrain,
                          double powerCost,double heatCost,double reloadCost) {
        public Profile {
            for(double x:new double[]{damage,speed,durability,crossSection,emissions,
                    powerCost,heatCost,reloadCost}){
                if(!Double.isFinite(x)||x<=0||x>12)throw new IllegalArgumentException("Unsafe payload profile");
            }
            if(!Double.isFinite(armorBypass)||armorBypass<0||armorBypass>0.85
                    ||!Double.isFinite(blastRadius)||blastRadius<0||blastRadius>250
                    ||!Double.isFinite(blastFraction)||blastFraction<0||blastFraction>1
                    ||!Double.isFinite(energyDrain)||energyDrain<0||energyDrain>5
                    ||!Double.isFinite(shieldDrain)||shieldDrain<0||shieldDrain>5
                    ||(blastRadius==0)!=(blastFraction==0))
                throw new IllegalArgumentException("Unsafe ammunition secondary effects");
        }
        public ProjectileModel.Spec physical(ProjectileModel.Spec source){
            if(source==null)throw new IllegalArgumentException("Projectile requires real physical profile");
            return new ProjectileModel.Spec(
                clamp(source.speed()*speed,10,1500),
                clamp(source.health()*durability,.1,200),
                clamp(source.crossSection()*crossSection,.04,8),
                clamp(source.signature()*emissions,.04,8));
        }
        private static double clamp(double x,double lo,double hi){
            return StrictMath.max(lo,StrictMath.min(hi,x));
        }
    }
    private static final Map<Kinetic,Profile> KINETIC=buildKinetics();
    private static final Map<Warhead,Profile> WARHEADS=buildWarheads();
    private static Map<Kinetic,Profile> buildKinetics(){
        EnumMap<Kinetic,Profile> m=new EnumMap<>(Kinetic.class);
        m.put(Kinetic.STANDARD,new Profile(1,1,1,1,1,0,0,0,0,0,1,1,1));
        m.put(Kinetic.ARMOR_PIERCING,new Profile(.90,1.13,1.25,.72,.86,.55,0,0,0,0,1.15,1.10,1.18));
        m.put(Kinetic.FRAGMENTATION,new Profile(.57,.83,.76,1.20,1.15,0,55,.40,0,0,1.05,1.14,1.15));
        m.put(Kinetic.HIGH_EXPLOSIVE,new Profile(.83,.89,1.04,1.18,1.21,0,78,.54,0,0,1.15,1.23,1.29));
        // A fictional high-velocity mass-driver slug: 2.4x simulation speed,
        // larger power/thermal/cycle budget. Not actual near-light-speed physics.
        m.put(Kinetic.RELATIVISTIC,new Profile(1.80,2.4,2.3,.66,1.65,.68,0,0,0,0,2.65,2.50,3.6));
        return Map.copyOf(m);
    }
    private static Map<Warhead,Profile> buildWarheads(){
        EnumMap<Warhead,Profile> m=new EnumMap<>(Warhead.class);
        m.put(Warhead.STANDARD,new Profile(1,1,1,1,1,0,0,0,0,0,1,1,1));
        m.put(Warhead.ARMOR_PIERCING,new Profile(.88,1.08,1.12,.89,.93,.52,0,0,0,0,1.10,1.10,1.12));
        m.put(Warhead.FRAGMENTATION,new Profile(.68,1.12,.82,.90,1.07,0,72,.44,0,0,1.10,1.12,1.14));
        m.put(Warhead.HIGH_EXPLOSIVE,new Profile(1.16,.89,1.20,1.22,1.28,0,105,.55,0,0,1.30,1.27,1.34));
        m.put(Warhead.EMP,new Profile(.50,1.02,.88,1.02,1.46,0,65,.24,1.25,.38,1.18,1.24,1.25));
        m.put(Warhead.SHIELD_BREAKER,new Profile(.84,.98,1.05,1.20,1.32,0,0,0,.12,1.65,1.22,1.21,1.30));
        return Map.copyOf(m);
    }
    public static Map<Kinetic,Profile> kinetics(){return KINETIC;}
    public static Map<Warhead,Profile> warheads(){return WARHEADS;}
    public static Profile kinetic(Kinetic kind){
        Profile p=KINETIC.get(kind);
        if(p==null)throw new IllegalArgumentException("Unknown kinetic ammunition: "+kind);
        return p;
    }
    public static Profile warhead(Warhead kind){
        Profile p=WARHEADS.get(kind);
        if(p==null)throw new IllegalArgumentException("Unknown guided payload: "+kind);
        return p;
    }
}
