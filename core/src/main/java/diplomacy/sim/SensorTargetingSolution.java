package diplomacy.sim;

/**
 * Reproducible finite targeting estimate made from an ARRIVED contact only.
 *
 * This is an actual aim coordinate, not an independent random hit percentage.
 * Unguided ballistic collision remains decided by the existing swept physical
 * intersection. An inaccurate solution can still intersect a ship's hull.
 * Computing the estimate never inspects the target entity or its live pose.
 */
public final class SensorTargetingSolution {
    private SensorTargetingSolution(){}
    public record Solution(double aimX,double aimY,double predictedX,
            double predictedY,double uncertaintyRadius,double ageSeconds,
            double flightSeconds,long measuredTick) {
        public Solution {
            if(!Double.isFinite(aimX)||!Double.isFinite(aimY)||
                    !Double.isFinite(predictedX)||!Double.isFinite(predictedY)||
                    !Double.isFinite(uncertaintyRadius)||uncertaintyRadius<0||
                    !Double.isFinite(ageSeconds)||ageSeconds<0||
                    !Double.isFinite(flightSeconds)||flightSeconds<0)
                throw new IllegalArgumentException("Nonphysical targeting solution");
        }
    }
    private static long mix(long value){
        value^=value>>>33;value*=0xff51afd7ed558ccdL;
        value^=value>>>33;value*=0xc4ceb9fe1a85ec53L;
        return value^(value>>>33);
    }
    private static double unit(long bits){
        return (bits>>>11)*0x1.0p-53;
    }
    /**
     * Physical world-space location from measured position, measured velocity,
     * measurement age, approximate projectile flight time and finite precision.
     * Channels represent observed evidence; multiple independent returns reduce
     * uncertainty, but no installed compute or real observation gives perfect aim.
     */
    public static Solution solve(SensorLightTimeHistory.Observation report,
            long now,double shooterX,double shooterY,double projectileSpeed,
            double computePrecision,long shotId){
        if(report==null||report.channels()==0||now<report.receivedTick()||
                now<report.measuredTick()||shotId<=0||
                !Double.isFinite(shooterX)||!Double.isFinite(shooterY)||
                !Double.isFinite(projectileSpeed)||projectileSpeed<=0||
                !Double.isFinite(computePrecision)||computePrecision<0)
            throw new IllegalArgumentException("Invalid received fire-control input");
        SensorLightTimeHistory.Sample sample=report.sample();
        double age=(now-report.measuredTick())*Battle.DT;
        double leadX=sample.x()+sample.vx()*age;
        double leadY=sample.y()+sample.vy()*age;
        double flight=0;
        // Fixed-cost two-pass linear motion extrapolation, not live enemy AI.
        for(int iteration=0;iteration<2;iteration++){
            flight=StrictMath.hypot(leadX-shooterX,leadY-shooterY)/projectileSpeed;
            leadX=sample.x()+sample.vx()*(age+flight);
            leadY=sample.y()+sample.vy()*(age+flight);
        }
        flight=StrictMath.hypot(leadX-shooterX,leadY-shooterY)/projectileSpeed;
        int channels=report.channels();
        double channelWeight=(channels&Battle.CONTACT_XRAY)!=0?.70:
                (channels&Battle.CONTACT_RADAR)!=0?.86:
                (channels&Battle.CONTACT_OPTICAL)!=0?1.0:
                (channels&Battle.CONTACT_INFRARED)!=0?1.12:1.20;
        double distance=StrictMath.hypot(leadX-shooterX,leadY-shooterY);
        double measuredSpeed=StrictMath.hypot(sample.vx(),sample.vy());
        double sigma=(2.0+distance*.008
                +age*(2.0+measuredSpeed*.09)
                +flight*(.5+measuredSpeed*.06))*channelWeight/
                (1+.65*computePrecision)/
                StrictMath.sqrt(Integer.bitCount(channels));
        // Hashes are isolated from the combat RNG stream and do not reveal
        // current target state. Uniform disk dispersion permits near misses,
        // actual physical hits and grazing intersections.
        long seed=mix(shotId*0x9e3779b97f4a7c15L^
                report.measuredTick()*0xc2b2ae3d27d4eb4fL^
                Double.doubleToLongBits(sample.x())^
                Double.doubleToLongBits(sample.y()));
        double angle=2*StrictMath.PI*unit(seed);
        double radial=StrictMath.sqrt(unit(mix(seed^0x632be59bd9b4e019L)));
        double error=sigma*radial;
        return new Solution(leadX+StrictMath.cos(angle)*error,
                leadY+StrictMath.sin(angle)*error,
                leadX,leadY,sigma,age,flight,report.measuredTick());
    }
}
