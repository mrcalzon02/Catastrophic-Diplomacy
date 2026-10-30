package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.sim.Battle;

/**
 * World-space emissive lighting overlay shared by GL2/ES2 and GL3 paths.
 * One generated radial falloff texture and one SpriteBatch: no per-light FBO,
 * shaders, texture allocations or draw calls. Lighting is cosmetic; the
 * authoritative combat simulation does not depend on GPU effects.
 *
 * The additive pass brightens actual hull pixels near the sources but is NOT a
 * shadow-casting normal-map system. Beam/missile trajectories are simulation-owned.
 */
final class CombatLightingRenderer implements Disposable {
    private static final int GLOW_SIZE = 64;
    private static final int MAX_BEAM_LIGHTS = 256;
    private static final int MAX_MISSILE_LIGHTS = 192;
    private final CombatLightingModel timeline = new CombatLightingModel();
    private final Texture glow;
    private int lastGlowQuads;
    private int lastSmokeQuads;

    CombatLightingRenderer() {
        Pixmap pixels = new Pixmap(GLOW_SIZE, GLOW_SIZE, Pixmap.Format.RGBA8888);
        pixels.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < GLOW_SIZE; y++) {
            for (int x = 0; x < GLOW_SIZE; x++) {
                float dx = (x + .5f - GLOW_SIZE / 2f) / (GLOW_SIZE / 2f);
                float dy = (y + .5f - GLOW_SIZE / 2f) / (GLOW_SIZE / 2f);
                float distance = (float) StrictMath.sqrt(dx * dx + dy * dy);
                float falloff = Math.max(0, 1 - distance);
                falloff = falloff * falloff * (3 - 2 * falloff);
                pixels.setColor(1, 1, 1, falloff);
                pixels.drawPixel(x, y);
            }
        }
        glow = new Texture(pixels);
        pixels.dispose();
        glow.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    private static boolean visible(OrthographicCamera camera, double x, double y, double radius) {
        double halfW = camera.viewportWidth * camera.zoom * .5;
        double halfH = camera.viewportHeight * camera.zoom * .5;
        return x + radius >= camera.position.x - halfW
                && x - radius <= camera.position.x + halfW
                && y + radius >= camera.position.y - halfH
                && y - radius <= camera.position.y + halfH;
    }

    private void spot(SpriteBatch batch, OrthographicCamera camera,
                      double x, double y, float radius, Color color, float alpha) {
        if (radius <= 0 || alpha <= 0 || !visible(camera, x, y, radius)) return;
        batch.setColor(color.r, color.g, color.b, Math.min(1, alpha));
        batch.draw(glow, (float) x - radius, (float) y - radius,
                radius * 2, radius * 2);
        lastGlowQuads++;
    }

    private void streak(SpriteBatch batch, OrthographicCamera camera,
                        double x1, double y1, double x2, double y2,
                        float width, Color color, float alpha) {
        if (width <= 0 || alpha <= 0) return;
        double minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        if (!visible(camera, (minX + maxX) * .5, (minY + maxY) * .5,
                (float) (StrictMath.hypot(maxX - minX, maxY - minY) * .5 + width)))
            return;
        float length = (float) StrictMath.hypot(x2 - x1, y2 - y1);
        if (length < .01f) return;
        float angle = (float) StrictMath.toDegrees(StrictMath.atan2(y2 - y1, x2 - x1));
        batch.setColor(color.r, color.g, color.b, Math.min(1, alpha));
        batch.draw(glow, (float) x1, (float) y1 - width / 2,
                0, width / 2, length, width, 1, 1, angle,
                0, 0, glow.getWidth(), glow.getHeight(), false, false);
        lastGlowQuads++;
    }

    static Color weaponColor(Battle.DamageType type) {
        return switch (type) {
            case KINETIC -> Color.GOLD;
            case PLASMA -> Color.SCARLET;
            case PARTICLE -> Color.VIOLET;
            case MISSILE, TORPEDO -> Color.ORANGE;
            default -> Color.CYAN;
        };
    }

    static Color eventColor(String weapon) {
        return switch (weapon) {
            case "KINETIC", "FLAK" -> Color.GOLD;
            case "PLASMA" -> Color.SCARLET;
            case "PARTICLE" -> Color.VIOLET;
            case "MISSILE", "TORPEDO", "DECOY" -> Color.ORANGE;
            default -> Color.CYAN;
        };
    }

    /** Dusty decorative smoke in space: alpha blended, persistent and tick-aged. */
    void renderSmoke(Battle battle, SpriteBatch batch, OrthographicCamera camera) {
        timeline.update(battle);
        lastSmokeQuads = 0;
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        for (CombatLightingModel.Smoke smoke : timeline.smoke()) {
            float age = (float) (battle.tick - smoke.bornTick())
                    / CombatLightingModel.SMOKE_LIFETIME_TICKS;
            if (age < 0 || age >= 1) continue;
            float radius = (smoke.heavy() ? 7.5f : 4.8f) * (1 + age * 1.65f);
            if (!visible(camera, smoke.x(), smoke.y(), radius)) continue;
            float fade = (1 - age);
            batch.setColor(.55f, .59f, .65f, .43f * fade * fade);
            batch.draw(glow, smoke.x() - radius, smoke.y() - radius,
                    radius * 2, radius * 2);
            lastSmokeQuads++;
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Called after hulls, real beam/ordnance textures and geometric fallbacks.
     * Emissive falloff lightens nearby ship surfaces as well as space around them.
     */
    void renderGlow(Battle battle, SpriteBatch batch, OrthographicCamera camera) {
        timeline.update(battle);
        lastGlowQuads = 0;
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();

        // Engine lights follow actual facing, rendered speed and hull visual scale.
        for (Battle.Ship ship : battle.ships()) {
            if (!ship.alive()) continue;
            float scale = (float) Math.min(3.5, ship.visualScale());
            float ratio = (float) Math.min(1, StrictMath.hypot(ship.vx, ship.vy)
                    / Math.max(1, ship.speed));
            float intensity = .14f + ratio * .7f;
            double dx = StrictMath.cos(ship.facing), dy = StrictMath.sin(ship.facing);
            double x = ship.x - dx * 12 * scale, y = ship.y - dy * 12 * scale;
            Color tint = ship.team == 0 ? Color.SKY : Color.ORANGE;
            spot(batch, camera, x, y, 12 * scale, tint, .65f * intensity);
            spot(batch, camera, x, y, 4.5f * scale, Color.WHITE, .52f * intensity);
            if (ratio > .05f)
                streak(batch, camera, x, y,
                        x - dx * (13 + 24 * ratio) * scale,
                        y - dy * (13 + 24 * ratio) * scale,
                        10 * scale, tint, .50f * intensity);
        }

        // Broad, tinted optical corona around the real live beam, never a second
        // synthetic beam or a light appearing before the weapon actually fires.
        int beams = 0;
        for (Battle.Beam beam : battle.beams()) {
            if (++beams > MAX_BEAM_LIGHTS) break;
            Color tint = weaponColor(beam.type);
            streak(batch, camera, beam.x1, beam.y1, beam.x2, beam.y2,
                    31, tint, .22f);
            streak(batch, camera, beam.x1, beam.y1, beam.x2, beam.y2,
                    12, tint, .40f);
            spot(batch, camera, beam.x1, beam.y1, 21, tint, .32f);
            spot(batch, camera, beam.x2, beam.y2, 23, tint, .38f);
        }

        // Missile light is on ONLY after ignition. Smoke is sampled separately
        // and survives the projectile for a short time after impact/interception.
        int missiles = 0;
        for (Battle.Missile missile : battle.missiles()) {
            if (++missiles > MAX_MISSILE_LIGHTS) break;
            if (missile.ignitionDelayTicks > 0 || missile.fuel <= 0) continue;
            double heading = StrictMath.atan2(missile.vy, missile.vx);
            double dx = StrictMath.cos(heading), dy = StrictMath.sin(heading);
            double x = missile.x - dx * (missile.torpedo ? 8 : 5);
            double y = missile.y - dy * (missile.torpedo ? 8 : 5);
            Color color = missile.torpedo ? Color.ORANGE : Color.GOLD;
            spot(batch, camera, x, y, missile.torpedo ? 30 : 20, color, .53f);
            spot(batch, camera, x, y, missile.torpedo ? 11 : 7, Color.WHITE, .7f);
        }

        // Only true simulation FIRE, IMPACT or INTERCEPT events cause bursts.
        // Timeouts deliberately do not become hits or unexplained explosions.
        for (CombatLightingModel.Flash flash : timeline.flashes()) {
            Battle.VisualEvent e = flash.event();
            int lifetime = CombatLightingModel.flashLife(e);
            float age = (float) (battle.tick - e.tick()) / lifetime;
            if (age < 0 || age >= 1) continue;
            boolean large = e.weapon().equals("KINETIC") || e.weapon().equals("TORPEDO");
            float baseRadius = large ? 47 : 29;
            float intensity = 1 - age;
            Color tint = eventColor(e.weapon());
            float expansion = .7f + age * .65f;
            spot(batch, camera, e.x(), e.y(), baseRadius * expansion,
                    tint, .76f * intensity);
            spot(batch, camera, e.x(), e.y(), baseRadius * .37f,
                    Color.WHITE, .67f * intensity * intensity);
        }
        batch.setColor(Color.WHITE);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    int glowQuads() { return lastGlowQuads; }
    int smokeQuads() { return lastSmokeQuads; }
    int smokeParticles() { return timeline.smokeCount(); }
    @Override public void dispose() { glow.dispose(); }
}
