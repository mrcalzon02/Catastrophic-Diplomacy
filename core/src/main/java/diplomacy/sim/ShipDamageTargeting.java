package diplomacy.sim;

import diplomacy.shipyard.Shipyard;
import java.util.List;

/**
 * Deterministic, module-biased surface coordinates. Ship-local +X points toward
 * the bow and +Y toward port. Shipyard hardpoints use +Y bow / +X starboard.
 * An impact gets one real, equipped module neighborhood (not a center-of-mass
 * dartboard) plus bounded scatter. Physics collision points take precedence.
 */
public final class ShipDamageTargeting {
    public record Point(double x,double y,int slotIndex) {
        public Point {
            if(!Double.isFinite(x)||!Double.isFinite(y))
                throw new IllegalArgumentException("Nonfinite surface point");
        }
    }
    private ShipDamageTargeting(){}

    private static long mix(long value){
        value^=value>>>30;value*=0xbf58476d1ce4e5b9L;
        value^=value>>>27;value*=0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    private static double fraction(long value){
        return ((mix(value)>>>11)*0x1.0p-53);
    }
    private static double clip(double value,double limit){
        return StrictMath.max(-limit,StrictMath.min(limit,value));
    }
    /** Physical projectile strike measured against the hull, not the world center. */
    public static Point physical(double hitX,double hitY,double heading){
        if(!Double.isFinite(hitX)||!Double.isFinite(hitY)||!Double.isFinite(heading))
            throw new IllegalArgumentException("Nonfinite physical strike");
        double c=StrictMath.cos(heading),s=StrictMath.sin(heading);
        double x=hitX*c+hitY*s,y=-hitX*s+hitY*c;
        // Physical projectile radius is small compared with real ship dimensions.
        return new Point(clip(x/25.0,0.92),clip(y/19.0,0.72),-1);
    }
    public static Point module(List<Shipyard.Slot> installed,double towardX,double towardY,
                               long salt){
        if(!Double.isFinite(towardX)||!Double.isFinite(towardY))
            throw new IllegalArgumentException("Nonfinite incoming bearing");
        if(installed==null||installed.isEmpty()){
            double length=StrictMath.hypot(towardX,towardY);
            return new Point(length<1e-9?0:clip(towardX/length*0.72,0.82),
                    length<1e-9?0:clip(towardY/length*0.55,0.65),-1);
        }
        double length=StrictMath.max(1e-9,StrictMath.hypot(towardX,towardY));
        double dx=towardX/length,dy=towardY/length;
        Shipyard.Slot best=null;double score=Double.NEGATIVE_INFINITY;
        for(Shipyard.Slot slot:installed){
            // A module on the exposed side is more likely, but not guaranteed.
            double x=clip(slot.y()/72.0,0.85),y=clip(-slot.x()/66.0,0.68);
            double facing=(x*dx+y*dy);
            double dice=fraction(salt ^ ((long)slot.index()*0x9e3779b97f4a7c15L));
            double choice=dice*1.55+facing*0.85;
            if(choice>score){score=choice;best=slot;}
        }
        long scatter=mix(salt^((long)best.index()<<32));
        double x=clip(best.y()/72.0+(fraction(scatter)-0.5)*0.20,0.89);
        double y=clip(-best.x()/66.0+(fraction(scatter+17)-0.5)*0.20,0.71);
        return new Point(x,y,best.index());
    }
    /** Impact footprint in armor-tile units. A torpedo peels a wider patch. */
    public static double radius(Battle.DamageType type){
        return switch(type){
            case LASER,PARTICLE -> 1.15;
            case KINETIC,FIGHTER -> 1.65;
            case PLASMA -> 2.15;
            case MISSILE -> 2.50;
            case TORPEDO -> 3.15;
        };
    }
}
