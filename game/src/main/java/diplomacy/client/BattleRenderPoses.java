package diplomacy.client;
import diplomacy.sim.Battle;
import diplomacy.sim.BattleView;
import diplomacy.sim.RotaryPointDefense;
import diplomacy.sim.RailPointDefense;
import java.util.HashMap;
import java.util.Map;

/** Render-only fixed-tick pose snapshots; never changes the simulation. */
final class BattleRenderPoses {
    record Pose(float x,float y,float angle,float vx,float vy){
        Pose(float x,float y,float angle){
            this(x,y,angle,Float.NaN,Float.NaN);
        }
    }
    private final Map<Integer,Pose> ships=new HashMap<>(),crafts=new HashMap<>();
    private final Map<Long,Pose> missiles=new HashMap<>(),rockets=new HashMap<>(),shells=new HashMap<>();
    private final Map<Long,Pose> flak=new HashMap<>(),rotary=new HashMap<>(),rails=new HashMap<>();
    private Battle source;
    /** Snapshot source tick: birth poses are valid only during the next tick. */
    private long sampledTick=Long.MIN_VALUE;
    private float fraction=1f;
    /** The caller counts real physics steps independently of render frames.
     * Only the last actual step contributes a previous pose to this frame.
     * Cast mirrors the float accumulator's subtraction in the combat loop. */
    static boolean captureBeforeLastStep(float accumulatedSeconds,int step,int limit){
        return step>=limit||(float)(accumulatedSeconds-Battle.DT)<Battle.DT;
    }
    void sync(Battle battle){
        if(source==battle)return;
        source=battle;ships.clear();crafts.clear();missiles.clear();rockets.clear();shells.clear();flak.clear();rotary.clear();rails.clear();
        sampledTick=Long.MIN_VALUE;
        fraction=1f;
    }
    void beforeStep(Battle battle){
        sync(battle);ships.clear();crafts.clear();missiles.clear();rockets.clear();shells.clear();flak.clear();rotary.clear();rails.clear();
        sampledTick=battle.tick;
        for(Battle.Ship s:battle.ships())if(s.alive())
            ships.put(s.id,new Pose((float)s.x,(float)s.y,(float)s.facing,
                    (float)s.vx,(float)s.vy));
        for(Battle.Craft c:battle.crafts())
            crafts.put(c.id,new Pose((float)c.x,(float)c.y,0,
                    (float)c.observedVx,(float)c.observedVy));
        for(Battle.Missile m:battle.missiles())
            missiles.put(m.visualId,new Pose((float)m.x,(float)m.y,
                    (float)Math.atan2(m.vy,m.vx),(float)m.vx,(float)m.vy));
        for(Battle.Rocket r:battle.rockets())
            rockets.put(r.visualId,new Pose((float)r.x,(float)r.y,
                    (float)r.heading,(float)r.vx,(float)r.vy));
        for(Battle.Ballistic b:battle.ballistics())
            shells.put(b.visualId,new Pose((float)b.x,(float)b.y,
                    (float)Math.atan2(b.vy,b.vx),(float)b.vx,(float)b.vy));
        for(Battle.FlakRound b:battle.flakRounds())
            flak.put(b.shotId,new Pose((float)b.x,(float)b.y,
                    (float)Math.atan2(b.vy,b.vx),(float)b.vx,(float)b.vy));
        for(RotaryPointDefense.Round b:battle.rotaryRounds())
            rotary.put(b.visualId,new Pose((float)b.x,(float)b.y,
                    (float)Math.atan2(b.vy,b.vx),(float)b.vx,(float)b.vy));
        for(RailPointDefense.Penetrator b:battle.railPenetrators())
            rails.put(b.visualId,new Pose((float)b.x,(float)b.y,
                    (float)Math.atan2(b.vy,b.vx),(float)b.vx,(float)b.vy));
    }
    /** Clear stale pre-tick samples when paused or otherwise frozen.
     * A resumed frame before its next physics tick must never move backwards. */
    void snap(Battle battle){
        sync(battle);
        ships.clear();crafts.clear();missiles.clear();rockets.clear();shells.clear();flak.clear();rotary.clear();rails.clear();
        sampledTick=Long.MIN_VALUE;
        fraction=1f;
    }
    void fraction(Battle battle,float value){
        sync(battle);
        fraction=Float.isFinite(value)?Math.max(0f,Math.min(1f,value)):1f;
    }
    /** Camera framing must use the very same interpolated poses as hulls.
     * The deterministic BattleView.of(battle) stays available to headless clients. */
    BattleView.Frame frame(Battle battle){
        sync(battle);
        double lowX=Double.POSITIVE_INFINITY,highX=Double.NEGATIVE_INFINITY;
        double lowY=Double.POSITIVE_INFINITY,highY=Double.NEGATIVE_INFINITY;
        for(Battle.Ship ship:battle.ships())if(ship.alive()){
            Pose shown=ship(ship);
            double radius=14*ship.visualScale();
            lowX=Math.min(lowX,shown.x()-radius);
            highX=Math.max(highX,shown.x()+radius);
            lowY=Math.min(lowY,shown.y()-radius);
            highY=Math.max(highY,shown.y()+radius);
        }
        if(!Double.isFinite(lowX))return new BattleView.Frame(640,360,1);
        double required=Math.max(1,Math.max((highX-lowX+64)/1180,
                (highY-lowY+85)/555));
        return new BattleView.Frame((lowX+highX)/2,(lowY+highY)/2,required);
    }
    /** Read-only velocity-aware cubic motion avoids visible speed kinks
     * between sparse physical ticks without changing authoritative physics. */
    private static float continuousAxis(float oldX,float newX,float oldV,float newV,float t){
        if(!Float.isFinite(oldV)||!Float.isFinite(newV))
            return oldX+(newX-oldX)*t;
        double u=t,s=u*u,h=s*u;
        double predicted=(2*h-3*s+1)*oldX+
                (h-2*s+u)*oldV*Battle.DT+
                (-2*h+3*s)*newX+
                (h-s)*newV*Battle.DT;
        // Positions must never overshoot known real simulation endpoints.
        return (float)Math.max(Math.min(oldX,newX),
                Math.min(Math.max(oldX,newX),predicted));
    }
    private Pose blend(Pose prior,Pose now){
        if(prior==null)return now;
        float dx=now.x()-prior.x(),dy=now.y()-prior.y();
        if(dx*dx+dy*dy>1000000f)return now;
        float turn=(float)Math.atan2(Math.sin(now.angle()-prior.angle()),
                Math.cos(now.angle()-prior.angle()));
        float angleTime=fraction*fraction*(3-2*fraction);
        return new Pose(continuousAxis(prior.x(),now.x(),prior.vx(),now.vx(),fraction),
                continuousAxis(prior.y(),now.y(),prior.vy(),now.vy(),fraction),
                prior.angle()+turn*angleTime,now.vx(),now.vy());
    }
    /** A fresh live projectile is already advanced within its birth tick.
     * Start presentation at its real launch coordinate, not at its first
     * post-physics position. This affects visuals only. */
    private Pose birth(Pose prior,long spawnTick,double muzzleX,double muzzleY,Pose now){
        if(prior!=null)return prior;
        if(source==null||sampledTick!=source.tick-1||spawnTick!=source.tick)
            return null;
        if(!Double.isFinite(muzzleX)||!Double.isFinite(muzzleY))return null;
        return new Pose((float)muzzleX,(float)muzzleY,now.angle(),now.vx(),now.vy());
    }
    Pose ship(Battle.Ship s){
        return blend(ships.get(s.id),new Pose((float)s.x,(float)s.y,(float)s.facing,
                (float)s.vx,(float)s.vy));
    }
    Pose craft(Battle.Craft c){
        Pose current=new Pose((float)c.x,(float)c.y,0,
                (float)c.observedVx,(float)c.observedVy);
        Pose previous=crafts.get(c.id);
        if(previous==null&&source!=null&&sampledTick==source.tick-1&&
                Double.isFinite(c.lastObservedX)&&Double.isFinite(c.lastObservedY))
            previous=new Pose((float)c.lastObservedX,(float)c.lastObservedY,0,
                    current.vx(),current.vy());
        return blend(previous,current);
    }
    Pose missile(Battle.Missile m){
        Pose current=new Pose((float)m.x,(float)m.y,
                (float)Math.atan2(m.vy,m.vx),(float)m.vx,(float)m.vy);
        return blend(birth(missiles.get(m.visualId),m.spawnTick,
                m.previousX,m.previousY,current),current);
    }
    Pose rocket(Battle.Rocket r){
        Pose current=new Pose((float)r.x,(float)r.y,(float)r.heading,
                (float)r.vx,(float)r.vy);
        return blend(birth(rockets.get(r.visualId),r.spawnTick,
                r.previousX,r.previousY,current),current);
    }
    Pose shell(Battle.Ballistic b){
        Pose current=new Pose((float)b.x,(float)b.y,
                (float)Math.atan2(b.vy,b.vx),(float)b.vx,(float)b.vy);
        return blend(birth(shells.get(b.visualId),b.spawnTick,
                b.previousX,b.previousY,current),current);
    }
    Pose flak(Battle.FlakRound b){
        Pose current=new Pose((float)b.x,(float)b.y,
                (float)Math.atan2(b.vy,b.vx),(float)b.vx,(float)b.vy);
        return blend(birth(flak.get(b.shotId),b.spawnTick,
                b.startX,b.startY,current),current);
    }
    Pose rotary(RotaryPointDefense.Round b){
        Pose current=new Pose((float)b.x,(float)b.y,
                (float)Math.atan2(b.vy,b.vx),(float)b.vx,(float)b.vy);
        return blend(birth(rotary.get(b.visualId),b.spawnTick,
                b.startX,b.startY,current),current);
    }
    Pose rail(RailPointDefense.Penetrator b){
        Pose current=new Pose((float)b.x,(float)b.y,
                (float)Math.atan2(b.vy,b.vx),(float)b.vx,(float)b.vy);
        return blend(birth(rails.get(b.visualId),b.spawnTick,
                b.startX,b.startY,current),current);
    }
    int trackedShips(){return ships.size();}
    int trackedMissiles(){return missiles.size();}
    int trackedRockets(){return rockets.size();}
}
