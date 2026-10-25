package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import java.util.Locale;

/**
 * Optional, QA-approved sprites installed from the verified VFX ZIP.
 * A missing or invalid file never disables the procedural battlefield fallback.
 */
final class WeatherFxAssets implements Disposable {
    private static final String ROOT = "effects/weather/";
    final Texture[] rocks = load("asteroid_rock", 8);
    final Texture[] impacts = load("asteroid_impact", 8);
    final Texture[] shockwaves = load("asteroid_shockwave", 7);
    final Texture[] solar = load("solar_beam", 5);
    final Texture[] neutron = load("neutron_beam", 5);
    final Texture[] lightning = load("ion_lightning", 8);
    final Texture[] radiation = load("radiation_motes", 4);
    final Texture[] nebula = load("nebula_haze", 4);

    private static Texture[] load(String prefix, int count) {
        Texture[] textures = new Texture[count];
        for (int i = 0; i < count; i++) {
            String path = ROOT + String.format(Locale.ROOT, "%s_%02d.png", prefix, i);
            if (!Gdx.files.internal(path).exists()) continue;
            try {
                Texture texture = new Texture(Gdx.files.internal(path));
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                textures[i] = texture;
            } catch (RuntimeException error) {
                Gdx.app.error("WEATHER-FX", "Rejected optional VFX: " + path, error);
            }
        }
        return textures;
    }
    @Override public void dispose() {
        for (Texture[] group : new Texture[][]{rocks, impacts, shockwaves, solar,
                neutron, lightning, radiation, nebula})
            for (Texture texture : group) if (texture != null) texture.dispose();
    }
}
