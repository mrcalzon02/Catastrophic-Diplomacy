package diplomacy.sim;

import java.util.Random;

/** Exact segment-collision parity for the exclusion-only rocket/kinetic
 * broadphase. The original solver remains the sole impact-time authority.
 */
public final class SweptProjectileBroadphaseTest {
    private SweptProjectileBroadphaseTest(){}
    private static void require(boolean condition,String reason){
        if(!condition)throw new AssertionError(reason);
    }
    private static double original(double ax,double ay,double bx,double by,
                                   double cx,double cy,double radius){
        return Battle.segmentHit(ax,ay,bx,by,cx,cy,radius);
    }
    private static double filtered(double ax,double ay,double bx,double by,
                                   double cx,double cy,double radius){
        if(!Battle.sweptAabbMayHit(StrictMath.min(ax,bx),StrictMath.max(ax,bx),
                StrictMath.min(ay,by),StrictMath.max(ay,by),cx,cy,radius))
            return Double.POSITIVE_INFINITY;
        return original(ax,ay,bx,by,cx,cy,radius);
    }
    private static void parity(double ax,double ay,double bx,double by,
                               double cx,double cy,double radius){
        double exact=original(ax,ay,bx,by,cx,cy,radius);
        double bounded=filtered(ax,ay,bx,by,cx,cy,radius);
        require(Double.doubleToLongBits(exact)==Double.doubleToLongBits(bounded),
                "Broadphase changed collision hit or entry time: "+
                ax+","+ay+" -> "+bx+","+by+" against "+cx+","+cy+
                " r="+radius+" exact="+exact+" filtered="+bounded);
    }
    public static void main(String[] args){
        Random random=new Random(0xB0A0D5E);
        long checks=0,nearby=0;
        for(int i=0;i<150_000;i++){
            double ax=-1200+random.nextDouble()*4700;
            double ay=-700+random.nextDouble()*2600;
            double bx=ax+(random.nextDouble()-.5)*1000;
            double by=ay+(random.nextDouble()-.5)*1000;
            double radius=4.2+random.nextDouble()*42;
            double cx,cy;
            if(i%4==0){
                double t=random.nextDouble();
                cx=ax+(bx-ax)*t+(random.nextDouble()-.5)*radius*2;
                cy=ay+(by-ay)*t+(random.nextDouble()-.5)*radius*2;
                nearby++;
            }else if(i%4==1){
                // Exact tangencies and endpoint/corner boundaries.
                cx=StrictMath.min(ax,bx)-radius+(random.nextDouble()-.5)*1e-7;
                cy=StrictMath.max(ay,by)+radius+(random.nextDouble()-.5)*1e-7;
            }else{
                cx=-1200+random.nextDouble()*4700;
                cy=-700+random.nextDouble()*2600;
            }
            parity(ax,ay,bx,by,cx,cy,radius);checks++;
        }
        for(double radius:new double[]{4.2,5.5,12,48})
            for(double shift:new double[]{-1e-8,-1e-10,0,1e-10,1e-8}){
                parity(10,20,10,20,10+radius+shift,20,radius);checks++;
                parity(10,20,110,20,10-radius+shift,20,radius);checks++;
                parity(10,20,110,20,110+radius+shift,20,radius);checks++;
                parity(10,20,110,20,60,20+radius+shift,radius);checks++;
            }
        final int n=75_000;
        double[] ax=new double[n],ay=new double[n],bx=new double[n],by=new double[n];
        double[] cx=new double[n],cy=new double[n],radius=new double[n];
        for(int i=0;i<n;i++){
            ax[i]=-1000+random.nextDouble()*4200;
            ay[i]=-600+random.nextDouble()*2200;
            bx[i]=ax[i]+(random.nextDouble()-.5)*250;
            by[i]=ay[i]+(random.nextDouble()-.5)*250;
            cx[i]=-1000+random.nextDouble()*4200;
            cy[i]=-600+random.nextDouble()*2200;
            radius[i]=5+random.nextDouble()*22;
        }
        long exactNs=0,filteredNs=0,exactHits=0,filteredHits=0;
        for(int run=0;run<8;run++){
            for(int order=0;order<2;order++){
                boolean useFiltered=((run+order)&1)==0;
                long start=System.nanoTime();
                long hits=0;
                for(int i=0;i<n;i++){
                    double hit=useFiltered?
                            filtered(ax[i],ay[i],bx[i],by[i],cx[i],cy[i],radius[i]):
                            original(ax[i],ay[i],bx[i],by[i],cx[i],cy[i],radius[i]);
                    if(Double.isFinite(hit))hits++;
                }
                long elapsed=System.nanoTime()-start;
                if(run>=2){
                    if(useFiltered){filteredNs+=elapsed;filteredHits+=hits;}
                    else{exactNs+=elapsed;exactHits+=hits;}
                }
            }
        }
        require(exactHits==filteredHits,"Broadphase changed benchmark collision count");
        System.out.println("PASS swept projectile broadphase parity cases="+checks+
                " nearPathCases="+nearby+" hits="+filteredHits+
                " narrowOnlyMs="+exactNs/1e6+
                " broadphaseMs="+filteredNs/1e6+
                " (timings diagnostic only)");
    }
}
