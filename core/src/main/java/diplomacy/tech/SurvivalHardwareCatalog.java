package diplomacy.tech;

import java.util.List;

/** Noncanonical survival, engineering and illicit retrofit modules.
 * The names are game-fiction only, not instructions for physical equipment. */
public final class SurvivalHardwareCatalog {
    private SurvivalHardwareCatalog(){}
    /** Runtime hazards retain their exact installed physical origin. */
    public static final int GOVERNOR=1, PLASMA_CELL=2, BATTERY_BRICK=4,
                            THERMAL_SHUNT=8, REACTOR_TAP=16;
    public record Family(String code,String parent,String name,String category,
                         int requiredTier,double mass,double power,double heat,
                         double integrity,double cost,double output) {
        public Family {
            if(code==null||parent==null||name==null||category==null||
                    requiredTier<0||requiredTier>9||
                    mass<=0||power<=0||heat<=0||integrity<=0||cost<=0||output<=0)
                throw new IllegalArgumentException("Invalid survival hardware");
        }
    }
    public static final List<Family> FAMILIES=List.of(
        new Family("DMC","REP","Distributed Damage-Control Station","support",1,1.12,1.25,1.19,1.15,1.26,1.11),
        new Family("HCR","CRW","Hardened Crew Compartment","habitat",2,1.45,.86,.90,1.50,1.41,1.11),
        new Family("CIC","SEN","Armored Command Citadel","systems",3,1.39,1.23,1.13,1.70,1.63,1.14),
        new Family("FBN","MAG","Armored Fuel Bunker","support",1,1.46,.80,.91,1.42,1.25,1.12),
        new Family("AMV","MAG","Isolated Ammunition Vault","support",2,1.40,.95,1.00,1.57,1.47,1.20),
        new Family("EPD","CRW","Dedicated Escape-Pod Battery","support",1,1.03,1.03,1.07,1.11,1.22,1.08),
        new Family("RCB","ECM","Emergency Recovery Beacon","support",1,.72,.90,.82,1.14,1.10,1.03),
        new Family("PGO","REA","Power Governor Override","systems",3,.83,1.43,1.65,.74,1.36,1.35),
        new Family("JPB","REA","Jailbroken Plasma Confinement Battery","systems",5,1.55,1.57,1.91,.70,1.58,1.75),
        new Family("IBR","REA","Unauthorized Battery Brick","systems",0,1.62,1.03,1.82,.51,.39,1.31),
        new Family("IST","RAD","Improvised Thermal Shunt","systems",0,.87,1.13,1.95,.66,.55,1.27),
        new Family("IRG","REA","Unlicensed Reactor Tap","systems",2,1.11,1.47,2.15,.56,.66,1.64)
    );
    public static Family find(String code) {
        if(code==null)return null;
        for(Family f:FAMILIES)if(f.code().equals(code))return f;
        return null;
    }
    public static int requiredTier(String code) {
        Family f=find(code);
        return f==null?0:f.requiredTier();
    }
}
