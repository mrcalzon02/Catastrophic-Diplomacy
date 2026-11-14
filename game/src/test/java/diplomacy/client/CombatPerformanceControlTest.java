package diplomacy.client;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

/** Portable regression for the actual discrete pace, governor and ZIP recorder. */
public final class CombatPerformanceControlTest {
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        check(CombatPace.DEFAULT==1f,"Player default must be 1x");
        float[] levels={.5f,1f,1.5f,2f,2.5f,3f};
        check(CombatPace.slower(1f)==.5f&&CombatPace.faster(1f)==1.5f,
                "Half-step controls");
        for(int i=0;i<levels.length;i++){
            check(CombatPace.clamp(levels[i])==levels[i],"Unknown level mapping");
            check(Math.abs(CombatPace.simulationMultiplier(levels[i])-
                    levels[i]*.10f)<1e-6f,"Legacy pace mapping "+i);
            if(i>0)check(CombatPace.slower(levels[i])==levels[i-1],"Lower neighbor "+i);
            if(i<levels.length-1)
                check(CombatPace.faster(levels[i])==levels[i+1],"Upper neighbor "+i);
        }
        check(CombatPace.simulationMultiplier(2f)==.2f,"Legacy perceived 2x");
        // Stage-A input boundaries: never create a seventh manual speed.
        check(CombatPace.clamp(-10f)==.5f&&CombatPace.clamp(99f)==3f,
                "Out-of-range finite requests must saturate at the end positions");
        check(CombatPace.clamp(1.25f)==1f&&CombatPace.clamp(1.26f)==1.5f,
                "Midpoint and nearest-neighbor speed snapping");
        check(CombatPace.clamp(Float.NaN)==1f&&
                CombatPace.clamp(Float.POSITIVE_INFINITY)==1f&&
                CombatPace.clamp(Float.NEGATIVE_INFINITY)==1f,
                "Nonfinite saved/user speeds must restore the 1x default");
        check("x2.5".equals(CombatPace.label(2.5f)),
                "Displayed speed label must match the selectable position");
        AdaptiveCombatPace auto=new AdaptiveCombatPace();
        auto.request(3f);
        check(auto.effective()==3f&&auto.enabled(),"Requested cap starts at 3x");
        boolean fell=false;
        // Stable simulation-heavy 20 FPS. One step down per evaluation window.
        for(int i=0;i<50;i++)
            fell|=auto.frame(.05,34_000_000L,18_000_000L,true);
        check(fell&&auto.effective()==2.5f,"Governor must reduce sim-bound workload");
        check(auto.p95Ms()>36,"Measured wall-frame p95 lost");
        check(auto.reason().contains("SIMULATION"),"Slowdown cause missing");
        auto.request(3f);
        auto.setEnabled(false);
        check(auto.effective()==3f&&!auto.frame(.05,34_000_000L,20_000_000L,true),
                "Manual mode must hold requested speed");
        // Turning off the automatic governor must NEVER turn off diagnostics.
        for(int i=0;i<45;i++)auto.frame(.05,33_000_000L,17_000_000L,true);
        check(auto.effective()==3f&&auto.p95Ms()>36&&"MANUAL".equals(auto.reason()),
                "Manual mode must retain accurate timing telemetry without changing pace");
        auto.setEnabled(true);
        for(int i=0;i<50;i++)auto.frame(.05,30_000_000L,500_000L,true);
        check(auto.effective()==3f&&auto.reason().contains("RENDER"),
                "GPU/render-bound frames must not be misclassified as sim bottlenecks");
        auto.request(3f);
        for(int i=0;i<45;i++)auto.frame(.05,33_000_000L,15_000_000L,true);
        check(auto.effective()==2.5f,"Second overload must step down");
        for(int i=0;i<480;i++)auto.frame(.0167,10_000_000L,1_000_000L,true);
        check(auto.effective()==3f,"Governor must recover to requested cap");
        auto.request(.5f);
        for(int i=0;i<80;i++)auto.frame(.05,35_000_000L,20_000_000L,true);
        check(auto.effective()==.5f,"Governor floor must not breach .5x");
        auto.frame(.016,5_000_000L,1_000_000L,false);
        // Inactive/paused gameplay must discard a partial overload window.
        // Otherwise twenty pre-pause and twenty post-resume slow frames could
        // trigger a governor reduction before two active seconds had elapsed.
        AdaptiveCombatPace pauseProbe=new AdaptiveCombatPace();
        pauseProbe.request(2.5f);
        for(int i=0;i<20;i++)
            check(!pauseProbe.frame(.05,35_000_000L,20_000_000L,true),
                    "One active second must not trigger a slowdown");
        check(!pauseProbe.frame(.5,35_000_000L,20_000_000L,false),
                "Paused gameplay must not trigger a transition");
        for(int i=0;i<20;i++)
            check(!pauseProbe.frame(.05,35_000_000L,20_000_000L,true),
                    "Paused overload samples must not carry into resume");
        check(pauseProbe.effective()==2.5f,
                "Effective speed must survive pause/resume unchanged");
        boolean postResumeReduction=false;
        for(int i=0;i<22;i++)
            postResumeReduction|=pauseProbe.frame(.05,35_000_000L,20_000_000L,true);
        check(postResumeReduction&&pauseProbe.effective()==2f,
                "A fresh two-second active overload must reduce one speed position");
        pauseProbe.request(1f);
        check(pauseProbe.requested()==1f&&pauseProbe.effective()==1f,
                "Manual cap reduction must take effect immediately after overload");
        pauseProbe.request(3f);
        check(pauseProbe.requested()==3f&&pauseProbe.effective()==3f,
                "An explicit higher cap must restore the chosen speed when at cap");

        CombatDiagnosticRecorder recorder=new CombatDiagnosticRecorder();
        recorder.start(20261104L,17L,400);
        recorder.event(17,1f,1f,"START");
        for(int i=0;i<3900;i++)
            recorder.frame(i,.033,11_000_000L,4_000_000L,32,2f,1.5f,
                    400,100,500,100,15,3);
        check(recorder.framesRecorded()==3900&&recorder.framesOverwritten()==300,
                "Capture must retain a bounded rolling frame trace");
        recorder.event(3900,2f,1.5f,"SIMULATION PRESSURE");
        recorder.stop();
        recorder.frame(3901,.033,11_000_000L,4_000_000L,32,2f,1.5f,
                0,0,0,0,0);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        recorder.export(bytes,"Desktop",2f,1.5f,3900);
        Map<String,String> files=new HashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                ByteArrayOutputStream data=new ByteArrayOutputStream();
                zip.transferTo(data);
                files.put(entry.getName(),data.toString(StandardCharsets.UTF_8));
            }
        }
        check(files.size()==5,"ZIP must contain metadata, frames, events, log and summary");
        CombatDiagnosticRecorder.verifyArchive(new ByteArrayInputStream(bytes.toByteArray()));
        boolean incompleteRejected=false;
        try{
            CombatDiagnosticRecorder.verifyArchive(new ByteArrayInputStream(new byte[]{80,75,3,4}));
        }catch(java.io.IOException expected){incompleteRejected=true;}
        check(incompleteRejected,"Invalid/truncated ZIP must never be reported as exported");
        check(files.get("metadata.json").contains("\"finalTick\": 3900"),"Metadata readback");
        check(files.get("metadata.json").contains("\"battleSeed\": 20261104") &&
                files.get("metadata.json").contains("\"captureStartTick\": 17") &&
                files.get("metadata.json").contains("\"shipsAtCaptureStart\": 400"),
                "Export must preserve original battle seed and capture starting state");
        // A large real frame must disclose lost wall time and actual
        // onscreen pose discontinuities without changing combat simulation.
        CombatDiagnosticRecorder motionCapture=new CombatDiagnosticRecorder();
        motionCapture.start();
        motionCapture.frame(1,.250,12_000_000,5_000_000,250,1f,1f,
                400,1,12,100,2,0,
                new CombatDiagnosticRecorder.MotionStats(170,.3,41,12,2,400));
        ByteArrayOutputStream motionBytes=new ByteArrayOutputStream();
        motionCapture.export(motionBytes,"Desktop",1f,1f,1);
        String motionSummary="";
        String motionCsv="";
        try(ZipInputStream zip=new ZipInputStream(
                new ByteArrayInputStream(motionBytes.toByteArray()))){
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(entry.getName().equals("summary.json"))
                    motionSummary=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                else if(entry.getName().equals("frame-samples.csv"))
                    motionCsv=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
            }
        }
        check(motionSummary.contains("\"totalClippedWallMs\": 170.0")&&
                motionSummary.contains("\"shipJumpFlags\": 2")&&
                motionSummary.contains("\"peakDisplayedCameraStepWorld\": 12.0"),
                "Live motion continuity totals missing from performance ZIP");
        check(motionCsv.contains("clipped_wall_ms,interpolation_alpha")&&
                motionCsv.contains(",170.0000,0.30000,41.00000,12.00000,2,400"),
                "Per-frame position and discarded time evidence missing");
        String csv=files.get("frame-samples.csv");
        check(csv.startsWith("frame,tick")&&csv.contains("300,300,")&&
                csv.contains("3899,3899,"),"Ring capture chronological readback");
        check(csv.contains("sim_ticks,simulation_cpu_ms_per_tick") &&
                csv.contains(",400,100,500,3,1.3333,100,15"),
                "Trace must separate simulation ticks from rendered frames and preserve ms/tick");
        check(files.get("summary.json").contains("\"simulationTicksRecorded\": 11700")
                &&files.get("summary.json").contains("\"meanSimulationCpuMsPerTick\": 1.333"),
                "Summary must reveal the actual physics-tick cost, not misleading forced-speed FPS");
        check(!csv.contains("\n299,299,"),"Oldest overwritten frame leaked");
        check(files.get("speed-events.csv").contains("SIMULATION PRESSURE"),
                "Performance transition missing from export");
        check(files.get("summary.json").contains("\"overwrittenFrames\": 300"),
                "Bounded export must disclose overwritten frames");
        check(files.get("summary.json").contains("\"wallFrameP95Ms\": 33.0")&&
                files.get("summary.json").contains("\"peakShips\": 400"),
                "Export summary must carry truthful performance and fleet metrics");
        check(files.get("diagnostic.log").contains("SIMULATION PRESSURE"),
                "Diagnostic log export missing speed transitions");
        // F4 export stops the active writer; the development capture manager
        // must be able to resume without losing samples after an I/O failure.
        check(!recorder.recording(),"Export must stop active ZIP sample writer");
        recorder.resume();
        recorder.frame(3902,.033,11_000_000L,4_000_000L,32,2f,1.5f,
                399,101,501,100,15,2);
        check(recorder.recording()&&recorder.framesRecorded()==3901&&
                        recorder.framesOverwritten()==301,
                "Development recording must resume with rolling evidence intact");
        // The probe must compare actual rendered world positions, not just
        // raw physics snapshots. A physically teleported ship must be flagged.
        diplomacy.sim.Battle jump=new diplomacy.sim.Battle(47L);
        diplomacy.sim.Battle.Ship subject=jump.addShip(0,
                diplomacy.sim.Battle.Faction.TERRAN,100,200);
        BattleRenderPoses poses=new BattleRenderPoses();
        CombatFrameContinuity probe=new CombatFrameContinuity();
        poses.fraction(jump,1);
        var baseline=probe.sample(jump,poses,0,0,1f/30f,.1);
        check(baseline.comparedShips()==0,"First frame has no prior visible sample");
        subject.x+=300;
        poses.fraction(jump,1);
        var discontinuity=probe.sample(jump,poses,10,0,1f/30f,.1);
        check(discontinuity.comparedShips()==1&&discontinuity.shipJumpFlags()==1&&
                discontinuity.maxShipWorldStep()>299&&
                discontinuity.cameraWorldStep()==10,
                "Visual teleport and camera movement must be separated");
        probe.reset();
        check(probe.sample(jump,poses,0,0,1f/30f,.1).shipJumpFlags()==0,
                "Pause/new-battle probe reset must not invent teleport flags");
        System.out.println("PASS discrete combat speeds, adaptive governor and ZIP telemetry readback");
    }
}
