package diplomacy.client;

import diplomacy.sim.Battle;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative-hit, render-only feedback. Samples defense pools on either
 * side of each fixed simulation step; creates no cosmetic shield impacts when
 * no actual shield or armor was lost. The battle AI and damage rules are not touched.
 */
final class CombatHitFeedback {
    static final int MAX_PULSES=160;
    static final float LIFETIME=.42f;
    enum Layer { SHIELD, ARMOR, HULL }
    record Defense(float shields,float armor,float hull,float x,float y,
                   float facing,float scale) {}
    static final class Pulse {
        final Layer layer;
        final float x,y,normalX,normalY,shipX,shipY,scale;
        final long tick,sequence;
        float age;
        Pulse(Layer layer,float x,float y,float normalX,float normalY,
              float shipX,float shipY,float scale,long tick,long sequence){
            this.layer=layer;this.x=x;this.y=y;this.normalX=normalX;
            this.normalY=normalY;this.shipX=shipX;this.shipY=shipY;
            this.scale=scale;this.tick=tick;this.sequence=sequence;
        }
    }
    private Battle tracked;
    private long sequence;
    private final Map<Integer,Defense> before=new HashMap<>();
    private final ArrayDeque<Pulse> active=new ArrayDeque<>();

    void sync(Battle battle){
        if(tracked==battle)return;
        tracked=battle;sequence=0;before.clear();active.clear();
    }
    void beforeStep(Battle battle){
        sync(battle);before.clear();
        for(Battle.Ship ship:battle.ships())if(ship.alive())
            before.put(ship.id,new Defense((float)ship.shields,
                    (float)ship.armor,(float)ship.hull,
                    (float)ship.x,(float)ship.y,(float)ship.facing,
                    (float)ship.visualScale()));
    }
    void afterStep(Battle battle){afterStep(battle,battle.visualEvents());}
    /** Explicit event input enables deterministic headless tests. */
    void afterStep(Battle battle,List<Battle.VisualEvent> events){
        sync(battle);
        Map<Integer,Battle.Ship> byId=new HashMap<>();
        for(Battle.Ship ship:battle.ships())byId.put(ship.id,ship);
        for(Battle.VisualEvent event:events){
            if(event.sequence()<=sequence)continue;
            sequence=event.sequence();
            if(event.tick()!=battle.tick||event.phase()!=Battle.VisualPhase.IMPACT)
                continue;
            Defense prior=before.get(event.targetId());
            Battle.Ship target=byId.get(event.targetId());
            if(prior==null||target==null)continue;
            Layer layer=changedLayer(prior,target.shields,target.armor,target.hull);
            if(layer==null)continue;
            float nx=(float)(event.x()-prior.x());
            float ny=(float)(event.y()-prior.y());
            float distance=(float)Math.hypot(nx,ny);
            if(!Float.isFinite(distance)||distance<.001f){
                nx=(float)Math.cos(prior.facing());
                ny=(float)Math.sin(prior.facing());
                distance=1;
            }
            nx/=distance;ny/=distance;
            float ring=15*prior.scale();
            float x=prior.x()+nx*Math.min(ring,distance);
            float y=prior.y()+ny*Math.min(ring,distance);
            if(active.size()>=MAX_PULSES)active.removeFirst();
            active.addLast(new Pulse(layer,x,y,nx,ny,prior.x(),prior.y(),
                    prior.scale(),event.tick(),event.sequence()));
        }
        before.clear();
    }
    static Layer changedLayer(Defense prior,double shields,double armor,double hull){
        // Each layer only lights when that resource genuinely dropped.
        // One simulation step may cross multiple protection layers. Visual
        // feedback should represent the deepest layer actually damaged.
        if(prior.hull()-hull>.01)return Layer.HULL;
        if(prior.armor()-armor>.01)return Layer.ARMOR;
        if(prior.shields()-shields>.01)return Layer.SHIELD;
        return null;
    }
    void advance(Battle battle,float delta,boolean running){
        sync(battle);
        if(!running||!Float.isFinite(delta))return;
        float dt=Math.max(0,Math.min(.08f,delta));
        for(Pulse pulse:active)pulse.age+=dt;
        while(!active.isEmpty()&&active.peekFirst().age>=LIFETIME)
            active.removeFirst();
    }
    List<Pulse> pulses(){return List.copyOf(active);}
    int count(){return active.size();}
}
