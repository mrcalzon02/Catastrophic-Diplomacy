package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts the actual fitted shipyard sockets to world-space muzzle points.
 * HullLayouts +Y is bow and +X is starboard. Battle heading zero is +world X;
 * therefore local starboard rotates into -world Y at heading zero.
 * Weapon origins are authoritative to projectiles and FIRE/beam visual events.
 */
public final class WeaponMuzzles {
    private WeaponMuzzles(){}
    public enum Family {
        LASER, KINETIC, PLASMA, PARTICLE, MISSILE, TORPEDO, POINT_DEFENSE
    }
    public record Emitter(int slotId,Family family,double localX,double localY) {}
    public record Origin(double x,double y,int slotId) {}
    public static Family family(String component){
        if(component==null)return null;
        int separator=component.lastIndexOf('-');
        String suffix=component.substring(separator+1);
        return switch(suffix){
            case "LAS" -> Family.LASER;
            case "KIN" -> Family.KINETIC;
            case "PLA" -> Family.PLASMA;
            case "PAR" -> Family.PARTICLE;
            case "MSL" -> Family.MISSILE;
            case "TOR" -> Family.TORPEDO;
            case "PD","FLK" -> Family.POINT_DEFENSE;
            default -> null;
        };
    }
    /** Keep only actual equipped weapon modules, not empty or defensive hull sockets. */
    public static List<Emitter> equipped(Shipyard.Design design,List<Shipyard.Slot> locations){
        if(design==null||locations==null)return List.of();
        List<Emitter> found=new ArrayList<>();
        for(Shipyard.Slot slot:locations){
            Shipyard.Installed part=design.at(slot.index());
            if(part==null)continue;
            Family family=family(part.component);
            if(family==null)continue;
            // The canonical HullLayouts positions already respect faction artwork.
            found.add(new Emitter(slot.index(),family,slot.x(),slot.y()));
        }
        return List.copyOf(found);
    }
    public static Origin at(Battle.Ship ship,Family family,long shotId){
        if(ship==null||family==null)throw new IllegalArgumentException("Ship and weapon family required");
        int matching=0;
        for(Emitter emitter:ship.weaponEmitters)if(emitter.family()==family)matching++;
        // Unfitted legacy test ships have no physically mounted hardpoints.
        if(matching==0)return new Origin(ship.x,ship.y,-1);
        int index=(int)Math.floorMod(shotId,matching);
        for(Emitter emitter:ship.weaponEmitters){
            if(emitter.family()!=family)continue;
            if(index-->0)continue;
            // 154 local longitudinal units correspond to a 34-unit displayed
            // hull at scale 1. This matches the canonical hull-art draw height.
            double unit=34.0/154.0*ship.visualScale();
            double c=StrictMath.cos(ship.facing),s=StrictMath.sin(ship.facing);
            double forward=emitter.localY()*unit;
            double starboard=emitter.localX()*unit;
            return new Origin(ship.x+forward*c+starboard*s,
                    ship.y+forward*s-starboard*c,emitter.slotId());
        }
        throw new IllegalStateException("Weapon emitter selection failed");
    }
}
