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
        check(CombatTimeAudio.combatPitch(1f,1f)==1f,
                "Authored SFX playback must stay unchanged at 1x");
        check(CombatTimeAudio.combatPitch(.5f,1f)<
                CombatTimeAudio.combatPitch(1f,1f) &&
                CombatTimeAudio.combatPitch(3f,1f)>
                CombatTimeAudio.combatPitch(2f,1f),
                "Relative combat audio pitch must respond monotonically to speed");
        check(CombatTimeAudio.combatPitch(3f,1f)<1.30f,
                "Maximum combat audio speed must retain reasonable timbre");
        check(Math.abs(CombatTimeAudio.enginePitch(1f,.93f)-.93f)<1e-5f,
                "Engine loops must preserve authored tuning at 1x");
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

        CombatDiagnosticRecorder recorder=new CombatDiagnosticRecorder();
        recorder.start();
        recorder.event(0,1f,1f,"START");
        for(int i=0;i<3900;i++)
            recorder.frame(i,.033,11_000_000L,4_000_000L,32,2f,1.5f,
                    400,100,500,100,15);
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
        check(files.get("metadata.json").contains("\"finalTick\": 3900"),"Metadata readback");
        String csv=files.get("frame-samples.csv");
        check(csv.startsWith("frame,tick")&&csv.contains("300,300,")&&
                csv.contains("3899,3899,"),"Ring capture chronological readback");
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
        System.out.println("PASS discrete combat speeds, adaptive governor and ZIP telemetry readback");
    }
}
