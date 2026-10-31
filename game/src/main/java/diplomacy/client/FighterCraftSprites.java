package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.sim.Battle;
import diplomacy.sim.FighterModel;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Original faction-specific fighter and drone sprite renderer. Never paints
 * colored squares/triangles over a successfully loaded craft sprite.
 * All 11 playable factions use the original 50 purpose-built fighter images.
 * Founder fleets share their corresponding fighter-art family; no resized
 * cruiser, destroyer or improvised triangle is ever used for craft.
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

    /**
     * The 50 dedicated, role-specific fighter sprites are the aircraft source
     * for ALL playable fleets. Founder factions are paired with their established
     * visual/mechanical counterpart. Terran selects the modular industrial
     * aircraft family. Never replace a fighter with a cruiser/destroyer hull.
     *
     * This is a graphics selection only; it does not alter faction mechanics,
     * fitted hangars, or the aircraft's weapons and movement.
     */
    static String fighterArtFamily(Battle.Faction faction) {
        if(faction==null)throw new IllegalArgumentException("Fighter faction required");
        return switch(faction) {
            case DRAXIAN, VAERITH_PRISM -> "vaerith_prism";
            case UZIPT, ZELTHAR_LUMINARI -> "zelthar_luminari";
            case MANDRALLY, AUREATE_SYNOD -> "aureate_synod";
            case CARNELIAN, NYX_BIOLITH -> "nyx_biolith";
            case ZIPPLEDROW, UMBRA_FOUNDRY, TERRAN -> "umbra_foundry";
        };
    }
    /** Authored craft image, not a generic mesh or downsized capital ship. */
    public static String spritePath(Battle.Faction faction,String role) {
        if(faction==null||role==null)throw new IllegalArgumentException("Craft identity required");
        boolean supported=false;
        for(String candidate:ROLES)if(candidate.equals(role)){supported=true;break;}
        if(!supported)throw new IllegalArgumentException("Unapproved fighter role: "+role);
        return "ships/fighters/"+fighterArtFamily(faction)+"/"+role+".png";
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
    /**
     * The original fighter atlas also has recon and heavy-bomber artwork.
     * Select it when the actual parent flight doctrine/launch hull matches:
     * screening interceptors are the scout variant; a dreadnought's torpedo
     * flight is the heavy-bomber variant. This is visual; weapon profiles
     * stay entirely authoritative in FighterModel.
     */
    static String operationalRole(FighterModel.Hull hull,
                                  diplomacy.sim.ShipTacticalDirectives.FighterMission mission,
                                  boolean dreadnoughtCarrier) {
        if(hull==null)throw new IllegalArgumentException("Fighter profile required");
        if(hull==FighterModel.Hull.INTERCEPTOR
                &&mission==diplomacy.sim.ShipTacticalDirectives.FighterMission.SCREEN)
            return "recon_fighter";
        if(hull==FighterModel.Hull.TORPEDO_BOMBER &&dreadnoughtCarrier)
            return "heavy_bomber";
        return switch(hull) {
            case INTERCEPTOR -> "interceptor";
            case ASSAULT_GUNSHIP -> "assault_gunship";
            case LASER_FIGHTER -> "superiority_fighter";
            case PARTICLE_FIGHTER -> "electronic_warfare";
            case MISSILE_FIGHTER -> "missile_fighter";
            case TORPEDO_BOMBER -> "torpedo_bomber";
        };
    }
    private static String role(Battle.Craft craft,Battle.Ship source) {
        if(!craft.fighter)return role(craft);
        diplomacy.sim.ShipTacticalDirectives.FighterMission mission=
                source.standingOrders==null?null:
                source.standingOrders.directives().fighterMission();
        boolean large=source.hullClass!=null &&
                source.hullClass.spriteHull()==diplomacy.shipyard.Shipyard.Hull.DREADNOUGHT;
        return operationalRole(craft.profile.hull(),mission,large);
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
        // Ten dedicated craft per palette; some factions use the same source
        // palette. All fifty are shared once in GPU memory, not reloaded.
        if(originals.size()!=50)
            throw new IllegalStateException(
                    "All 50 authored fighter/drone sprites must load: "+originals.size());
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
            String artRole=role(craft,source);
            Texture picture=family.get(artRole);
            if(picture==null)
                throw new IllegalStateException("Approved craft sprite not loaded: "+source.faction+"/"+artRole);
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
