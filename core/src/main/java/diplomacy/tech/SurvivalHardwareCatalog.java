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

    /** Four independent physical alternatives for each existing survival root.
     * These are not research branches or manufactured equipment marks. */
    public record Variant(String code,String root,String name,int requiredTier,
                          double performance,double mass,double power,double heat,
                          double integrity,double price,double risk){
        public Variant{
            if(code==null||root==null||name==null||code.length()!=4||
                    requiredTier<0||requiredTier>9)
                throw new IllegalArgumentException("Invalid survival variant identity");
            for(double n:new double[]{performance,mass,power,heat,integrity,price,risk})
                if(!Double.isFinite(n)||n<=0)
                    throw new IllegalArgumentException("Invalid survival variant property");
        }
    }
    private static final String[][] VARIANT_NAMES={
        {"Field Patch Locker", "Compact Repair Servitor", "Blastproof Repair Workshop", "Autonomous Damage-Control Hub"},
        {"Emergency Shock Berths", "Folded Survival Quarters", "Armored Survival Dormitory", "Adaptive Crew Sanctuary"},
        {"Emergency Bridge Shelter", "Compact Battle Bridge", "Reinforced Command Bunker", "Redundant Tactical Citadel"},
        {"Flexible Fuel Bladder", "Nested Fuel Cassette", "Segmented Fuel Vault", "Multi-Cell Isolation Bunker"},
        {"Firebreak Shell Locker", "Compact Isolated Magazine", "Blast-Segmented Magazine", "Automated Quench Vault"},
        {"Liferaft Pod Brace", "Folded Evacuation Pods", "Reinforced Escape Capsules", "Adaptive Rescue Launch Rail"},
        {"Passive Wreck Marker", "Compact Rescue Pinger", "Shielded Recovery Transponder", "Autonomous Recovery Buoy"},
        {"Unsanctioned Shunt Switch", "Pocket Governor Bypass", "Armored Overdrive Regulator", "Predictive Overdrive Controller"},
        {"Salvaged Plasma Bank", "Compact Confinement Stack", "Caged High-Flux Cell", "High-Density Cascade Battery"},
        {"Scrap Battery Bundle", "Compressed Contraband Cell", "Shielded Illegal Pack", "Unrated High-Output Cell"},
        {"Scrap Cooling Bypass", "Compact Heat Bleed Manifold", "Reinforced Improvised Radiator", "Overdriven Thermal Dump"},
        {"Emergency Reactor Bridge", "Compact Unlicensed Tap", "Shielded Hot Bus", "Cascade Reactor Draw-Off"}
    };
    private static List<Variant> makeVariants(){
        if(VARIANT_NAMES.length!=FAMILIES.size())
            throw new IllegalStateException("Every survival root needs four alternatives");
        String[] suffix={"P","C","H","A"};
        int[] tiers={0,2,4,6};
        double[] performance={.65,.85,1.15,1.58};
        double[] mass={.76,.64,1.35,1.42};
        double[] power={.88,1.15,1.22,1.48};
        double[] heat={.90,1.25,1.12,1.64};
        double[] integrity={.68,.72,1.56,1.10};
        double[] price={.55,.90,1.60,2.05};
        double[] risk={1.26,1.15,.78,1.47};
        java.util.ArrayList<Variant> result=new java.util.ArrayList<>(48);
        for(int i=0;i<FAMILIES.size();i++){
            Family f=FAMILIES.get(i);
            if(VARIANT_NAMES[i].length!=4)
                throw new IllegalStateException("Missing hardware alternatives "+f.code());
            boolean unsafe=switch(f.code()){
                case "PGO","JPB","IBR","IST","IRG" -> true;
                default -> false;
            };
            for(int j=0;j<4;j++)
                result.add(new Variant(f.code()+suffix[j],f.code(),
                        (unsafe?"UNSAFE RETROFIT: ":"")+VARIANT_NAMES[i][j],
                        Math.max(f.requiredTier(),tiers[j]),performance[j],
                        mass[j],power[j],heat[j],integrity[j],price[j],risk[j]));
        }
        return List.copyOf(result);
    }
    public static final List<Variant> VARIANTS=makeVariants();
    public static Variant findVariant(String code){
        if(code==null)return null;
        for(Variant v:VARIANTS)if(v.code().equals(code))return v;
        return null;
    }
    public static String root(String code){
        Variant v=findVariant(code);
        return v==null?code:v.root();
    }
    public static double performance(String code){
        Variant v=findVariant(code);
        return v==null?1:v.performance();
    }
    public static double risk(String code){
        Variant v=findVariant(code);
        return v==null?1:v.risk();
    }
    public static Family find(String code) {
        if(code==null)return null;
        for(Family f:FAMILIES)if(f.code().equals(code))return f;
        return null;
    }
    public static int requiredTier(String code) {
        Variant variant=findVariant(code);
        if(variant!=null)return variant.requiredTier();
        Family f=find(code);
        return f==null?0:f.requiredTier();
    }
}
