package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.BattleView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Exhibition camera: close hull shots, dogfight tracking and guided-ordnance
 * fly-alongs. Never changes battle orders, simulation time or battle RNG.
 */
final class ScreensaverCameraDirector {
    static final float MIN_SHOT_SECONDS=5.0f, MAX_SHOT_SECONDS=8.0f;
    static final float PAN_RESPONSE_PER_SECOND=1.85f;
    static final float ZOOM_RESPONSE_PER_SECOND=2.1f;
    static final float MIN_ZOOM=.075f, MAX_ZOOM=8f;
    static final float MANUAL_HOLD_SECONDS=9f;
    private static final long SEED_SALT=0x43414D4552414C31L;

    enum TargetKind { SHIP, FIGHTER, MISSILE, OVERVIEW }
    record Shot(float centerX,float centerY,float zoom,int shipId,
                TargetKind kind,long subjectId) { }

    private Battle currentBattle;
    private Random random;
    private TargetKind targetKind=TargetKind.OVERVIEW;
    private long targetId=-1;
    private int previousTeam=-1, targetChanges;
    private float centerX,centerY,zoom,desiredZoom,shotTimeRemaining,shotElapsed;
    private float manualHold;

    private static float clamp(float value,float minimum,float maximum){
        return Math.max(minimum,Math.min(maximum,value));
    }

    void reset(Battle battle,long roundSeed) {
        if(battle==null)throw new IllegalArgumentException("Exhibition battle required");
        currentBattle=battle;
        random=new Random(roundSeed ^ SEED_SALT);
        BattleView.Frame overview=BattleView.of(battle);
        centerX=(float)overview.centerX();
        centerY=(float)overview.centerY();
        zoom=clamp((float)overview.zoom(),.16f,MAX_ZOOM);
        desiredZoom=zoom;
        targetId=-1;
        targetKind=TargetKind.OVERVIEW;
        previousTeam=-1;
        targetChanges=0;
        shotTimeRemaining=shotElapsed=manualHold=0;
        chooseTarget();
    }

    private static Battle.Ship findLive(Battle battle,long id) {
        for(Battle.Ship s:battle.ships())
            if(s.id==id&&s.alive())return s;
        return null;
    }
    private static Battle.Craft findFighter(Battle battle,long id) {
        for(Battle.Craft c:battle.crafts())
            if(c.id==id&&c.fighter&&c.integrity>0&&c.fuel>0)return c;
        return null;
    }
    private static Battle.Missile findMissile(Battle battle,long id) {
        for(Battle.Missile m:battle.missiles())
            if(m.visualId==id&&m.integrity>0&&m.fuel>0&&!m.decoy)return m;
        return null;
    }

    private boolean targetExists(Battle battle){
        return switch(targetKind){
            case SHIP -> findLive(battle,targetId)!=null;
            case FIGHTER -> findFighter(battle,targetId)!=null;
            case MISSILE -> findMissile(battle,targetId)!=null;
            case OVERVIEW -> true;
        };
    }

    private void chooseTarget() {
        List<Battle.Ship> ships=new ArrayList<>(),otherTeam=new ArrayList<>(),
                otherHull=new ArrayList<>();
        for(Battle.Ship s:currentBattle.ships())if(s.alive()){
            ships.add(s);
            if(s.team!=previousTeam)otherTeam.add(s);
            if(s.id!=targetId||targetKind!=TargetKind.SHIP)otherHull.add(s);
        }
        shotElapsed=0;
        // During a round's victory pause, glide to a fleet overview instead
        // of chasing a departing missile after the final ship is destroyed.
        if(ships.isEmpty()){
            targetKind=TargetKind.OVERVIEW;
            targetId=-1;
            shotTimeRemaining=MAX_SHOT_SECONDS;
            return;
        }
        List<Battle.Craft> fighters=new ArrayList<>();
        for(Battle.Craft c:currentBattle.crafts())
            if(c.fighter&&c.integrity>0&&c.fuel>0)fighters.add(c);
        List<Battle.Missile> ordnance=new ArrayList<>();
        for(Battle.Missile m:currentBattle.missiles())
            if(m.integrity>0&&m.fuel>0&&!m.decoy)ordnance.add(m);

        int roll=random.nextInt(100);
        if(roll<20&&!ordnance.isEmpty()){
            Battle.Missile next=ordnance.get(random.nextInt(ordnance.size()));
            targetKind=TargetKind.MISSILE;
            targetId=next.visualId;
            previousTeam=next.team;
            desiredZoom=.075f+random.nextFloat()*.07f;
            shotTimeRemaining=2.1f+random.nextFloat()*1.4f;
        }else if(roll<50&&!fighters.isEmpty()){
            Battle.Craft next=fighters.get(random.nextInt(fighters.size()));
            targetKind=TargetKind.FIGHTER;
            targetId=next.id;
            previousTeam=next.team;
            desiredZoom=.095f+random.nextFloat()*.085f;
            shotTimeRemaining=3.0f+random.nextFloat()*2.2f;
        }else{
            // Capital and escort shots alternate factions whenever possible.
            List<Battle.Ship> pool=!otherTeam.isEmpty()?otherTeam:
                    !otherHull.isEmpty()?otherHull:ships;
            Battle.Ship next=pool.get(random.nextInt(pool.size()));
            targetKind=TargetKind.SHIP;
            targetId=next.id;
            previousTeam=next.team;
            desiredZoom=clamp((float)(.14+random.nextDouble()*.15+
                    next.visualScale()*.047),.16f,.41f);
            shotTimeRemaining=MIN_SHOT_SECONDS+
                    random.nextFloat()*(MAX_SHOT_SECONDS-MIN_SHOT_SECONDS);
        }
        targetChanges++;
    }

    /** Single-finger drag: permit framing without dismissing the exhibition. */
    void manualPan(float dx,float dy){
        if(!Float.isFinite(dx)||!Float.isFinite(dy))return;
        centerX+=dx;centerY+=dy;
        manualHold=MANUAL_HOLD_SECONDS;
    }

    /** Ratio >1 zooms out; ratio <1 zooms in. Returns the new camera zoom. */
    float manualScale(float ratio){
        if(Float.isFinite(ratio)&&ratio>0){
            zoom=clamp(zoom*ratio,MIN_ZOOM,MAX_ZOOM);
            manualHold=MANUAL_HOLD_SECONDS;
        }
        return zoom;
    }

    /** Explicit AUTO control gives the cinematic director the view back. */
    void resumeAuto(){
        manualHold=0;
        shotTimeRemaining=0;
    }

    boolean manual(){return manualHold>0;}
    int targetChanges(){return targetChanges;}

    Shot update(Battle battle,float elapsedSeconds){
        return update(battle,elapsedSeconds,null);
    }
    Shot update(Battle battle,float elapsedSeconds,BattleRenderPoses poses){
        if(battle==null)throw new IllegalArgumentException("Exhibition battle required");
        if(currentBattle!=battle)reset(battle,battle.seed());
        float dt=clamp(elapsedSeconds,0,.08f);
        if(manualHold>0){
            manualHold=Math.max(0,manualHold-dt);
            return shot();
        }
        if(!targetExists(battle)||(shotTimeRemaining-=dt)<=0)
            chooseTarget();
        shotElapsed+=dt;

        double aimX,aimY;
        switch(targetKind){
            case SHIP -> {
                Battle.Ship subject=findLive(battle,targetId);
                BattleRenderPoses.Pose aim=poses==null?
                        new BattleRenderPoses.Pose((float)subject.x,(float)subject.y,
                                (float)subject.facing):poses.ship(subject);
                aimX=aim.x();aimY=aim.y();
            }
            case FIGHTER -> {
                Battle.Craft subject=findFighter(battle,targetId);
                BattleRenderPoses.Pose aim=poses==null?
                        new BattleRenderPoses.Pose((float)subject.x,(float)subject.y,0):
                        poses.craft(subject);
                aimX=aim.x();aimY=aim.y();
            }
            case MISSILE -> {
                Battle.Missile subject=findMissile(battle,targetId);
                BattleRenderPoses.Pose aim=poses==null?
                        new BattleRenderPoses.Pose((float)subject.x,(float)subject.y,0):
                        poses.missile(subject);
                // Keep a short amount of flight path visible in front of the
                // rocket, even during terminal correction / burn transitions.
                aimX=aim.x()+clamp((float)(subject.vx*.035),-19,19);
                aimY=aim.y()+clamp((float)(subject.vy*.035),-19,19);
            }
            default -> {
                BattleView.Frame view=BattleView.of(battle);
                aimX=view.centerX();aimY=view.centerY();
                desiredZoom=clamp((float)view.zoom(),.16f,MAX_ZOOM);
            }
        }
        // Ease into a new subject, then tighten tracking for fast moving craft.
        float response=PAN_RESPONSE_PER_SECOND;
        if(shotElapsed>.75f){
            if(targetKind==TargetKind.MISSILE)response=11f;
            else if(targetKind==TargetKind.FIGHTER)response=6.5f;
        }
        float panFactor=(float)(1-StrictMath.exp(-response*dt));
        float zoomFactor=(float)(1-StrictMath.exp(-ZOOM_RESPONSE_PER_SECOND*dt));
        centerX+=(aimX-centerX)*panFactor;
        centerY+=(aimY-centerY)*panFactor;
        zoom+= (desiredZoom-zoom)*zoomFactor;
        zoom=clamp(zoom,MIN_ZOOM,MAX_ZOOM);
        return shot();
    }

    private Shot shot(){
        int shipId=targetKind==TargetKind.SHIP?(int)targetId:-1;
        return new Shot(centerX,centerY,zoom,shipId,targetKind,targetId);
    }
}
