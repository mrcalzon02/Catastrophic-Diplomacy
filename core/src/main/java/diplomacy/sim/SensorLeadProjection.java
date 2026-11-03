package diplomacy.sim;

/**
 * A read-only target-leading pip and straight-flight trajectory derived ONLY
 * from a received/stored tactical contact. It never looks up target entities,
 * their present pose, hidden AI, or their future maneuvers.
 *
 * The pip marks the mean estimate, not a guaranteed impact. Uncertainty
 * widens with contact age, range and flight time. Renderers should label
 * stale tracks and cap visual pips to avoid clutter in 200v200 engagements.
 */
public final class SensorLeadProjection {
    private SensorLeadProjection(){}
    public record Lead(int targetId,double observedX,double observedY,
            double launchX,double launchY,double predictedX,double predictedY,
            double impactX,double impactY,double flightSeconds,
            double contactAgeSeconds,double uncertaintyRadius,
            long observedTick,boolean stale) {
        public Lead {
            if(targetId<=0||!Double.isFinite(predictedX)||
                    !Double.isFinite(predictedY)||!Double.isFinite(impactX)||
                    !Double.isFinite(impactY)||!Double.isFinite(flightSeconds)||
                    flightSeconds<0||!Double.isFinite(uncertaintyRadius)||
                    uncertaintyRadius<0)
                throw new IllegalArgumentException("Invalid lead pip");
        }
        public double trajectoryX(double fraction){
            double u=StrictMath.max(0,StrictMath.min(1,fraction));
            return launchX+(impactX-launchX)*u;
        }
        public double trajectoryY(double fraction){
            double u=StrictMath.max(0,StrictMath.min(1,fraction));
            return launchY+(impactY-launchY)*u;
        }
    }
    public static Lead predict(Battle.TacticalContact contact,long now,
            double shooterX,double shooterY,double projectileSpeed,
            double precision){
        if(contact==null||now<contact.observedTick()||
                !Double.isFinite(shooterX)||!Double.isFinite(shooterY)||
                !Double.isFinite(projectileSpeed)||projectileSpeed<=0||
                !Double.isFinite(precision)||precision<0)
            throw new IllegalArgumentException("Need received track and physical weapon speed");
        double age=(now-contact.observedTick())*Battle.DT;
        double futureX=contact.x()+contact.vx()*age;
        double futureY=contact.y()+contact.vy()*age;
        double flight=0;
        for(int i=0;i<3;i++){
            flight=StrictMath.hypot(futureX-shooterX,futureY-shooterY)/projectileSpeed;
            futureX=contact.x()+contact.vx()*(age+flight);
            futureY=contact.y()+contact.vy()*(age+flight);
        }
        flight=StrictMath.hypot(futureX-shooterX,futureY-shooterY)/projectileSpeed;
        double distance=StrictMath.hypot(futureX-shooterX,futureY-shooterY);
        double speed=StrictMath.hypot(contact.vx(),contact.vy());
        int channels=contact.channels();
        double channelFactor=contact.stale()?1.65:
                (channels&Battle.CONTACT_XRAY)!=0?.70:
                (channels&Battle.CONTACT_RADAR)!=0?.86:
                (channels&Battle.CONTACT_OPTICAL)!=0?1.0:
                (channels&Battle.CONTACT_INFRARED)!=0?1.12:1.2;
        double uncertainty=(2+distance*.008+
                age*(2+speed*.09)+flight*(.5+speed*.06))*
                channelFactor/(1+.65*precision)/
                StrictMath.sqrt(StrictMath.max(1,Integer.bitCount(channels)));
        return new Lead(contact.targetId(),contact.x(),contact.y(),
                shooterX,shooterY,futureX,futureY,futureX,futureY,
                flight,age,uncertainty,contact.observedTick(),contact.stale());
    }
}
