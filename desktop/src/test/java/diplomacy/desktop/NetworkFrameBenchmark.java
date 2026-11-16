package diplomacy.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.utils.ScreenUtils;
import diplomacy.client.CatastrophicGame;
import diplomacy.net.BattleConnection;
import diplomacy.server.BattleServer;
import diplomacy.sim.BattleOrders;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/** Opt-in real server JVM plus a rendered player and an independent second player connection. */
public final class NetworkFrameBenchmark extends ApplicationAdapter {
    private final CatastrophicGame game = new CatastrophicGame();
    private final int perSide = Integer.getInteger("bench.net.ships", 16);
    private final int samples = Integer.getInteger("bench.net.frames", 48);
    private final int warm = Integer.getInteger("bench.net.warm", 15);
    private final long[] tick = new long[samples], spent = new long[samples], interval = new long[samples],
            cpu = new long[samples];
    private final int[] draws = new int[samples], binds = new int[samples], vertices = new int[samples], entities = new int[samples];
    private final java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private Process host;
    private BattleConnection player, other;
    private GLProfiler profiler;
    private Object interpolation;
    private java.lang.reflect.Method hostTick;
    private long previousStart, seqA, seqB, deadline;
    private int index, warmFrames;
    private BufferedReader messages;

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "host".equals(args[0])) {
            try (var server = BattleServer.openLan("127.0.0.1", 0, 2479L, Integer.parseInt(args[1]))) {
                System.out.println("PORT " + server.port());
                System.out.flush();
                new BufferedReader(new InputStreamReader(System.in)).readLine();
                System.out.println("HOST tick=" + server.tick() + " maxStepNs=" + server.maxTickNanos()
                        + " totalQueuedBytes=" + server.bytesQueued());
            }
            return;
        }
        var benchmark = new NetworkFrameBenchmark();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setPreferencesConfig("benchmark-prefs/",com.badlogic.gdx.Files.FileType.Local);
        config.setWindowedMode(1280,720);
        config.setForegroundFPS(0);
        config.useVsync(false);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        try { new Lwjgl3Application(benchmark,config); }
        finally { benchmark.stopHost(); }
    }
    private static Field field(Object target,String name) throws ReflectiveOperationException {
        Field f=target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private int launchHost() throws Exception {
        String executable = Path.of(System.getProperty("java.home"),"bin","java").toString();
        var cmd = new java.util.ArrayList<String>();
        String cpu = System.getProperty("bench.net.hostCpu");
        if(cpu!=null) java.util.Collections.addAll(cmd,"taskset","-c",cpu);
        java.util.Collections.addAll(cmd,executable,"-Xmx450m","-XX:ActiveProcessorCount=2");
        String recording=System.getProperty("bench.net.hostJfr");
        if(recording!=null)cmd.add("-XX:StartFlightRecording=settings=profile,dumponexit=true,filename="+Path.of(recording).toAbsolutePath());
        java.util.Collections.addAll(cmd,"-cp",System.getProperty("java.class.path"),getClass().getName(),"host",Integer.toString(perSide));
        host = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        messages = new BufferedReader(new InputStreamReader(host.getInputStream()));
        long end=System.nanoTime()+30_000_000_000L;
        while(System.nanoTime()<end && host.isAlive()) {
            if(messages.ready()) {
                String line=messages.readLine();
                if(line!=null && line.startsWith("PORT "))return Integer.parseInt(line.substring(5));
            }
            Thread.sleep(10);
        }
        throw new IOException("Dedicated host did not start");
    }
    @Override public void create() {
        try {
            if(warm<1 || samples<2)throw new IllegalArgumentException("Positive warmup and at least two samples required");
            game.create();
            var tap=game.getClass().getDeclaredMethod("tapFrontEnd",float.class,float.class);
            tap.setAccessible(true);
            tap.invoke(game,600f,128f); // Native desktop home menu Multiplayer action.
            Object panel=field(game,"multiplayer").get(game);
            if(panel==null)throw new IllegalStateException("Multiplayer menu did not open");
            int port=launchHost();
            player=BattleConnection.openLan("127.0.0.1",port,false);
            other=BattleConnection.openLan("127.0.0.1",port,false);
            deadline=System.nanoTime()+150_000_000_000L;
            while((player.snapshot()==null || other.snapshot()==null) && System.nanoTime()<deadline)Thread.sleep(1);
            if(player.snapshot()==null || other.snapshot()==null || player.team!=0 || other.team!=1)
                throw new IllegalStateException("Both independent player seats are required");
            seqA=player.order(BattleOrders.PointDefense.BALANCED,BattleOrders.Position.KEEP_MOVING);
            seqB=other.order(BattleOrders.PointDefense.BALANCED,BattleOrders.Position.KEEP_MOVING);
            field(panel,"connection").set(panel,player);
            var enter=panel.getClass().getDeclaredMethod("enterBattleView");
            enter.setAccessible(true);
            enter.invoke(panel);
            if(!field(panel,"battleView").getBoolean(panel))throw new IllegalStateException("Network battle view did not open");
            var camera=(OrthographicCamera)field(game,"battleCamera").get(game);
            camera.zoom=Float.parseFloat(System.getProperty("bench.net.zoom","1"));
            camera.update();
            interpolation=field(game,"remoteShipInterpolation").get(game);
            hostTick=interpolation.getClass().getDeclaredMethod("tick");
            hostTick.setAccessible(true);
            if(Boolean.getBoolean("bench.net.counters")){profiler=new GLProfiler(Gdx.graphics);profiler.enable();}
            System.out.printf(Locale.ROOT,"CONNECTED gpu=%s screen=%dx%d shipsPerSide=%d zoom=%.2f seed=%d frames=%d%n",
                    Gdx.gl.glGetString(GL20.GL_RENDERER),Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight(),
                    perSide,camera.zoom,player.seed,samples);
        } catch(Exception e){throw new IllegalStateException("Network fixture could not initialize",e);}
    }
    @Override public void resize(int width,int height){game.resize(width,height);}
    @Override public void render(){
        try {
            if(!player.connected() || !other.connected() || System.nanoTime()>deadline)
                throw new IllegalStateException("Connected benchmark timed out or lost a seat");
            if(profiler!=null)profiler.reset();
            long start=System.nanoTime(), cpuStart=threads.getCurrentThreadCpuTime();
            game.render();
            long elapsed=System.nanoTime()-start, threadNs=threads.getCurrentThreadCpuTime()-cpuStart;
            long presented=(long)hostTick.invoke(interpolation);
            boolean measuring=warmFrames++>=warm && presented>=90
                    && player.acknowledged()==seqA && other.acknowledged()==seqB;
            if(measuring){
                tick[index]=presented;spent[index]=elapsed;interval[index]=start-previousStart;cpu[index]=threadNs;
                entities[index]=player.snapshot().decodedEntities().size();
                draws[index]=profiler==null?-1:profiler.getDrawCalls();
                binds[index]=profiler==null?-1:profiler.getTextureBindings();
                vertices[index]=profiler==null?-1:(int)profiler.getVertexCount().total;
                if(++index==samples)finish();
            }
            previousStart=start;
        } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private void finish() throws ReflectiveOperationException {
        // Replay an optional previously captured network fixture at the same settled pose on
        // both builds. Its readback happens after all live timing samples.
        String proofPath=System.getProperty("bench.net.proofRows");
        if(proofPath!=null)fixedFrame(Path.of(proofPath));
        if(tick[samples-1]<=tick[0])throw new IllegalStateException("Rendered authority did not advance");
        var pixels=ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
        CRC32 crc=new CRC32();
        try{
            crc.update(pixels.getPixels().duplicate());
            String path=System.getProperty("bench.net.output");
            if(path!=null){
                PixmapIO.PNG png=new PixmapIO.PNG();
                try{
                    // OpenGL readback starts at the lower-left corner; PNG starts at the top.
                    png.setFlipY(true);
                    png.write(Gdx.files.absolute(path),pixels);
                }catch(IOException e){throw new UncheckedIOException(e);}
                finally{png.dispose();}
            }
        }finally{pixels.dispose();}
        System.out.println("frame,rendered_tick,interval_ms,submit_ms,render_thread_cpu_ms,latest_entity_count,draws,binds,vertices");
        for(int i=0;i<samples;i++)System.out.printf(Locale.ROOT,"%d,%d,%.4f,%.4f,%.4f,%d,%d,%d,%d%n",
                i,tick[i],interval[i]/1e6,spent[i]/1e6,cpu[i]/1e6,entities[i],draws[i],binds[i],vertices[i]);
        System.out.printf("PASS renderedTicks=%d..%d latestPeerTick=%d acks=%d/%d rgba=%08x%n",
                tick[0],tick[samples-1],other.snapshot().tick(),player.acknowledged(),other.acknowledged(),crc.getValue());
        Gdx.app.exit();
    }
    private void fixedFrame(Path file) throws ReflectiveOperationException {
        List<String> rows;
        try{rows=Files.readAllLines(file);}
        catch(IOException e){throw new UncheckedIOException(e);}
        var decode=BattleConnection.class.getDeclaredMethod("decodeEntities",List.class,int.class);
        decode.setAccessible(true);
        @SuppressWarnings("unchecked")
        var typed=(List<BattleConnection.Entity>)decode.invoke(null,rows,2);
        var fixture=new BattleConnection.Snapshot("fixed-pixel-replay",90,0,rows,typed);
        var draw=game.getClass().getDeclaredMethod("drawRemoteBattle",BattleConnection.Snapshot.class);
        draw.setAccessible(true);
        draw.invoke(game,fixture);
        if(profiler!=null)profiler.reset();
        draw.invoke(game,fixture);
        System.out.printf("FIXED rows=%d draws=%d binds=%d vertices=%d%n",rows.size(),
                profiler==null?-1:profiler.getDrawCalls(),profiler==null?-1:profiler.getTextureBindings(),
                profiler==null?-1:(int)profiler.getVertexCount().total);
    }
    private void stopHost(){
        if(host==null)return;
        try{
            host.getOutputStream().close();
            if(host.waitFor(5,TimeUnit.SECONDS) && messages!=null)messages.lines().forEach(System.out::println);
            else host.destroyForcibly();
        }catch(Exception ignored){host.destroyForcibly();}
    }
    @Override public void dispose(){
        if(profiler!=null)profiler.disable();
        if(other!=null)other.close();
        game.dispose();
    }
}
