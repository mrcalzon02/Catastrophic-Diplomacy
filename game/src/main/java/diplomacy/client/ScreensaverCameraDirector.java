package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.BattleView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Autonomous screensaver exhibition camera. Picks real surviving ships from
 * both fleets, holds each subject for several seconds, then glides toward a
 * different hull. Wall-clock scheduling is independent of battle time scale.
 *
 * All state is presentation-only. No commands or battle RNG are modified.
 */
final class ScreensaverCameraDirector {
    static final float MIN_SHOT_SECONDS=5.0f;
    static final float MAX_SHOT_SECONDS=8.0f;
    static final float PAN_RESPONSE_PER_SECOND=1.65f;
    static final float ZOOM_RESPONSE_PER_SECOND=1.25f;
    private static final long SEED_SALT=0x43414D4552414C31L;

    record Shot(float centerX,float centerY,float zoom,int shipId) { }

    private Battle currentBattle;
    private Random random;
    private int targetShipId=-1,previousTeam=-1;
    private float centerX,centerY,zoom,desiredZoom,shotTimeRemaining;
    private int targetChanges;

    void reset(Battle battle,long roundSeed) {
        if(battle==null)throw new IllegalArgumentException("Exhibition battle required");
        currentBattle=battle;
        random=new Random(roundSeed ^ SEED_SALT);
        BattleView.Frame overview=BattleView.of(battle);
        centerX=(float)overview.centerX();
        centerY=(float)overview.centerY();
        zoom=(float)Math.max(.16,Math.min(8,overview.zoom()));
        targetShipId=-1;
        previousTeam=-1;
        targetChanges=0;
        shotTimeRemaining=0;
        desiredZoom=zoom;
        chooseTarget();
    }

    private static Battle.Ship findLive(Battle battle,int id) {
        for(Battle.Ship s:battle.ships())
            if(s.id==id&&s.alive())return s;
        return null;
    }

    private void chooseTarget() {
        List<Battle.Ship> alive=new ArrayList<>();
        List<Battle.Ship> otherTeam=new ArrayList<>();
        List<Battle.Ship> otherVessel=new ArrayList<>();
        for(Battle.Ship s:currentBattle.ships())if(s.alive()){
            alive.add(s);
            if(s.team!=previousTeam)otherTeam.add(s);
            if(s.id!=targetShipId)otherVessel.add(s);
        }
        if(alive.isEmpty()){
            targetShipId=-1;shotTimeRemaining=MAX_SHOT_SECONDS;return;
        }
        // Prefer switching faction viewpoints when possible, then a different
        // hull; a single survivor may remain the focus without an infinite loop.
        List<Battle.Ship> pool=!otherTeam.isEmpty()?otherTeam:
                !otherVessel.isEmpty()?otherVessel:alive;
        Battle.Ship next=pool.get(random.nextInt(pool.size()));
        targetShipId=next.id;
        previousTeam=next.team;
        desiredZoom=(float)Math.max(.48,Math.min(1.25,
                .51+random.nextDouble()*.45+next.visualScale()*.12));
        shotTimeRemaining=MIN_SHOT_SECONDS+
                random.nextFloat()*(MAX_SHOT_SECONDS-MIN_SHOT_SECONDS);
        targetChanges++;
    }

    Shot update(Battle battle,float elapsedSeconds) {
        return update(battle,elapsedSeconds,null);
    }
    Shot update(Battle battle,float elapsedSeconds,BattleRenderPoses poses) {
        if(battle==null)throw new IllegalArgumentException("Exhibition battle required");
        if(currentBattle!=battle)reset(battle,battle.seed());
        // Cap a delayed frame so suspending/resuming Android cannot skip
        // a sequence of targets or jerk the entire scene across the map.
        float dt=Math.max(0,Math.min(.08f,elapsedSeconds));
        Battle.Ship subject=findLive(battle,targetShipId);
        if(subject==null || (shotTimeRemaining-=dt)<=0){
            chooseTarget();
            subject=findLive(battle,targetShipId);
        }
        double aimX,aimY;
        if(subject!=null){
            // Follow the exact same visual pose as the hull sprite rather than
            // chasing its un-interpolated physics position by one fixed tick.
            BattleRenderPoses.Pose aim=poses==null?
                    new BattleRenderPoses.Pose((float)subject.x,(float)subject.y,(float)subject.facing):
                    poses.ship(subject);
            aimX=aim.x();
            aimY=aim.y();
        }else{
            BattleView.Frame frame=BattleView.of(battle);
            aimX=frame.centerX();aimY=frame.centerY();
            desiredZoom=(float)Math.max(.16,Math.min(8,frame.zoom()));
        }
        float panFactor=(float)(1-StrictMath.exp(-PAN_RESPONSE_PER_SECOND*dt));
        float zoomFactor=(float)(1-StrictMath.exp(-ZOOM_RESPONSE_PER_SECOND*dt));
        centerX+=(aimX-centerX)*panFactor;
        centerY+=(aimY-centerY)*panFactor;
        zoom+=(desiredZoom-zoom)*zoomFactor;
        return new Shot(centerX,centerY,zoom,targetShipId);
    }

    int targetChanges(){return targetChanges;}
}
