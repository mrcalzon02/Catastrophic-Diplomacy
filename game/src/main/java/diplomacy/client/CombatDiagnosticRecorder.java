package diplomacy.client;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Opt-in, bounded, platform-independent combat performance capture.
 * CSV and event history can be read back directly from the exported ZIP.
 * Never records private files, host paths or user identifiers.
 */
final class CombatDiagnosticRecorder {
    private static final int MAX_FRAMES=3600;
    private static final int MAX_EVENTS=256;
    private static final String FRAME_HEADER=
            "frame,tick,wall_delta_ms,cpu_frame_ms,simulation_cpu_ms,non_sim_cpu_ms,"+
            "p95_wall_ms,requested_speed,effective_speed,ships,craft,ordnance,draw_calls,material_binds\n";
    private static final String EVENTS_HEADER=
            "frame,tick,requested_speed,effective_speed,reason\n";
    private final String[] frames=new String[MAX_FRAMES];
    private final String[] events=new String[MAX_EVENTS];
    private int frameCount,eventCount,framePosition,eventPosition;
    private long totalFrames,droppedFrames,startedMillis;
    private boolean recording;
    boolean recording(){return recording;}
    long framesRecorded(){return totalFrames;}
    long framesOverwritten(){return droppedFrames;}
    void start(){
        frameCount=eventCount=framePosition=eventPosition=0;
        totalFrames=droppedFrames=0;
        startedMillis=System.currentTimeMillis();
        recording=true;
    }
    void stop(){recording=false;}
    void frame(long tick,double wallSeconds,long cpuNs,long simNs,
               double p95Ms,float requested,float effective,
               int ships,int craft,int ordnance,int draws,int binds){
        if(!recording)return;
        if(!Double.isFinite(wallSeconds)||wallSeconds<0||simNs<0||
                cpuNs<simNs||ships<0||craft<0||ordnance<0)return;
        String line=String.format(Locale.ROOT,
                "%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.1f,%.1f,%d,%d,%d,%d,%d\n",
                totalFrames,tick,wallSeconds*1000,cpuNs/1e6,simNs/1e6,
                (cpuNs-simNs)/1e6,p95Ms,requested,effective,
                ships,craft,ordnance,draws,binds);
        frames[framePosition]=line;
        framePosition=(framePosition+1)%MAX_FRAMES;
        if(frameCount<MAX_FRAMES)frameCount++;
        else droppedFrames++;
        totalFrames++;
    }
    void event(long tick,float requested,float effective,String cause){
        if(!recording)return;
        String safe=cause==null?"UNKNOWN":cause.replace(',',' ').replace('\n',' ')
                .replace('\r',' ');
        events[eventPosition]=totalFrames+","+tick+","+
                CombatPace.label(requested)+","+CombatPace.label(effective)+","+safe+"\n";
        eventPosition=(eventPosition+1)%MAX_EVENTS;
        if(eventCount<MAX_EVENTS)eventCount++;
    }
    private static void entry(ZipOutputStream zip,String name,String content) throws IOException{
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private String frameCsv(){
        StringBuilder b=new StringBuilder(FRAME_HEADER);
        int start=(framePosition-frameCount+MAX_FRAMES)%MAX_FRAMES;
        for(int i=0;i<frameCount;i++)b.append(frames[(start+i)%MAX_FRAMES]);
        return b.toString();
    }
    private String eventCsv(){
        StringBuilder b=new StringBuilder(EVENTS_HEADER);
        int start=(eventPosition-eventCount+MAX_EVENTS)%MAX_EVENTS;
        for(int i=0;i<eventCount;i++)b.append(events[(start+i)%MAX_EVENTS]);
        return b.toString();
    }
    void export(OutputStream destination,String platform,float requested,float effective,
                long finalTick) throws IOException{
        if(destination==null)throw new IllegalArgumentException("Destination required");
        recording=false;
        String p=platform==null?"unknown":platform.replace("\\","/")
                .replace("\"","'").replace("\n"," ");
        try(ZipOutputStream zip=new ZipOutputStream(destination,StandardCharsets.UTF_8)){
            entry(zip,"metadata.json","{\n  \"schema\": 1,\n  \"platform\": \""+
                    p+"\",\n  \"startedEpochMillis\": "+startedMillis+
                    ",\n  \"finalTick\": "+finalTick+
                    ",\n  \"requestedSpeed\": "+requested+
                    ",\n  \"effectiveSpeed\": "+effective+"\n}\n");
            entry(zip,"frame-samples.csv",frameCsv());
            entry(zip,"speed-events.csv",eventCsv());
            entry(zip,"summary.json","{\n  \"recordedFrames\": "+totalFrames+
                    ",\n  \"retainedFrames\": "+frameCount+
                    ",\n  \"overwrittenFrames\": "+droppedFrames+
                    ",\n  \"speedEventsRetained\": "+eventCount+"\n}\n");
        }
    }
}
