package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.FactionArt;
import diplomacy.sim.Battle;
import diplomacy.sim.DirectionalDefense;
import java.util.EnumMap;

/**
 * First Terran concept fleet atlas, with a generic exposed internal hull beneath.
 * Battle never depends on sprite pixels; tile transparency reflects core simulation.
 */
public final class TerranPlaceholderSprites implements Disposable {
    private static final int GRID=DirectionalDefense.GRID;
    private final EnumMap<Battle.Faction,EnumMap<Shipyard.Hull,Texture>> textures=
            new EnumMap<>(Battle.Faction.class);
    // Every exposed interior pixel is masked by the ORIGINAL hull PNG alpha.
    // Never put an oval generic material under a rectangular sprite region.
    private final java.util.Map<Texture,String> originals =
            new java.util.IdentityHashMap<>();
    private final java.util.Map<Texture,Texture> damagedLayers =
            new java.util.IdentityHashMap<>();
    private final java.util.Map<Texture,Texture> severeLayers =
            new java.util.IdentityHashMap<>();
    private final Pixmap damagedMaterial, destroyedMaterial, shieldMaterial;
    // Four shield facings share real source pixels and alpha-masked overlays;
    // textures are created per hull, not per vessel or recharge tick.
    private final java.util.Map<Texture,Texture[]> shieldSections =
            new java.util.IdentityHashMap<>();
    // A compact per-vessel overlay keeps irregular hull peeling inexpensive
    // even when several hundred damaged vessels share the same original PNG.
    private static final int MASK_W=96, MASK_H=144;
    private static final class Peel {
        Texture overlay, original;
        long signature=Long.MIN_VALUE;
    }
    private final java.util.Map<Integer,Peel> peeled=new java.util.HashMap<>();
    private final java.util.Map<Texture,Pixmap> sourcePixels =
            new java.util.IdentityHashMap<>();
    private Battle activeBattle;
    // One shader + cached textures for all faction/hull instances; no per-frame Pixmaps.
    private final ShipSurfaceLighting surfaces=new ShipSurfaceLighting();
    public int litMaterialCount(){return surfaces.materialCount();}
    public int materialTextureBindings(){return surfaces.textureBindings();}
    private static String spriteName(Shipyard.Hull hull) {
        return switch(hull.spriteHull()) {
            case INTERCEPTOR -> "interceptor";
            case CORVETTE -> "corvette";
            case MISSILE_CORVETTE -> "missile_corvette";
            case FRIGATE -> "frigate";
            case DESTROYER -> "destroyer";
            case LIGHT_CRUISER, CRUISER -> "light_cruiser";
            case HEAVY_CRUISER -> "heavy_cruiser";
            case BATTLECRUISER -> "battlecruiser";
            case CARRIER -> "carrier";
            case DREADNOUGHT -> "dreadnought";
            default -> throw new IllegalArgumentException("Invalid sprite family");
        };
    }
    public TerranPlaceholderSprites() {
        damagedMaterial=loadMaterial("materials/structure_damaged.png");
        destroyedMaterial=loadMaterial("materials/structure_destroyed.png");
        shieldMaterial=loadMaterial("materials/shield_energy_blue.png");
        // Only the owning civilization may use its approved sheet.
        // Share GPU textures when hull variants use one image, never across races.
        java.util.Map<String,Texture> uniqueTextures=new java.util.HashMap<>();
        for(Battle.Faction faction:Battle.Faction.values()){
            EnumMap<Shipyard.Hull,Texture> fleet=new EnumMap<>(Shipyard.Hull.class);
            for(Shipyard.Hull hull:Shipyard.Hull.values()){
                String file=faction==Battle.Faction.TERRAN
                        ?"ships/terran/"+spriteName(hull)+".png"
                        :FactionArt.runtimePath(faction,hull);
                if(file==null||!Gdx.files.internal(file).exists())continue;
                // Source art may contain a UNIQUE dreadnought (Terran and the
                // five legacy fleets). Reuse textures only when runtime paths
                // actually match, via the shared per-path cache below.
                if(fleet.containsKey(hull.spriteHull())){
                    fleet.put(hull,fleet.get(hull.spriteHull()));
                    continue;
                }
                try{
                    Texture tex=uniqueTextures.get(file);
                    if(tex==null){
                        tex=new Texture(Gdx.files.internal(file));
                        tex.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
                        uniqueTextures.put(file,tex);
                        originals.put(tex,file);
                    }
                    fleet.put(hull,tex);
                }catch(RuntimeException ex){
                    Gdx.app.error("SPRITES","Unable to load selected faction sprite: "+file,ex);
                }
            }
            if(!fleet.isEmpty())textures.put(faction,fleet);
        }
        // A full artwork release may NOT silently substitute colored geometric
        // hulls for a missing PNG. The 200v200 verification previously accepted
        // 333 sprites while ignoring 67 missing Terran ships.
        for(Battle.Faction faction:Battle.Faction.values())
            if(faction==Battle.Faction.TERRAN||FactionArt.supports(faction)||FactionArt.hasLegacyArt(faction))
                for(Shipyard.Hull hull:Shipyard.Hull.values())
                    if(texture(faction,hull)==null)
                        throw new IllegalStateException(
                                "Approved combat ship sprite missing from packaged assets: "+
                                faction+"/"+hull);
        // All original civilizations now load their own source-sheet sprites.
        // Missing generated art is a genuine packaging failure, not permission
        // to display blue geometric hulls for those factions.
    }
    private static Pixmap loadMaterial(String path) {
        if(!Gdx.files.internal(path).exists())
            throw new IllegalStateException("Approved damaged-hull material missing: "+path);
        return new Pixmap(Gdx.files.internal(path));
    }

    /**
     * Produce a UV-matched exposed interior for exactly one approved sprite.
     * The original alpha channel is authoritative: the structural material can
     * NEVER draw outside a ship's actual silhouette, at any heading or zoom.
     * Generated lazily and shared across ships of the same hull/faction.
     */
    private Texture exposedInterior(Texture original, boolean severe) {
        java.util.Map<Texture,Texture> cache=severe?severeLayers:damagedLayers;
        Texture layer=cache.get(original);
        if(layer!=null)return layer;
        String path=originals.get(original);
        if(path==null)throw new IllegalStateException("Missing original hull PNG identity");
        Pixmap src=new Pixmap(Gdx.files.internal(path));
        Pixmap material=severe?destroyedMaterial:damagedMaterial;
        Pixmap masked=new Pixmap(src.getWidth(),src.getHeight(),Pixmap.Format.RGBA8888);
        masked.setBlending(Pixmap.Blending.None);
        try {
            for(int y=0;y<src.getHeight();y++)for(int x=0;x<src.getWidth();x++){
                int pixel=src.getPixel(x,y),alpha=pixel&255;
                if(alpha==0){masked.drawPixel(x,y,0);continue;}
                int metal=material.getPixel(
                        x*material.getWidth()/src.getWidth(),
                        y*material.getHeight()/src.getHeight());
                // Keep the distinctive original vessel plating under exposed
                // battle damage, rather than a generic grayscale oval.
                int r=((((pixel>>>24)&255)*3)+(((metal>>>24)&255)*7))/10;
                int g=((((pixel>>>16)&255)*3)+(((metal>>>16)&255)*7))/10;
                int b=((((pixel>>>8)&255)*3)+(((metal>>>8)&255)*7))/10;
                masked.drawPixel(x,y,(r<<24)|(g<<16)|(b<<8)|alpha);
            }
            layer=new Texture(masked);
            layer.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            cache.put(original,layer);
            return layer;
        } finally {
            masked.dispose();
            src.dispose();
        }
    }

    private static boolean breached(Battle.Ship ship) {
        for(int row=0;row<GRID;row++)for(int col=0;col<GRID;col++){
            var tile=ship.defense.tile(row,col);
            if(tile.occupied && tile.exposure()>0.001)return true;
        }
        return false;
    }

    /**
     * Make four TRUE shield-material images following this sprite's silhouette.
     * A sector with zero shield energy is invisible even while the other three
     * facings remain energized. The mechanical shield model remains authoritative.
     */
    private void prepareShields(Texture original) {
        if(shieldSections.containsKey(original))return;
        Pixmap source=sourcePixels(original);
        Texture[] layers=new Texture[DirectionalDefense.Facing.values().length];
        for(DirectionalDefense.Facing face:DirectionalDefense.Facing.values()){
            Pixmap pix=new Pixmap(MASK_W,MASK_H,Pixmap.Format.RGBA8888);
            pix.setBlending(Pixmap.Blending.None);
            try {
                for(int y=0;y<MASK_H;y++)for(int x=0;x<MASK_W;x++){
                    double towardBow=1-(y+0.5)*2.0/MASK_H;
                    double towardPort=1-(x+0.5)*2.0/MASK_W;
                    if(DirectionalDefense.facingFor(towardBow,towardPort)!=face)
                        continue;
                    int art=source.getPixel(x*source.getWidth()/MASK_W,
                            y*source.getHeight()/MASK_H);
                    int alpha=art&255;
                    if(alpha==0)continue;
                    int material=shieldMaterial.getPixel(
                            x*shieldMaterial.getWidth()/MASK_W,
                            y*shieldMaterial.getHeight()/MASK_H);
                    int materialAlpha=material&255;
                    int strength=alpha*materialAlpha/255;
                    if(strength==0)continue;
                    pix.drawPixel(x,y,(material&0xffffff00)|strength);
                }
                Texture layer=new Texture(pix);
                layer.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
                layers[face.ordinal()]=layer;
            } finally {pix.dispose();}
        }
        shieldSections.put(original,layers);
    }

    private Pixmap sourcePixels(Texture texture){
        Pixmap source=sourcePixels.get(texture);
        if(source!=null)return source;
        String path=originals.get(texture);
        if(path==null)throw new IllegalStateException("Original hull art source not indexed");
        source=new Pixmap(Gdx.files.internal(path));
        sourcePixels.put(texture,source);
        return source;
    }
    /** Only armor changes invalidate the overlay; regenerating shields do not. */
    private static long armorSignature(Battle.Ship ship) {
        long result=0xcbf29ce484222325L;
        for(int row=0;row<GRID;row++)for(int col=0;col<GRID;col++){
            var tile=ship.defense.tile(row,col);
            long quantized=tile.occupied?StrictMath.round(tile.exposure()*128.0):0;
            result=(result^quantized)*0x100000001b3L;
        }
        return result;
    }
    private static float exposure(Battle.Ship ship,int row,int col) {
        if(row<0||col<0||row>=GRID||col>=GRID)return 0;
        var tile=ship.defense.tile(row,col);
        return tile.occupied?(float)tile.exposure():0;
    }
    private static float surfaceExposure(Battle.Ship ship,double row,double col) {
        int r=(int)StrictMath.floor(row),c=(int)StrictMath.floor(col);
        float dy=(float)(row-r),dx=(float)(col-c);
        float first=exposure(ship,r,c)*(1-dx)+exposure(ship,r,c+1)*dx;
        float second=exposure(ship,r+1,c)*(1-dx)+exposure(ship,r+1,c+1)*dx;
        return first*(1-dy)+second*dy;
    }
    private static float noise(int x,int y,int salt) {
        int hash=x*374761393+y*668265263+salt*224682251;
        hash=(hash^(hash>>>13))*1274126177;
        return ((hash^(hash>>>16))&0xffff)/65535f-0.5f;
    }
    /**
     * Per-pixel original hull opacity is eroded by the actual tile armor loss.
     * Neighbor bilinear sampling removes grid edges; stable fine/coarse noise
     * makes irregular crater lips instead of square checkerboard transparency.
     * The pixel's existing RGBA alpha is never increased or extended off-hull.
     */
    private Texture erodedExterior(Battle.Ship ship,Texture original){
        Peel item=peeled.computeIfAbsent(ship.id,id->new Peel());
        long signature=armorSignature(ship);
        if(item.original==original && item.overlay!=null && item.signature==signature)
            return item.overlay;
        Pixmap source=sourcePixels(original);
        Pixmap output=new Pixmap(MASK_W,MASK_H,Pixmap.Format.RGBA8888);
        output.setBlending(Pixmap.Blending.None);
        try {
            for(int y=0;y<MASK_H;y++)for(int x=0;x<MASK_W;x++){
                int originalPixel=source.getPixel(x*source.getWidth()/MASK_W,
                        y*source.getHeight()/MASK_H);
                int originalAlpha=originalPixel&255;
                if(originalAlpha==0){output.drawPixel(x,y,0);continue;}
                double tileRow=(y+0.5)*GRID/MASK_H-0.5;
                double tileCol=(x+0.5)*GRID/MASK_W-0.5;
                float damage=surfaceExposure(ship,tileRow,tileCol);
                float rough=noise(x/4,y/4,ship.id)*0.70f+
                        noise(x,y,ship.id*31)*0.30f;
                float erosion=Math.max(0,Math.min(1,
                        damage*1.16f+rough*Math.min(0.45f,damage*0.70f)));
                int alpha=Math.max(0,Math.min(255,
                        (int)(originalAlpha*(1f-erosion)+0.5f)));
                int darkened=ShipDamageScorch.darken(originalPixel,damage,rough);
                output.drawPixel(x,y,(darkened&0xffffff00)|alpha);
            }
            if(item.overlay==null){
                item.overlay=new Texture(output);
                item.overlay.setFilter(Texture.TextureFilter.Linear,
                        Texture.TextureFilter.Linear);
            }else item.overlay.draw(output,0,0);
            item.original=original;
            item.signature=signature;
            return item.overlay;
        } finally { output.dispose(); }
    }
    private void resetPeelCache() {
        for(Peel item:peeled.values())if(item.overlay!=null)item.overlay.dispose();
        peeled.clear();
    }
    /** Current count of individual ship masks, useful for mass-fleet verification. */
    public int individualPeelMasks(){return peeled.size();}

    /** Runtime diagnostic: distinct original-art silhouettes with generated, alpha-masked internal layers. */
    public int damageLayersBuilt() { return damagedLayers.size()+severeLayers.size(); }

    public int loaded() {
        int count=0;
        for(var fleet:textures.values())count+=fleet.size();
        return count;
    }
    private Texture texture(Battle.Faction faction,Shipyard.Hull hull) {
        var fleet=textures.get(faction);
        return fleet==null?null:fleet.get(hull);
    }
    /** Retired hulls still retain their authentic faction art for post-mortem shards. */
    Texture sourceTexture(Battle.Faction faction,Shipyard.Hull hull){
        return hull==null?null:texture(faction,hull);
    }
    /** Preview is an art reference, not yet a source of fitted module UV anchors. */
    public boolean hasHull(Battle.Faction faction, Shipyard.Hull hull) {
        return texture(faction,hull)!=null;
    }
    public boolean drawPreview(SpriteBatch batch,Battle.Faction faction,Shipyard.Hull hull,
                               float centerX,float centerY,float maxWidth,float maxHeight) {
        if(!hasHull(faction,hull))return false;
        Texture texture=texture(faction,hull);
        float factor=Math.min(maxWidth/texture.getWidth(),maxHeight/texture.getHeight());
        float w=texture.getWidth()*factor,h=texture.getHeight()*factor;
        batch.setColor(1,1,1,0.94f);
        batch.draw(texture,centerX-w/2,centerY-h/2,w,h);
        batch.setColor(1,1,1,1);
        return true;
    }
    /** True only when this living vessel has an actual image, never for unapproved alien sheets. */
    public boolean hasSprite(Battle.Ship ship) {
        return ship != null && ship.alive() && ship.hullClass != null
                && texture(ship.faction,ship.hullClass)!=null;
    }
    public void render(Battle battle,SpriteBatch batch) {render(battle,batch,null,null);}
    public void render(Battle battle,SpriteBatch batch,BattleRenderPoses poses) {
        render(battle,batch,poses,null);
    }
    public void render(Battle battle,SpriteBatch batch,BattleRenderPoses poses,
                       CombatViewBounds view) {
        if(textures.isEmpty())return;
        if(battle!=activeBattle){resetPeelCache();activeBattle=battle;}
        // Upload damage masks outside the SpriteBatch; only changed quantized
        // armor cells rebuild. One damaged ship draws two hull quads, not 256.
        for(Battle.Ship ship:battle.ships())if(hasSprite(ship)){
            BattleRenderPoses.Pose position=poses==null?
                    new BattleRenderPoses.Pose((float)ship.x,(float)ship.y,(float)ship.facing):
                    poses.ship(ship);
            if(view!=null&&!view.ship(position,ship.visualScale()))continue;
            Texture tex=texture(ship.faction,ship.hullClass);
            surfaces.prepare(tex,originals.get(tex));
            if(ship.shields>0)prepareShields(tex);
            if(breached(ship)){
                exposedInterior(tex,ship.hull<ship.maxHull*0.30);
                erodedExterior(ship,tex);
            }
        }
        surfaces.begin(batch,battle,view);
        for(Battle.Ship ship:battle.ships()) {
            if(!hasSprite(ship))continue;
            Texture tex=texture(ship.faction,ship.hullClass);
            if(tex==null)continue;
            float height=(float)(34*ship.visualScale());
            float width=height*tex.getWidth()/(float)tex.getHeight();
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)ship.x,(float)ship.y,(float)ship.facing):
                    poses.ship(ship);
            if(view!=null&&!view.ship(pose,ship.visualScale()))continue;
            float rotation=(float)(StrictMath.toDegrees(pose.angle())-90);
            surfaces.bind(batch,tex);
            ShipSurfaceLighting.heading(batch,rotation,(float)Math.max(0,
                    Math.min(1,ship.hull/Math.max(1,ship.maxHull))));
            if(!breached(ship)){
                drawWhole(batch,tex,pose,width,height,rotation);
            }else{
                // Damage remains authored-UV registered: peel alpha stays under
                // the surface shader; uncovered steel is a separate image layer.
                drawWhole(batch,exposedInterior(tex,ship.hull<ship.maxHull*.30),
                        pose,width,height,rotation);
                drawWhole(batch,peeled.get(ship.id).overlay,pose,width,height,rotation);
            }
        }
        batch.setColor(1,1,1,1);
        surfaces.end(batch);
        // Shields use original alpha-masked materials, not fabricated normal
        // directions or emissive pixels. Separate translucent overlay pass.
        batch.begin();
        for(Battle.Ship ship:battle.ships()){
            if(!hasSprite(ship)||ship.shields<=0)continue;
            Texture tex=texture(ship.faction,ship.hullClass);
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)ship.x,(float)ship.y,(float)ship.facing):
                    poses.ship(ship);
            if(view!=null&&!view.ship(pose,ship.visualScale()))continue;
            float height=(float)(34*ship.visualScale());
            float width=height*tex.getWidth()/(float)tex.getHeight();
            drawShield(batch,ship,tex,pose,width,height,
                    (float)(StrictMath.toDegrees(pose.angle())-90));
        }
        batch.setColor(1,1,1,1);
        batch.end();
    }
    private void drawShield(SpriteBatch batch,Battle.Ship ship,Texture original,
                            BattleRenderPoses.Pose pose,float width,float height,float rotation) {
        if(ship.maxShields<=0||ship.shields<=0)return;
        Texture[] sectors=shieldSections.get(original);
        if(sectors==null)throw new IllegalStateException("Missing actual shield texture overlay");
        for(DirectionalDefense.Facing face:DirectionalDefense.Facing.values()){
            double maximum=ship.defense.maxShields(face);
            if(maximum<=0)continue;
            float strength=(float)Math.min(1,ship.defense.shields(face)/maximum);
            if(strength<=0.003f)continue;
            batch.setColor(1,1,1,0.38f*strength);
            drawWhole(batch,sectors[face.ordinal()],pose,width,height,rotation);
        }
        batch.setColor(1,1,1,1);
    }
    private static void drawWhole(SpriteBatch batch,Texture tex,BattleRenderPoses.Pose pose,
                                  float width,float height,float rotation) {
        batch.draw(tex,pose.x()-width/2,pose.y()-height/2,
                width/2,height/2,width,height,1,1,rotation,
                0,0,tex.getWidth(),tex.getHeight(),false,false);
    }
    @Override public void dispose() {
        java.util.Set<Texture> disposed=java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Texture,Boolean>());
        for(var fleet:textures.values())
            for(Texture image:fleet.values())if(disposed.add(image))image.dispose();
        textures.clear();
        resetPeelCache();activeBattle=null;
        for(Texture[] sections:shieldSections.values())
            for(Texture sector:sections)if(sector!=null)sector.dispose();
        shieldSections.clear();
        for(Pixmap pixels:sourcePixels.values())pixels.dispose();
        sourcePixels.clear();
        for(Texture image:damagedLayers.values())image.dispose();
        for(Texture image:severeLayers.values())image.dispose();
        damagedLayers.clear();severeLayers.clear();originals.clear();
        surfaces.dispose();
        damagedMaterial.dispose();
        destroyedMaterial.dispose();
        shieldMaterial.dispose();
    }
}
