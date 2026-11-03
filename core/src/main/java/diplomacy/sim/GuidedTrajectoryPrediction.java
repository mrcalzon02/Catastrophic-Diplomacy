package diplomacy.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only guided-body flight forecast based on the live FRIENDLY body's own
 * measured state and its stored photon-delayed seeker estimate. Never queries
 * the target ship, future photons, AI orders, hull geometry or battle RNG.
 *
 * This assumes NO FUTURE seeker updates, weather or unforeseen maneuvers.
 * Predicted interception is possible geometry, NOT an authorized guaranteed
 * hit. Actual simulation still resolves all movement, PD and collision.
 */
public final class GuidedTrajectoryPrediction {
    private GuidedTrajectoryPrediction(){}
    /** At most 13 path points per evaluated missile; HUD evaluates at most 3. */
    public static final int PATH_SEGMENTS=12;
    public record Point(double x,double y,double seconds) {}
    public record Forecast(double closestX,double closestY,
                           double estimatedTargetX,double estimatedTargetY,
                           double closestDistance,double etaSeconds,
                           double uncertaintyRadius,boolean plausible,
                           List<Point> path){
        public Forecast{
            if(!Double.isFinite(closestX)||!Double.isFinite(closestY)||
                    !Double.isFinite(estimatedTargetX)||
                    !Double.isFinite(estimatedTargetY)||
                    !Double.isFinite(closestDistance)||closestDistance<0||
                    !Double.isFinite(etaSeconds)||etaSeconds<0||
                    !Double.isFinite(uncertaintyRadius)||uncertaintyRadius<0||
                    path==null||path.isEmpty()||path.size()>PATH_SEGMENTS+2)
                throw new IllegalArgumentException("Invalid guided flight forecast");
            path=List.copyOf(path);
        }
    }
    public static Forecast predict(Battle.Missile body,long now){
        if(body==null||now<body.spawnTick||now<body.lastObservationTick||
                !Double.isFinite(body.fuel)||!Double.isFinite(body.propellant)||
                body.propellant<0||!Double.isFinite(body.seekerUncertainty)||
                body.seekerUncertainty<0)
            throw new IllegalArgumentException("Invalid observable guided body");
        double x=body.x,y=body.y,vx=body.vx,vy=body.vy;
        double propellant=body.propellant;
        double age=(now-body.lastObservationTick)*Battle.DT;
        double targetVX=body.lastKnownVX,targetVY=body.lastKnownVY;
        double targetX=body.lastKnownX+targetVX*age;
        double targetY=body.lastKnownY+targetVY*age;
        double nearest=StrictMath.hypot(targetX-x,targetY-y),eta=0;
        double nearX=x,nearY=y,nearTargetX=targetX,nearTargetY=targetY;
        double maxFlight=StrictMath.max(0,StrictMath.min(body.fuel,
                BattleArena.MAX_GUIDED_SECONDS-(now-body.spawnTick)*Battle.DT));
        int steps=StrictMath.min(600,(int)StrictMath.floor(maxFlight/Battle.DT+1e-9));
        int sampleEvery=StrictMath.max(1,(steps+PATH_SEGMENTS-1)/PATH_SEGMENTS);
        List<Point> points=new ArrayList<>(PATH_SEGMENTS+2);
        points.add(new Point(x,y,0));
        int ignition=body.ignitionDelayTicks;
        for(int step=1;step<=steps;step++){
            double time=step*Battle.DT;
            targetX=body.lastKnownX+targetVX*(age+time);
            targetY=body.lastKnownY+targetVY*(age+time);
            double distance=StrictMath.hypot(targetX-x,targetY-y);
            var phase=OrdnancePropulsion.phase(body.propulsionMode,
                    body.flightTicks+step,ignition,distance,body.torpedo);
            if(ignition>0)ignition--;
            if(propellant<=1e-9&&OrdnancePropulsion.burning(phase))
                phase=OrdnancePropulsion.Phase.COAST;
            double guidance=StrictMath.min(.35,
                    WeaponBehaviorTable.gun(body.torpedo?
                            Battle.DamageType.TORPEDO:Battle.DamageType.MISSILE)
                            .guidancePerTick()*body.guidanceQuality*
                    OrdnancePropulsion.guidanceMultiplier(phase)*
                    (body.seekerLocked?1:0));
            var steering=OrdnanceGuidance.aim(body.guidanceMode,x,y,vx,vy,
                    targetX,targetY,targetVX,targetVY,
                    body.flightTicks+step,body.approachSide);
            var next=OrdnancePropulsion.advanceVelocity(vx,vy,
                    steering.dx(),steering.dy(),body.spec.speed(),
                    guidance,phase,Battle.DT,propellant);
            propellant=StrictMath.max(0,propellant-
                    StrictMath.hypot(next.vx()-vx,next.vy()-vy));
            vx=next.vx();vy=next.vy();
            x+=vx*Battle.DT;y+=vy*Battle.DT;
            double miss=StrictMath.hypot(targetX-x,targetY-y);
            if(miss<nearest){
                nearest=miss;eta=time;nearX=x;nearY=y;
                nearTargetX=targetX;nearTargetY=targetY;
            }
            if(step%sampleEvery==0||step==steps)
                points.add(new Point(x,y,time));
            if(!BattleArena.contains(x,y))break;
        }
        double measuredSpeed=StrictMath.hypot(targetVX,targetVY);
        // Future target maneuvers and imperfect velocity estimation can only
        // expand the envelope. No unknown future acceleration is invented.
        double sigma=StrictMath.min(240,
                body.seekerUncertainty+(age+eta)*
                (0.85+measuredSpeed*.045)+.4*eta*eta);
        // This is an estimated near-intersection envelope, not actual hull
        // overlap or a hit roll. Real radius/collision remain in Battle.
        boolean plausible=nearest<=13+sigma;
        return new Forecast(nearX,nearY,nearTargetX,nearTargetY,
                nearest,eta,sigma,plausible,points);
    }
}
