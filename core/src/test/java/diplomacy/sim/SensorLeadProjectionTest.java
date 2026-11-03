package diplomacy.sim;

/** Read-only in-game target lead pips must share causal fire-control math. */
public final class SensorLeadProjectionTest {
    private SensorLeadProjectionTest(){}
    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }
    private static Battle.TacticalContact contact(long tick,int channels,boolean stale){
        return new Battle.TacticalContact(47,4,channels,
                600,100,0,12,tick,stale?0:.7f,false,stale);
    }
    public static void main(String[] args){
        long now=80;
        var mark=contact(40,Battle.CONTACT_RADAR,false);
        var pip=SensorLeadProjection.predict(mark,now,100,100,400,1);
        require(pip.predictedY()>mark.y()&&pip.flightSeconds()>0,
                "Maneuvering contact did not receive physical forward lead");
        require(pip.predictedX()==600&&pip.observedY()==100,
                "Pip substituted unknown current target trajectory");
        require(pip.trajectoryX(0)==100&&pip.trajectoryY(0)==100&&
                pip.trajectoryX(1)==pip.impactX()&&
                pip.trajectoryY(1)==pip.impactY(),
                "Visible projectile path has wrong launch and intercept endpoints");
        var fast=SensorLeadProjection.predict(mark,now,100,100,900,1);
        require(fast.flightSeconds()<pip.flightSeconds()&&
                fast.predictedY()<pip.predictedY(),
                "Different physical projectile speeds did not change target lead");
        var older=SensorLeadProjection.predict(contact(10,Battle.CONTACT_RADAR,false),
                now,100,100,400,1);
        require(older.uncertaintyRadius()>pip.uncertaintyRadius(),
                "Older photon measurements did not enlarge uncertainty");
        var reduced=SensorLeadProjection.predict(mark,now,100,100,400,3);
        require(reduced.uncertaintyRadius()<pip.uncertaintyRadius(),
                "More targeting computation failed to narrow the pip uncertainty");
        var stale=SensorLeadProjection.predict(contact(40,0,true),
                now,100,100,400,1);
        require(stale.uncertaintyRadius()>pip.uncertaintyRadius()&&stale.stale(),
                "Dead-reckoned historical fix was drawn like an active lock");
        var synthetic=new SensorLightTimeHistory.Observation(38,40,80,
                Battle.CONTACT_RADAR,
                new SensorLightTimeHistory.Sample(40,600,100,0,12,true));
        var actual=SensorTargetingSolution.solve(
                synthetic,now,100,100,400,1,9717);
        require(StrictMath.hypot(actual.predictedX()-pip.predictedX(),
                actual.predictedY()-pip.predictedY())<.01,
                "Displayed mean target pip disagrees with actual kinetic fire-control");
        require(pip.equals(SensorLeadProjection.predict(mark,now,
                100,100,400,1)),
                "Read-only trajectory prediction is nondeterministic");
        System.out.println("PASS velocity lead pips, age/compute uncertainty, physical trajectory and firing-solution parity");
    }
}
