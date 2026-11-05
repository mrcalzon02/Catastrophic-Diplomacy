package diplomacy.sim;

import java.util.Random;

/** Exact light-time oracle vs conservative squared-shell rejection.
 * No real combat uses wall time; measurements here are diagnostics only.
 */
public final class PhotonArrivalPruningTest {
    private PhotonArrivalPruningTest(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    private static boolean oldFormula(double dx,double dy,int age){
        return SensorLightTimeHistory.propagationTicks(
                StrictMath.hypot(dx,dy))==age;
    }
    private static void compare(double dx,double dy,int age){
        boolean expected=oldFormula(dx,dy,age);
        boolean actual=SensorLightTimeHistory.arrivesAtAge(dx,dy,age);
        check(expected==actual,"Causal arrival changed age="+age+
                " delta="+dx+","+dy+" exact="+expected+" pruned="+actual);
    }
    public static void main(String[] args){
        final double scale=WorldScale.DIAGONAL_WORLD_UNITS/30.0;
        final int limit=SensorLightTimeHistory.propagationTicks(
                WorldScale.DIAGONAL_WORLD_UNITS)+2;
        long checks=0;
        // Include boundaries at every shell, at all four quadrants and
        // at gaps small enough to exercise the exact fallback's guard.
        for(int age=0;age<=limit;age++){
            for(int threshold=0;threshold<=limit;threshold++){
                double base=threshold*scale;
                for(double offset:new double[]{-.01,-.001,-.0000001,-1e-12,
                                               0,1e-12,.0000001,.001,.01}){
                    double distance=StrictMath.max(0,base+offset);
                    for(double[] d:new double[][]{
                            {distance,0},{0,distance},{-distance,0},
                            {distance*.6,distance*.8}}){
                        compare(d[0],d[1],age);checks++;
                    }
                }
            }
        }
        Random deterministic=new Random(0xA11C0B5L);
        for(int i=0;i<65000;i++){
            double dx=(deterministic.nextDouble()*2-1)*4500;
            double dy=(deterministic.nextDouble()*2-1)*2600;
            int age=deterministic.nextInt(limit+1);
            compare(dx,dy,age);checks++;
        }
        // Warmed, counterbalanced microbenchmark; counts must agree.
        final int size=55000;
        double[] dx=new double[size],dy=new double[size];
        int[] age=new int[size];
        for(int i=0;i<size;i++){
            dx[i]=(deterministic.nextDouble()*2-1)*3500;
            dy[i]=(deterministic.nextDouble()*2-1)*2000;
            age[i]=deterministic.nextInt(limit+1);
        }
        long optimizedNs=0,exactNs=0;
        int oldHits=0,newHits=0;
        for(int round=0;round<8;round++){
            boolean optimizedFirst=(round&1)==0;
            for(int mode=0;mode<2;mode++){
                boolean optimized=(mode==0)==optimizedFirst;
                long start=System.nanoTime();
                int hits=0;
                for(int i=0;i<size;i++)
                    if(optimized
                            ?SensorLightTimeHistory.arrivesAtAge(dx[i],dy[i],age[i])
                            :oldFormula(dx[i],dy[i],age[i]))hits++;
                long elapsed=System.nanoTime()-start;
                if(round>=2){
                    if(optimized){optimizedNs+=elapsed;newHits+=hits;}
                    else{exactNs+=elapsed;oldHits+=hits;}
                }
            }
        }
        check(oldHits==newHits,"Optimization changed photon arrival count");
        System.out.println("PASS photon shell exact oracle checks="+checks+
                " arrivals="+newHits+" optimizedMs="+optimizedNs/1e6+
                " exactMs="+exactNs/1e6+
                " (JVM timings diagnostic, not a release gate)");
    }
}
