package diplomacy.client;
import diplomacy.sim.Battle;
import java.util.HashMap;
import java.util.Map;

/** Render-only fixed-tick pose snapshots; never changes the simulation. */
final class BattleRenderPoses {
    record Pose(float x,float y,float angle){}
    private final Map<Integer,Pose> ships=new HashMap<>(),crafts=new HashMap<>();
    private final Map<Long,Pose> missiles=new HashMap<>(),shells=new HashMap<>();
    private Battle source;
    private float fraction=1f;
    void sync(Battle battle){
        if(source==battle)return;
        source=battle;ships.clear();crafts.clear();missiles.clear();shells.clear();
        fraction=1f;
    }
    void beforeStep(Battle battle){
        sync(battle);ships.clear();crafts.clear();missiles.clear();shells.clear();
        for(Battle.Ship s:battle.ships())if(s.alive())
            ships.put(s.id,new Pose((float)s.x,(float)s.y,(float)s.facing));
        for(Battle.Craft c:battle.crafts())
            crafts.put(c.id,new Pose((float)c.x,(float)c.y,0));
        for(Battle.Missile m:battle.missiles())
            missiles.put(m.visualId,new Pose((float)m.x,(float)m.y,
                    (float)Math.atan2(m.vy,m.vx)));
        for(Battle.Ballistic b:battle.ballistics())
            shells.put(b.visualId,new Pose((float)b.x,(float)b.y,
                    (float)Math.atan2(b.vy,b.vx)));
    }
    void fraction(Battle battle,float value){
        sync(battle);
        fraction=Float.isFinite(value)?Math.max(0f,Math.min(1f,value)):1f;
    }
    private Pose blend(Pose prior,Pose now){
        if(prior==null)return now;
        float dx=now.x()-prior.x(),dy=now.y()-prior.y();
        if(dx*dx+dy*dy>1000000f)return now;
        float rotation=(float)Math.atan2(Math.sin(now.angle()-prior.angle()),
                Math.cos(now.angle()-prior.angle()));
        return new Pose(prior.x()+dx*fraction,prior.y()+dy*fraction,
                prior.angle()+rotation*fraction);
    }
    Pose ship(Battle.Ship s){
        return blend(ships.get(s.id),new Pose((float)s.x,(float)s.y,(float)s.facing));
    }
    Pose craft(Battle.Craft c){
        return blend(crafts.get(c.id),new Pose((float)c.x,(float)c.y,0));
    }
    Pose missile(Battle.Missile m){
        return blend(missiles.get(m.visualId),new Pose((float)m.x,(float)m.y,
                (float)Math.atan2(m.vy,m.vx)));
    }
    Pose shell(Battle.Ballistic b){
        return blend(shells.get(b.visualId),new Pose((float)b.x,(float)b.y,
                (float)Math.atan2(b.vy,b.vx)));
    }
    int trackedShips(){return ships.size();}
    int trackedMissiles(){return missiles.size();}
}
