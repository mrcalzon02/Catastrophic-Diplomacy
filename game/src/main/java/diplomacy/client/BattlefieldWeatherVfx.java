package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.sim.SpaceWeather;

/**
 * Visual-only space weather. No visual RNG, colliders, or damage calculations.
 * Every animated detail is derived from the fixed simulation tick and event seed.
 * Drawing is bounded by fixed counts and SpaceWeather's capped 28 meteors.
 */
public final class BattlefieldWeatherVfx implements Disposable {
    private static final float W = 1280f, H = 720f;
    private final Texture halo;

    public BattlefieldWeatherVfx() {
        Pixmap p = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            float dx = (x - 31.5f) / 31.5f, dy = (y - 31.5f) / 31.5f;
            float r2 = dx * dx + dy * dy;
            float opacity = (float) StrictMath.pow(Math.max(0f, 1f - r2), 2.2);
            p.setColor(1, 1, 1, opacity);
            p.drawPixel(x, y);
        }
        halo = new Texture(p);
        halo.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
    }

    /** Diffuse weather below hulls and weapons, including warning beams. */
    public void behind(ShapeRenderer shapes, SpriteBatch batch, OrthographicCamera camera,
                       SpaceWeather weather) {
        if (!weather.preset().enabled()) return;
        long tick = weather.tick();
        long seed = weather.preset().seed();
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        if (weather.preset().includes(SpaceWeather.Kind.NEBULA)) {
            for (int i = 0; i < 19; i++) {
                long h = mix(seed + i * 719L);
                float x = 40 + unit(h) * 1200;
                float y = 35 + unit(mix(h + 1)) * 620;
                float drift = (float) StrictMath.sin(tick * 0.003 + i * 1.13) * 21f;
                float size = 190 + unit(mix(h + 2)) * 190;
                boolean blue = (i & 1) == 0;
                batch.setColor(blue ? .13f : .52f, blue ? .50f : .20f,
                        blue ? .82f : .70f, .035f + .018f * unit(mix(h + 3)));
                batch.draw(halo, x - size / 2 + drift, y - size / 2 - drift, size, size);
            }
        }
        if (weather.preset().includes(SpaceWeather.Kind.RADIATION)) {
            for (int i = 0; i < 30; i++) {
                long h = mix(seed ^ (i * 9277L));
                float x = mod(unit(h) * 1340f + tick * (0.24f + i % 4 * .09f), 1340f) - 30;
                float y = mod(unit(mix(h + 1)) * 780f + tick * .13f, 780f) - 30;
                float size = 18f + 16f * unit(mix(h + 2));
                batch.setColor(.39f, 1f, .34f, .045f);
                batch.draw(halo, x - size / 2, y - size / 2, size, size);
            }
        }
        if (weather.solarActive()) {
            float pulse = .5f + .5f * (float) StrictMath.sin(tick * .047);
            batch.setColor(1f, .42f, .12f, .075f + .05f * pulse);
            batch.draw(halo, -400, -220, 1550, 1150);
        }
        if (weather.neutronActive()) {
            float cx = (float) SpaceWeather.CENTER_X, cy = (float) SpaceWeather.CENTER_Y;
            float angle = (float) weather.neutronAngle();
            float nx = (float) StrictMath.cos(angle), ny = (float) StrictMath.sin(angle);
            batch.setColor(.16f, .64f, 1f, .22f);
            for (int n = -2; n <= 2; n++) {
                float x = cx + nx * n * 180, y = cy + ny * n * 180;
                batch.draw(halo, x - 110, y - 110, 220, 220);
            }
        }
        batch.end();

        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (weather.preset().includes(SpaceWeather.Kind.SOLAR_FLARE)) {
            int phase = (int) (tick % 540);
            float warn = phase >= 240 && phase < 300 ? (phase - 240) / 60f : 0f;
            if (weather.solarActive() || warn > 0) {
                float intensity = weather.solarActive() ? 1f : warn * .30f;
                for (int i = 0; i < 9; i++) {
                    float y = 35f + i * 82f + (float) StrictMath.sin(tick * .026 + i * 2.1) * 36f;
                    float a = .05f + .05f * (float) StrictMath.sin(tick * .06 + i);
                    shapes.setColor(1f, .33f + i * .025f, .055f, Math.max(0f, a * intensity));
                    shapes.rectLine(-130, y - 105, 1410, y + 170, 22 + (i % 3) * 9);
                    shapes.setColor(1f, .86f, .30f, .23f * intensity);
                    shapes.rectLine(-130, y - 105, 1410, y + 170, 1.7f);
                }
            }
        }
        if (weather.preset().includes(SpaceWeather.Kind.NEUTRON_SWEEP)) {
            int phase = (int) (tick % 720);
            boolean warning = phase >= 60 && phase < 150;
            if (weather.neutronActive() || warning) {
                float angle = weather.neutronActive()
                        ? (float) weather.neutronAngle()
                        : (float) ((seed & 255L) / 255.0 * StrictMath.PI);
                float nx = (float) StrictMath.cos(angle), ny = (float) StrictMath.sin(angle);
                float px = -ny, py = nx;
                float x = (float) SpaceWeather.CENTER_X, y = (float) SpaceWeather.CENTER_Y;
                float activity = weather.neutronActive() ? 1f : .17f + .28f * (phase - 60) / 90f;
                for (int i = 0; i < 4; i++) {
                    float offset = i == 0 ? 0 : i == 1 ? 14 : i == 2 ? 29 : 38;
                    float alpha = new float[]{.08f, .16f, .25f, .45f}[i] * activity;
                    shapes.setColor(i == 3 ? .8f : .12f, i == 3 ? .98f : .66f, 1f, alpha);
                    shapes.rectLine(x - nx * 1400 + px * offset, y - ny * 1400 + py * offset,
                            x + nx * 1400 + px * offset, y + ny * 1400 + py * offset,
                            i == 0 ? 105 : i == 1 ? 66 : i == 2 ? 27 : 3);
                }
                // Mirrored feathering: hit corridor is 76 units in total.
                shapes.setColor(.55f, .93f, 1f, .14f * activity);
                shapes.rectLine(x - nx * 1400 - px * 38, y - ny * 1400 - py * 38,
                        x + nx * 1400 - px * 38, y + ny * 1400 - py * 38, 2f);
            }
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Strikes and electric arcs above hulls, before ammunition and HUD. */
    public void above(ShapeRenderer shapes, SpriteBatch batch, OrthographicCamera camera,
                      SpaceWeather weather) {
        if (!weather.preset().enabled()) return;
        long tick = weather.tick();
        long seed = weather.preset().seed();
        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (weather.preset().includes(SpaceWeather.Kind.ION_STORM)) {
            // Broad translucent blue corona under narrow branching forked arcs.
            drawLightning(shapes, tick, seed, 9f, .13f);
            drawLightning(shapes, tick, seed, 2.1f, .62f);
        }
        if (weather.preset().includes(SpaceWeather.Kind.RADIATION)) {
            for (int i = 0; i < 22; i++) {
                long h = mix(seed + 3003L * i);
                float x = mod(unit(h) * 1350f + tick * (.48f + (i % 3) * .09f), 1350f) - 35f;
                float y = mod(unit(mix(h + 2)) * 760f + tick * .23f, 760f) - 20f;
                shapes.setColor(.54f, .98f, .32f, .16f);
                shapes.rectLine(x, y, x + 8, y + 15, 1.2f);
            }
        }
        for (SpaceWeather.Meteor m : weather.meteors()) {
            float x = (float) m.x(), y = (float) m.y();
            float r = (float) m.radius();
            float travel = clamp((tick - m.arrivalTick()) /
                    (float) Math.max(1, m.strikeTick() - m.arrivalTick()));
            if (!m.resolved()) {
                float v = .24f + .76f * travel;
                float bx = x - 75f * (1 - travel), by = y - 102f * (1 - travel);
                shapes.setColor(1f, .66f, .23f, .20f * v);
                shapes.rectLine(bx - 27, by - 32, bx + 3, by + 1, 12f * v);
                shapes.setColor(1f, .84f, .51f, .69f * v);
                shapes.rectLine(bx - 17, by - 23, bx, by, 2.4f);
                // No physical collision: a silhouette rising into the combat plane.
                drawRock(shapes, bx, by, r * (.30f + .70f * travel), seed ^ m.arrivalTick(), v);
            } else {
                float life = clamp((m.expiryTick() - tick) /
                        (float) Math.max(1, m.expiryTick() - m.strikeTick()));
                float strength = m.hit() ? 1f : .36f;
                shapes.setColor(1f, m.hit() ? .63f : .79f, .38f, life * strength * .15f);
                shapes.circle(x, y, r + (1 - life) * 38, 28);
                for (int i = 0; i < 12; i++) {
                    long h = mix(seed + m.arrivalTick() * 131L + i * 17L);
                    float angle = unit(h) * (float) (2 * StrictMath.PI);
                    float len = (10 + unit(mix(h + 1)) * 37) * (1 - life);
                    float px = x + (float) StrictMath.cos(angle) * len;
                    float py = y + (float) StrictMath.sin(angle) * len;
                    shapes.setColor(1f, .64f + unit(mix(h + 2)) * .20f, .33f,
                            .77f * life * strength);
                    shapes.rectLine(px, py, px + (float) StrictMath.cos(angle) * 5,
                            py + (float) StrictMath.sin(angle) * 5, 1.8f * life + .3f);
                }
            }
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (SpaceWeather.Meteor m : weather.meteors()) {
            float x = (float) m.x(), y = (float) m.y(), r = (float) m.radius();
            if (!m.resolved()) {
                float approach = clamp((tick - m.arrivalTick()) /
                        (float) Math.max(1, m.strikeTick() - m.arrivalTick()));
                shapes.setColor(1f, .78f, .30f, .10f + approach * .67f);
                shapes.circle(x, y, r + 30f * (1 - approach), 24);
                shapes.circle(x, y, r + 6, 18);
                shapes.line(x - r - 15, y, x - r - 4, y);
                shapes.line(x + r + 4, y, x + r + 15, y);
            } else {
                float life = clamp((m.expiryTick() - tick) /
                        (float) Math.max(1, m.expiryTick() - m.strikeTick()));
                shapes.setColor(m.hit() ? 1f : .55f, m.hit() ? .67f : .72f, .38f,
                        .80f * life);
                shapes.circle(x, y, r + (1 - life) * 47, 28);
                shapes.circle(x, y, r + (1 - life) * 26, 24);
            }
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (SpaceWeather.Meteor m : weather.meteors()) {
            float x = (float) m.x(), y = (float) m.y();
            if (m.resolved()) {
                float life = clamp((m.expiryTick() - tick) /
                        (float) Math.max(1, m.expiryTick() - m.strikeTick()));
                float size = (float) m.radius() * 7 + (1 - life) * 65;
                batch.setColor(1f, m.hit() ? .65f : .79f, .31f,
                        (m.hit() ? .57f : .16f) * life);
                batch.draw(halo, x - size/2, y - size/2, size, size);
            }
        }
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void drawLightning(ShapeRenderer shapes, long tick, long seed,
                                      float width, float strength) {
        long period = tick / 7;
        for (int arc = 0; arc < 7; arc++) {
            long h = mix(seed + arc * 983L + period * 173L);
            // Stable frame buckets: bolts shimmer, but do not use gameplay RNG.
            if (unit(mix(h + 55)) < .20f) continue;
            float x = 35 + unit(h) * 1200;
            float y = 40 + unit(mix(h + 1)) * 590;
            float dirX = unit(mix(h + 2)) < .5f ? -1f : 1f;
            float dirY = unit(mix(h + 3)) < .5f ? -1f : 1f;
            for (int j = 0; j < 9; j++) {
                float nextX = x + dirX * (10 + unit(mix(h + 21 * j)) * 17);
                float nextY = y + dirY * (7 + unit(mix(h + 22 * j)) * 19);
                shapes.setColor(.35f + .35f * strength, .80f, 1f,
                        strength * (1f - j * .055f));
                shapes.rectLine(x, y, nextX, nextY, width);
                if (j == 4 && arc % 2 == 0) {
                    shapes.rectLine(nextX, nextY, nextX + dirX * 23, nextY - dirY * 34,
                            width * .56f);
                }
                x = nextX; y = nextY;
            }
        }
    }
    private static void drawRock(ShapeRenderer shapes, float x, float y,
                                 float radius, long seed, float opacity) {
        // Irregular asteroid built as eight triangular wedges; no static PNG needed.
        for (int i = 0; i < 9; i++) {
            float a = (float) (i * 2 * StrictMath.PI / 9);
            float b = (float) ((i + 1) * 2 * StrictMath.PI / 9);
            float ra = radius * (.70f + unit(mix(seed + i)) * .44f);
            float rb = radius * (.70f + unit(mix(seed + i + 1)) * .44f);
            float shade = .23f + unit(mix(seed + i * 41L)) * .23f;
            shapes.setColor(shade + .20f, shade + .12f, shade, .92f * opacity);
            shapes.triangle(x, y,
                    x + (float) StrictMath.cos(a) * ra, y + (float) StrictMath.sin(a) * ra,
                    x + (float) StrictMath.cos(b) * rb, y + (float) StrictMath.sin(b) * rb);
        }
    }
    private static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
    private static float unit(long x) { return (float) ((x >>> 11) * 0x1.0p-53); }
    private static float clamp(float x) { return Math.max(0f, Math.min(1f, x)); }
    private static float mod(float x, float m) { return x - (float) StrictMath.floor(x / m) * m; }
    @Override public void dispose() { halo.dispose(); }
}