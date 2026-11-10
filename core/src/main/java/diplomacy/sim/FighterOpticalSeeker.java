package diplomacy.sim;

import java.util.List;

/**
 * Passive reflected-visible-light fighter missile head.
 *
 * No emissions, active radar returns, thermal heat, live fighter coordinates,
 * global contact relay, or free target reacquisition. Each optical detection
 * intersects a recorded target photon front with the receiver's actual
 * previous-to-current movement segment. The historical optical signature is
 * separate from the historical thermal emission.
 *
 * Chaff does not resemble an optical aircraft. An actually emitted FLARE can
 * temporarily saturate this head only when its light arrives along nearly
 * the same angular sightline as the historical fighter image; glare produces
 * NO fabricated coordinates, seeker diversion or free proximity fuze.
 */
public final class FighterOpticalSeeker {
    private FighterOpticalSeeker(){}
    public enum Head {
        NARROW_TELESCOPIC(290,.43,7),
        WIDE_FIELD(180,1.23,5);
        public final double range,halfAngleRadians;
        public final int scanTicks;
        Head(double range,double halfAngleRadians,int scanTicks){
            this.range=range;this.halfAngleRadians=halfAngleRadians;
            this.scanTicks=scanTicks;
        }
    }
    /** The base optics require a fitted tier-three FTR system. */
    public static Head installed(int tier){
        if(tier<0||tier>9)
            throw new IllegalArgumentException("Invalid optical FTR stage");
        if(tier<3)
            throw new IllegalArgumentException("Optical head requires paid tier three");
        return tier>=7?Head.WIDE_FIELD:Head.NARROW_TELESCOPIC;
    }
    private static boolean inView(Head head,double x,double y,
                                  double tx,double ty,double vx,double vy){
        double dx=tx-x,dy=ty-y;
        double range=StrictMath.hypot(dx,dy);
        if(range>head.range)return false;
        if(range<=1e-9)return true;
        double v=StrictMath.hypot(vx,vy);
        return v>1e-9&&(dx*vx+dy*vy)/(range*v)>=
                StrictMath.cos(head.halfAngleRadians);
    }
    /** Front outside at N-1, received by N. Reusing a passed photon
     * after receiver displacement is forbidden. */
    private static boolean arrived(double previous,double current,int age,double c){
        return age>0&&previous>c*(age-1)+1e-9&&current<=c*age+1e-9;
    }
    public static FighterInfraredSeeker.Reading receive(Head head,
            FighterInfraredSeeker.History history,
            List<FighterCountermeasures.Cloud> clouds,
            int targetCraftId,int missileTeam,
            double priorX,double priorY,double currentX,double currentY,
            double headingX,double headingY,long tick){
        return receive(head,history,clouds,targetCraftId,missileTeam,
                priorX,priorY,currentX,currentY,headingX,headingY,tick,
                SpaceWeather.clear());
    }
    /** Live battle variant: use the current encounter's own weather, NEVER
     * the environment or fighter pose from a future tick. */
    public static FighterInfraredSeeker.Reading receive(Head head,
            FighterInfraredSeeker.History history,
            List<FighterCountermeasures.Cloud> clouds,
            int targetCraftId,int missileTeam,
            double priorX,double priorY,double currentX,double currentY,
            double headingX,double headingY,long tick,SpaceWeather.Preset environment){
        if(head==null||history==null||clouds==null||environment==null||
                targetCraftId<=0||(missileTeam!=0&&missileTeam!=1)||tick<0||
                !Double.isFinite(priorX)||!Double.isFinite(priorY)||
                !Double.isFinite(currentX)||!Double.isFinite(currentY)||
                !Double.isFinite(headingX)||!Double.isFinite(headingY))
            throw new IllegalArgumentException("Invalid optical receiver worldline");
        double c=WorldScale.SIGNAL_C_WORLD_UNITS_PER_SIM_SECOND*Battle.DT;
        int horizon=StrictMath.min(FighterInfraredSeeker.History.CAPACITY-1,
                SensorLightTimeHistory.propagationTicks(
                        WorldScale.DIAGONAL_WORLD_UNITS)+2);
        FighterInfraredSeeker.Reading best=null;
        for(int age=1;age<=horizon&&age<=tick;age++){
            long emission=tick-age;
            var sample=history.at(emission);
            if(sample==null||sample.opticalContrast()<=0||
                    !inView(head,currentX,currentY,
                            sample.x(),sample.y(),headingX,headingY))
                continue;
            double r0=StrictMath.hypot(sample.x()-priorX,sample.y()-priorY);
            double r1=StrictMath.hypot(sample.x()-currentX,sample.y()-currentY);
            if(!arrived(r0,r1,age,c))continue;
            // Target silhouette, not IR heat or radar cross-section from a
            // future tick; finite visible-light contrast attenuates with range.
            double signal=sample.opticalContrast()/(1+r1*r1/30000)
                    *SpaceWeather.opticalTransmission(
                            environment,emission,tick,r1);
            if(signal<.035)continue;
            if(best==null||signal>best.strength())
                best=new FighterInfraredSeeker.Reading(
                        sample.x(),sample.y(),sample.vx(),sample.vy(),
                        emission,tick,0,signal);
        }
        if(best==null)return null;
        double targetRange=StrictMath.hypot(best.x()-currentX,best.y()-currentY);
        // Only close angular alignment of an independently arriving
        // historical bright flare can block the optical image. A flare is
        // NEVER selected as an aircraft or an optical proximity-fuze target.
        for(var cloud:clouds){
            if(cloud.kind()!=FighterCountermeasures.Kind.FLARE||
                    cloud.craftId()!=targetCraftId||cloud.team()==missileTeam)
                continue;
            int minAge=(int)StrictMath.max(1,
                    tick-(cloud.bornTick()+cloud.lifetimeTicks()-1));
            int maxAge=(int)StrictMath.min(StrictMath.min(horizon,tick),
                    tick-cloud.bornTick());
            for(int age=minAge;age<=maxAge;age++){
                long emission=tick-age;
                double luminous=cloud.signal(
                        FighterCountermeasures.Kind.FLARE,emission);
                if(luminous<=0)continue;
                double x=cloud.xAt(emission),y=cloud.yAt(emission);
                if(!BattleArena.contains(x,y)||
                        !inView(head,currentX,currentY,x,y,headingX,headingY))
                    continue;
                double r0=StrictMath.hypot(x-priorX,y-priorY);
                double r1=StrictMath.hypot(x-currentX,y-currentY);
                if(!arrived(r0,r1,age,c)||r1<=1e-9||
                        targetRange<=1e-9)continue;
                double dx=best.x()-currentX,dy=best.y()-currentY;
                double fx=x-currentX,fy=y-currentY;
                double dot=(dx*fx+dy*fy)/(targetRange*r1);
                double sin=StrictMath.abs(dx*fy-dy*fx)/(targetRange*r1);
                double flareSignal=luminous/(1+r1*r1/30000)
                        *SpaceWeather.opticalTransmission(
                                environment,emission,tick,r1);
                if(dot>0&&sin<.075&&
                        flareSignal>=best.strength()*1.25)
                    return null;
            }
        }
        return best;
    }
}
