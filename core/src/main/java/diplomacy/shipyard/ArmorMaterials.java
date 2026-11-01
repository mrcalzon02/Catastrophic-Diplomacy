package diplomacy.shipyard;

import diplomacy.sim.Battle;

/**
 * Armor material interactions on the authoritative physical armor layer.
 * Factors below one are resistance, above one are vulnerability. These do
 * not affect shields or the underlying hull after a plate is depleted.
 * A reactive-cell unit is finite because its physical armor capacity wears
 * down under actual hits; no free cooldown refresh is introduced.
 */
public final class ArmorMaterials {
    private ArmorMaterials(){}
    public static boolean isPlating(String code){
        return "ARM".equals(code)||"CMP".equals(code)||"ABL".equals(code)
                ||"DEN".equals(code)||"RAC".equals(code);
    }
    public static double factor(String code,Battle.DamageType type,
                                Shipyard.Track track,int rank){
        if(!isPlating(code)||type==null||rank<0||rank>9||
                (track==null&&rank!=0)||(track!=null&&rank==0))
            throw new IllegalArgumentException("Invalid armor material or research");
        double value=switch(code){
            case "ARM" -> 1.0; // Existing legacy armor remains unchanged.
            case "CMP" -> switch(type){
                case LASER -> .88;case KINETIC -> .89;case PLASMA -> .90;
                case PARTICLE -> .93;case MISSILE -> .91;case TORPEDO -> .95;
                case FIGHTER -> .90;
            };
            case "ABL" -> switch(type){
                case LASER -> .56;case PLASMA -> .61;case PARTICLE -> .85;
                case KINETIC -> 1.17;case MISSILE -> 1.13;case TORPEDO -> 1.20;
                case FIGHTER -> 1.08;
            };
            case "DEN" -> switch(type){
                case KINETIC -> .54;case MISSILE -> .77;case TORPEDO -> .80;
                case FIGHTER -> .75;case LASER -> 1.17;case PLASMA -> 1.18;
                case PARTICLE -> 1.12;
            };
            case "RAC" -> switch(type){
                case MISSILE -> .50;case TORPEDO -> .55;case KINETIC -> .82;
                case FIGHTER -> .78;case LASER -> 1.20;case PLASMA -> 1.22;
                case PARTICLE -> 1.16;
            };
            default -> throw new IllegalArgumentException("Unknown armor material "+code);
        };
        // One exclusive upgrade path per installed armor module. Increased
        // material resistance competes with OUTPUT and EFFICIENCY upgrades.
        if(!"ARM".equals(code) && rank>0){
            if(track==Shipyard.Track.RESILIENCE)value-=.012*rank;
            if(track==Shipyard.Track.SPECIALIST)value-=.018*rank;
        }
        return StrictMath.max(.30,StrictMath.min(1.8,value));
    }
}
