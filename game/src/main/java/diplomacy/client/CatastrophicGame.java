package diplomacy.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.FitViewport;
import diplomacy.sim.Battle;

/** Same interactive client on Windows/Linux desktop and Android; no duplicate rules. */
public final class CatastrophicGame extends ApplicationAdapter {
    private static final float W = 1280, H = 720;
    private OrthographicCamera camera;
    private FitViewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private InstancedShipRenderer instanced;
    private Battle battle;
    private Battle.Faction left = Battle.Faction.TERRAN, right = Battle.Faction.DRAXIAN;
    private long nextSeed = 2479;
    private float accumulator;
    private boolean running = true;
    private int speed = 1;
    private String renderer = "software geometry";

    @Override public void create() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(W, H, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        shapes = new ShapeRenderer(); batch = new SpriteBatch(); font = new BitmapFont();
        font.getData().setScale(1.23f);
        try {
            instanced = new InstancedShipRenderer();
            renderer = "OpenGL 3 instancing";
        } catch (RuntimeException error) {
            Gdx.app.error("GPU", "Instanced renderer unavailable; falling back", error);
        }
        reset();
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key == Input.Keys.SPACE) { running = !running; return true; }
                if (key == Input.Keys.R) { reset(); return true; }
                if (key == Input.Keys.PLUS || key == Input.Keys.EQUALS) { speed = Math.min(16, speed * 2); return true; }
                if (key == Input.Keys.MINUS) { speed = Math.max(1, speed / 2); return true; }
                if (key == Input.Keys.Q) { left = next(left); reset(); return true; }
                if (key == Input.Keys.E) { right = next(right); reset(); return true; }
                return false;
            }
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                com.badlogic.gdx.math.Vector2 world = viewport.unproject(new com.badlogic.gdx.math.Vector2(x, y));
                if (world.y > 650) {
                    if (world.x < 125) running = !running;
                    else if (world.x < 250) reset();
                    else if (world.x < 365) speed = Math.max(1, speed / 2);
                    else if (world.x < 475) speed = Math.min(16, speed * 2);
                    else if (world.x < 850) { left = next(left); reset(); }
                    else { right = next(right); reset(); }
                    return true;
                }
                return false;
            }
        });
    }

    private static Battle.Faction next(Battle.Faction f) {
        Battle.Faction[] values = Battle.Faction.values();
        return values[(f.ordinal() + 1) % values.length];
    }
    private void reset() {
        battle = Battle.duel(nextSeed++, 16, left, right);
        accumulator = 0;
        running = true;
    }

    @Override public void render() {
        float delta = Math.min(0.08f, Gdx.graphics.getDeltaTime());
        if (running && !battle.finished()) {
            accumulator += delta * speed;
            int turns = 0;
            while (accumulator >= Battle.DT && turns++ < 64) {
                battle.step(); accumulator -= Battle.DT;
            }
            if (turns >= 64) accumulator = 0; // Frame cannot create unbounded catch-up latency.
        }
        Gdx.gl.glClearColor(0.015f, 0.025f, 0.055f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        viewport.apply(); camera.update();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.11f, 0.16f, 0.26f, 0.75f);
        for (int x = 40; x <= 1240; x += 40) shapes.line(x, 25, x, 645);
        for (int y = 25; y <= 645; y += 40) shapes.line(40, y, 1240, y);
        for (Battle.Beam beam : battle.beams()) {
            shapes.setColor(beam.team == 0 ? Color.CYAN : Color.CORAL);
            shapes.line((float) beam.x1, (float) beam.y1, (float) beam.x2, (float) beam.y2);
        }
        shapes.end();
        if (instanced == null) drawShipsFallback();
        else {
            try { instanced.render(battle, camera); }
            catch (RuntimeException error) {
                Gdx.app.error("GPU", "Switching to per-ship rendering", error);
                instanced.close(); instanced = null; renderer = "software geometry";
                drawShipsFallback();
            }
        }
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Battle.Missile m : battle.missiles()) {
            shapes.setColor(m.team == 0 ? Color.CYAN : Color.SCARLET);
            shapes.circle((float) m.x, (float) m.y, 2.8f, 7);
        }
        shapes.setColor(0.08f, 0.13f, 0.22f, 0.94f);
        shapes.rect(0, 649, W, 71);
        shapes.end();
        batch.setProjectionMatrix(camera.combined); batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, running ? "PAUSE" : "RESUME", 15, 693);
        font.draw(batch, "RESTART", 139, 693);
        font.draw(batch, "SLOW", 270, 693);
        font.draw(batch, "FAST x" + speed, 378, 693);
        font.setColor(left.r, left.g, left.b, 1);
        font.draw(batch, "< " + left.displayName, 508, 693);
        font.setColor(right.r, right.g, right.b, 1);
        font.draw(batch, "< " + right.displayName, 866, 693);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "FLEETS " + battle.alive(0) + " / " + battle.alive(1)
                + "   T+" + (battle.tick / 30) + "s   HITS " + battle.hitCount
                + "   PD " + battle.missileIntercepts + "   " + renderer, 22, 38);
        if (battle.finished()) {
            font.setColor(Color.GOLD);
            font.draw(batch, "ENGAGEMENT COMPLETE  —  " + (battle.alive(0) > 0 ? left.displayName : right.displayName)
                    + " HOLDS THE FIELD", 330, 617);
        }
        batch.end();
    }

    private void drawShipsFallback() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Battle.Ship s : battle.ships()) if (s.alive()) {
            shapes.setColor(s.faction.r, s.faction.g, s.faction.b, 1);
            float x = (float) s.x, y = (float) s.y;
            float a = (float) Math.cos(s.facing), b = (float) Math.sin(s.facing);
            shapes.triangle(x + a * 14, y + b * 14,
                    x - a * 8 - b * 7, y - b * 8 + a * 7,
                    x - a * 8 + b * 7, y - b * 8 - a * 7);
        }
        shapes.end();
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
    @Override public void dispose() {
        if (instanced != null) instanced.close();
        shapes.dispose(); batch.dispose(); font.dispose();
    }
}
