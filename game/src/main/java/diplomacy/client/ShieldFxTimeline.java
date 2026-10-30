package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.DirectionalDefense;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Render-only, per-facing shield lifecycle. Samples authoritative protection
 * before/after fixed simulation steps; never invents charge or changes combat.
 * Covers direct hits, sector collapse, natural/support recharge and restart.
 */
final class ShieldFxTimeline {
    static final int MAX_EVENTS=192;
    enum Kind { IMPACT, COLLAPSE, RECHARGE, RESTART }
    static final class Effect {
        final Battle.Ship ship;
        final DirectionalDefense.Facing facing;
        final Kind kind;
        final Battle.DamageType weapon;
        final float localAngle, intensity;
        final long tick;
        float age;
        Effect(Battle.Ship ship,DirectionalDefense.Facing facing,Kind kind,
               Battle.DamageType weapon,float localAngle,float intensity,long tick){
            this.ship=ship;this.facing=facing;this.kind=kind;this.weapon=weapon;
            this.localAngle=localAngle;this.intensity=intensity;this.tick=tick;
        }
        float duration(){
            return switch(kind){
                case IMPACT -> .40f;
                case COLLAPSE -> .85f;
                case RECHARGE -> .52f;
                case RESTART -> .95f;
            };
        }
        float progress(){return Math.min(1f,age/duration());}
    }
    private static final class Facings {
        final double[] previous=new double[4];
        final float[] flash=new float[4],regen=new float[4];
    }
    private Battle source;
    private final Map<Integer,Facings> states=new HashMap<>();
    private final ArrayDeque<Effect> events=new ArrayDeque<>();
    private long lastEventTick=-1;
    void sync(Battle battle){
        if(source==battle)return;
        source=battle;states.clear();events.clear();lastEventTick=-1;
    }
    void beforeStep(Battle battle){
        sync(battle);
        for(Battle.Ship ship:battle.ships())if(ship.alive()){
            Facings state=states.computeIfAbsent(ship.id,id->new Facings());
            for(DirectionalDefense.Facing face:DirectionalDefense.Facing.values())
                state.previous[face.ordinal()]=ship.defense.shields(face);
        }
    }
    void afterStep(Battle battle){afterStep(battle,battle.visualEvents());}
    /** Event injection permits headless verification without GL or video frames. */
    void afterStep(Battle battle,List<Battle.VisualEvent> visualEvents){
        sync(battle);
        // Only inspect events from the current tick, not the 8192-entry history.
        Map<Integer,Battle.VisualEvent> impacts=new HashMap<>();
        for(int i=visualEvents.size()-1;i>=0;i--){
            Battle.VisualEvent e=visualEvents.get(i);
            if(e.tick()<battle.tick)break;
            if(e.tick()==battle.tick&&e.phase()==Battle.VisualPhase.IMPACT)
                impacts.putIfAbsent(e.targetId(),e);
        }
        for(Battle.Ship ship:battle.ships()){
            if(!ship.alive())continue;
            Facings state=states.get(ship.id);
            if(state==null)continue;
            Battle.VisualEvent contact=impacts.get(ship.id);
            for(DirectionalDefense.Facing facing:DirectionalDefense.Facing.values()){
                int slot=facing.ordinal();
                double before=state.previous[slot],now=ship.defense.shields(facing);
                double capacity=ship.defense.maxShields(facing);
                if(capacity<=0)continue;
                double loss=before-now,gain=now-before;
                if(loss>.015){
                    float strength=(float)Math.min(1,loss/Math.max(1,capacity));
                    state.flash[slot]=Math.max(state.flash[slot],Math.min(1f,.38f+strength*.95f));
                    // An actual sector loss is authoritative even for area damage.
                    // The impact event, when available, colors it by weapon family.
                    Battle.DamageType weapon=weapon(contact);
                    float angle=impactAngle(ship,facing,contact);
                    add(new Effect(ship,facing,Kind.IMPACT,weapon,angle,
                            Math.min(1f,.42f+strength),battle.tick));
                    if(before>.015 && now<=.015)
                        add(new Effect(ship,facing,Kind.COLLAPSE,weapon,angle,1f,battle.tick));
                }else if(gain>.015){
                    state.regen[slot]=Math.max(state.regen[slot],.50f);
                    if(before<=.015&&now>.015){
                        add(new Effect(ship,facing,Kind.RESTART,null,centerAngle(facing),.9f,battle.tick));
                    }else if((battle.tick+ship.id+slot*5)%18==0){
                        // Not one particle burst per shield point: 400-ship safe.
                        add(new Effect(ship,facing,Kind.RECHARGE,null,centerAngle(facing),
                                Math.min(.72f,.22f+(float)(gain/capacity)*4f),battle.tick));
                    }
                }
            }
        }
        lastEventTick=battle.tick;
    }
    private static Battle.DamageType weapon(Battle.VisualEvent event){
        if(event==null)return null;
        try{return Battle.DamageType.valueOf(event.weapon());}
        catch(IllegalArgumentException|NullPointerException ignored){return null;}
    }
    static float centerAngle(DirectionalDefense.Facing facing){
        return switch(facing){
            case FORWARD -> 0f;
            case PORT -> 90f;
            case AFT -> 180f;
            case STARBOARD -> -90f;
        };
    }
    static float impactAngle(Battle.Ship ship,DirectionalDefense.Facing face,
                             Battle.VisualEvent event){
        float center=centerAngle(face);
        if(event==null)return center;
        double dx=event.x()-ship.x,dy=event.y()-ship.y;
        if(!Double.isFinite(dx)||!Double.isFinite(dy)||dx*dx+dy*dy<.0001)return center;
        double relative=StrictMath.toDegrees(StrictMath.atan2(dy,dx)-ship.facing);
        double offset=((relative-center+540)%360)-180;
        // A report in another sector must never draw a strike across this facing.
        return Math.abs(offset)<=43 ? center+(float)offset : center;
    }
    private void add(Effect effect){
        if(events.size()==MAX_EVENTS)events.removeFirst();
        events.addLast(effect);
    }
    void advance(Battle battle,float delta,boolean running){
        sync(battle);
        if(!running||!Float.isFinite(delta))return;
        float dt=Math.min(.08f,Math.max(0f,delta));
        for(Facings state:states.values())for(int i=0;i<4;i++){
            state.flash[i]=Math.max(0,state.flash[i]-dt*3.3f);
            state.regen[i]=Math.max(0,state.regen[i]-dt*.95f);
        }
        for(Effect effect:events)effect.age+=dt;
        // The queue is ordered by creation, but kinds have different lifetimes.
        // Remove individually so a long collapse never blocks a short impact.
        events.removeIf(effect->effect.age>=effect.duration());
    }
    float flash(int shipId,DirectionalDefense.Facing face){
        Facings state=states.get(shipId);
        return state==null?0f:state.flash[face.ordinal()];
    }
    float recharge(int shipId,DirectionalDefense.Facing face){
        Facings state=states.get(shipId);
        return state==null?0f:state.regen[face.ordinal()];
    }
    float opacity(Battle.Ship ship,DirectionalDefense.Facing face,float base){
        if(base<=0f)return 0f; // depleted or absent facings cannot glow
        return ShieldSurfaceModel.energizedOpacity(base,flash(ship.id,face),
                recharge(ship.id,face));
    }
    List<Effect> events(){return List.copyOf(events);}
    int count(){return events.size();}
    long lastEventTick(){return lastEventTick;}
}
