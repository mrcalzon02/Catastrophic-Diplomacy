package diplomacy.client;

import diplomacy.sim.Battle;
import java.util.HashMap;
import java.util.Map;

/**
 * Live display-only continuity metrics for exported performance traces.
 * Does not alter camera positions, simulation orders or collision outcomes.
 * Readouts use the actual rendered pose, not the raw physics coordinate.
 */
final class CombatFrameContinuity {
    record Sample(double maxShipWorldStep,double cameraWorldStep,
                  int shipJumpFlags,int comparedShips) {
        static final Sample EMPTY=new Sample(0,0,0,0);
    }
    private static final class Previous {
        float x,y;
        double speed;
        long frame;
        Previous(float x,float y,double speed,long frame){
            this.x=x;this.y=y;this.speed=speed;this.frame=frame;
        }
    }
    private final Map<Integer,Previous> last=new HashMap<>();
    private Battle source;
    private long frame;
    private double cameraX,cameraY;
    private boolean cameraKnown;

    void reset(){
        last.clear();
        source=null;
        frame=0;
        cameraKnown=false;
    }

    Sample sample(Battle battle,BattleRenderPoses poses,
                  double cameraWorldX,double cameraWorldY,
                  double wallSeconds,double simulationMultiplier){
        if(battle==null||poses==null)return Sample.EMPTY;
        if(source!=battle){
            reset();
            source=battle;
        }
        frame++;
        double cameraDelta=cameraKnown?
                Math.hypot(cameraWorldX-cameraX,cameraWorldY-cameraY):0;
        cameraX=cameraWorldX;
        cameraY=cameraWorldY;
        cameraKnown=true;
        double boundedTime=Math.max(0,Math.min(.08,
                Double.isFinite(wallSeconds)?wallSeconds:0));
        double speed=Math.max(0,Double.isFinite(simulationMultiplier)?
                simulationMultiplier:0);
        double maxStep=0;
        int flags=0,compared=0;
        for(Battle.Ship ship:battle.ships()){
            if(!ship.alive())continue;
            BattleRenderPoses.Pose shown=poses.ship(ship);
            Previous prior=last.get(ship.id);
            double shipSpeed=Math.hypot(ship.vx,ship.vy);
            if(prior!=null&&prior.frame==frame-1){
                double travelled=Math.hypot(shown.x()-prior.x,
                        shown.y()-prior.y);
                maxStep=Math.max(maxStep,travelled);
                compared++;
                // Allow acceleration, AI heading changes and fractional frame
                // jitter; log severe anomalies rather than gate releases.
                double expected=Math.max(prior.speed,shipSpeed)*
                        boundedTime*speed;
                if(travelled>Math.max(3.0,expected*4+1.0))flags++;
            }
            if(prior==null){
                prior=new Previous(shown.x(),shown.y(),shipSpeed,frame);
                last.put(ship.id,prior);
            }else{
                prior.x=shown.x();prior.y=shown.y();
                prior.speed=shipSpeed;prior.frame=frame;
            }
        }
        return new Sample(maxStep,cameraDelta,flags,compared);
    }
}
