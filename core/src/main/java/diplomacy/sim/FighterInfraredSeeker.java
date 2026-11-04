package diplomacy.sim;

import java.util.List;

/**
 * Two real passive missile seeker heads for fighter interception. Every
 * observation is a historical light-front arrival at the missile's moving
 * receiver, never an enemy craft's current hidden position. Infrared
 * cartridge clouds and the assigned fighter compete in the SAME band.
 */
public final class FighterInfraredSeeker {
    private FighterInfraredSeeker(){}
    public enum Head {
        FIXED_FORWARD(240,.58,6),
        WIDE_ANGLE(165,1.42,5);
        public final double range,halfAngleRadians;
        public final int scanTicks;
        Head(double range,double halfAngleRadians,int scanTicks){
            this.range=range;this.halfAngleRadians=halfAngleRadians;
            this.scanTicks=scanTicks;
        }
    }
    public static Head installed(int tier){
        if(tier<0||tier>9)throw new IllegalArgumentException("Invalid FTR seeker research");
        return tier>=5?Head.WIDE_ANGLE:Head.FIXED_FORWARD;
    }
    public static int interceptorMagazine(FighterModel.Hull hull,int tier){
        if(hull==null||tier<0||tier>9)
            throw new IllegalArgumentException("Invalid interceptor fitting");
        return hull==FighterModel.Hull.INTERCEPTOR?2+tier/3:0;
    }
    /** One immutable historical fighter emission and motion sample. Optical
     * contrast is recorded separately from thermal heat: optical seekers
     * never use the craft's current live state or its infrared signature. */
    public record Frame(long tick,double x,double y,double vx,double vy,
                        double heat,double opticalContrast){
        public Frame {
            if(tick<0||!Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(vx)||!Double.isFinite(vy)||
                    !Double.isFinite(heat)||heat<=0||
                    !Double.isFinite(opticalContrast)||opticalContrast<0)
                throw new IllegalArgumentException("Invalid historical fighter emission");
        }
        /** Existing deterministic infrared-only test fixtures remain valid. */
        public Frame(long tick,double x,double y,double vx,double vy,double heat){
            this(tick,x,y,vx,vy,heat,1.0);
        }
    }
    public static final class History {
        public static final int CAPACITY=64;
        private final Frame[] frames=new Frame[CAPACITY];
        public void capture(long tick,Battle.Craft craft){
            if(tick<0||craft==null||!craft.fighter||craft.integrity<=0)
                throw new IllegalArgumentException("Cannot record dead/non-fighter light source");
            // Engines and craft fittings set the physical relative emission.
            frames[(int)(tick%CAPACITY)]=new Frame(tick,
                    craft.x,craft.y,craft.observedVx,craft.observedVy,
                    craft.profile.signature(),
                    craft.profile.opticalContrast());
        }
        public Frame at(long tick){
            if(tick<0)return null;
            Frame f=frames[(int)(tick%CAPACITY)];
            return f!=null&&f.tick()==tick?f:null;
        }
    }
    public record Reading(double x,double y,double vx,double vy,
                          long emittedTick,long receivedTick,long flareId,
                          double strength){
        public Reading {
            if(!Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(vx)||!Double.isFinite(vy)||
                    !Double.isFinite(strength)||strength<=0||
                    emittedTick<0||receivedTick<emittedTick||flareId<0)
                throw new IllegalArgumentException("Invalid received seeker photons");
        }
        public boolean decoy(){return flareId>0;}
    }
    private static boolean inView(Head head,double fromX,double fromY,
                                 double toX,double toY,double headingX,
                                 double headingY){
        double dx=toX-fromX,dy=toY-fromY;
        double range=StrictMath.hypot(dx,dy);
        if(range>head.range)return false;
        if(range<1e-8)return true;
        double speed=StrictMath.hypot(headingX,headingY);
        if(speed<1e-8)return false;
        return (dx*headingX+dy*headingY)/(range*speed)>=
                StrictMath.cos(head.halfAngleRadians);
    }
    /** One actual previous-to-current missile receiver segment. A photon
     * front which passed LAST tick cannot be reobserved as a new return.
     * Receivers never inspect an unrecorded future enemy position.
     *
     * Emitters compete by physically received band strength divided by
     * finite spreading; flares only count after their own emissions arrive.
     */
    public static Reading receive(Head head,History history,
                                  List<FighterCountermeasures.Cloud> clouds,
                                  int targetCraftId,int missileTeam,
                                  double priorX,double priorY,
                                  double currentX,double currentY,
                                  double headingX,double headingY,long tick){
        if(head==null||history==null||clouds==null||targetCraftId<=0||
                missileTeam<0||missileTeam>1||tick<0||
                !Double.isFinite(priorX)||!Double.isFinite(priorY)||
                !Double.isFinite(currentX)||!Double.isFinite(currentY)||
                !Double.isFinite(headingX)||!Double.isFinite(headingY))
            throw new IllegalArgumentException("Invalid missile IR receiver worldline");
        int limit=StrictMath.min(History.CAPACITY-1,
                SensorLightTimeHistory.propagationTicks(
                        WorldScale.DIAGONAL_WORLD_UNITS)+2);
        double lightStep=WorldScale.C_WORLD_UNITS_PER_SIM_SECOND*Battle.DT;
        Reading best=null;
        double brightest=0;
        for(int age=1;age<=limit&&age<=tick;age++){
            long emission=tick-age;
            Frame real=history.at(emission);
            if(real!=null&&inView(head,currentX,currentY,
                    real.x(),real.y(),headingX,headingY)){
                double r0=StrictMath.hypot(real.x()-priorX,real.y()-priorY);
                double r1=StrictMath.hypot(real.x()-currentX,real.y()-currentY);
                if(r0>lightStep*(age-1)+1e-9&&
                        r1<=lightStep*age+1e-9){
                    double received=real.heat()/(1+r1*r1/25000);
                    if(received>brightest){
                        brightest=received;
                        best=new Reading(real.x(),real.y(),real.vx(),real.vy(),
                                emission,tick,0,received);
                    }
                }
            }
        }
        // Fleet-scale cost: filter the global cloud list ONCE for this
        // assigned fighter. Only actual matching flare emitters require a
        // bounded historical worldline scan. Complexity is O(history +
        // clouds + matchingFlareHistory), not O(history * allClouds).
        for(var cloud:clouds){
            if(cloud.kind()!=FighterCountermeasures.Kind.FLARE||
                    cloud.team()==missileTeam||
                    cloud.craftId()!=targetCraftId)continue;
            int minAge=(int)StrictMath.max(1,
                    tick-(cloud.bornTick()+cloud.lifetimeTicks()-1));
            int maxAge=(int)StrictMath.min(StrictMath.min(limit,tick),
                    tick-cloud.bornTick());
            for(int age=minAge;age<=maxAge;age++){
                long emission=tick-age;
                double power=cloud.signal(FighterCountermeasures.Kind.FLARE,emission);
                if(power<=0)continue;
                double cx=cloud.xAt(emission),cy=cloud.yAt(emission);
                if(!BattleArena.contains(cx,cy)||
                        !inView(head,currentX,currentY,cx,cy,
                                headingX,headingY))continue;
                double r0=StrictMath.hypot(cx-priorX,cy-priorY);
                double r1=StrictMath.hypot(cx-currentX,cy-currentY);
                if(r0<=lightStep*(age-1)+1e-9||
                        r1>lightStep*age+1e-9)continue;
                double received=power/(1+r1*r1/25000);
                if(received>brightest){
                    brightest=received;
                    best=new Reading(cx,cy,cloud.vx(),cloud.vy(),
                            emission,tick,cloud.id(),received);
                }
            }
        }
        return best;
    }
}
