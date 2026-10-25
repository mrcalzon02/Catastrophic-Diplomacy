package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional curated UI from two original art sheets. The game's existing text,
 * input regions, and combat behavior remain authoritative if art is absent.
 */
final class TacticalUiIcons {
    private static final int TILE = 80;
    private static final String ATLAS = "ui/tactical/icons.png";
    private static final String INDEX = "ui/tactical/icons.tsv";
    private Texture texture;
    private final Map<String, Texture> buttons = new HashMap<>();
    private final Map<String, TextureRegion> regions = new HashMap<>();

    TacticalUiIcons() {
        FileHandle atlas = Gdx.files.internal(ATLAS);
        FileHandle registry = Gdx.files.internal(INDEX);
        if (!atlas.exists() || !registry.exists()) return;
        try {
            texture = new Texture(atlas);
            if (texture.getWidth() != 800 || texture.getHeight() != 720)
                throw new IllegalArgumentException("Tactical UI atlas must be 800x720");
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            for (String line : registry.readString("UTF-8").split("\\r?\\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t");
                if (fields.length != 3) throw new IllegalArgumentException("Bad UI atlas row: " + line);
                int x = Integer.parseInt(fields[1]);
                int y = Integer.parseInt(fields[2]);
                if (x < 0 || x >= 10 || y < 0 || y >= 9 || regions.containsKey(fields[0]))
                    throw new IllegalArgumentException("Bad UI cell/id: " + line);
                regions.put(fields[0], new TextureRegion(texture, x * TILE, y * TILE, TILE, TILE));
            }
            if (regions.size() != 87) throw new IllegalArgumentException("Expected 87 approved UI icons");
            for (String id : new String[]{"primary","secondary","special","danger","confirm","cancel","info"}) {
                loadLayer(buttons, "ui/tactical/buttons/" + id + ".png", id);
            }
            // Status-bar source slices are excluded until their cropped ends are revalidated.
        } catch (RuntimeException failed) {
            if (texture != null) texture.dispose();
            texture = null;
            regions.clear();
            clearLayers();
            if (Gdx.app != null) Gdx.app.error("UI", "UI sprite atlas unavailable, preserving text controls", failed);
        }
    }

    private static void loadLayer(Map<String,Texture> target, String path, String id) {
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) return;
        Texture layer = new Texture(file);
        layer.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        target.put(id, layer);
    }

    void drawButton(SpriteBatch batch, String style, float x, float y, float width, float height) {
        Texture layer = buttons.get(style);
        if (layer == null) return;
        batch.setColor(1,1,1,0.92f);
        batch.draw(layer, x, y, width, height);
        batch.setColor(1,1,1,1);
    }

    private void clearLayers() {
        for (Texture layer : buttons.values()) layer.dispose();
        buttons.clear();
    }

    boolean available() {
        return texture != null;
    }

    void draw(SpriteBatch batch, String id, float x, float y, float size) {
        TextureRegion region = regions.get(id);
        if (region != null) batch.draw(region, x, y, size, size);
    }

    void dispose() {
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
        regions.clear();
        clearLayers();
    }
}
