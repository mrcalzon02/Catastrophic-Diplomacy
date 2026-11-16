package diplomacy.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import diplomacy.client.ProceduralStarfield;
import diplomacy.sim.LevelSky;
import java.util.EnumMap;
import java.util.Locale;
import java.util.zip.CRC32;

/** Same ordered inputs for independent source builds. Readbacks occur after the timed section. */
public final class SkyFrameBenchmark extends ApplicationAdapter {
    private static final int FRAMES = 32;
    private final OrthographicCamera hud = new OrthographicCamera();
    private final OrthographicCamera battle = new OrthographicCamera();
    private final FitViewport viewport = new FitViewport(1280, 720, hud);
    private final int[] levels = new int[LevelSky.Formation.values().length];
    private ProceduralStarfield sky;
    private SpriteBatch batch;
    private int frame;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.disableAudio(true);
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(0);
        config.useVsync(false);
        if (!Boolean.getBoolean("bench.es2"))
            config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        new Lwjgl3Application(new SkyFrameBenchmark(), config);
    }

    @Override public void create() {
        var found = new EnumMap<LevelSky.Formation, Integer>(LevelSky.Formation.class);
        for (int i = 1; i <= 500; i++) found.putIfAbsent(LevelSky.forLevel(i).formation(), i);
        for (var formation : LevelSky.Formation.values()) levels[formation.ordinal()] = found.get(formation);
        sky = new ProceduralStarfield();
        batch = new SpriteBatch();
        System.out.printf("DEVICE %s GL30=%s%n", Gdx.gl.glGetString(GL20.GL_RENDERER), Gdx.gl30 != null);
        System.out.println("frame,level,motion,width,height,complete_ms,rgba_crc32");
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }

    @Override public void render() {
        int phase = frame % FRAMES;
        int level = levels[frame / FRAMES];
        if (phase == 0) sky.useLevel(level);
        // Include warm stationary views, a continuous pan, a zoom change
        // followed by repeated identical views, and an odd letterboxed window.
        int pan = Math.max(0, Math.min(8, phase - 12));
        battle.position.set(640 + pan * 11, 360 - pan * 7, 0);
        battle.zoom = phase < 21 ? 1.13f : .72f;
        viewport.apply();
        Gdx.gl.glClearColor(.015f, .025f, .055f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        long start = System.nanoTime();
        sky.render(batch, hud, battle, 1280, 720);
        Gdx.gl.glFinish();
        long end = System.nanoTime();
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        Pixmap image = ScreenUtils.getFrameBufferPixmap(0, 0, width, height);
        CRC32 crc = new CRC32();
        try { crc.update(image.getPixels().duplicate()); }
        finally { image.dispose(); }
        int glError = Gdx.gl.glGetError();
        if (glError != GL20.GL_NO_ERROR) throw new AssertionError("GL error " + glError);
        String motion = phase == 0 ? "level" : phase == 21 ? "zoom" :
                phase > 12 && phase <= 20 ? "pan" : "stationary";
        System.out.printf(Locale.ROOT, "%d,%d,%s,%d,%d,%.4f,%08x%n",
                frame, level, motion, width, height, (end - start) / 1e6, crc.getValue());
        frame++;
        if (frame == 3 * FRAMES) Gdx.graphics.setWindowedMode(1057, 769);
        if (frame == levels.length * FRAMES) Gdx.app.exit();
    }

    @Override public void dispose() { batch.dispose(); sky.dispose(); }
}
