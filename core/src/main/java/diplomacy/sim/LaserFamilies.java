package diplomacy.sim;

import diplomacy.shipyard.ModuleVersions;
import diplomacy.shipyard.Shipyard;
import java.util.HashMap;
import java.util.Map;

/**
 * Five physically distinct laser configurations selected by the existing
 * installed module manufacturing pattern. No parallel research authorization.
 * All multipliers are relative to the actual fitted laser's research-adjusted
 * damage, cycle, range and tracking. STANDARD Mk I remains unchanged.
 */
public final class LaserFamilies {
    private LaserFamilies(){}
    public enum Type {
        PULSE(1,1,1,1,1,1,.13,6.3f),
        RAPID(.68,.62,.90,.96,.72,.72,.08,3.3f),
        LANCE(1.58,1.75,1.34,1.04,1.50,1.42,.23,8.5f),
        OVERDRIVE(1.86,1.28,.88,.94,1.74,2.05,.19,9.4f),
        NEEDLE(.79,1.12,1.45,1.18,.86,.67,.12,2.15f);
        public final double damage,cycle,range,aim,power,heat,lifetime;
        public final float thickness;
        Type(double damage,double cycle,double range,double aim,
             double power,double heat,double lifetime,float thickness){
            this.damage=damage;this.cycle=cycle;this.range=range;
            this.aim=aim;this.power=power;this.heat=heat;
            this.lifetime=lifetime;this.thickness=thickness;
        }
    }
    public static Type fromPattern(ModuleVersions.Pattern pattern){
        if(pattern==null)throw new IllegalArgumentException("Laser module pattern required");
        return switch(pattern){
            case STANDARD -> Type.PULSE;
            case COMPACT -> Type.RAPID;
            case REINFORCED -> Type.LANCE;
            case OVERCHARGED -> Type.OVERDRIVE;
            case LOW_EMISSION -> Type.NEEDLE;
        };
    }
    public static Map<Integer,Type> fitted(Shipyard.Design design){
        if(design==null)throw new IllegalArgumentException("Laser fitting required");
        Map<Integer,Type> types=new HashMap<>();
        for(int slot=0;slot<design.slots();slot++){
            Shipyard.Installed installed=design.at(slot);
            if(installed!=null&&installed.component.endsWith("-LAS"))
                types.put(slot,fromPattern(installed.pattern));
        }
        return Map.copyOf(types);
    }
    /** No physical emitter: old simulation fixtures retain standard lasers. */
    public static Type selected(Battle.Ship ship,int slotId){
        return ship.laserFittings.getOrDefault(slotId,Type.PULSE);
    }
    public static String eventWeapon(Type type){
        return type==Type.PULSE?"LASER":"LASER_"+type.name();
    }
    public static Type fromWeapon(String label){
        if(label==null)return null;
        if(label.equals("LASER"))return Type.PULSE;
        if(!label.startsWith("LASER_"))return null;
        try{return Type.valueOf(label.substring(6));}
        catch(IllegalArgumentException unknown){return null;}
    }
}
