package diplomacy.client;

import diplomacy.sim.Battle;
import diplomacy.sim.BattleView;
import diplomacy.sim.SublightPropulsion;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Deterministic headless battlespace timing fixture. It uses the ACTUAL
 * CombatPace mapping, Battle.DT, shared render poses, and real inertial coast
 * integrator; no GPU or synthetic visual tween is involved.
 *
 * CSV evidence is deliberately diagnostic, not a release-quality FPS gate.
 * This fixture checks smooth presentation of continuous movement. Full combat
 * determinism and live device frame pacing remain separate tests.
 */
public final class CombatTimelineDiagnostic {
    private static final float[] SPEEDS={.5f,1f,1.5f,2f,2.5f,3f};
    private static final int[] RENDER_FPS={15,30,60};
    private static final int DURATION_SECONDS=12;
    private static final double VELOCITY=90.0;
    private static final double START_X=100.0;
    private static final double STATIONARY_X=900.0;
    private static final String HEADER=
            "speed,fps,frame,wall_seconds,tick,alpha,ship_x,camera_center_x,"+
            "pose_delta,camera_delta,clamped_wall_ms\n";
    private static void require(boolean condition,String description){
        if(!condition)throw new AssertionError(description);
    }
    private record Result(float speed,int fps,int ticks,double maxPoseJump,
                          double maxCameraJump,double lastPose,double lostWallMs){}

    private static Result run(float speed,int fps,StringBuilder frameCsv){
        Battle battle=new Battle(20261105L);
        Battle.Ship moving=battle.addShip(0,Battle.Faction.TERRAN,START_X,250);
        battle.addShip(1,Battle.Faction.DRAXIAN,STATIONARY_X,250);
        moving.vx=VELOCITY;
        BattleRenderPoses poses=new BattleRenderPoses();
        float accumulator=0f;
        double maxPoseJump=0,maxCameraJump=0,lostWallMs=0;
        double previousX=Double.NaN,previousCamera=Double.NaN,lastX=START_X;
        int ticks=0;
        float delta=1f/fps;
        for(int frame=0;frame<DURATION_SECONDS*fps;frame++){
            float boundedDelta=Math.min(.08f,delta);
            lostWallMs+=(delta-boundedDelta)*1000.0;
            accumulator+=boundedDelta*CombatPace.simulationMultiplier(speed);
            int turns=0;
            while(accumulator>=Battle.DT&&turns++<64){
                if(BattleRenderPoses.captureBeforeLastStep(accumulator,turns,64))
                    poses.beforeStep(battle);
                SublightPropulsion.coast(moving,Battle.DT);
                battle.tick++;
                ticks++;
                accumulator-=Battle.DT;
            }
            require(turns<64,"Unbounded fixture catch-up at "+speed+"x/"+fps+"FPS");
            poses.fraction(battle,accumulator/(float)Battle.DT);
            BattleRenderPoses.Pose shown=poses.ship(moving);
            BattleView.Frame camera=poses.frame(battle);
            // One moving hull and one stationary hull must give an exact
            // interpolated midpoint; raw camera bounds would jump per tick.
            double expectedCenter=(shown.x()+STATIONARY_X)/2.0;
            require(Math.abs(camera.centerX()-expectedCenter)<.004,
                    "Raw-tick camera framing at "+speed+"x/"+fps+"FPS frame "+frame);
            double dx=Double.isNaN(previousX)?0:Math.abs(shown.x()-previousX);
            double dc=Double.isNaN(previousCamera)?0:
                    Math.abs(camera.centerX()-previousCamera);
            // Skip the initial sample: no previous physics state exists yet.
            if(ticks>0){
                maxPoseJump=Math.max(maxPoseJump,dx);
                maxCameraJump=Math.max(maxCameraJump,dc);
                double expectedMax=VELOCITY*
                        CombatPace.simulationMultiplier(speed)/fps*1.6+.10;
                require(dx<=expectedMax,
                        "Visible ship teleport at "+speed+"x/"+fps+"FPS frame "+
                                frame+" delta="+dx+" max="+expectedMax);
            }
            frameCsv.append(String.format(Locale.ROOT,
                    "%.1f,%d,%d,%.5f,%d,%.5f,%.5f,%.5f,%.5f,%.5f,%.3f%n",
                    speed,fps,frame,(frame+1)/(double)fps,ticks,
                    accumulator/Battle.DT,shown.x(),camera.centerX(),dx,dc,
                    (delta-boundedDelta)*1000.0));
            previousX=shown.x();
            previousCamera=camera.centerX();
            lastX=shown.x();
        }
        int expectedTicks=(int)Math.floor(
                DURATION_SECONDS*CombatPace.simulationMultiplier(speed)/Battle.DT);
        require(Math.abs(ticks-expectedTicks)<=1,
                "Wrong tick cadence "+speed+"x/"+fps+"FPS actual="+ticks+
                        " expected="+expectedTicks);
        // With fixed-tick interpolation, presentation is intentionally one
        // tick behind authoritative motion, independent of render FPS.
        double expectedX=START_X+VELOCITY*
                (DURATION_SECONDS*CombatPace.simulationMultiplier(speed)-Battle.DT);
        require(Math.abs(lastX-expectedX)<.3,
                "Wrong interpolated timeline "+speed+"x/"+fps+
                        "FPS actual="+lastX+" expected="+expectedX);
        return new Result(speed,fps,ticks,maxPoseJump,maxCameraJump,lastX,lostWallMs);
    }

    private static void diagnoseLargeFrame(StringBuilder frames){
        // The current client clamps 250ms stalls to 80ms. Quantify the missing
        // wall time explicitly, rather than calling that a physical tick fault.
        float wallDelta=.25f,used=Math.min(.08f,wallDelta);
        double discardedMs=(wallDelta-used)*1000.0;
        require(discardedMs>169&&discardedMs<171,
                "Long-frame clipping diagnostic missing");
        frames.append(String.format(Locale.ROOT,
                "1.0,30,-1,0.25000,0,0.00000,100.00000,500.00000,"+
                        "0.00000,0.00000,%.3f%n",discardedMs));
    }

    /** A real device does not deliver perfectly spaced 15/30/60 FPS
     * frames. Exercise uneven wall-clock intervals and an occasional
     * quarter-second stall with the exact gameplay accumulator contract.
     * No fake motion interpolation, rendering API, or independent engine. */
    private static void diagnoseIrregularFrames(StringBuilder summary){
        final float[] wallPattern={
                1f/60,1f/28,1f/45,1f/22,1f/38,
                1f/15,1f/56,.25f,1f/30,1f/50
        };
        for(float speed:SPEEDS){
            Battle battle=new Battle(20261105L);
            Battle.Ship moving=battle.addShip(0,Battle.Faction.TERRAN,START_X,250);
            battle.addShip(1,Battle.Faction.DRAXIAN,STATIONARY_X,250);
            moving.vx=VELOCITY;
            BattleRenderPoses poses=new BattleRenderPoses();
            float accumulator=0;
            double previousX=Double.NaN,maximumJump=0,clippedMs=0;
            double totalUsedSeconds=0;
            int steps=0;
            for(int frame=0;frame<480;frame++){
                float wallDelta=wallPattern[frame%wallPattern.length];
                float bounded=Math.min(.08f,wallDelta);
                clippedMs+=(wallDelta-bounded)*1000;
                totalUsedSeconds+=bounded;
                accumulator+=bounded*CombatPace.simulationMultiplier(speed);
                int turns=0;
                while(accumulator>=Battle.DT&&turns++<64){
                    if(BattleRenderPoses.captureBeforeLastStep(accumulator,turns,64))
                        poses.beforeStep(battle);
                    SublightPropulsion.coast(moving,Battle.DT);
                    battle.tick++;
                    steps++;
                    accumulator-=Battle.DT;
                }
                require(turns<64,"Unbounded irregular frame catch-up at "+speed+"x");
                poses.fraction(battle,accumulator/(float)Battle.DT);
                BattleRenderPoses.Pose shown=poses.ship(moving);
                double expectedCenter=(shown.x()+STATIONARY_X)/2.0;
                require(Math.abs(poses.frame(battle).centerX()-expectedCenter)<.004,
                        "Camera drifted from interpolated ship in jittered sequence");
                if(Double.isFinite(previousX)&&steps>0){
                    double step=Math.abs(shown.x()-previousX);
                    maximumJump=Math.max(maximumJump,step);
                    double permitted=VELOCITY*CombatPace.simulationMultiplier(speed)*
                            bounded*1.7+.18;
                    require(step<=permitted,
                            "Variable-frame visible jump at "+speed+"x frame="+frame+
                            " distance="+step+" allowed="+permitted);
                }
                previousX=shown.x();
            }
            double expected=START_X+VELOCITY*Math.max(0,
                    totalUsedSeconds*CombatPace.simulationMultiplier(speed)-Battle.DT);
            require(Math.abs(previousX-expected)<.35,
                    "Irregular-frame pacing altered net motion at "+speed+
                    "x: actual="+previousX+" expected="+expected);
            require(clippedMs>0,"Stall clipping evidence was not recorded");
            summary.append(String.format(Locale.ROOT,
                    "%.1f,%d,%.6f,%.6f,%.3f%n",
                    speed,steps,previousX,maximumJump,clippedMs));
        }
        System.out.println("PASS six variable-frame timeline tests with 250ms stalls");
    }

    public static void main(String[] arguments)throws Exception{
        StringBuilder frames=new StringBuilder(850_000).append(HEADER);
        StringBuilder summaries=new StringBuilder(
                "speed,fps,ticks,max_pose_jump,max_camera_jump,last_pose_x,"+
                        "clamped_wall_ms\n");
        int scenarios=0;
        for(float speed:SPEEDS)for(int fps:RENDER_FPS){
            Result r=run(speed,fps,frames);
            summaries.append(String.format(Locale.ROOT,
                    "%.1f,%d,%d,%.6f,%.6f,%.6f,%.3f%n",
                    r.speed(),r.fps(),r.ticks(),r.maxPoseJump(),
                    r.maxCameraJump(),r.lastPose(),r.lostWallMs()));
            scenarios++;
        }
        diagnoseLargeFrame(frames);
        StringBuilder jitter=new StringBuilder(
                "speed,ticks,final_ship_x,max_frame_world_step,clipped_wall_ms\\n");
        diagnoseIrregularFrames(jitter);
        Path folder=Path.of("test-output","combat-timeline");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("frames.csv"),frames,
                StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("scenarios.csv"),summaries,
                StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("irregular-frame-scenarios.csv"),jitter,
                StandardCharsets.UTF_8);
        System.out.println("PASS "+scenarios+
                " fixed-step presentation environments (six speeds x three FPS); "+
                "pause/resume and shared camera checks in BattleRenderPosesTest");
        System.out.println("Evidence: "+folder.toAbsolutePath());
    }
}
