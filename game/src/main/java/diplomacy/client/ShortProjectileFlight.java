package diplomacy.client;

import diplomacy.sim.Battle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An in-tick projectile can be born and destroyed before the next GL frame.
 * Replay ONLY its host/simulation-recorded FIRE-to-terminal segment across
 * that tick's existing presentation interval. Do not add a second projectile
 * to the battle or invent a hit, trajectory, position or extra lifetime.
 */
final class ShortProjectileFlight {
    record Path(long shotId,long tick,String weapon,double fromX,double fromY,
                double toX,double toY){}
    record Point(float x,float y){}

    private static final int MAX_FLIGHTS=256;
    private final Map<Long,Battle.VisualEvent> fired=new HashMap<>();
    private final Set<Long> complete=new HashSet<>();
    private final List<Path> flights=new ArrayList<>();
    private long tick=Long.MIN_VALUE;

    static boolean supported(String weapon){
        if(weapon==null)return false;
        return switch(weapon){
            case "KINETIC","FLAK","ROTARY_CIWS","RAIL_PD",
                    "ROCKET","MISSILE","TORPEDO","DECOY" -> true;
            default -> weapon.endsWith("_MISSILE");
        };
    }
    void beginTick(long currentTick){
        if(tick==currentTick)return;
        tick=currentTick;
        fired.clear();
        complete.clear();
        flights.clear();
    }
    void consume(Battle.VisualEvent e){
        if(e==null||e.tick()!=tick||e.shotId()<=0)return;
        if(e.phase()==Battle.VisualPhase.FIRE){
            if(supported(e.weapon()))fired.put(e.shotId(),e);
            return;
        }
        if(e.phase()!=Battle.VisualPhase.IMPACT&&
                e.phase()!=Battle.VisualPhase.INTERCEPT&&
                e.phase()!=Battle.VisualPhase.TIMEOUT)return;
        Battle.VisualEvent source=fired.remove(e.shotId());
        if(source==null||source.tick()!=tick||!complete.add(e.shotId()))return;
        if(!Double.isFinite(source.x())||!Double.isFinite(source.y())||
                !Double.isFinite(e.x())||!Double.isFinite(e.y()))return;
        double length=Math.hypot(e.x()-source.x(),e.y()-source.y());
        if(length<.01)return;
        if(flights.size()==MAX_FLIGHTS)flights.remove(0);
        flights.add(new Path(e.shotId(),tick,source.weapon(),source.x(),source.y(),
                e.x(),e.y()));
    }
    List<Path> flights(){return flights;}
    boolean has(long id,long atTick){return atTick==tick&&complete.contains(id);}
    static Point at(Path path,float fraction){
        float t=Float.isFinite(fraction)?Math.max(0f,Math.min(1f,fraction)):1f;
        return new Point((float)(path.fromX()+(path.toX()-path.fromX())*t),
                (float)(path.fromY()+(path.toY()-path.fromY())*t));
    }
    void reset(){tick=Long.MIN_VALUE;fired.clear();complete.clear();flights.clear();}
}
