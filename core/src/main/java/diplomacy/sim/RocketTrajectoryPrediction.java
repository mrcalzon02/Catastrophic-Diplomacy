package diplomacy.sim;

/**
 * Constant-bearing, unpowered-seeker rocket interception computed from a
 * RECEIVED tracking sample. Nothing reads a target entity or future AI state.
 *
 * This solver uses RocketBarrage.nextSpeed, the same discrete acceleration
 * law as real Battle.Rocket bodies. A terminal speed is NOT a constant flight
 * velocity, especially for boost/coast and researched sustained motors.
 * Pips are predictions, not guarantees of hitting evasive ships.
 */
public final class RocketTrajectoryPrediction {
    private RocketTrajectoryPrediction(){}
    public record Flight(double seconds,double travelled,boolean reachable) {}
    public record Intercept(double x,double y,double seconds,
                            double ageSeconds,double travelled,
                            boolean reachable) {}

    public static double travelled(RocketBarrage.Rack rack,double seconds){
        if(rack==null||!Double.isFinite(seconds)||seconds<0||
                seconds>RocketBarrage.MAX_FLIGHT_SECONDS)
            throw new IllegalArgumentException("Invalid rocket flight time");
        double speed=78,distance=0,time=0;
        int tick=0;
        // Truncate exactly at the requested fraction of the LAST tick,
        // while applying thrust at that step's beginning like real flight.
        while(time+1e-10<seconds){
            double step=StrictMath.min(Battle.DT,seconds-time);
            speed=RocketBarrage.nextSpeed(rack.size,rack.motor,tick++,
                    speed,rack.body.speed(),rack.sustainedThrustFactor);
            distance+=speed*step;
            time+=step;
        }
        return distance;
    }

    public static Flight timeToDistance(RocketBarrage.Rack rack,double distance){
        if(rack==null||!Double.isFinite(distance)||distance<0)
            throw new IllegalArgumentException("Invalid rocket intercept range");
        if(distance==0)return new Flight(0,0,true);
        double covered=0,speed=78;
        int steps=(int)StrictMath.ceil(RocketBarrage.MAX_FLIGHT_SECONDS/Battle.DT);
        for(int tick=0;tick<steps;tick++){
            speed=RocketBarrage.nextSpeed(rack.size,rack.motor,tick,
                    speed,rack.body.speed(),rack.sustainedThrustFactor);
            double next=covered+speed*Battle.DT;
            if(next>=distance){
                double fraction=(distance-covered)/StrictMath.max(1e-9,speed);
                return new Flight(tick*Battle.DT+fraction,distance,true);
            }
            covered=next;
        }
        return new Flight(RocketBarrage.MAX_FLIGHT_SECONDS,covered,false);
    }
    public static Intercept lead(double x,double y,double vx,double vy,
            long observedTick,long now,double originX,double originY,
            RocketBarrage.Rack rack){
        if(rack==null||observedTick<0||now<observedTick||
                !Double.isFinite(x)||!Double.isFinite(y)||
                !Double.isFinite(vx)||!Double.isFinite(vy)||
                !Double.isFinite(originX)||!Double.isFinite(originY))
            throw new IllegalArgumentException("Invalid causal rocket lead");
        double age=(now-observedTick)*Battle.DT;
        double seconds=0,range=0;
        double predictedX=x+vx*age,predictedY=y+vy*age;
        // Fixed iteration count keeps combat deterministic, bounded and cheap.
        // For an escaping target, the finite-life result is NOT an intercept.
        Flight flight=null;
        for(int iteration=0;iteration<5;iteration++){
            range=StrictMath.hypot(predictedX-originX,predictedY-originY);
            flight=timeToDistance(rack,range);
            seconds=flight.seconds();
            predictedX=x+vx*(age+seconds);
            predictedY=y+vy*(age+seconds);
        }
        range=StrictMath.hypot(predictedX-originX,predictedY-originY);
        flight=timeToDistance(rack,range);
        seconds=flight.seconds();
        predictedX=x+vx*(age+seconds);
        predictedY=y+vy*(age+seconds);
        return new Intercept(predictedX,predictedY,seconds,age,
                flight.travelled(),flight.reachable());
    }
}
