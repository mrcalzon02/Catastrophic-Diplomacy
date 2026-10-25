package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Color;
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
    private Texture solid; // A generated pixel, not a crop from any unapproved button image.

    TacticalUiIcons() {
        // Procedural chrome supports button styles without ever shipping broken button crops.
        try {
            Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            try {
                pixel.setColor(Color.WHITE);
                pixel.fill();
                solid = new Texture(pixel);
            } finally {
                pixel.dispose();
            }
        } catch (RuntimeException error) {
            if (Gdx.app != null) Gdx.app.error("UI", "Procedural button chrome unavailable", error);
        }
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

    boolean has(String id) {
        return icons.containsKey(id);
    }

    /**
     * Draw unobtrusive HUD button chrome using a generated 1x1 texture only.
     * These names are semantic styles, NOT images from rejected or unvetted atlases.
     * The caller owns hit areas, focus behavior and legible text labels.
     */
    void drawButton(SpriteBatch batch, String style, float x, float y, float w, float h) {
        if (solid == null || w < 8 || h < 8) return;
        Color border, center;
        switch (style) {
            case "confirm" -> {
                border = new Color(0.16f, 0.73f, 0.46f, 0.95f);
                center = new Color(0.025f, 0.16f, 0.11f, 0.94f);
            }
            case "danger", "cancel" -> {
                border = new Color(0.85f, 0.30f, 0.32f, 0.96f);
                center = new Color(0.21f, 0.04f, 0.07f, 0.94f);
            }
            case "special" -> {
                border = new Color(0.73f, 0.50f, 0.93f, 0.96f);
                center = new Color(0.16f, 0.08f, 0.25f, 0.93f);
            }
            case "info" -> {
                border = new Color(0.35f, 0.66f, 0.84f, 0.95f);
                center = new Color(0.06f, 0.13f, 0.23f, 0.92f);
            }
            case "secondary" -> {
                border = new Color(0.33f, 0.48f, 0.60f, 0.88f);
                center = new Color(0.055f, 0.085f, 0.13f, 0.92f);
            }
            case "primary" -> {
                border = new Color(0.22f, 0.77f, 0.98f, 0.96f);
                center = new Color(0.035f, 0.15f, 0.27f, 0.93f);
            }
            default -> throw new IllegalArgumentException("Unknown procedural button style: " + style);
        }
        Color prior = new Color(batch.getColor());
        try {
            batch.setColor(border);
            batch.draw(solid,x,y,w,h);
            batch.setColor(center);
            batch.draw(solid,x+2,y+2,w-4,h-4);
            batch.setColor(border.r,border.g,border.b,0.50f);
            batch.draw(solid,x+4,y+h-5,w-8,1);
        } finally {
            batch.setColor(prior);
        }
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
        if (solid != null) { solid.dispose(); solid = null; }
    }
}
