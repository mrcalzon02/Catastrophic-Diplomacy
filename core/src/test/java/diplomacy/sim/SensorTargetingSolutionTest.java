package diplomacy.sim;

/**
 * Detects instantaneous target truth, non-causal lead, misleading certainty
 * and "wrong estimate always misses" regressions without any art dependency.
 */
public final class SensorTargetingSolutionTest {
    private SensorTargetingSolutionTest(){}
    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }
    private static SensorLightTimeHistory.Observation report(long measured,
            long received,int channels,double x,double y,double vx,double vy){
        var sample=new SensorLightTimeHistory.Sample(measured,x,y,vx,vy,true);
        return new SensorLightTimeHistory.Observation(
                measured-2,measured,received,channels,sample);
    }
    private static void deterministic(){
        var evidence=report(50,60,Battle.CONTACT_RADAR,1500,0,12,0);
        var shot=SensorTargetingSolution.solve(evidence,70,0,0,500,1.4,711);
        var replay=SensorTargetingSolution.solve(evidence,70,0,0,500,1.4,711);
        require(shot.equals(replay),"Targeting estimation is not deterministic");
        require(shot.measuredTick()==50&&shot.ageSeconds()>0,
                "Estimated track silently became fresh target truth");
        require(shot.predictedX()>1500+12*shot.ageSeconds(),
                "Ballistic firing estimate failed to lead observed target velocity");
        require(shot.uncertaintyRadius()>0,
                "Information age and computation incorrectly guarantee perfect aim");
        var better=SensorTargetingSolution.solve(evidence,70,0,0,500,4.0,711);
        require(better.uncertaintyRadius()<shot.uncertaintyRadius(),
                "Higher tracking computation failed to narrow uncertainty");
        var older=SensorTargetingSolution.solve(evidence,100,0,0,500,1.4,711);
        require(older.uncertaintyRadius()>shot.uncertaintyRadius(),
                "Old contact did not increase target uncertainty");
        var xray=SensorTargetingSolution.solve(
                report(50,60,Battle.CONTACT_XRAY,1500,0,12,0),
                70,0,0,500,1.4,711);
        require(xray.uncertaintyRadius()<shot.uncertaintyRadius(),
                "High-resolution X-ray failed to improve target solution");
        System.out.println("PASS bounded deterministic aim, measured velocity lead and finite compute benefit");
    }
    private static void partialErrorsStillStrike(){
        var contact=report(50,60,Battle.CONTACT_OPTICAL,1500,0,0,0);
        int geometricalHits=0,geometricalMisses=0,offCenterHits=0;
        // Actual ballistic tests below consume the same physical ray; this
        // checks that a nonzero aim error does not force a miss by decree.
        for(int shot=1;shot<=160;shot++){
            var estimate=SensorTargetingSolution.solve(
                    contact,70,0,0,500,0,shot);
            double travelX=estimate.aimX(),travelY=estimate.aimY();
            double rayLength=StrictMath.hypot(travelX,travelY);
            double missDistance=StrictMath.abs(1500*travelY/rayLength);
            if(missDistance<=12){
                geometricalHits++;
                if(StrictMath.hypot(estimate.aimX()-1500,
                        estimate.aimY())>0.01)offCenterHits++;
            }else geometricalMisses++;
        }
        require(geometricalHits>0&&geometricalMisses>0&&offCenterHits>0,
                "Imperfect target estimates must yield both intersections and misses");
        System.out.println("PASS imperfect aim can intersect real 12-unit hulls; "+
                "hits="+geometricalHits+" misses="+geometricalMisses+
                " nonzero-error-hits="+offCenterHits);
    }
    public static void main(String[] args){
        deterministic();
        partialErrorsStillStrike();
        System.out.println("PASS physically meaningful historical sensor targeting estimation");
    }
}
