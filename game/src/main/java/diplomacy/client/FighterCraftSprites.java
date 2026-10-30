package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.shipyard.FactionArt;
import diplomacy.sim.Battle;
import diplomacy.sim.FighterModel;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Original faction-specific fighter and drone sprite renderer. Never paints
 * colored squares/triangles over a successfully loaded craft sprite.
 * The five inherited civilizations use the same approved visual families
 * as their gameplay derivatives. The Terran interceptor is an original
 * small-craft image used until class-specific Terran fighter art is authored.
 */
public final class FighterCraftSprites implements Disposable {
    private static final String[] ROLES={
        "interceptor","superiority_fighter","assault_gunship","missile_fighter",
        "torpedo_bomber","recon_fighter","heavy_bomber","point_defense_fighter",
        "electronic_warfare","support_drone"
    };
    private final Map<String,Texture> originals=new HashMap<>();
    private final EnumMap<Battle.Faction,Map<String,Texture>> palettes=
            new EnumMap<>(Battle.Faction.class);

    /** Stable original-art path used in both APK assets and desktop JAR. */
    public static String spritePath(Battle.Faction faction,String role) {
        if(faction==null||role==null)throw new IllegalArgumentException("Craft identity required");
        String chosen=null;
        for(String candidate:ROLES)if(candidate.equals(role)){chosen=role;break;}
        if(chosen==null)throw new IllegalArgumentException("Unapproved fighter role: "+role);
        if(faction==Battle.Faction.TERRAN)
            return "ships/terran/interceptor.png"; // actual Terran artwork, not an alien impostor
        FactionArt.Entry art=FactionArt.visualArt(faction);
        if(art==null)throw new IllegalStateException("No approved fighter art family: "+faction);
        return "ships/fighters/"+art.id()+"/"+chosen+".png";
    }
    /** Distinguish six real fighter fittings and independently launched support drones. */
    public static String role(Battle.Craft craft) {
        if(!craft.fighter)return craft.repair?"support_drone":"point_defense_fighter";
        if(craft.profile==null)throw new IllegalStateException("Fighter missing combat profile");
        return switch(craft.profile.hull()) {
            case INTERCEPTOR -> "interceptor";
            case ASSAULT_GUNSHIP -> "assault_gunship";
            case LASER_FIGHTER -> "superiority_fighter";
            case PARTICLE_FIGHTER -> "electronic_warfare";
            case MISSILE_FIGHTER -> "missile_fighter";
            case TORPEDO_BOMBER -> "torpedo_bomber";
        };
    }
    public FighterCraftSprites() {
        for(Battle.Faction faction:Battle.Faction.values()) {
            Map<String,Texture> palette=new HashMap<>();
            for(String role:ROLES) {
                String path=spritePath(faction,role);
                Texture picture=originals.get(path);
                if(picture==null) {
                    if(!Gdx.files.internal(path).exists())
                        throw new IllegalStateException("Original fighter art missing from game build: "+path);
                    picture=new Texture(Gdx.files.internal(path));
                    picture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
                    originals.put(path,picture);
                }
                palette.put(role,picture);
            }
            palettes.put(faction,palette);
        }
        if(originals.size()!=51)
            throw new IllegalStateException("Expected 50 approved alien fighters plus original Terran interceptor: "+originals.size());
    }
    public int loaded() { return originals.size(); }
    public void render(Battle battle,SpriteBatch batch) {
        Map<Integer,Battle.Ship> vesselById=new HashMap<>();
        for(Battle.Ship ship:battle.ships())vesselById.put(ship.id,ship);
        batch.begin();
        batch.setColor(1,1,1,1);
        for(Battle.Craft craft:battle.crafts()) {
            Battle.Ship source=vesselById.get(craft.sourceId);
            if(source==null)
                throw new IllegalStateException("Autonomous craft has no parent faction: "+craft.sourceId);
            Texture picture=palettes.get(source.faction).get(role(craft));
            if(picture==null)
                throw new IllegalStateException("Original craft sprite not loaded: "+source.faction+"/"+role(craft));
            float height=craft.fighter?switch(craft.profile.hull()) {
                case INTERCEPTOR -> 13f;
                case ASSAULT_GUNSHIP -> 17f;
                case LASER_FIGHTER, PARTICLE_FIGHTER -> 14f;
                case MISSILE_FIGHTER -> 15f;
                case TORPEDO_BOMBER -> 18f;
            }:11f;
            float width=height*picture.getWidth()/(float)picture.getHeight();
            Battle.Ship target=vesselById.get(craft.targetId);
            double bearing=target!=null ?
                    StrictMath.atan2(target.y-craft.y,target.x-craft.x) : source.facing;
            float rotation=(float)StrictMath.toDegrees(bearing)-90f;
            batch.draw(picture,(float)craft.x-width/2,(float)craft.y-height/2,
                    width/2,height/2,width,height,1,1,rotation,
                    0,0,picture.getWidth(),picture.getHeight(),false,false);
        }
        batch.end();
    }
    @Override public void dispose(){
        for(Texture texture:originals.values())texture.dispose();
        originals.clear();palettes.clear();
    }
}
