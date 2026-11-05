package diplomacy.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

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
            "p95_wall_ms,requested_speed,effective_speed,ships,craft,ordnance,"+
            "sim_ticks,simulation_cpu_ms_per_tick,draw_calls,material_binds\n";
    private static final String EVENTS_HEADER=
            "frame,tick,requested_speed,effective_speed,reason\n";
    private final String[] frames=new String[MAX_FRAMES];
    private final String[] events=new String[MAX_EVENTS];
    private final double[] frameTimes=new double[MAX_FRAMES];
    private int peakShips,peakCraft,peakOrdnance;
    private int frameCount,eventCount,framePosition,eventPosition;
    private long totalFrames,droppedFrames,startedMillis;
    private long totalSimulationTicks,measuredSimulationNs;
    private long battleSeed,firstTick;
    private int startingShips;
    private boolean hasBattleMetadata;
    private boolean recording;
    boolean recording(){return recording;}
    long framesRecorded(){return totalFrames;}
    long framesOverwritten(){return droppedFrames;}
    void start(){start(0L,0L,0,false);}
    /** Capture the original deterministic encounter identity, not a later restart. */
    void start(long seed,long tick,int ships){start(seed,tick,ships,true);}
    private void start(long seed,long tick,int ships,boolean hasMetadata){
        battleSeed=seed;
        firstTick=tick;
        startingShips=Math.max(0,ships);
        hasBattleMetadata=hasMetadata;
        frameCount=eventCount=framePosition=eventPosition=0;
        totalFrames=droppedFrames=totalSimulationTicks=measuredSimulationNs=0;
        peakShips=peakCraft=peakOrdnance=0;
        startedMillis=System.currentTimeMillis();
        recording=true;
    }
    void stop(){recording=false;}
    /** Resume after ZIP export failure without discarding the rolling evidence. */
    void resume(){recording=true;}
    /** Compatibility overload for callers without step-level timing metadata. */
    void frame(long tick,double wallSeconds,long cpuNs,long simNs,
               double p95Ms,float requested,float effective,
               int ships,int craft,int ordnance,int draws,int binds){
        frame(tick,wallSeconds,cpuNs,simNs,p95Ms,requested,effective,
                ships,craft,ordnance,draws,binds,-1);
    }
    /** Record real physics steps separately from rendered frames. A forced
     * verification-speed run must never be mistaken for native 1x FPS. */
    void frame(long tick,double wallSeconds,long cpuNs,long simNs,
               double p95Ms,float requested,float effective,
               int ships,int craft,int ordnance,int draws,int binds,int simTicks){
        if(!recording)return;
        if(!Double.isFinite(wallSeconds)||wallSeconds<0||simNs<0||
                cpuNs<simNs||ships<0||craft<0||ordnance<0||simTicks< -1)return;
        double msPerTick=simTicks>0?simNs/(1_000_000.0*simTicks):0.0;
        String line=String.format(Locale.ROOT,
                "%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.1f,%.1f,%d,%d,%d,%d,%.4f,%d,%d\n",
                totalFrames,tick,wallSeconds*1000,cpuNs/1e6,simNs/1e6,
                (cpuNs-simNs)/1e6,p95Ms,requested,effective,
                ships,craft,ordnance,simTicks,msPerTick,draws,binds);
        if(simTicks>0){
            totalSimulationTicks+=simTicks;
            measuredSimulationNs+=simNs;
        }
        frames[framePosition]=line;
        frameTimes[framePosition]=wallSeconds*1000.0;
        peakShips=Math.max(peakShips,ships);
        peakCraft=Math.max(peakCraft,craft);
        peakOrdnance=Math.max(peakOrdnance,ordnance);
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
    private double percentile(double proportion){
        if(frameCount==0)return 0;
        double[] data=new double[frameCount];
        int start=(framePosition-frameCount+MAX_FRAMES)%MAX_FRAMES;
        for(int i=0;i<frameCount;i++)data[i]=frameTimes[(start+i)%MAX_FRAMES];
        Arrays.sort(data);
        return data[Math.max(0,(int)Math.ceil(data.length*proportion)-1)];
    }
    /**
     * Streaming on-device readback. Android launchers may have a 16 MiB heap;
     * do not materialize the full ZIP to verify a written trace.
     * Each expected entry must be readable, nonempty, and CRC-valid.
     */
    static void verifyArchive(InputStream source) throws IOException{
        if(source==null)throw new IllegalArgumentException("ZIP source required");
        Set<String> expected=Set.of("metadata.json","frame-samples.csv",
                "speed-events.csv","summary.json","diagnostic.log");
        Set<String> found=new HashSet<>();
        byte[] chunk=new byte[4096];
        long archiveBytes=0;
        try(ZipInputStream zip=new ZipInputStream(source,StandardCharsets.UTF_8)){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(entry.isDirectory() || !expected.contains(entry.getName()) ||
                        !found.add(entry.getName()))
                    throw new IOException("Unexpected or duplicated diagnostics entry: "+
                            entry.getName());
                long entryBytes=0;
                int n;
                while((n=zip.read(chunk))!=-1){
                    entryBytes+=n;
                    archiveBytes+=n;
                    if(archiveBytes>8_000_000L)
                        throw new IOException("Performance diagnostics archive exceeds safe limit");
                }
                if(entryBytes==0)throw new IOException("Empty diagnostics entry: "+entry.getName());
                zip.closeEntry();
            }
        }
        if(!found.equals(expected))
            throw new IOException("Missing diagnostics entries: "+expected.toString());
    }

    void export(OutputStream destination,String platform,float requested,float effective,
                long finalTick) throws IOException{
        if(destination==null)throw new IllegalArgumentException("Destination required");
        recording=false;
        String p=platform==null?"unknown":platform.replace("\\","/")
                .replace("\"","'").replace("\n"," ");
        try(ZipOutputStream zip=new ZipOutputStream(destination,StandardCharsets.UTF_8)){
            entry(zip,"metadata.json","{\n  \"schema\": 2,\n  \"platform\": \""+
                    p+"\",\n  \"startedEpochMillis\": "+startedMillis+
                    ",\n  \"battleSeed\": "+(hasBattleMetadata?Long.toString(battleSeed):"null")+
                    ",\n  \"captureStartTick\": "+(hasBattleMetadata?Long.toString(firstTick):"null")+
                    ",\n  \"shipsAtCaptureStart\": "+startingShips+
                    ",\n  \"finalTick\": "+finalTick+
                    ",\n  \"requestedSpeed\": "+requested+
                    ",\n  \"effectiveSpeed\": "+effective+"\n}\n");
            entry(zip,"frame-samples.csv",frameCsv());
            entry(zip,"speed-events.csv",eventCsv());
            entry(zip,"summary.json","{\n  \"recordedFrames\": "+totalFrames+
                    ",\n  \"retainedFrames\": "+frameCount+
                    ",\n  \"overwrittenFrames\": "+droppedFrames+
                    ",\n  \"simulationTicksRecorded\": "+totalSimulationTicks+
                    ",\n  \"meanSimulationCpuMsPerTick\": "+
                    (totalSimulationTicks>0?measuredSimulationNs/(1_000_000.0*totalSimulationTicks):0.0)+
                    ",\n  \"wallFrameP50Ms\": "+percentile(.50)+
                    ",\n  \"wallFrameP95Ms\": "+percentile(.95)+
                    ",\n  \"wallFrameWorstMs\": "+percentile(1.0)+
                    ",\n  \"peakShips\": "+peakShips+
                    ",\n  \"peakCraft\": "+peakCraft+
                    ",\n  \"peakOrdnance\": "+peakOrdnance+
                    ",\n  \"speedEventsRetained\": "+eventCount+"\n}\n");
            entry(zip,"diagnostic.log",eventCsv());
        }
    }
}
