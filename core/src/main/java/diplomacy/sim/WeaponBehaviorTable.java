package diplomacy.sim;

import java.util.EnumMap;
import java.util.Map;

/**
 * Authoritative ship-mounted weapon FIRE-CONTROL table.
 *
 * A `Gun` describes the shared operational law for a damage family; the
 * actual number, hardware generation, exclusive research and faction stats
 * come from installed shipyard modules. ProjectileModel is the independent
 * source of truth for physical projectile speed, HP, RCS and emissions.
 * FighterModel is the separate source of truth for autonomous craft payloads.
 *
 * Units: world distance, simulation seconds, energy/heat per fired shot.
 * This table is used by Battle.fire itself, not only by an information panel.
 */
public final class WeaponBehaviorTable {
    private WeaponBehaviorTable(){}
    public enum Delivery { INSTANT_BEAM, BALLISTIC_BODY, GUIDED_BODY }
    public record Gun(Battle.DamageType damageType,String module,Delivery delivery,
                      double maximumRange,double minimumRange,
                      double cooldownSeconds,double powerPerShot,double heatPerShot,
                      double heatFractionLimit,double accuracyCap,
                      double baseAimMultiplier,double lockAimMultiplier,
                      double conditionFloor,double requiredWeaponCondition,
                      double requiredMagazineCondition,boolean consumesAmmunition,
                      ProjectileModel.Kind projectile,double flightSeconds,
                      double guidancePerTick) {
        public Gun {
            if(damageType==null||module==null||module.isBlank()||delivery==null
                    ||!Double.isFinite(maximumRange)||maximumRange<=minimumRange
                    ||!Double.isFinite(minimumRange)||minimumRange<0
                    ||!Double.isFinite(cooldownSeconds)||cooldownSeconds<=0
                    ||!Double.isFinite(powerPerShot)||powerPerShot<0
                    ||!Double.isFinite(heatPerShot)||heatPerShot<0
                    ||!Double.isFinite(heatFractionLimit)||heatFractionLimit<=0||heatFractionLimit>1
                    ||!Double.isFinite(conditionFloor)||conditionFloor<=0||conditionFloor>1
                    ||!Double.isFinite(requiredWeaponCondition)||requiredWeaponCondition<0||requiredWeaponCondition>1
                    ||!Double.isFinite(requiredMagazineCondition)||requiredMagazineCondition<0||requiredMagazineCondition>1
                    ||!Double.isFinite(accuracyCap)||accuracyCap<0||accuracyCap>1
                    ||!Double.isFinite(baseAimMultiplier)||baseAimMultiplier<0
                    ||!Double.isFinite(lockAimMultiplier)||lockAimMultiplier<0
                    ||!Double.isFinite(flightSeconds)||flightSeconds<0
                    ||!Double.isFinite(guidancePerTick)||guidancePerTick<0
                    ||(delivery==Delivery.INSTANT_BEAM && projectile!=null)
                    ||(delivery==Delivery.GUIDED_BODY && (projectile==null||flightSeconds<=0||guidancePerTick<=0))
                    ||(delivery==Delivery.BALLISTIC_BODY && projectile!=ProjectileModel.Kind.KINETIC))
                throw new IllegalArgumentException("Invalid ship weapon behavior "+damageType);
        }
        public double range(double moduleRange){
            if(!Double.isFinite(moduleRange)||moduleRange<=0)
                throw new IllegalArgumentException("Invalid installed module reach");
            return maximumRange*moduleRange;
        }
        /** Strict range and power rules match legacy skirmish conditions. */
        public boolean available(double installedDamage,int ammo,double distance,
                                 double fittedReach,double remainingCooldown,
                                 double availablePower,double heat,double maxHeat,
                                 double weaponCondition,double magazineCondition){
            return installedDamage>0 && (!consumesAmmunition||ammo>0)
                    && Double.isFinite(distance) && (minimumRange==0||distance>minimumRange)
                    && distance<range(fittedReach) && remainingCooldown<=0
                    && availablePower>powerPerShot
                    && (heatFractionLimit==1.0 || heat<maxHeat*heatFractionLimit)
                    && (requiredWeaponCondition==0 || weaponCondition>requiredWeaponCondition)
                    && (requiredMagazineCondition==0 || magazineCondition>requiredMagazineCondition);
        }
        public double reload(double fittedCycle,double weaponCondition){
            if(!Double.isFinite(fittedCycle)||fittedCycle<=0)
                throw new IllegalArgumentException("Invalid installed firing cycle");
            return cooldownSeconds*fittedCycle/StrictMath.max(conditionFloor,weaponCondition);
        }
        /** Only instant-fire and unguided ballistic weapons use an aim roll. */
        public double aim(double accuracy,double tracking,double weather,double lock){
            if(delivery==Delivery.GUIDED_BODY)
                throw new IllegalStateException("Guided ordnance uses physical guidance, not a hitscan roll");
            return StrictMath.max(0,StrictMath.min(accuracyCap,
                    accuracy*tracking*weather*(baseAimMultiplier+lockAimMultiplier*lock)));
        }
    }
    /** One PD/FLK fire-control cycle per ship in the current aggregated model. */
    public record Defense(double ordnanceCycle,double craftCycle,double powerPerShot,
                          double heatPerShot,double minimumCondition,
                          double minimumPower,double craftDamageMultiplier) {
        public Defense {
            for(double n:new double[]{ordnanceCycle,craftCycle,powerPerShot,heatPerShot,
                    minimumCondition,minimumPower,craftDamageMultiplier})
                if(!Double.isFinite(n)||n<=0)
                    throw new IllegalArgumentException("Invalid point-defense behavior");
        }
        public double reload(boolean craft,double installedCycle,double condition){
            return (craft?craftCycle:ordnanceCycle)*installedCycle/condition;
        }
    }
    public static final double BALLISTIC_MISS_ANGLE_RADIANS=0.24;
    public static final Defense POINT_DEFENSE=new Defense(0.16,0.18,1.5,0.8,0.20,1.5,6.5);
    /**
     * Baseline damage behavior against the directional defensive layers.
     * This is the shared source for DirectionalDefense's initial facings.
     * Individual facings can later be tuned by armor/technology modules.
     * The last three fields represent confirmed secondary effects of
     * penetrating STRUCTURE damage, not free extra raw damage.
     */
    public record Damage(double shield,double armor,double structure,
                         double energyDrain,double sensorDegradation,
                         double addedHeat){
        public Damage {
            for(double value:new double[]{shield,armor,structure}){
                if(!Double.isFinite(value)||value<=0||value>5)
                    throw new IllegalArgumentException("Invalid directional damage multiplier");
            }
            for(double value:new double[]{energyDrain,sensorDegradation,addedHeat}){
                if(!Double.isFinite(value)||value<0||value>5)
                    throw new IllegalArgumentException("Invalid secondary effect");
            }
        }
    }
    private static final Map<Battle.DamageType,Damage> DAMAGE=buildDamage();
    private static Map<Battle.DamageType,Damage> buildDamage(){
        EnumMap<Battle.DamageType,Damage> table=new EnumMap<>(Battle.DamageType.class);
        table.put(Battle.DamageType.LASER,new Damage(1,0.8,1,0,0,0));
        table.put(Battle.DamageType.KINETIC,new Damage(1.3,1.25,1,0,0,0));
        table.put(Battle.DamageType.PLASMA,new Damage(0.65,1,1.15,0,0,0.35));
        table.put(Battle.DamageType.PARTICLE,new Damage(1.4,1,1.3,0.45,0.15,0));
        table.put(Battle.DamageType.MISSILE,new Damage(1,1,1,0,0,0));
        table.put(Battle.DamageType.TORPEDO,new Damage(1.5,1.5,1.3,0,0,0));
        table.put(Battle.DamageType.FIGHTER,new Damage(1,1,1,0,0,0));
        return Map.copyOf(table);
    }
    public static Map<Battle.DamageType,Damage> damageTable(){return DAMAGE;}
    public static Damage damage(Battle.DamageType kind){
        Damage behavior=DAMAGE.get(kind);
        if(behavior==null)throw new IllegalArgumentException("Unknown damage behavior "+kind);
        return behavior;
    }
    private static final Map<Battle.DamageType,Gun> MAIN=build();
    private static Map<Battle.DamageType,Gun> build(){
        EnumMap<Battle.DamageType,Gun> m=new EnumMap<>(Battle.DamageType.class);
        register(m,new Gun(Battle.DamageType.LASER,"LAS",Delivery.INSTANT_BEAM,
                530,0,1.05,9,7,0.91,0.98,0.70,0.45,0.30,0,0,false,null,0,0));
        register(m,new Gun(Battle.DamageType.KINETIC,"KIN",Delivery.BALLISTIC_BODY,
                445,0,0.75,5,4,0.95,0.97,0.72,0.40,0.25,0,0,false,
                ProjectileModel.Kind.KINETIC,0,0));
        register(m,new Gun(Battle.DamageType.PLASMA,"PLA",Delivery.INSTANT_BEAM,
                245,0,1.8,14,17,0.86,0.94,0.80,0.40,0.25,0,0,false,null,0,0));
        register(m,new Gun(Battle.DamageType.PARTICLE,"PAR",Delivery.INSTANT_BEAM,
                565,0,2.1,17,10,0.90,0.98,0.76,0.45,0.25,0,0,false,null,0,0));
        // No thermal hard-stop existed for launchers; 1.0 avoids inventing one
        // while preserving the normal reactor/magazine limits.
        register(m,new Gun(Battle.DamageType.MISSILE,"MSL",Delivery.GUIDED_BODY,
                690,80,3.2,13,5,1.0,0,0,0,0.40,0.15,0.15,true,
                ProjectileModel.Kind.MISSILE,10,0.04));
        register(m,new Gun(Battle.DamageType.TORPEDO,"TOR",Delivery.GUIDED_BODY,
                840,110,6.4,18,9,1.0,0,0,0,0.25,0,0.15,true,
                ProjectileModel.Kind.TORPEDO,14,0.025));
        return Map.copyOf(m);
    }
    private static void register(EnumMap<Battle.DamageType,Gun> all,Gun value){
        if(all.putIfAbsent(value.damageType(),value)!=null)
            throw new IllegalArgumentException("Duplicate weapon behavior "+value.damageType());
    }
    public static Map<Battle.DamageType,Gun> guns(){return MAIN;}
    public static Gun gun(Battle.DamageType type){
        Gun behavior=MAIN.get(type);
        if(behavior==null)throw new IllegalArgumentException("No installed gun behavior for "+type);
        return behavior;
    }
    public static ProjectileModel.Spec projectileStandard(Battle.DamageType type){
        Gun weapon=gun(type);
        return weapon.projectile()==null?null:ProjectileModel.standard(weapon.projectile());
    }
}
