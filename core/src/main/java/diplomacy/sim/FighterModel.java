package diplomacy.sim;

/**
 * Autonomous strike-craft fitting profiles. Fighters are armed vehicles, not
 * guided missiles. Their weapon payloads enter Battle's real ordnance lists.
 * All distances are world units; fuel and reload are simulated seconds.
 */
public final class FighterModel {
    private FighterModel() { }

    public enum Hull {
        INTERCEPTOR, ASSAULT_GUNSHIP, LASER_FIGHTER, PARTICLE_FIGHTER,
        MISSILE_FIGHTER, TORPEDO_BOMBER
    }
    public enum Weapon { CANNON, LASER, PARTICLE, MISSILE, TORPEDO }

    /** HP, armor pool, speed, sensor reach, radar cross-section, emissions,
     * fuel seconds, weapon hit damage, range, cooldown seconds, and carried shots. */
    public record Profile(Hull hull, Weapon weapon, double health, double armor,
                          double speed, double sensors, double crossSection,
                          double signature, double fuel, double damage,
                          double range, double cooldown, int ammunition,
                          double accuracy) {
        public Profile {
            if(hull==null||weapon==null
                ||!finite(health,1,1000)||!finite(armor,0,500)
                ||!finite(speed,10,1000)||!finite(sensors,20,1200)
                ||!finite(crossSection,0.04,8)||!finite(signature,0.04,8)
                ||!finite(fuel,1,400)||!finite(damage,0.1,500)
                ||!finite(range,10,1000)||!finite(cooldown,0.1,40)
                ||ammunition<0||ammunition>1000||!finite(accuracy,0.05,1))
                throw new IllegalArgumentException("Invalid autonomous fighter profile");
        }
        /** Radar and PD use the same fourth-root detection logic as missiles. */
        public ProjectileModel.Spec radarProfile() {
            return new ProjectileModel.Spec(speed,health+armor,crossSection,signature);
        }
        public boolean antiCraft() { return hull==Hull.INTERCEPTOR; }
    }
    private static boolean finite(double x,double lo,double hi) {
        return Double.isFinite(x)&&x>=lo&&x<=hi;
    }
    public static Profile standard(Hull hull) { return forFaction(hull,Battle.Faction.TERRAN); }
    public static Profile forFaction(Hull hull,Battle.Faction faction) {
        if(hull==null||faction==null)throw new IllegalArgumentException("Fighter identity missing");
        Weapon weapon;
        double hp,armor,speed,sensors,cross,emissions,fuel,damage,range,delay,accuracy;
        int ammo;
        switch(hull) {
            case INTERCEPTOR -> {
                weapon=Weapon.CANNON;hp=23;armor=4;speed=240;sensors=280;
                cross=0.22;emissions=0.65;fuel=35;damage=6.5;range=128;delay=0.65;ammo=28;accuracy=0.83;
            }
            case ASSAULT_GUNSHIP -> {
                weapon=Weapon.CANNON;hp=56;armor=20;speed=152;sensors=245;
                cross=0.78;emissions=1.08;fuel=39;damage=14;range=155;delay=1.02;ammo=36;accuracy=0.74;
            }
            case LASER_FIGHTER -> {
                weapon=Weapon.LASER;hp=32;armor=7;speed=202;sensors=315;
                cross=0.34;emissions=0.95;fuel=36;damage=10;range=185;delay=1.10;ammo=0;accuracy=0.81;
            }
            case PARTICLE_FIGHTER -> {
                weapon=Weapon.PARTICLE;hp=35;armor=10;speed=181;sensors=350;
                cross=0.43;emissions=1.12;fuel=35;damage=11.5;range=215;delay=1.35;ammo=0;accuracy=0.78;
            }
            case MISSILE_FIGHTER -> {
                weapon=Weapon.MISSILE;hp=39;armor=11;speed=175;sensors=390;
                cross=0.59;emissions=0.84;fuel=40;damage=20;range=365;delay=2.4;ammo=5;accuracy=0.79;
            }
            case TORPEDO_BOMBER -> {
                weapon=Weapon.TORPEDO;hp=68;armor=24;speed=117;sensors=420;
                cross=1.13;emissions=1.4;fuel=52;damage=53;range=440;delay=4.5;ammo=3;accuracy=0.70;
            }
            default -> throw new IllegalArgumentException("Unknown fighter hull");
        }
        double speedRatio=1,defenseRatio=1,crossRatio=1,emissionRatio=1,sensorRatio=1;
        switch(faction) {
            case TERRAN -> {}
            case DRAXIAN -> {defenseRatio=1.12;emissionRatio=1.06;}
            case CARNELIAN -> {speedRatio=0.87;defenseRatio=1.3;crossRatio=1.20;}
            case ZIPPLEDROW -> {speedRatio=1.17;defenseRatio=0.82;crossRatio=0.8;}
            case UZIPT -> {speedRatio=1.08;sensorRatio=1.22;emissionRatio=0.8;}
            case MANDRALLY -> {defenseRatio=1.16;emissionRatio=0.78;}
        }
        return new Profile(hull,weapon,hp*defenseRatio,armor*defenseRatio,
                speed*speedRatio,sensors*sensorRatio,cross*crossRatio,
                emissions*emissionRatio,fuel,damage,range,delay,ammo,accuracy);
    }
}
