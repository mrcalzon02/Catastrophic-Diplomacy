package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional, individually reviewed tactical UI assets. The rejected 87-cell
 * generated atlas is deliberately NOT loaded. Original text controls and
 * pointer/touch hit areas remain authoritative when sprites are absent.
 */
final class TacticalUiIcons {
    private static final String BASE = "ui/tactical/selected/";
    private static final String[] REVIEWED = {
        "restart", "speed", "squadron", "shipyard_ship",
        "complete", "information", "close", "back", "next",
        "target", "shield", "repair", "energy", "fleet",
        "weapons", "armor", "engine", "drone", "turret",
        "missile", "laser", "emp", "stealth", "detect"
    };
    private final Map<String, Texture> icons = new HashMap<>();

    TacticalUiIcons() {
        for (String name : REVIEWED) {
            FileHandle image = Gdx.files.internal(BASE + name + ".png");
            if (!image.exists()) continue;
            try {
                Texture texture = new Texture(image);
                // Prevent unexpectedly large sprites from exhausting mobile VRAM.
                if (texture.getWidth() < 16 || texture.getHeight() < 16
                        || texture.getWidth() > 256 || texture.getHeight() > 256) {
                    texture.dispose();
                    throw new IllegalArgumentException("Unexpected dimensions for " + name);
                }
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                icons.put(name, texture);
            } catch (RuntimeException error) {
                if (Gdx.app != null)
                    Gdx.app.error("UI", "Skipping unusable optional icon: " + name, error);
            }
        }
    }

    boolean available() {
        return !icons.isEmpty();
    }

    /**
     * Fit a source image into a size x size slot without stretching its frame.
     * Called while SpriteBatch is begun; missing images draw nothing.
     */
    void draw(SpriteBatch batch, String id, float x, float y, float size) {
        Texture texture = icons.get(id);
        if (texture == null || size <= 0) return;
        float scale = Math.min(size / texture.getWidth(), size / texture.getHeight());
        float width = texture.getWidth() * scale;
        float height = texture.getHeight() * scale;
        batch.draw(texture, x + (size - width) * 0.5f,
                y + (size - height) * 0.5f, width, height);
    }

    int loaded() {
        return icons.size();
    }

    void dispose() {
        for (Texture texture : icons.values()) texture.dispose();
        icons.clear();
    }
}
