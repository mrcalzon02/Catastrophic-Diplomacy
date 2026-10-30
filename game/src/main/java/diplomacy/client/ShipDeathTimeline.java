package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.shipyard.Shipyard;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cosmetic ship losses, independently seeded from the authoritative battle RNG.
 * Deaths are detected across EACH simulation step, not just rendered frames;
 * even at 8x time acceleration a kill cannot be skipped between draws.
 */
final class ShipDeathTimeline {
    static final int MAX_ACTIVE=24;
    static final float LIFETIME=3.75f;
    static final float FRAGMENT_LIFE=2.55f;
    static final int FRAGMENTS_PER_HULL=8;
    static final int SMOKE_PER_HULL=10;
    static final short[] PENTAGON_TRIANGLES={0,1,2,0,2,3,0,3,4};
    private static final long DEATH_SALT=0x5368617474657265L;

    record Snapshot(int id,int team,Battle.Faction faction,Shipyard.Hull hull,
                    float x,float y,float facing,float vx,float vy,float scale){}
    /** Normalized vertices are indexed around one ragged wedge of the full sprite. */
    record Fragment(float[] vertices,float vx,float vy,float spin,
                    float intensity) {}
    record Puff(float offsetX,float offsetY,float vx,float vy,float radius,
                float drift) {}
    static final class Burst {
        final Snapshot ship;
        final long seed,tick;
        final List<Fragment> fragments;
        final List<Puff> smoke;
        float age;
        Burst(Snapshot ship,long seed,long tick,List<Fragment> pieces,List<Puff> puffs){
            this.ship=ship;this.seed=seed;this.tick=tick;
            this.fragments=List.copyOf(pieces);this.smoke=List.copyOf(puffs);
        }
    }
    private final Map<Integer,Snapshot> livingBefore=new HashMap<>();
    private final ArrayDeque<Burst> active=new ArrayDeque<>();
    private Battle battle;
    private long lastObservedTick=-1;

    void sync(Battle current){
        if(battle==current)return;
        battle=current;livingBefore.clear();active.clear();lastObservedTick=-1;
    }
    void beforeStep(Battle current){
        sync(current);
        livingBefore.clear();
        for(Battle.Ship ship:current.ships())if(ship.alive())
            livingBefore.put(ship.id,snapshot(ship));
    }
    void afterStep(Battle current){
        sync(current);
        if(current.tick==lastObservedTick)return;
        lastObservedTick=current.tick;
        for(Battle.Ship ship:current.ships()){
            Snapshot before=livingBefore.get(ship.id);
            // Escapes, completed withdrawals and nonlethal retirement are not deaths.
            if(before==null || ship.hull>0 || ship.escaped)continue;
            if(active.size()>=MAX_ACTIVE)active.removeFirst();
            long seed=mix(current.seed() ^ DEATH_SALT ^
                    (((long)ship.id)<<32) ^ current.tick);
            active.addLast(create(before,current.tick,seed));
        }
        livingBefore.clear();
    }
    void advance(Battle current,float delta,boolean running){
        sync(current);
        if(!running)return;
        float dt=Float.isFinite(delta)?Math.max(0,Math.min(.08f,delta)):0;
        for(Burst burst:active)burst.age+=dt;
        while(!active.isEmpty()&&active.peekFirst().age>=LIFETIME)
            active.removeFirst();
    }
    List<Burst> bursts(){return List.copyOf(active);}
    int count(){return active.size();}

    private static Snapshot snapshot(Battle.Ship s){
        return new Snapshot(s.id,s.team,s.faction,s.hullClass,
                (float)s.x,(float)s.y,(float)s.facing,
                (float)s.vx,(float)s.vy,(float)s.visualScale());
    }
    static long mix(long x){
        x^=x>>>30;x*=0xbf58476d1ce4e5b9L;
        x^=x>>>27;x*=0x94d049bb133111ebL;
        return x^(x>>>31);
    }
    static Burst create(Snapshot ship,long tick,long seed){
        Random random=new Random(seed);
        // The perimeter closes at all four corners; shared inward kinks
        // preserve continuous coverage at t=0, then separate along zigzag seams.
        float cx=.44f+random.nextFloat()*.12f;
        float cy=.44f+random.nextFloat()*.12f;
        float[][] edges={
                {0,0},{.28f+random.nextFloat()*.44f,0},
                {1,0},{1,.28f+random.nextFloat()*.44f},
                {1,1},{.28f+random.nextFloat()*.44f,1},
                {0,1},{0,.28f+random.nextFloat()*.44f}
        };
        float[][] kinks=new float[FRAGMENTS_PER_HULL][2];
        for(int i=0;i<kinks.length;i++){
            float dx=edges[i][0]-cx,dy=edges[i][1]-cy;
            float length=(float)Math.hypot(dx,dy);
            float side=(random.nextFloat()-.5f)*.10f;
            float at=.43f+random.nextFloat()*.18f;
            kinks[i][0]=cx+dx*at-side*dy/length;
            kinks[i][1]=cy+dy*at+side*dx/length;
        }
        List<Fragment> pieces=new ArrayList<>(FRAGMENTS_PER_HULL);
        for(int i=0;i<FRAGMENTS_PER_HULL;i++){
            int next=(i+1)%FRAGMENTS_PER_HULL;
            float[] vertex={
                    cx,cy,kinks[i][0],kinks[i][1],
                    edges[i][0],edges[i][1],
                    edges[next][0],edges[next][1],
                    kinks[next][0],kinks[next][1]
            };
            float dx=(edges[i][0]+edges[next][0])*.5f-cx;
            float dy=(edges[i][1]+edges[next][1])*.5f-cy;
            float size=(float)Math.max(.001,Math.hypot(dx,dy));
            float force=(12+random.nextFloat()*36)*ship.scale();
            float vx=dx/size*force+ship.vx()*.20f;
            float vy=dy/size*force+ship.vy()*.20f;
            float spin=(random.nextBoolean()?1:-1)*(45+random.nextFloat()*190);
            pieces.add(new Fragment(vertex,vx,vy,spin,.70f+random.nextFloat()*.30f));
        }
        List<Puff> smoke=new ArrayList<>(SMOKE_PER_HULL);
        for(int i=0;i<SMOKE_PER_HULL;i++){
            float angle=(float)(random.nextFloat()*Math.PI*2);
            float speed=(4+random.nextFloat()*20)*ship.scale();
            float r=(5+random.nextFloat()*10)*ship.scale();
            smoke.add(new Puff(
                    (random.nextFloat()-.5f)*12*ship.scale(),
                    (random.nextFloat()-.5f)*12*ship.scale(),
                    (float)Math.cos(angle)*speed+ship.vx()*.12f,
                    (float)Math.sin(angle)*speed+ship.vy()*.12f,
                    r,.55f+random.nextFloat()*.75f));
        }
        return new Burst(ship,seed,tick,pieces,smoke);
    }
}
