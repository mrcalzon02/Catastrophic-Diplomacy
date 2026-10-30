package diplomacy.client;

import diplomacy.sim.Battle;
import java.util.HashSet;
import java.util.Set;

/** Headless assurance of randomized, gentle, repeated screensaver ship tracking. */
public final class ScreensaverCameraDirectorTest {
    private static void require(boolean condition,String why){
        if(!condition)throw new AssertionError(why);
    }
    private static Battle fixture(){
        Battle battle=new Battle(44821L);
        for(int i=0;i<20;i++){
            int team=i%2;
            battle.addShip(team,team==0?Battle.Faction.DRAXIAN:
                    Battle.Faction.CARNELIAN,team==0?60+i*18:880+i*15,120+i*20);
        }
        return battle;
    }
    public static void main(String[] args) {
        Battle battle=fixture();
        ScreensaverCameraDirector camera=new ScreensaverCameraDirector();
        camera.reset(battle,991L);
        Battle repeatedBattle=fixture();
        ScreensaverCameraDirector replay=new ScreensaverCameraDirector();
        replay.reset(repeatedBattle,991L);
        Set<Integer> seen=new HashSet<>();
        Set<Integer> teams=new HashSet<>();
        var previous=camera.update(battle,0);
        float firstX=previous.centerX();
        for(int i=0;i<1400;i++){
            var shot=camera.update(battle,.05f);
            var repeated=replay.update(repeatedBattle,.05f);
            require(shot.shipId()==repeated.shipId(),"seed-replay target sequence");
            require(Math.abs(shot.centerX()-repeated.centerX())<.001f,
                    "seed-replay camera path");
            require(Float.isFinite(shot.centerX())&&Float.isFinite(shot.centerY())
                    &&Float.isFinite(shot.zoom()),"finite view");
            require(shot.zoom()>=.16f&&shot.zoom()<=8f,"bounded zoom");
            require(Math.abs(shot.centerX()-previous.centerX())<125f,
                    "no abrupt frame-to-frame camera teleport");
            if(shot.shipId()>0){
                seen.add(shot.shipId());
                for(Battle.Ship s:battle.ships())if(s.id==shot.shipId()){
                    require(s.alive(),"only live targets");
                    teams.add(s.team);
                }
            }
            previous=shot;
        }
        require(previous.zoom()<.45f,"close-up ship camera must zoom far past old 0.48 floor");
        camera.manualPan(130,60);
        var manualShot=camera.update(battle,.05f);
        require(camera.manual()&&Math.abs(manualShot.centerX()-previous.centerX())>110,
                "manual drag must override automatic shot");
        float closer=camera.manualScale(.5f);
        require(closer<manualShot.zoom(),"manual pinch must zoom in");
        camera.resumeAuto();
        require(!camera.manual(),"AUTO action must restore cinematic camera");
        require(seen.size()>=5,"must visit multiple distinct ships");
        require(teams.size()==2,"must show both fleets");
        require(camera.targetChanges()>=9,"camera must regularly change subjects");
        require(Math.abs(previous.centerX()-firstX)>10,"camera must move");
        // The cinematic director must aim at the SAME interpolated hull pose
        // that the sprite renderer sees, not its future physics-tick location.
        Battle interpolated=fixture();
        ScreensaverCameraDirector smooth=new ScreensaverCameraDirector();
        ScreensaverCameraDirector raw=new ScreensaverCameraDirector();
        smooth.reset(interpolated,773L);
        raw.reset(interpolated,773L);
        BattleRenderPoses poses=new BattleRenderPoses();
        poses.beforeStep(interpolated);
        for(Battle.Ship ship:interpolated.ships())ship.x+=1000;
        poses.fraction(interpolated,0f);
        var smoothFrame=smooth.update(interpolated,.05f,poses);
        var rawFrame=raw.update(interpolated,.05f);
        require(Math.abs(rawFrame.centerX()-smoothFrame.centerX())>40,
                "cinematic shot must follow interpolated, not future tick, pose");
        int currently=previous.shipId();
        for(Battle.Ship s:battle.ships())if(s.id==currently)s.hull=0;
        var switched=camera.update(battle,.016f);
        require(switched.shipId()!=currently,"dead target immediately replaced");
        camera.reset(battle,992L);
        require(camera.targetChanges()==1,"fresh round resets shot schedule");
        System.out.println("PASS: multi-faction autonomous tracking, smooth shots and death retargeting");
    }
}
