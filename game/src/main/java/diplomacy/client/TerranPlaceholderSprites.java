package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import java.util.EnumMap;

/**
 * Optional first-faction top-down concept sprites. Original Terran concept art
 * is installed at assets/ships/terran/*.png using the published crop manifest.
 * Missing files never prevent launching or using the existing polygon renderer.
 */
public final class TerranPlaceholderSprites implements Disposable {
    private final EnumMap<Shipyard.Hull,Texture> textures = new EnumMap<>(Shipyard.Hull.class);
    private static String spriteName(Shipyard.Hull hull) {
        return switch(hull) {
            case CORVETTE -> "corvette";
            case FRIGATE -> "frigate";
            case CRUISER -> "light_cruiser";
            case DREADNOUGHT -> "dreadnought";
        };
    }
    public TerranPlaceholderSprites() {
        for (Shipyard.Hull hull:Shipyard.Hull.values()) {
            String file="ships/terran/"+spriteName(hull)+".png";
            if (!Gdx.files.internal(file).exists())continue;
            try {
                Texture tex=new Texture(Gdx.files.internal(file));
                tex.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
                textures.put(hull,tex);
            }catch(RuntimeException ex) {
                Gdx.app.error("SPRITES","Unable to load optional Terran placeholder: "+file,ex);
            }
        }
    }
    public int loaded() { return textures.size(); }
    public void render(Battle battle,SpriteBatch batch) {
        if(textures.isEmpty())return;
        batch.begin();
        for(Battle.Ship ship:battle.ships()) {
            if(!ship.alive()||ship.faction!=Battle.Faction.TERRAN||ship.hullClass==null)continue;
            Texture tex=textures.get(ship.hullClass);
            if(tex==null)continue;
            float height=(float)(34*ship.visualScale());
            float width=height*tex.getWidth()/(float)tex.getHeight();
            // PNG points north, while simulation facing 0 points east.
            float rotation=(float)(StrictMath.toDegrees(ship.facing)-90);
            batch.setColor(1,1,1,1);
            batch.draw(tex,(float)ship.x-width/2,(float)ship.y-height/2,
                    width/2,height/2,width,height,1,1,rotation,0,0,tex.getWidth(),tex.getHeight(),false,false);
        }
        batch.end();
    }
    @Override public void dispose() {
        for(Texture texture:textures.values())texture.dispose();
        textures.clear();
    }
}
