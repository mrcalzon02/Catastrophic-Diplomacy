package diplomacy.sim;

/**
 * Whole-aircraft protection and crew escape. A fighter is too small for
 * directional shields/armor: exactly one shield capacitor and one armor pool.
 * FTR research improves survivability; native carrier launch bays alone do
 * not grant an advanced shield. No RNG draw changes weapon trajectories.
 */
public final class FighterSurvivability {
    private FighterSurvivability(){}
    public static final int SHIELD_RECHARGE_DELAY_TICKS=60;

    /** All fighter profiles have at least one crew member with a genuine
     * (nonzero, non-guaranteed) chance of surviving catastrophic loss. */
    public record Fitting(int crew,double shieldCapacity,double shieldRechargePerSecond,
                          double ejectionChance){
        public Fitting {
            if(crew<1||crew>4||!Double.isFinite(shieldCapacity)||shieldCapacity<0||
                    !Double.isFinite(shieldRechargePerSecond)||shieldRechargePerSecond<0||
                    !Double.isFinite(ejectionChance)||ejectionChance<=0||ejectionChance>=1)
                throw new IllegalArgumentException("Invalid fighter rescue/shield fitting");
        }
    }
    public static Fitting installed(FighterModel.Profile profile,int ftrRank){
        if(profile==null||ftrRank<0||ftrRank>9)
            throw new IllegalArgumentException("Invalid fitted fighter survival equipment");
        int crew=switch(profile.hull()){
            case INTERCEPTOR,LASER_FIGHTER,PARTICLE_FIGHTER -> 1;
            case ASSAULT_GUNSHIP,MISSILE_FIGHTER -> 2;
            case TORPEDO_BOMBER -> 3;
        };
        double compartment=switch(profile.hull()){
            case INTERCEPTOR -> .00;
            case LASER_FIGHTER,PARTICLE_FIGHTER -> .015;
            case ASSAULT_GUNSHIP,MISSILE_FIGHTER -> .035;
            case TORPEDO_BOMBER -> .055;
        };
        double chance=StrictMath.min(.87,.13+ftrRank*.064+compartment);
        // Paid late FTR engineering is the *only* source of an aircraft
        // shield. Heavier hulls gain capacitor area but not shield facings.
        double capacity=ftrRank<6?0:
                (12+(ftrRank-6)*10)*StrictMath.sqrt(profile.health()/35.0);
        double recharge=ftrRank<6?0:
                (.50+(ftrRank-6)*.30)*StrictMath.sqrt(profile.health()/35.0);
        return new Fitting(crew,capacity,recharge,chance);
    }
    /** Actual damage to the one global shield -> one global armor -> hull.
     * No hit facing, rotation, zone index or sprite pixel is consulted. */
    public record Hit(double shieldAbsorbed,double armorAbsorbed,double hullDamage){}
    public static Hit absorb(Battle.Craft craft,double damage,long tick){
        if(craft==null||!Double.isFinite(damage)||damage<0||tick<0)
            throw new IllegalArgumentException("Invalid fighter strike");
        if(craft.integrity<=0||damage==0)return new Hit(0,0,0);
        double blocked=StrictMath.min(craft.shields,damage);
        craft.shields-=blocked;
        double remaining=damage-blocked;
        if(craft.fighter&&craft.maxShields>0&&damage>0)
            craft.lastShieldHitTick=tick;
        double armor=StrictMath.min(craft.armor,remaining*.48);
        craft.armor-=armor;
        double structural=StrictMath.max(0,remaining-armor);
        craft.integrity-=structural;
        return new Hit(blocked,armor,structural);
    }
    /** Capacitor recharge waits after ANY actual attack (including one
     * which completely emptied the shield), then restores one scalar cell. */
    public static void recharge(Battle.Craft craft,long tick,double dt){
        if(craft==null||tick<0||!Double.isFinite(dt)||dt<0)
            throw new IllegalArgumentException("Invalid fighter recharge tick");
        if(!craft.fighter||craft.integrity<=0||craft.maxShields<=0||
                tick-craft.lastShieldHitTick<SHIELD_RECHARGE_DELAY_TICKS)return;
        craft.shields=StrictMath.min(craft.maxShields,
                craft.shields+craft.shieldRechargePerSecond*dt);
    }
    /** Per-seat deterministic ejection roll, independent of random draws
     * for weapons, weather, target selection and other fighters. */
    public static boolean survived(long battleSeed,int craftId,int seat,double chance){
        if(craftId<=0||seat<0||seat>3||!Double.isFinite(chance)||
                chance<=0||chance>=1)
            throw new IllegalArgumentException("Invalid fighter escape outcome");
        long value=battleSeed^0x6573636170657064L
                ^((long)craftId*0x9e3779b97f4a7c15L)
                ^((long)(seat+1)*0xd1b54a32d192ed03L);
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        value^=value>>>31;
        double roll=(value>>>11)*0x1.0p-53;
        return roll<chance;
    }
}
