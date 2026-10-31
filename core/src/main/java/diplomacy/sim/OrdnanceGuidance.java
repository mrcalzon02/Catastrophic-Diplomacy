package diplomacy.sim;

/**
 * Deterministic guided-ordnance steering plans. Produces a desired aim vector;
 * the propulsion integrator remains authoritative about acceleration, remaining
 * delta-v and whether the engine can actually turn the body. No random numbers,
 * global target searches or teleportation.
 */
public final class OrdnanceGuidance {
    private OrdnanceGuidance(){}
    public enum Mode { PURSUIT, LEAD_INTERCEPT, DOGLEG, PINCER, EVASIVE_WEAVE }
    public record Aim(double dx,double dy) {}

    public static Aim aim(Mode mode,double missileX,double missileY,
                          double missileVX,double missileVY,
                          double targetX,double targetY,double targetVX,double targetVY,
                          int flightTicks,int side){
        if(mode==null||flightTicks<0||(side!=1&&side!=-1)
                ||!Double.isFinite(missileX)||!Double.isFinite(missileY)
                ||!Double.isFinite(missileVX)||!Double.isFinite(missileVY)
                ||!Double.isFinite(targetX)||!Double.isFinite(targetY)
                ||!Double.isFinite(targetVX)||!Double.isFinite(targetVY))
            throw new IllegalArgumentException("Invalid ordnance guidance state");
        double dx=targetX-missileX,dy=targetY-missileY;
        double distance=StrictMath.hypot(dx,dy);
        if(mode==Mode.PURSUIT||distance<1e-9)return new Aim(dx,dy);
        // Bounded intercept estimation: never assume infinite target knowledge.
        double ownSpeed=StrictMath.max(45,StrictMath.hypot(missileVX,missileVY));
        double seconds=StrictMath.min(2.5,distance/ownSpeed);
        double aimX=targetX+targetVX*seconds;
        double aimY=targetY+targetVY*seconds;
        if(mode==Mode.LEAD_INTERCEPT)return new Aim(aimX-missileX,aimY-missileY);
        // Offset corridors gradually collapse to a terminal attack. These
        // desired offsets do not grant free course changes while coasting.
        double px=-dy/distance,py=dx/distance;
        double lateral=0;
        switch(mode){
            case DOGLEG -> {
                if(flightTicks<48&&distance>175)
                    lateral=side*StrictMath.min(170,distance*.32);
            }
            case PINCER -> {
                if(distance>145)lateral=side*StrictMath.min(125,distance*.25);
            }
            case EVASIVE_WEAVE -> {
                if(distance>100)lateral=((flightTicks/16)%2==0?1:-1)
                        *side*StrictMath.min(75,distance*.17);
            }
            default -> {}
        }
        return new Aim(aimX+px*lateral-missileX,aimY+py*lateral-missileY);
    }
}
