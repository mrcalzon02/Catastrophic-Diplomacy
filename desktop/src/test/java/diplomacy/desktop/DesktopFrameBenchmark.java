package diplomacy.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.utils.ScreenUtils;
import diplomacy.client.CatastrophicGame;
import diplomacy.sim.Battle;
import diplomacy.sim.ScenarioCatalog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.zip.CRC32;

/** Opt-in desktop benchmark. Fixed-delta frames compare the same battle pixels.
 * Live frames measure uncapped start-to-start intervals through the normal swap path.
 * Reflection is confined to this fixture so shipping clients gain no diagnostic switches. */
public final class DesktopFrameBenchmark extends ApplicationAdapter {
    private final CatastrophicGame game=new CatastrophicGame();
    private final boolean live=Boolean.getBoolean("bench.live");
    private final int warm=Integer.getInteger("bench.warm",18);
    private final int samples=Integer.getInteger("bench.frames",80);
    private final String output=System.getProperty("bench.output");
    private Field delta;
    private Battle battle;
    private GLProfiler profiler;
    private int index;
    private long previousStart,startingTick;

    public static void main(String[] args){
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setPreferencesConfig("benchmark-prefs/", com.badlogic.gdx.Files.FileType.Local);
        config.setWindowedMode(1280,720);
        config.setForegroundFPS(0);
        config.useVsync(false);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new DesktopFrameBenchmark(),config);
    }

    private static Field field(Object owner,String name) throws ReflectiveOperationException{
        Field field=owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static Method method(Object owner,String name,Class<?>... arguments)
            throws ReflectiveOperationException{
        Method method=owner.getClass().getDeclaredMethod(name,arguments);
        method.setAccessible(true);
        return method;
    }
    @Override public void create(){
        if(warm<1||samples<1)throw new IllegalArgumentException("Positive warmup and sample counts required");
        game.create();
        try{
            if(live){
                method(game,"selectScenario",ScenarioCatalog.Preset.class)
                        .invoke(game,ScenarioCatalog.Preset.ESCORT);
                method(game,"openSkirmishDeployment").invoke(game);
                method(game,"launchSkirmish").invoke(game);
            }else{
                // Canonical proving ground with the initial backdrop held constant.
                // Bypass menu navigation only. All draws use the ordinary 1x client.
                delta=field(Gdx.graphics,"deltaTime");
                Field screen=field(game,"screen");
                for(Object state:screen.getType().getEnumConstants()){
                    if(((Enum<?>)state).name().equals("BATTLE"))screen.set(game,state);
                }
                field(game,"scenario").set(game,ScenarioCatalog.Preset.PROVING_GROUND);
                method(game,"reset").invoke(game);
            }
            battle=(Battle)field(game,"battle").get(game);
            if(battle==null||battle.ships().size()!=(live?32:400))
                throw new IllegalStateException("Requested battle failed to deploy");
            // Keep dynamic FPS text out of the fixed-pixel comparison. Force 1x
            // across all runs; the normal simulation still handles frame clipping.
            field(game,"fpsOverlayEnabled").setBoolean(game,false);
            field(game,"speed").setFloat(game,1f);
            Object governor=field(game,"paceGovernor").get(game);
            method(governor,"request",float.class).invoke(governor,1f);
            method(governor,"setEnabled",boolean.class).invoke(governor,false);
            if(!live)for(int i=0;i<90;i++)battle.step();
            else if(Boolean.getBoolean("bench.engaged")){
                while(battle.tick<1200&&!battle.finished()&&
                        (battle.hitCount==0||battle.missiles().isEmpty()&&battle.ballistics().isEmpty()))
                    battle.step();
                if(battle.finished()||battle.hitCount==0||
                        battle.missiles().isEmpty()&&battle.ballistics().isEmpty())
                    throw new IllegalStateException("No active mid-battle fixture");
            }
        }catch(ReflectiveOperationException error){
            throw new IllegalStateException("Desktop benchmark could not initialize",error);
        }
        if(Boolean.getBoolean("bench.counters")){
            profiler=new GLProfiler(Gdx.graphics);
            profiler.enable();
        }
        System.out.printf(Locale.ROOT,"BENCH %s %dx%d seed=%d ships=%d live=%b counters=%b warm=%d samples=%d%n",
                Gdx.gl.glGetString(GL20.GL_RENDERER),Gdx.graphics.getBackBufferWidth(),
                Gdx.graphics.getBackBufferHeight(),battle.seed(),battle.ships().size(),
                live,profiler!=null,warm,samples);
        startingTick=battle.tick;
        System.out.printf(Locale.ROOT,"BEGIN tick=%d state=%016x hits=%d missiles=%d ballistics=%d%n",battle.tick,battle.fingerprint(),
                battle.hitCount,battle.missiles().size(),battle.ballistics().size());
        System.out.println("frame,tick,interval_ms,submit_ms,complete_ms,draws,binds,rgba_crc32");
    }

    @Override public void resize(int width,int height){game.resize(width,height);}
    @Override public void render(){
        if(profiler!=null)profiler.reset();
        if(!live){
            try{delta.setFloat(Gdx.graphics,1f/60);}
            catch(IllegalAccessException error){throw new IllegalStateException(error);}
        }
        long start=System.nanoTime();
        long interval=start-previousStart;
        previousStart=start;
        game.render();
        long submitted=System.nanoTime();
        if(!live)Gdx.gl.glFinish();
        long completed=System.nanoTime();
        if(index>=warm){
            int draws=profiler==null?-1:profiler.getDrawCalls();
            int binds=profiler==null?-1:profiler.getTextureBindings();
            String checksum="";
            if(!live&&(index==warm||index==warm+samples-1))
                checksum=capture();
            System.out.printf(Locale.ROOT,"%d,%d,%.4f,%.4f,%.4f,%d,%d,%s%n",
                    index,battle.tick,interval/1e6,(submitted-start)/1e6,
                    live?-1.0:(completed-start)/1e6,draws,binds,checksum);
        }
        if(++index>=warm+samples){
            if(battle.tick<=startingTick)
                throw new IllegalStateException("No battle ticks advanced during the frame trial");
            System.out.printf(Locale.ROOT,"END tick=%d state=%016x hits=%d%n",
                    battle.tick,battle.fingerprint(),battle.hitCount);
            Gdx.app.exit();
        }
    }
    private String capture(){
        Pixmap image=ScreenUtils.getFrameBufferPixmap(0,0,
                Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
        try{
            CRC32 checksum=new CRC32();
            checksum.update(image.getPixels().duplicate());
            if(output!=null){
                var folder=Gdx.files.absolute(output);folder.mkdirs();
                PixmapIO.PNG png=new PixmapIO.PNG();
                try{
                    png.setFlipY(true);
                    png.write(folder.child("frame-"+index+".png"),image);
                }catch(java.io.IOException error){throw new IllegalStateException(error);}
                finally{png.dispose();}
            }
            return Long.toHexString(checksum.getValue());
        }finally{image.dispose();}
    }
    @Override public void dispose(){
        if(profiler!=null)profiler.disable();
        game.dispose();
    }
}
