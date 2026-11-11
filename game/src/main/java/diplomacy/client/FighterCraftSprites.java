package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.net.BattleConnection;
import diplomacy.sim.Battle;
import diplomacy.sim.FighterModel;
import diplomacy.sim.WorldScale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
    // Keep only textures actually used by recent battles, not 50 RGB + 100
    // material textures permanently resident on Android GL at game startup.
    private static final int MAX_RESIDENT_CRAFT_ART=24;
    private final Map<String,Texture> originals=new LinkedHashMap<>(32,.75f,true);
    private final Set<String> missingPaths=new HashSet<>();
    private final Map<Integer,Battle.Ship> vesselById=new HashMap<>();
    // Reuse per-frame membership instead of allocating a new HashSet at every draw.
    private final Set<String> usedThisFrame=new HashSet<>();
    private int offscreenCraftRejectedLastFrame;
    private final ShipSurfaceLighting surfaces=new ShipSurfaceLighting();
    public int offscreenCraftRejectedLastFrame(){return offscreenCraftRejectedLastFrame;}
    public int litCraftMaterials(){return surfaces.materialCount();}
    public int materialTextureBindings(){return surfaces.textureBindings();}

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
        // Shader only. Sprite and material textures are loaded when actual
        // fighters appear, not for all civilizations on every main-menu boot.
    }
    /**
     * Reject distant craft before loading any GL source art or material.
     * 80 world units is deliberately far larger than the tiny fighter footprint;
     * the existing exact texture-aspect test still decides final visibility.
     * This protects extreme silhouette ratios and camera-edge rotations.
     */
    static boolean potentiallyVisible(CombatViewBounds view,
                                      BattleRenderPoses.Pose position,float height){
        return view==null||view.circle(position.x(),position.y(),
                Math.max(80.0,height*8.0));
    }
    private Texture art(String path){
        Texture existing=originals.get(path);
        if(existing!=null)return existing;
        if(missingPaths.contains(path))return null;
        if(!Gdx.files.internal(path).exists()){
            missingPaths.add(path);
            Gdx.app.error("CRAFT-ART","Original fighter image missing: "+path);
            return null;
        }
        Texture picture=null;
        try{
            picture=new Texture(Gdx.files.internal(path));
            picture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            surfaces.prepare(picture,path);
            originals.put(path,picture);
            return picture;
        }catch(RuntimeException failed){
            if(picture!=null)picture.dispose();
            missingPaths.add(path);
            Gdx.app.error("CRAFT-ART","Unable to load fighter materials: "+path,failed);
            return null;
        }
    }
    private void trimAfterFrame(Set<String> used){
        if(originals.size()<=MAX_RESIDENT_CRAFT_ART)return;
        Iterator<Map.Entry<String,Texture>> entries=originals.entrySet().iterator();
        while(originals.size()>MAX_RESIDENT_CRAFT_ART&&entries.hasNext()){
            Map.Entry<String,Texture> entry=entries.next();
            if(used.contains(entry.getKey()))continue;
            surfaces.forget(entry.getValue());
            entry.getValue().dispose();
            entries.remove();
        }
    }
    /** Fighters keep their role-specific proportions, but remain far below ship interceptors. */
    static float fighterHeight(FighterModel.Hull hull){
        if(hull==null)throw new IllegalArgumentException("Missing fighter hull");
        float original=switch(hull){
            case INTERCEPTOR -> 13f;
            case ASSAULT_GUNSHIP -> 17f;
            case LASER_FIGHTER, PARTICLE_FIGHTER -> 14f;
            case MISSILE_FIGHTER -> 15f;
            case TORPEDO_BOMBER -> 18f;
        };
        return original*(float)WorldScale.SHIP_WORLD_SCALE*CombatEffectScale.FIGHTER_CRAFT;
    }
    static float droneHeight(){
        return 11f*(float)WorldScale.SHIP_WORLD_SCALE*CombatEffectScale.FIGHTER_CRAFT;
    }
    public int loaded() { return originals.size(); }
    public void render(Battle battle,SpriteBatch batch) {render(battle,batch,null,null);}
    public void render(Battle battle,SpriteBatch batch,BattleRenderPoses poses) {
        render(battle,batch,poses,null);
    }
    public void render(Battle battle,SpriteBatch batch,BattleRenderPoses poses,
                       CombatViewBounds view) {
        vesselById.clear();
        usedThisFrame.clear();
        offscreenCraftRejectedLastFrame=0;
        for(Battle.Ship ship:battle.ships())vesselById.put(ship.id,ship);
        if(battle.crafts().isEmpty())return;
        surfaces.begin(batch,battle,view);
        try{
        batch.setColor(1,1,1,1);
        for(Battle.Craft craft:battle.crafts()) {
            Battle.Ship source=vesselById.get(craft.sourceId);
            if(source==null)
                throw new IllegalStateException("Autonomous craft has no parent faction: "+craft.sourceId);
            BattleRenderPoses.Pose position=poses==null?
                    new BattleRenderPoses.Pose((float)craft.x,(float)craft.y,0):poses.craft(craft);
            float height=craft.fighter?fighterHeight(craft.profile.hull()):droneHeight();
            if(!potentiallyVisible(view,position,height)){
                offscreenCraftRejectedLastFrame++;
                continue;
            }
            String artRole=role(craft,source);
            String path=spritePath(source.faction,artRole);
            Texture picture=art(path);
            if(picture==null)continue;
            usedThisFrame.add(path);
            float width=height*picture.getWidth()/(float)picture.getHeight();
            if(view!=null&&!view.circle(position.x(),position.y(),
                    Math.max(width,height)*.72))continue;
            Battle.Ship target=vesselById.get(craft.targetId);
            BattleRenderPoses.Pose aim=target==null?null:poses==null?
                    new BattleRenderPoses.Pose((float)target.x,(float)target.y,(float)target.facing):
                    poses.ship(target);
            double bearing=aim!=null?
                    StrictMath.atan2(aim.y()-position.y(),aim.x()-position.x()):source.facing;
            float rotation=(float)StrictMath.toDegrees(bearing)-90f;
            surfaces.bind(batch,picture);
            surfaces.color(batch,rotation);
            batch.draw(picture,position.x()-width/2,position.y()-height/2,
                    width/2,height/2,width,height,1,1,rotation,
                    0,0,picture.getWidth(),picture.getHeight(),false,false);
        }
        }finally{
            // Finish the GL draw before evicting any material still referenced
            // by pending SpriteBatch vertices.
            surfaces.end(batch);
            // The hull material shader encodes rotation in SpriteBatch RGB.
            // That packed value is NOT a tint: never leak it into the next
            // pass (capital turrets are drawn immediately after fighters).
            batch.setColor(1f,1f,1f,1f);
            trimAfterFrame(usedThisFrame);
        }
    }
    /** Render host-owned network craft without constructing or stepping local craft simulation. */
    void renderRemote(Battle presentation,
                      java.util.List<BattleConnection.CraftEntity> crafts,
                      RemoteFleetMirrors mirrors,SpriteBatch batch,
                      CombatViewBounds view){
        if(crafts==null||mirrors==null||crafts.isEmpty())return;
        usedThisFrame.clear();
        offscreenCraftRejectedLastFrame=0;
        surfaces.begin(batch,presentation,view);
        try{
            batch.setColor(1,1,1,1);
            for(BattleConnection.CraftEntity craft:crafts){
                Battle.Ship source=mirrors.byHostId(craft.sourceId());
                if(source==null)continue; // v1 compatibility body lacks source identity.
                float height=craft.fighter()?
                        fighterHeight(craft.fighterHull()):droneHeight();
                BattleRenderPoses.Pose position=new BattleRenderPoses.Pose(
                        (float)craft.x(),(float)craft.y(),0);
                if(!potentiallyVisible(view,position,height)){
                    offscreenCraftRejectedLastFrame++;continue;
                }
                String artRole;
                if(!craft.fighter())artRole=craft.repair()?
                        "support_drone":"point_defense_fighter";
                else artRole=switch(craft.fighterHull()){
                    case INTERCEPTOR -> "interceptor";
                    case ASSAULT_GUNSHIP -> "assault_gunship";
                    case LASER_FIGHTER -> "superiority_fighter";
                    case PARTICLE_FIGHTER -> "electronic_warfare";
                    case MISSILE_FIGHTER -> "missile_fighter";
                    case TORPEDO_BOMBER -> "torpedo_bomber";
                };
                String path=spritePath(source.faction,artRole);
                Texture picture=art(path);
                if(picture==null)continue;
                usedThisFrame.add(path);
                float width=height*picture.getWidth()/(float)picture.getHeight();
                if(view!=null&&!view.circle(position.x(),position.y(),
                        Math.max(width,height)*.72))continue;
                Battle.Ship target=mirrors.byHostId(craft.targetId());
                double bearing=target==null?source.facing:
                        StrictMath.atan2(target.y-position.y(),target.x-position.x());
                float rotation=(float)StrictMath.toDegrees(bearing)-90f;
                surfaces.bind(batch,picture);
                surfaces.color(batch,rotation);
                batch.draw(picture,position.x()-width/2,position.y()-height/2,
                        width/2,height/2,width,height,1,1,rotation,
                        0,0,picture.getWidth(),picture.getHeight(),false,false);
            }
        }finally{
            surfaces.end(batch);
            batch.setColor(1f,1f,1f,1f);
            trimAfterFrame(usedThisFrame);
        }
    }

    @Override public void dispose(){
        surfaces.dispose();
        for(Texture texture:originals.values())texture.dispose();
        originals.clear();
        missingPaths.clear();
    }
}
