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
 * Every alien sprite belongs ONLY to its named civilization. Gameplay
 * archetypes do not imply permission to borrow another faction's graphics.
 * The six founding civilizations use their OWN approved interceptor artwork
 * for fighter and drone roles until role-specific sheets are approved.
 * No faction ever substitutes a generic triangle or another faction's art.
 */
public final class FighterCraftSprites implements Disposable {
    private static final String[] ROLES={
        "interceptor","superiority_fighter","assault_gunship","missile_fighter",
        "torpedo_bomber","recon_fighter","heavy_bomber","point_defense_fighter",
        "electronic_warfare","support_drone"
    };
    private final Map<String,Texture> originals=new HashMap<>();
    private final Map<Integer,Battle.Ship> vesselById=new HashMap<>();
    private final ShipSurfaceLighting surfaces=new ShipSurfaceLighting();
    public int litCraftMaterials(){return surfaces.materialCount();}
    public int materialTextureBindings(){return surfaces.textureBindings();}
    private final EnumMap<Battle.Faction,Map<String,Texture>> palettes=
            new EnumMap<>(Battle.Faction.class);

    /** Stable original-art path used in both APK assets and desktop JAR. */
    public static String spritePath(Battle.Faction faction,String role) {
        if(faction==null||role==null)throw new IllegalArgumentException("Craft identity required");
        String chosen=null;
        for(String candidate:ROLES)if(candidate.equals(role)){chosen=role;break;}
        if(chosen==null)throw new IllegalArgumentException("Unapproved fighter role: "+role);
        if(faction==Battle.Faction.TERRAN)
            return "ships/terran/interceptor.png";
        // These five original factions already have genuine, distinct hull
        // sprites. Until their class-specific fighter atlases are approved,
        // their own interceptor is the correct stand-in, just like Terran.
        if(FactionArt.hasLegacyArt(faction))
            return FactionArt.runtimePath(faction,diplomacy.shipyard.Shipyard.Hull.INTERCEPTOR);
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
                    surfaces.prepare(picture,path);
                    originals.put(path,picture);
                }
                palette.put(role,picture);
            }
            palettes.put(faction,palette);
        }
        // One true interceptor each for Terran and the five legacy factions,
        // plus fifty specialized sprites for the five newer civilizations.
        if(originals.size()!=56)
            throw new IllegalStateException("Expected all 56 original faction fighter/interceptor sprites: "+originals.size());
    }
    public int loaded() { return originals.size(); }
    public void render(Battle battle,SpriteBatch batch) {render(battle,batch,null,null);}
    public void render(Battle battle,SpriteBatch batch,BattleRenderPoses poses) {
        render(battle,batch,poses,null);
    }
    public void render(Battle battle,SpriteBatch batch,BattleRenderPoses poses,
                       CombatViewBounds view) {
        vesselById.clear();
        for(Battle.Ship ship:battle.ships())vesselById.put(ship.id,ship);
        surfaces.begin(batch,battle,view);
        batch.setColor(1,1,1,1);
        for(Battle.Craft craft:battle.crafts()) {
            Battle.Ship source=vesselById.get(craft.sourceId);
            if(source==null)
                throw new IllegalStateException("Autonomous craft has no parent faction: "+craft.sourceId);
            Map<String,Texture> family=palettes.get(source.faction);
            if(family==null)
                throw new IllegalStateException("Approved craft art missing for faction: "+source.faction);
            Texture picture=family.get(role(craft));
            if(picture==null)
                throw new IllegalStateException("Approved craft sprite not loaded: "+source.faction+"/"+role(craft));
            float height=craft.fighter?switch(craft.profile.hull()) {
                case INTERCEPTOR -> 13f;
                case ASSAULT_GUNSHIP -> 17f;
                case LASER_FIGHTER, PARTICLE_FIGHTER -> 14f;
                case MISSILE_FIGHTER -> 15f;
                case TORPEDO_BOMBER -> 18f;
            }:11f;
            float width=height*picture.getWidth()/(float)picture.getHeight();
            Battle.Ship target=vesselById.get(craft.targetId);
            BattleRenderPoses.Pose position=poses==null?
                    new BattleRenderPoses.Pose((float)craft.x,(float)craft.y,0):poses.craft(craft);
            BattleRenderPoses.Pose aim=target==null?null:poses==null?
                    new BattleRenderPoses.Pose((float)target.x,(float)target.y,(float)target.facing):
                    poses.ship(target);
            double bearing=aim!=null?
                    StrictMath.atan2(aim.y()-position.y(),aim.x()-position.x()):source.facing;
            float rotation=(float)StrictMath.toDegrees(bearing)-90f;
            if(view!=null&&!view.circle(position.x(),position.y(),
                    Math.max(width,height)*.72))continue;
            surfaces.bind(batch,picture);
            surfaces.color(batch,rotation);
            batch.draw(picture,position.x()-width/2,position.y()-height/2,
                    width/2,height/2,width,height,1,1,rotation,
                    0,0,picture.getWidth(),picture.getHeight(),false,false);
        }
        surfaces.end(batch);
    }
    @Override public void dispose(){
        surfaces.dispose();
        for(Texture texture:originals.values())texture.dispose();
        originals.clear();palettes.clear();
    }
}
