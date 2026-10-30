package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.OrdnancePropulsion;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Render-only emissive/exhaust history, captured at every authoritative
 * simulation tick (not each rendered frame). No effect alters a projectile.
 */
final class CombatLightingModel {
    static final int SMOKE_LIFETIME_TICKS=36;
    static final int MAX_SMOKE=640;
    static final int MAX_FLASHES=384;
    static final int MAX_TRACKED_MISSILES=192;
    static final int MAX_IGNITIONS=128;
    static final int IGNITION_LIFETIME_TICKS=8;

    record Smoke(float x,float y,long bornTick,boolean heavy,boolean decoy) {}
    record Ignition(float x,float y,float dx,float dy,long bornTick,
                    boolean heavy,boolean decoy,boolean terminal) {}
    record Flash(Battle.VisualEvent event) {}
    private record Position(float x,float y,boolean burning) {}

    private final ArrayDeque<Smoke> smoke=new ArrayDeque<>();
    private final ArrayDeque<Flash> flashes=new ArrayDeque<>();
    private final ArrayDeque<Ignition> ignitions=new ArrayDeque<>();
    private Map<Long,Position> previousMissiles=new HashMap<>();
    private Battle trackedBattle;
    private long lastTick=-1;
    private long lastSequence;

    Iterable<Smoke> smoke(){return smoke;}
    Iterable<Flash> flashes(){return flashes;}
    Iterable<Ignition> ignitions(){return ignitions;}
    int smokeCount(){return smoke.size();}
    int flashCount(){return flashes.size();}
    int ignitionCount(){return ignitions.size();}

    private void reset(Battle battle){
        trackedBattle=battle;lastTick=-1;lastSequence=0;
        smoke.clear();flashes.clear();ignitions.clear();previousMissiles.clear();
    }
    static int flashLife(Battle.VisualEvent event){
        if(event.phase()==Battle.VisualPhase.TIMEOUT)return 0;
        if(event.phase()==Battle.VisualPhase.INTERCEPT)return 5;
        if(event.phase()==Battle.VisualPhase.IMPACT)return 9;
        return event.weapon().equals("KINETIC")?8:6;
    }

    void update(Battle battle){
        if(battle!=trackedBattle || (battle!=null && battle.tick<lastTick))reset(battle);
        if(battle==null || battle.tick==lastTick)return;
        // Event-sequenced: skipped frames cannot replay the same explosion.
        for(Battle.VisualEvent event:battle.visualEvents()){
            if(event.sequence()<=lastSequence)continue;
            lastSequence=event.sequence();
            int life=flashLife(event);
            if(life>0 && battle.tick>=event.tick()&&battle.tick-event.tick()<life)
                flashes.addLast(new Flash(event));
        }
        while(!flashes.isEmpty()
                && battle.tick-flashes.peekFirst().event().tick()
                >=flashLife(flashes.peekFirst().event()))flashes.removeFirst();
        while(flashes.size()>MAX_FLASHES)flashes.removeFirst();

        // The motor phase comes directly from Battle.updateMissiles. No engine
        // flare or smoke while ejecting, coasting, or after fuel exhaustion.
        Map<Long,Position> current=new HashMap<>();
        for(Battle.Missile missile:battle.missiles()){
            if(current.size()>=MAX_TRACKED_MISSILES)break;
            float x=(float)missile.x,y=(float)missile.y;
            boolean burning=OrdnancePropulsion.burning(missile.thrustPhase)
                    &&missile.fuel>0;
            Position previous=previousMissiles.get(missile.visualId);
            current.put(missile.visualId,new Position(x,y,burning));
            if(!burning)continue;
            float speed=(float)StrictMath.hypot(missile.vx,missile.vy);
            float dx=speed>.0001f?(float)(missile.vx/speed):1f;
            float dy=speed>.0001f?(float)(missile.vy/speed):0f;
            float tail=CombatEffectScale.of(missile.torpedo?10f:6f);
            float tx=x-dx*tail,ty=y-dy*tail;
            // Ignition occurs on *every* motor restart, including after a long
            // unpowered coast before terminal intercept approach.
            if(previous==null || !previous.burning()){
                ignitions.addLast(new Ignition(tx,ty,dx,dy,battle.tick,
                        missile.torpedo,missile.decoy,
                        missile.thrustPhase==OrdnancePropulsion.Phase.TERMINAL));
            }
            if((battle.tick&1)!=0)continue;
            if(previous==null || !previous.burning()){
                smoke.addLast(new Smoke(tx,ty,battle.tick,missile.torpedo,missile.decoy));
            }else{
                float px=previous.x()-dx*tail,py=previous.y()-dy*tail;
                float distance=(float)StrictMath.hypot(tx-px,ty-py);
                int steps=Math.min(3,Math.max(1,(int)(distance/9)));
                for(int step=1;step<=steps;step++){
                    float part=step/(float)steps;
                    smoke.addLast(new Smoke(px+(tx-px)*part,
                            py+(ty-py)*part,battle.tick,missile.torpedo,missile.decoy));
                }
            }
        }
        previousMissiles=current;
        while(!smoke.isEmpty()
                && battle.tick-smoke.peekFirst().bornTick()>=SMOKE_LIFETIME_TICKS)
            smoke.removeFirst();
        while(smoke.size()>MAX_SMOKE)smoke.removeFirst();
        while(!ignitions.isEmpty()
                && battle.tick-ignitions.peekFirst().bornTick()>=IGNITION_LIFETIME_TICKS)
            ignitions.removeFirst();
        while(ignitions.size()>MAX_IGNITIONS)ignitions.removeFirst();
        lastTick=battle.tick;
    }
}
