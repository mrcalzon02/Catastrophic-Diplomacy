package diplomacy.sim;

import java.util.List;

/**
 * Radar-guided fighter missile sensor. Radar is ACTIVE: a missile must emit
 * a paid pulse from its historical physical pose, illuminate a historical
 * fighter/chaff position, then wait for the returning photon front to cross
 * its moving receiver. No instant ping, live target position, or cloud magic.
 */
public final class FighterRadarSeeker {
    private FighterRadarSeeker(){}

    public enum Head {
        NARROW_BEAM(280,.48,6,1.0),
        WIDE_AREA(175,1.30,5,1.45);
        public final double range,halfAngleRadians,pulseCost;
        public final int pulseTicks;
        Head(double range,double halfAngleRadians,int pulseTicks,
             double pulseCost){
            this.range=range;this.halfAngleRadians=halfAngleRadians;
            this.pulseTicks=pulseTicks;this.pulseCost=pulseCost;
        }
    }
    public static Head installed(int tier){
        if(tier<0||tier>9)
            throw new IllegalArgumentException("Invalid radar seeker research");
        if(tier<5)
            throw new IllegalArgumentException("Radar head requires fitted FTR tier 5");
        return tier>=8?Head.WIDE_AREA:Head.NARROW_BEAM;
    }

    public record Pulse(long tick,double x,double y,
                        double headingX,double headingY){
        public Pulse{
            if(tick<0||!Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(headingX)||!Double.isFinite(headingY)||
                    StrictMath.hypot(headingX,headingY)<1e-9)
                throw new IllegalArgumentException("Invalid emitted radar photon pulse");
        }
    }
    /** Each radar missile owns one bounded ring; no global beams or
     * synthetic reflections spawned when a defender is acquired. */
    public static final class PulseHistory {
        public static final int CAPACITY=24;
        private final Pulse[] pulses=new Pulse[CAPACITY];
        public void emit(long tick,double x,double y,double vx,double vy){
            Pulse p=new Pulse(tick,x,y,vx,vy);
            pulses[(int)(tick%CAPACITY)]=p;
        }
        public Pulse at(long tick){
            if(tick<0)return null;
            Pulse p=pulses[(int)(tick%CAPACITY)];
            return p!=null&&p.tick()==tick?p:null;
        }
        public long fingerprint(long now){
            long h=0xcbf29ce484222325L;
            for(int i=0;i<CAPACITY;i++){
                Pulse p=pulses[i];
                if(p==null||p.tick()>now)continue;
                h=(h^p.tick())*0x100000001b3L;
                h=(h^Double.doubleToLongBits(p.x()))*0x100000001b3L;
                h=(h^Double.doubleToLongBits(p.y()))*0x100000001b3L;
                h=(h^Double.doubleToLongBits(p.headingX()))*0x100000001b3L;
                h=(h^Double.doubleToLongBits(p.headingY()))*0x100000001b3L;
            }
            return h;
        }
    }
    public record Echo(double x,double y,double vx,double vy,
                       long transmittedTick,long reflectedTick,
                       long receivedTick,long chaffId,double strength){
        public Echo{
            if(!Double.isFinite(x)||!Double.isFinite(y)||
                    !Double.isFinite(vx)||!Double.isFinite(vy)||
                    !Double.isFinite(strength)||strength<=0||
                    transmittedTick<0||transmittedTick>=reflectedTick||
                    reflectedTick>=receivedTick||chaffId<0)
                throw new IllegalArgumentException("Invalid radar echo chronology");
        }
        public boolean decoy(){return chaffId>0;}
    }
    private static boolean inView(Head head,double fromX,double fromY,
                                 double toX,double toY,
                                 double headingX,double headingY){
        double dx=toX-fromX,dy=toY-fromY;
        double distance=StrictMath.hypot(dx,dy);
        if(distance>head.range)return false;
        if(distance<1e-9)return true;
        double speed=StrictMath.hypot(headingX,headingY);
        if(speed<1e-9)return false;
        return (dx*headingX+dy*headingY)/(distance*speed)>=
                StrictMath.cos(head.halfAngleRadians);
    }
    /** Swept one-tick crossing for BOTH outbound and inbound light fronts.
     * A previously passed pulse cannot be received twice by repositioning.
     * Target reflection is evaluated against historical recorded frames. */
    private static boolean crossed(double oldDistance,double newDistance,
                                   long elapsedTicks,double lightStep){
        return elapsedTicks>0&&oldDistance>lightStep*(elapsedTicks-1)+1e-9
                &&newDistance<=lightStep*elapsedTicks+1e-9;
    }
    public static Echo receive(Head head,FighterInfraredSeeker.History targetHistory,
                               double fighterCrossSection,
                               List<FighterCountermeasures.Cloud> clouds,
                               PulseHistory outgoing,int targetCraftId,int team,
                               double fromX,double fromY,double toX,double toY,
                               double headingX,double headingY,long now){
        if(head==null||targetHistory==null||clouds==null||outgoing==null||
                targetCraftId<=0||(team!=0&&team!=1)||now<0||
                !Double.isFinite(fighterCrossSection)||fighterCrossSection<=0||
                !Double.isFinite(fromX)||!Double.isFinite(fromY)||
                !Double.isFinite(toX)||!Double.isFinite(toY)||
                !Double.isFinite(headingX)||!Double.isFinite(headingY))
            throw new IllegalArgumentException("Invalid radar echo receiver");
        double c=WorldScale.C_WORLD_UNITS_PER_SIM_SECOND*Battle.DT;
        int oneWay=(int)StrictMath.ceil(head.range/c)+2;
        int horizon=StrictMath.min(PulseHistory.CAPACITY-1,
                oneWay*2+3);
        Echo best=null;
        double brightest=0;
        for(long transmitted=StrictMath.max(0,now-horizon);
                 transmitted<now;transmitted++){
            Pulse pulse=outgoing.at(transmitted);
            if(pulse==null)continue;
            for(long reflected=transmitted+1;
                    reflected<now&&reflected<=transmitted+oneWay;reflected++){
                var current=targetHistory.at(reflected);
                var previous=targetHistory.at(reflected-1);
                if(current!=null&&previous!=null&&
                        inView(head,pulse.x(),pulse.y(),
                                current.x(),current.y(),
                                pulse.headingX(),pulse.headingY())&&
                        inView(head,toX,toY,current.x(),current.y(),
                                headingX,headingY)){
                    double outPrior=StrictMath.hypot(
                            previous.x()-pulse.x(),previous.y()-pulse.y());
                    double outNow=StrictMath.hypot(
                            current.x()-pulse.x(),current.y()-pulse.y());
                    double backPrior=StrictMath.hypot(
                            current.x()-fromX,current.y()-fromY);
                    double backNow=StrictMath.hypot(
                            current.x()-toX,current.y()-toY);
                    if(crossed(outPrior,outNow,reflected-transmitted,c)&&
                            crossed(backPrior,backNow,now-reflected,c)){
                        double strength=fighterCrossSection/
                                (1+outNow*outNow/40000);
                        if(strength>brightest){
                            brightest=strength;
                            best=new Echo(current.x(),current.y(),
                                    current.vx(),current.vy(),transmitted,
                                    reflected,now,0,strength);
                        }
                    }
                }
            }
        }
        // Scan the actual chaff emitters once per receiver. Only radar chaff
        // from the assigned fighter enters this target discriminator; flares
        // do NOT produce radar echoes and cannot win a radar lock.
        for(var cloud:clouds){
            if(cloud.kind()!=FighterCountermeasures.Kind.CHAFF||
                    cloud.team()==team||cloud.craftId()!=targetCraftId)
                continue;
            for(long transmitted=StrictMath.max(0,now-horizon);
                    transmitted<now;transmitted++){
                Pulse pulse=outgoing.at(transmitted);
                if(pulse==null)continue;
                for(long reflected=StrictMath.max(transmitted+1,cloud.bornTick());
                        reflected<now&&reflected<=transmitted+oneWay;reflected++){
                    if(!cloud.active(reflected))continue;
                    double cx=cloud.xAt(reflected),cy=cloud.yAt(reflected);
                    if(!BattleArena.contains(cx,cy)||
                            !inView(head,pulse.x(),pulse.y(),cx,cy,
                                    pulse.headingX(),pulse.headingY())||
                            !inView(head,toX,toY,cx,cy,headingX,headingY))
                        continue;
                    double priorOut=StrictMath.hypot(
                            cloud.xAt(reflected-1)-pulse.x(),
                            cloud.yAt(reflected-1)-pulse.y());
                    double outNow=StrictMath.hypot(cx-pulse.x(),cy-pulse.y());
                    double backPrior=StrictMath.hypot(cx-fromX,cy-fromY);
                    double backNow=StrictMath.hypot(cx-toX,cy-toY);
                    if(!crossed(priorOut,outNow,reflected-transmitted,c)||
                            !crossed(backPrior,backNow,now-reflected,c))
                        continue;
                    double strength=cloud.signal(
                            FighterCountermeasures.Kind.CHAFF,reflected)/
                            (1+outNow*outNow/40000);
                    if(strength>brightest){
                        brightest=strength;
                        best=new Echo(cx,cy,cloud.vx(),cloud.vy(),
                                transmitted,reflected,now,cloud.id(),strength);
                    }
                }
            }
        }
        return best;
    }
}
