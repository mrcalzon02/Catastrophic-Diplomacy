package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.shipyard.Shipyard;
import diplomacy.shipyard.FactionArt;
import diplomacy.shipyard.HullLayouts;
import diplomacy.sim.Battle;
import diplomacy.sim.DirectionalDefense;
import diplomacy.sim.DeceptionProjection;
import java.util.EnumMap;
import java.util.List;

/**
 * First Terran concept fleet atlas, with a generic exposed internal hull beneath.
 * Battle never depends on sprite pixels; tile transparency reflects core simulation.
 */
public final class TerranPlaceholderSprites implements Disposable {
    private static final int GRID=DirectionalDefense.GRID;
    private final EnumMap<Battle.Faction,EnumMap<Shipyard.Hull,Texture>> textures=
            new EnumMap<>(Battle.Faction.class);
    /** Manifest paths stay cheap on startup; actual GPU images load on first use.
     * Only an idle, unused image can be evicted after a finished draw frame. */
    private final EnumMap<Battle.Faction,EnumMap<Shipyard.Hull,String>> assetPaths=
            new EnumMap<>(Battle.Faction.class);
    private final java.util.LinkedHashMap<String,Texture> resident=
            new java.util.LinkedHashMap<>(32,.75f,true);
    private final java.util.Map<String,Long> lastUsedFrame=new java.util.HashMap<>();
    private final java.util.Set<String> unavailable=new java.util.HashSet<>();
    private final boolean boundedResidency=Gdx.app!=null &&
            Gdx.app.getType()==com.badlogic.gdx.Application.ApplicationType.Android;
    private static final int MOBILE_MAX_SHIP_IMAGES=24;
    private long artFrame;
    private int retiredOriginalTextures,peakResidentOriginalTextures;
    public int residentOriginalTextures(){return resident.size();}
    public int peakResidentOriginalTextures(){return peakResidentOriginalTextures;}
    public int evictedOriginalTextures(){return retiredOriginalTextures;}
    /** RGBA8888 full mip chain is roughly 4/3 the base level; estimate only. */
    public long estimatedResidentAlbedoBytes(){
        long bytes=0;
        for(Texture texture:resident.values()){
            int w=texture.getWidth(),h=texture.getHeight();
            for(;;){
                bytes+=(long)w*h*4;
                if(!ShipTextureSampling.mipmapsAvailable()||(w==1&&h==1))break;
                w=Math.max(1,w/2);h=Math.max(1,h/2);
            }
        }
        return bytes;
    }
    /** Campaign flagships use separate art, never overwrite faction hulls. */
    private final java.util.Map<String,Texture> bossTextures=new java.util.HashMap<>();
    // Shipyard hardpoints are aligned once to each approved transparent PNG.
    // Cached normalized anchors are shared by mouse, touch and rendering.
    record ArtAnchor(float u,float v) {}
    private final java.util.Map<String,ArtAnchor[]> shipyardAnchors=new java.util.HashMap<>();
    // Small, immutable alpha point clouds, one for each unique authored PNG.
    // Sharing across specialist variants avoids decoding the same picture
    // forty-six times per faction. No GPU readback or per-frame allocations.
    private record AlphaCloud(int width,int height,int[] opaque) {}
    private final java.util.Map<Texture,AlphaCloud> alphaClouds=
            new java.util.IdentityHashMap<>();

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
    /** One shared hex-texture field shell, split into the same four mechanical facings. */
    private Texture[] shieldShell;
    /** Optional cosmetic shader; failure must never prevent the game launching. */
    private final ShieldMaterialShader shieldInk;
    // A compact per-vessel overlay keeps irregular hull peeling inexpensive
    // even when several hundred damaged vessels share the same original PNG.
    private static final int MASK_W=96, MASK_H=144;
    private static final class Peel {
        Texture overlay, original;
        int[] levels;
        long signature=Long.MIN_VALUE;
    }
    private final java.util.Map<Integer,Peel> peeled=new java.util.HashMap<>();
    private final java.util.Set<Integer> retainedDamageIds=new java.util.HashSet<>();
    private final java.util.Map<Texture,Pixmap> sourcePixels =
            new java.util.IdentityHashMap<>();
    // One source-alpha registration per artwork texture, shared by every ship
    // of that hull/faction; only the visual stamps shift, never impact physics.
    private final java.util.Map<Texture,ShipArmorStampAnchors> armorAnchors =
            new java.util.IdentityHashMap<>();
    private Battle activeBattle;
    // Session counters are read only at diagnostics time, never inside GPU
    // callbacks. They expose upload area and retained CPU cache pressure.
    private long maskPixelsUploaded;
    private int maskTextureUploads;
    public long maskPixelsUploaded(){return maskPixelsUploaded;}
    public int maskTextureUploads(){return maskTextureUploads;}
    public long cpuSourceCacheBytes(){
        return (long)sourcePixels.size()*MASK_W*MASK_H*4;
    }
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
        ShieldMaterialShader candidate=null;
        try{candidate=new ShieldMaterialShader();}
        catch(RuntimeException shaderFailure){
            if(Gdx.app!=null)Gdx.app.error("SHIELD-FX",
                    "Source-art shield tint unavailable; using original texture fallback",shaderFailure);
        }
        shieldInk=candidate;
        damagedMaterial=loadMaterial("materials/structure_damaged.png");
        destroyedMaterial=loadMaterial("materials/structure_destroyed.png");
        shieldMaterial=loadMaterial("materials/shield_energy_blue.png");
        // Discover ORIGINAL approved source sprites without decoding all
        // eleven navies into GPU memory at the main menu.
        for(Battle.Faction faction:Battle.Faction.values()){
            EnumMap<Shipyard.Hull,String> paths=new EnumMap<>(Shipyard.Hull.class);
            for(Shipyard.Hull hull:Shipyard.Hull.values()){
                String file=faction==Battle.Faction.TERRAN
                        ?"ships/terran/"+spriteName(hull)+".png"
                        :FactionArt.runtimePath(faction,hull);
                if(file==null||!Gdx.files.internal(file).exists())continue;
                if(paths.containsKey(hull.spriteHull())){
                    paths.put(hull,paths.get(hull.spriteHull()));
                }else paths.put(hull,file);
            }
            if(!paths.isEmpty())assetPaths.put(faction,paths);
        }
        // Optional boss artwork is also loaded on first real appearance.
        // All originals retain their complete source-pixel dimensions.
        // A full artwork release may NOT silently substitute colored geometric
        // hulls for a missing PNG. The 200v200 verification previously accepted
        // 333 sprites while ignoring 67 missing Terran ships.
        for(Battle.Faction faction:Battle.Faction.values())
            if(faction==Battle.Faction.TERRAN||FactionArt.supports(faction)||FactionArt.hasLegacyArt(faction))
                for(Shipyard.Hull hull:Shipyard.Hull.values())
                    if(!hasHull(faction,hull))
                        throw new IllegalStateException(
                                "Approved combat ship sprite missing from packaged assets: "+
                                faction+"/"+hull);
        // All original civilizations now load their own source-sheet sprites.
        // Missing generated art is a genuine packaging failure, not permission
        // to display blue geometric hulls for those factions.
        // Installed before any campaign/skirmish ship spawns. The same exact
        // projected slots drive FIRE origins, visible turret art, and editor taps.
        HullLayouts.useSpriteProjector(this::projectOriginalArt);
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
                // 4x darker exposed interior; source alpha is authoritative.
                // If the underlay material has no pixel at this UV, fall back
                // to a shadowed original hull pixel, NEVER invent a sprite
                // beyond the original silhouette or assume underlying art.
                masked.drawPixel(x,y,ShipSurfaceDamageVisual.interiorPixel(pixel,metal));
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
        // One downsample of the *original* silhouette is reused for all four
        // quadrants, rather than sampling a Pixmap 4x for every neighbor.
        int[] alpha=new int[MASK_W*MASK_H];
        for(int y=0;y<MASK_H;y++)for(int x=0;x<MASK_W;x++){
            alpha[y*MASK_W+x]=source.getPixel(x*source.getWidth()/MASK_W,
                    y*source.getHeight()/MASK_H)&255;
        }
        for(DirectionalDefense.Facing face:DirectionalDefense.Facing.values()){
            Pixmap pix=new Pixmap(MASK_W,MASK_H,Pixmap.Format.RGBA8888);
            pix.setBlending(Pixmap.Blending.None);
            try {
                for(int y=0;y<MASK_H;y++)for(int x=0;x<MASK_W;x++){
                    double towardBow=1-(y+0.5)*2.0/MASK_H;
                    double towardPort=1-(x+0.5)*2.0/MASK_W;
                    if(DirectionalDefense.facingFor(towardBow,towardPort)!=face)
                        continue;
                    int offset=y*MASK_W+x;
                    int sourceAlpha=alpha[offset];
                    if(sourceAlpha==0)continue;
                    int left=alpha[y*MASK_W+Math.max(0,x-1)];
                    int right=alpha[y*MASK_W+Math.min(MASK_W-1,x+1)];
                    int up=alpha[Math.max(0,y-1)*MASK_W+x];
                    int down=alpha[Math.min(MASK_H-1,y+1)*MASK_W+x];
                    int left2=alpha[y*MASK_W+Math.max(0,x-2)];
                    int right2=alpha[y*MASK_W+Math.min(MASK_W-1,x+2)];
                    int up2=alpha[Math.max(0,y-2)*MASK_W+x];
                    int down2=alpha[Math.min(MASK_H-1,y+2)*MASK_W+x];
                    int edge=ShieldSurfaceModel.edge(sourceAlpha,left,right,up,down,
                            left2,right2,up2,down2);
                    int material=shieldMaterial.getPixel(
                            x*shieldMaterial.getWidth()/MASK_W,
                            y*shieldMaterial.getHeight()/MASK_H);
                    pix.drawPixel(x,y,ShieldSurfaceModel.material(material,sourceAlpha,edge));
                }
                Texture layer=new Texture(pix);
                layer.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
                layers[face.ordinal()]=layer;
            } finally {pix.dispose();}
        }
        shieldSections.put(original,layers);
    }

    /**
     * Cache the actual authored energy-field sprite on an elliptical shell.
     * Texture preparation happens once per renderer, never once per ship/frame.
     * Each transparent quarter corresponds to an independently powered facing.
     */
    private void prepareShieldShell(){
        if(shieldShell!=null)return;
        final int size=192;
        Texture[] built=new Texture[DirectionalDefense.Facing.values().length];
        try {
            for(DirectionalDefense.Facing face:DirectionalDefense.Facing.values()){
                Pixmap pixels=new Pixmap(size,size,Pixmap.Format.RGBA8888);
                pixels.setBlending(Pixmap.Blending.None);
                try {
                    for(int y=0;y<size;y++)for(int x=0;x<size;x++){
                        double nx=(x+.5)*2.0/size-1.0;
                        double ny=(y+.5)*2.0/size-1.0;
                        double distance=StrictMath.hypot(nx,ny);
                        if(distance>=1)continue;
                        if(DirectionalDefense.facingFor(-ny,-nx)!=face)continue;
                        int material=shieldMaterial.getPixel(
                                x*shieldMaterial.getWidth()/size,
                                y*shieldMaterial.getHeight()/size);
                        int rgba=ShieldSurfaceModel.shellMaterial(material,distance);
                        if((rgba&255)!=0)pixels.drawPixel(x,y,rgba);
                    }
                    Texture sector=new Texture(pixels);
                    sector.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
                    built[face.ordinal()]=sector;
                }finally{pixels.dispose();}
            }
            shieldShell=built;
        }catch(RuntimeException error){
            for(Texture part:built)if(part!=null)part.dispose();
            throw error;
        }
    }
    private Pixmap sourcePixels(Texture texture){
        Pixmap cached=sourcePixels.get(texture);
        if(cached!=null)return cached;
        String path=originals.get(texture);
        if(path==null)throw new IllegalStateException("Original hull art source not indexed");
        // The full-resolution PNG still lives in the ORIGINAL GPU texture.
        // All three CPU consumers (shield stencil, armor registration, damage
        // overlay) sample only these same fixed 96x144 positions. Retaining
        // full decoded Pixmaps for all faction hulls needlessly consumes
        // hundreds of MB of heap on older Android phones.
        Pixmap decoded=new Pixmap(Gdx.files.internal(path));
        Pixmap sampled=null;
        try {
            sampled=new Pixmap(MASK_W,MASK_H,Pixmap.Format.RGBA8888);
            sampled.setBlending(Pixmap.Blending.None);
            for(int y=0;y<MASK_H;y++)for(int x=0;x<MASK_W;x++)
                sampled.drawPixel(x,y,decoded.getPixel(
                        ShipSurfaceSampleGrid.sourcePixel(x,decoded.getWidth(),MASK_W),
                        ShipSurfaceSampleGrid.sourcePixel(y,decoded.getHeight(),MASK_H)));
            sourcePixels.put(texture,sampled);
            return sampled;
        }catch(RuntimeException failure){
            if(sampled!=null)sampled.dispose();
            throw failure;
        }finally{
            decoded.dispose();
        }
    }
    private ShipArmorStampAnchors armorAnchors(Texture original){
        ShipArmorStampAnchors registered=armorAnchors.get(original);
        if(registered!=null)return registered;
        Pixmap source=sourcePixels(original);
        int[] alpha=new int[MASK_W*MASK_H];
        for(int y=0;y<MASK_H;y++)for(int x=0;x<MASK_W;x++)
            alpha[y*MASK_W+x]=source.getPixel(
                    x*source.getWidth()/MASK_W,
                    y*source.getHeight()/MASK_H)&255;
        registered=ShipArmorStampAnchors.fromAlpha(alpha,MASK_W,MASK_H);
        armorAnchors.put(original,registered);
        return registered;
    }
    /** Only armor changes invalidate the overlay; regenerating shields do not. */
    private static long armorSignature(Battle.Ship ship) {
        long result=0xcbf29ce484222325L;
        for(int row=0;row<GRID;row++)for(int col=0;col<GRID;col++){
            var tile=ship.defense.tile(row,col);
            long armor=tile.occupied?StrictMath.round(tile.exposure()*128.0):0;
            long internal=tile.occupied?StrictMath.round(tile.internalDamage()*128.0):0;
            result=(result^armor)*0x100000001b3L;
            result=(result^internal)*0x100000001b3L;
        }
        return result;
    }
    private static float cellDamage(float[][] values,int row,int col) {
        if(row<0||col<0||row>=GRID||col>=GRID)return 0;
        return values[row][col];
    }
    private static float surfaceDamage(float[][] values,double row,double col) {
        int r=(int)StrictMath.floor(row),c=(int)StrictMath.floor(col);
        float dy=(float)(row-r),dx=(float)(col-c);
        float first=cellDamage(values,r,c)*(1-dx)+cellDamage(values,r,c+1)*dx;
        float second=cellDamage(values,r+1,c)*(1-dx)+cellDamage(values,r+1,c+1)*dx;
        return first*(1-dy)+second*dy;
    }
    private static float noise(int x,int y,int salt) {
        int hash=x*374761393+y*668265263+salt*224682251;
        hash=(hash^(hash>>>13))*1274126177;
        return ((hash^(hash>>>16))&0xffff)/65535f-0.5f;
    }
    /**
     * Read a physical crater at a specified mask pixel. The neighboring
     * samples for the rim use exactly the same registered armor cells as the
     * alpha mask; they cannot invent additional geometry.
     */
    private static float craterAt(float[][] wear,float[][] internal,
                                   ShipArmorStampAnchors aligned,
                                   float tileRow,float tileCol,int shipId) {
        int centerR=(int)StrictMath.floor(tileRow+.5);
        int centerC=(int)StrictMath.floor(tileCol+.5);
        float crater=0f;
        for(int r=Math.max(0,centerR-1);r<=Math.min(GRID-1,centerR+1);r++)
            for(int c=Math.max(0,centerC-1);c<=Math.min(GRID-1,centerC+1);c++){
                if(wear[r][c]<=.70f&&internal[r][c]<=.00001f)continue;
                crater=Math.max(crater,ShipArmorStamps.openingAt(
                        tileRow,tileCol,r,c,aligned.row(r,c),aligned.col(r,c),
                        wear[r][c],internal[r][c],shipId));
            }
        return crater;
    }
    /**
     * Shallow armor wear darkens plating without opening a hole. Severe armor
     * loss or actual internal damage erodes hull alpha to reveal structural
     * material; bilinear cells and stable noise shape the crater lips.
     * The pixel's existing RGBA alpha is never increased or extended off-hull.
     */
    private Texture erodedExterior(Battle.Ship ship,Texture original){
        Peel item=peeled.computeIfAbsent(ship.id,id->new Peel());
        long signature=armorSignature(ship);
        if(item.original==original && item.overlay!=null && item.signature==signature)
            return item.overlay;
        if(item.original!=original && item.overlay!=null){
            item.overlay.dispose();
            item.overlay=null;
            item.levels=null;
        }
        Pixmap source=sourcePixels(original);
        ShipArmorStampAnchors aligned=armorAnchors(original);
        // Snapshot only 256 actual protection cells once per changed vessel.
        // Avoid dereferencing defenses and recomputing integrity for every
        // mask pixel (and all 9 neighboring tiles) in mass engagements.
        float[][] wear=new float[GRID][GRID],internal=new float[GRID][GRID];
        int[] levels=new int[GRID*GRID*2];
        for(int r=0;r<GRID;r++)for(int c=0;c<GRID;c++){
            var tile=ship.defense.tile(r,c);
            if(!tile.occupied)continue;
            wear[r][c]=(float)tile.exposure();
            internal[r][c]=(float)tile.internalDamage();
            int index=(r*GRID+c)*2;
            levels[index]=(int)StrictMath.round(tile.exposure()*128.0);
            levels[index+1]=(int)StrictMath.round(tile.internalDamage()*128.0);
        }
        ShipDamageMaskRegion.Rect region=ShipDamageMaskRegion.changed(
                item.levels,levels,MASK_W,MASK_H);
        if(region==null){
            item.signature=signature;
            return item.overlay;
        }
        Pixmap output=new Pixmap(region.width(),region.height(),Pixmap.Format.RGBA8888);
        output.setBlending(Pixmap.Blending.None);
        try {
            for(int localY=0;localY<region.height();localY++)
                for(int localX=0;localX<region.width();localX++){
                int x=localX+region.x(),y=localY+region.y();
                int originalPixel=source.getPixel(x*source.getWidth()/MASK_W,
                        y*source.getHeight()/MASK_H);
                int originalAlpha=originalPixel&255;
                if(originalAlpha==0){output.drawPixel(localX,localY,0);continue;}
                double tileRow=(y+0.5)*GRID/MASK_H-0.5;
                double tileCol=(x+0.5)*GRID/MASK_W-0.5;
                // Wear darkens source plating; only localized physically
                // damaged cells cut alpha. Neighbor lookups catch crater lips.
                float damage=surfaceDamage(wear,tileRow,tileCol);
                float row=(float)tileRow,col=(float)tileCol;
                float crater=craterAt(wear,internal,aligned,row,col,ship.id);
                float rim=0f;
                // Interior render layer is clipped to original opaque pixels.
                // No original hull pixel => the branch above already skipped.
                // No breach => no 4-neighbor sampling or emissive overhead.
                if(crater>.07f && crater<.98f){
                    float sx=GRID/(float)MASK_W,sy=GRID/(float)MASK_H;
                    float left=craterAt(wear,internal,aligned,row,col-sx,ship.id);
                    float right=craterAt(wear,internal,aligned,row,col+sx,ship.id);
                    float up=craterAt(wear,internal,aligned,row-sy,col,ship.id);
                    float down=craterAt(wear,internal,aligned,row+sy,col,ship.id);
                    // The interior layer shares the source alpha silhouette.
                    rim=ShipSurfaceDamageVisual.breachRim(crater,left,right,up,down,
                            originalAlpha,originalAlpha);
                }
                float rough=noise(x/4,y/4,ship.id)*0.70f+
                        noise(x,y,ship.id*31)*0.30f;
                output.drawPixel(localX,localY,ShipSurfaceDamageVisual.stampedPixel(
                        originalPixel,damage,crater,rim,rough));
            }
            if(item.overlay==null){
                item.overlay=new Texture(output);
                item.overlay.setFilter(Texture.TextureFilter.Linear,
                        Texture.TextureFilter.Linear);
            }else item.overlay.draw(output,region.x(),region.y());
            maskPixelsUploaded+=region.pixels();
            maskTextureUploads++;
            item.original=original;
            item.levels=levels;
            item.signature=signature;
            return item.overlay;
        } finally { output.dispose(); }
    }
    private void resetPeelCache() {
        for(Peel item:peeled.values())if(item.overlay!=null)item.overlay.dispose();
        peeled.clear();
        retainedDamageIds.clear();
    }
    /** Repaired and destroyed vessels never retain obsolete GPU damage masks. */
    private void retireUnusedPeels(){
        for(var iter=peeled.entrySet().iterator();iter.hasNext();){
            var item=iter.next();
            if(retainedDamageIds.contains(item.getKey()))continue;
            if(item.getValue().overlay!=null)item.getValue().overlay.dispose();
            iter.remove();
        }
    }
    /** Current count of individual ship masks, useful for mass-fleet verification. */
    public int individualPeelMasks(){return peeled.size();}

    /** Runtime diagnostic: distinct original-art silhouettes with generated, alpha-masked internal layers. */
    public int damageLayersBuilt() { return damagedLayers.size()+severeLayers.size(); }

    public int loaded() {
        // Number of valid source-art assignments, not resident GL textures.
        int count=0;
        for(var fleet:assetPaths.values())count+=fleet.size();
        return count;
    }
    private Texture acquire(String path){
        if(path==null||unavailable.contains(path))return null;
        Texture image=resident.get(path);
        if(image==null){
            try{
                // Actual GPU mip levels; close-up remains the original RGBA PNG.
                image=ShipTextureSampling.load(path);
                resident.put(path,image);
                originals.put(image,path);
                peakResidentOriginalTextures=Math.max(peakResidentOriginalTextures,resident.size());
            }catch(RuntimeException failure){
                unavailable.add(path);
                Gdx.app.error("SHIP-TEXTURE","Original source art could not be uploaded: "+path,
                        failure);
                return null;
            }
        }
        lastUsedFrame.put(path,artFrame);
        return image;
    }
    private Texture texture(Battle.Faction faction,Shipyard.Hull hull) {
        var paths=assetPaths.get(faction);
        if(paths==null||hull==null)return null;
        String path=paths.get(hull);
        if(path==null)return null;
        Texture result=acquire(path);
        if(result!=null)textures.computeIfAbsent(faction,
                key->new EnumMap<>(Shipyard.Hull.class)).put(hull,result);
        return result;
    }
    /** Release every derived resource before destroying its original GL image. */
    private void evict(String path,Texture image){
        for(var fleet:textures.values())fleet.values().removeIf(t->t==image);
        bossTextures.values().removeIf(t->t==image);
        for(var it=peeled.entrySet().iterator();it.hasNext();){
            Peel peel=it.next().getValue();
            if(peel.original!=image)continue;
            if(peel.overlay!=null)peel.overlay.dispose();
            it.remove();
        }
        Texture damaged=damagedLayers.remove(image);
        if(damaged!=null)damaged.dispose();
        Texture severe=severeLayers.remove(image);
        if(severe!=null)severe.dispose();
        Texture[] shield=shieldSections.remove(image);
        if(shield!=null)for(Texture sector:shield)if(sector!=null)sector.dispose();
        Pixmap source=sourcePixels.remove(image);
        if(source!=null)source.dispose();
        alphaClouds.remove(image);
        armorAnchors.remove(image);
        surfaces.forget(image);
        originals.remove(image);
        lastUsedFrame.remove(path);
        image.dispose();
        retiredOriginalTextures++;
    }
    /** Called only after all SpriteBatches finish, never during drawing. */
    private void trimIdleShipTextures(){
        if(!boundedResidency||resident.size()<=MOBILE_MAX_SHIP_IMAGES)return;
        var iterator=resident.entrySet().iterator();
        while(resident.size()>MOBILE_MAX_SHIP_IMAGES&&iterator.hasNext()){
            var entry=iterator.next();
            // Do not force a reload every frame for fleets containing more
            // distinct hulls than the cache budget; protect recent ship draws.
            if(artFrame-lastUsedFrame.getOrDefault(entry.getKey(),artFrame)<90)continue;
            iterator.remove();
            evict(entry.getKey(),entry.getValue());
        }
    }
    /** Retired hulls still retain their authentic faction art for post-mortem shards. */
    Texture sourceTexture(Battle.Faction faction,Shipyard.Hull hull){
        return hull==null?null:texture(faction,hull);
    }
    /**
     * Only deep, connected-looking opaque source-art pixels may carry hardware.
     * Avoid the top/bottom caption/thruster bands and antialiased outer edge.
     * The alpha sampling is deterministic on every supported libGDX backend.
     */
    private AlphaCloud alphaCloud(Texture tex){
        AlphaCloud cached=alphaClouds.get(tex);
        if(cached!=null)return cached;
        int width=tex.getWidth(),height=tex.getHeight();
        int[] candidates=new int[((width+3)/4)*((height+3)/4)];
        int count=0;
        String path=originals.get(tex);
        Pixmap image=null;
        try {
            image=new Pixmap(Gdx.files.internal(path));
            int clear=Math.max(7,Math.round(height*.028f));
            int firstY=Math.max(clear,Math.round(height*.09f));
            int lastY=Math.min(height-clear,Math.round(height*.87f));
            for(int y=firstY;y<lastY;y+=4)
                for(int x=clear;x<width-clear;x+=4){
                    if((image.getPixel(x,y)&255)<210)continue;
                    // Keep the physical turret footprint on solid hull metal.
                    if((image.getPixel(x-clear,y)&255)<150
                            ||(image.getPixel(x+clear,y)&255)<150
                            ||(image.getPixel(x,y-clear)&255)<150
                            ||(image.getPixel(x,y+clear)&255)<150)continue;
                    double localX=154.0*(x+.5-width*.5)/height;
                    if(Math.abs(localX)>72)continue;
                    candidates[count++]=y*width+x;
                }
        }catch(RuntimeException error){
            if(Gdx.app!=null)Gdx.app.error("HARDPOINTS",
                    "Original ship alpha unavailable for "+path,error);
        }finally{if(image!=null)image.dispose();}
        AlphaCloud cloud=new AlphaCloud(width,height,java.util.Arrays.copyOf(candidates,count));
        alphaClouds.put(tex,cloud);
        return cloud;
    }

    /** Deterministic variant-specific offsets, not per-frame visual jitter. */
    private static int hashSlot(Battle.Faction faction,Shipyard.Hull hull,int socket){
        int key=0x6b8b4567 ^ (faction.ordinal()*0x9e3779b9)
                ^ (hull.ordinal()*0x85ebca6b) ^ (socket*0xc2b2ae35);
        key^=key>>>16;key*=0x7feb352d;key^=key>>>15;
        key*=0x846ca68b;return key^(key>>>16);
    }
    private List<Shipyard.Slot> projectOriginalArt(Battle.Faction faction,
              Shipyard.Hull hull,List<Shipyard.Slot> schematic){
        Texture tex=texture(faction,hull);
        if(tex==null)return schematic;
        AlphaCloud cloud=alphaCloud(tex);
        if(cloud.opaque().length<schematic.size())return schematic;
        var outline=HullLayouts.outline(faction,hull);
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE;
        int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        for(var point:outline){
            minX=Math.min(minX,point.x());maxX=Math.max(maxX,point.x());
            minY=Math.min(minY,point.y());maxY=Math.max(maxY,point.y());
        }
        int width=cloud.width(),height=cloud.height();
        // Hardware must not merely touch the silhouette by its centre:
        // leave enough solid plating between sockets for drawn gun footprints.
        // Large capital turrets require proportionally more room than icons.
        // The second, exact-alpha pass below handles exceptionally crowded art.
        int minimum=Math.max(17,Math.min(43,
                (int)Math.round(height*.063)));
        int minSq=minimum*minimum;
        Shipyard.Slot[] placed=new Shipyard.Slot[schematic.size()];
        java.util.List<Integer> usedPixels=new java.util.ArrayList<>();
        // Reserve the deepest, best fitting alpha-core locations for *visible*
        // barrels and launcher bodies first. Machinery and accommodation are
        // internal equipment and can occupy remaining plates afterwards.
        List<Shipyard.Slot> ordered=new java.util.ArrayList<>(schematic);
        ordered.sort(java.util.Comparator.comparingInt(slot->switch(slot.mount()){
            case WEAPON -> 0;
            case DEFENSE -> 1;
            case SUPPORT -> 2;
            case SYSTEM -> 3;
            case HABITAT -> 4;
        }));
        for(Shipyard.Slot slot:ordered){
            double nx=(slot.x()-minX)/(double)Math.max(1,maxX-minX);
            double ny=(slot.y()-minY)/(double)Math.max(1,maxY-minY);
            int random=hashSlot(faction,hull,slot.index());
            int idealX=(int)Math.round((.10+.80*nx)*(width-1))
                    +Math.floorMod(random,19)-9;
            int idealY=(int)Math.round((.10+.80*(1-ny))*(height-1))
                    +Math.floorMod(random>>>12,19)-9;
            int selected=-1;
            long nearest=Long.MAX_VALUE;
            for(int candidate:cloud.opaque()){
                int cx=candidate%width,cy=candidate/width;
                long dx=cx-idealX,dy=cy-idealY,dist=dx*dx+dy*dy;
                if(dist>=nearest)continue;
                boolean separated=true;
                for(int previous:usedPixels){
                    int px=cx-previous%width,py=cy-previous/width;
                    if(px*px+py*py<minSq){separated=false;break;}
                }
                if(separated){selected=candidate;nearest=dist;}
            }
            // If unusually dense, reduce separation before giving up a socket.
            if(selected<0){
                for(int candidate:cloud.opaque()){
                    if(usedPixels.contains(candidate))continue;
                    int cx=candidate%width,cy=candidate/width;
                    long dx=cx-idealX,dy=cy-idealY,d=dx*dx+dy*dy;
                    if(d<nearest){nearest=d;selected=candidate;}
                }
            }
            if(selected<0)return schematic;
            usedPixels.add(selected);
            int px=selected%width,py=selected/width;
            int localX=(int)Math.round(154.0*(px+.5-width*.5)/height);
            int localY=(int)Math.round(154.0*(height*.5-py-.5)/height);
            placed[slot.index()]=new Shipyard.Slot(
                    slot.index(),slot.mount(),localX,localY);
        }
        return java.util.List.of(placed);
    }

    /**
     * The preview and combat both read precisely the same snapped sockets.
     * Their local coordinates are derived from the *actual* PNG aspect ratio:
     * battle height = 34*scale and width = height*pngWidth/pngHeight.
     */
    private ArtAnchor[] buildShipyardAnchors(Battle.Faction faction,Shipyard.Hull hull) {
        Texture tex=texture(faction,hull);
        if(tex==null)return new ArtAnchor[0];
        var slots=HullLayouts.slots(faction,hull);
        ArtAnchor[] result=new ArtAnchor[slots.size()];
        for(var socket:slots){
            float u=.5f+socket.x()*tex.getHeight()/(154f*tex.getWidth());
            float v=.5f+socket.y()/154f;
            result[socket.index()]=new ArtAnchor(u,v);
        }
        return result;
    }

    private ArtAnchor shipyardAnchor(Battle.Faction faction,Shipyard.Hull hull,int socket) {
        if(!hasHull(faction,hull))return null;
        ArtAnchor[] anchors=shipyardAnchors.computeIfAbsent(
                faction.name()+"/"+hull.name(),key->buildShipyardAnchors(faction,hull));
        return socket<0||socket>=anchors.length?null:anchors[socket];
    }

    private static float previewScale(Texture tex,float maxWidth,float maxHeight){
        return Math.min(maxWidth/tex.getWidth(),maxHeight/tex.getHeight());
    }

    /** Same exact sprite-space mapping for drawn dots and Android/desktop taps. */
    float previewMountX(Battle.Faction faction,Shipyard.Hull hull,int socket,
                        float centerX,float maxWidth,float maxHeight) {
        ArtAnchor anchor=shipyardAnchor(faction,hull,socket);
        if(anchor==null)return centerX;
        Texture tex=texture(faction,hull);
        return centerX+(anchor.u()-.5f)*tex.getWidth()*previewScale(tex,maxWidth,maxHeight);
    }
    float previewMountY(Battle.Faction faction,Shipyard.Hull hull,int socket,
                        float centerY,float maxWidth,float maxHeight) {
        ArtAnchor anchor=shipyardAnchor(faction,hull,socket);
        if(anchor==null)return centerY;
        Texture tex=texture(faction,hull);
        return centerY+(anchor.v()-.5f)*tex.getHeight()*previewScale(tex,maxWidth,maxHeight);
    }

    /** Approved source artwork is the primary fitting canvas. */
    public boolean hasHull(Battle.Faction faction, Shipyard.Hull hull) {
        var paths=assetPaths.get(faction);
        return hull!=null&&paths!=null&&paths.containsKey(hull);
    }
    public boolean drawPreview(SpriteBatch batch,Battle.Faction faction,Shipyard.Hull hull,
                               float centerX,float centerY,float maxWidth,float maxHeight) {
        if(!hasHull(faction,hull))return false;
        Texture texture=texture(faction,hull);
        // An optional failed GL upload must not crash the shipyard itself.
        if(texture==null)return false;
        float factor=previewScale(texture,maxWidth,maxHeight);
        float w=texture.getWidth()*factor,h=texture.getHeight()*factor;
        batch.setColor(1,1,1,0.94f);
        batch.draw(texture,centerX-w/2,centerY-h/2,w,h);
        batch.setColor(1,1,1,1);
        return true;
    }
    private Texture texture(Battle.Ship ship){
        if(ship==null)return null;
        Texture override=ship.campaignBossArt==null?null:bossTextures.get(ship.campaignBossArt);
        if(override==null&&"warrant_spire".equals(ship.campaignBossArt) &&
                Gdx.files.internal("ships/bosses/warrant_spire.png").exists()){
            override=acquire("ships/bosses/warrant_spire.png");
            if(override!=null)bossTextures.put("warrant_spire",override);
        }
        return override!=null?override:ship.hullClass==null?null:texture(ship.faction,ship.hullClass);
    }
    /** True only when this living vessel has an actual image, never for unapproved alien sheets. */
    public boolean hasSprite(Battle.Ship ship) {
        return ship != null && ship.alive() && texture(ship)!=null;
    }
    public void render(Battle battle,SpriteBatch batch) {render(battle,batch,null,null);}
    public void render(Battle battle,SpriteBatch batch,BattleRenderPoses poses) {
        render(battle,batch,poses,null);
    }
    public void render(Battle battle,SpriteBatch batch,BattleRenderPoses poses,
                       CombatViewBounds view) {
        render(battle,batch,poses,view,null);
    }
    public void render(Battle battle,SpriteBatch batch,BattleRenderPoses poses,
                       CombatViewBounds view,ShieldFxTimeline shieldFx) {
        if(assetPaths.isEmpty())return;
        artFrame++;
        if(battle!=activeBattle){resetPeelCache();activeBattle=battle;}
        // Upload damage masks outside the SpriteBatch; only changed quantized
        // armor cells rebuild. One damaged ship draws two hull quads, not 256.
        retainedDamageIds.clear();
        for(Battle.Ship ship:battle.ships())if(hasSprite(ship)){
            boolean damaged=breached(ship);
            if(damaged)retainedDamageIds.add(ship.id);
            BattleRenderPoses.Pose position=poses==null?
                    new BattleRenderPoses.Pose((float)ship.x,(float)ship.y,(float)ship.facing):
                    poses.ship(ship);
            if(view!=null&&!view.ship(position,ship.visualScale()))continue;
            Texture tex=texture(ship);
            if(!ShipSurfaceLighting.flatAt(view))
                surfaces.prepare(tex,originals.get(tex));
            if(ship.shields>0){
                prepareShields(tex);
                prepareShieldShell();
            }
            if(damaged){
                exposedInterior(tex,ship.hull<ship.maxHull*0.30);
                erodedExterior(ship,tex);
            }
        }
        // Prune only truly repaired/dead/missing ships. Off-screen vessels
        // retain their masks so a camera pan cannot cause texture thrashing.
        retireUnusedPeels();
        surfaces.begin(batch,battle,view);
        for(Battle.Ship ship:battle.ships()) {
            if(!hasSprite(ship))continue;
            Texture tex=texture(ship);
            if(tex==null)continue;
            float height=(float)(34*ship.visualScale());
            float width=height*tex.getWidth()/(float)tex.getHeight();
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)ship.x,(float)ship.y,(float)ship.facing):
                    poses.ship(ship);
            if(view!=null&&!view.ship(pose,ship.visualScale()))continue;
            float rotation=(float)(StrictMath.toDegrees(pose.angle())-90);
            surfaces.bind(batch,tex);
            surfaces.color(batch,rotation,(float)Math.max(0,
                    Math.min(1,ship.hull/Math.max(1,ship.maxHull))),
                    DeceptionProjection.hullOpacity(ship,battle.tick));
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
        // Active holographic ECM displays false electronic contacts using
        // transparent copies of the EXACT source-art hull. These are never
        // second physical ships and receive no damage/shields/hardpoints.
        renderHolographicContacts(battle,batch,poses,view);
        // Cached source-art hex textures are recolored using per-vertex RGB:
        // one shader for all factions, generators and independent facings.
        batch.setShader(shieldInk==null?null:shieldInk.program());
        batch.begin();
        if(shieldInk!=null)shieldInk.configure(false,battle.tick);
        for(Battle.Ship ship:battle.ships()){
            if(!hasSprite(ship)||ship.shields<=0)continue;
            Texture tex=texture(ship);
            BattleRenderPoses.Pose pose=poses==null?
                    new BattleRenderPoses.Pose((float)ship.x,(float)ship.y,(float)ship.facing):
                    poses.ship(ship);
            if(view!=null&&!view.ship(pose,ship.visualScale()))continue;
            float height=(float)(34*ship.visualScale());
            float width=height*tex.getWidth()/(float)tex.getHeight();
            if(!ShieldSurfaceModel.drawable(height,view==null?1280: view.right-view.left))
                continue;
            drawShield(batch,ship,tex,pose,width,height,
                    (float)(StrictMath.toDegrees(pose.angle())-90),battle.tick,shieldFx);
        }
        // A collapsed mechanical sector dissipates a last, red, ALPHA-MASKED
        // copy of the original art rather than a fabricated solid ring.
        if(shieldFx!=null && shieldShell!=null)
            for(ShieldFxTimeline.Effect fx:shieldFx.events()){
                if(fx.kind!=ShieldFxTimeline.Kind.COLLAPSE||!hasSprite(fx.ship)
                        ||fx.ship.defense.shields(fx.facing)>.015)continue;
                BattleRenderPoses.Pose pose=poses==null
                        ?new BattleRenderPoses.Pose((float)fx.ship.x,(float)fx.ship.y,(float)fx.ship.facing)
                        :poses.ship(fx.ship);
                if(view!=null&&!view.ship(pose,fx.ship.visualScale()))continue;
                float height=(float)(34*fx.ship.visualScale());
                if(!ShieldSurfaceModel.drawable(height,view==null?1280:view.right-view.left))
                    continue;
                Texture tex=texture(fx.ship);
                if(tex==null)continue;
                float width=height*tex.getWidth()/(float)tex.getHeight();
                float fade=1f-fx.progress();
                batch.setColor(1f,.065f,.04f,.55f*fade*fade);
                drawWhole(batch,shieldShell[fx.facing.ordinal()],pose,
                        width*1.31f,height*1.27f,
                        (float)(StrictMath.toDegrees(pose.angle())-90));
            }
        batch.setColor(1,1,1,1);
        batch.end();
        // Recharge/restart scan over exactly the same source-art sector textures
        // in a separate, additive, grouped pass. No per-ship textures or uniforms.
        if(shieldFx!=null && shieldShell!=null){
            batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                    com.badlogic.gdx.graphics.GL20.GL_ONE);
            batch.begin();
            if(shieldInk!=null)shieldInk.configure(true,battle.tick);
            for(Battle.Ship ship:battle.ships()){
                if(!hasSprite(ship)||ship.shields<=0)continue;
                BattleRenderPoses.Pose pose=poses==null
                        ?new BattleRenderPoses.Pose((float)ship.x,(float)ship.y,(float)ship.facing)
                        :poses.ship(ship);
                if(view!=null&&!view.ship(pose,ship.visualScale()))continue;
                Texture tex=texture(ship);
                float height=(float)(34*ship.visualScale());
                if(!ShieldSurfaceModel.drawable(height,view==null?1280:view.right-view.left))
                    continue;
                float width=height*tex.getWidth()/(float)tex.getHeight();
                Texture[] sectors=shieldSections.get(tex);
                if(sectors==null)continue;
                float rotation=(float)(StrictMath.toDegrees(pose.angle())-90);
                for(DirectionalDefense.Facing face:DirectionalDefense.Facing.values()){
                    float recharge=shieldFx.recharge(ship.id,face);
                    double max=ship.defense.maxShields(face);
                    if(recharge<=.001f||max<=0)continue;
                    float fraction=(float)(ship.defense.shields(face)/max);
                    if(fraction<=.003f)continue;
                    ShieldSurfaceModel.Style type=ShieldSurfaceModel.style(ship.shieldFieldBranch);
                    batch.setColor(ShieldSurfaceModel.channel(type,fraction,0),
                            ShieldSurfaceModel.channel(type,fraction,1),
                            ShieldSurfaceModel.channel(type,fraction,2),
                            Math.min(.48f,.34f*recharge));
                    drawWhole(batch,shieldShell[face.ordinal()],pose,
                            width*1.31f,height*1.27f,rotation);
                    drawWhole(batch,sectors[face.ordinal()],pose,width,height,rotation);
                }
            }
            batch.setColor(1,1,1,1);
            batch.end();
            batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                    com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        batch.setShader(null);
        trimIdleShipTextures();
    }
    private void renderHolographicContacts(Battle battle,SpriteBatch batch,
                                           BattleRenderPoses poses,CombatViewBounds view){
        boolean drawing=false;
        for(Battle.Ship ship:battle.ships()){
            int contacts=DeceptionProjection.falseContacts(ship);
            if(contacts==0)continue;
            Texture original=texture(ship);
            if(original==null)continue;
            BattleRenderPoses.Pose actual=poses==null
                    ?new BattleRenderPoses.Pose((float)ship.x,(float)ship.y,(float)ship.facing)
                    :poses.ship(ship);
            float height=(float)(34*ship.visualScale());
            float width=height*original.getWidth()/(float)original.getHeight();
            for(int contact=0;contact<contacts;contact++){
                float x=actual.x()+(float)(DeceptionProjection.ghostX(ship,contact,battle.tick)-ship.x);
                float y=actual.y()+(float)(DeceptionProjection.ghostY(ship,contact,battle.tick)-ship.y);
                BattleRenderPoses.Pose phantom=new BattleRenderPoses.Pose(x,y,actual.angle());
                if(view!=null&&!view.ship(phantom,ship.visualScale()))continue;
                if(!drawing){batch.setShader(null);batch.begin();drawing=true;}
                batch.setColor(.31f,.78f,1f,.21f);
                drawWhole(batch,original,phantom,width*.96f,height*.96f,
                        (float)(StrictMath.toDegrees(phantom.angle())-90));
            }
        }
        if(drawing){batch.setColor(1,1,1,1);batch.end();}
    }
    private void drawShield(SpriteBatch batch,Battle.Ship ship,Texture original,
                            BattleRenderPoses.Pose pose,float width,float height,
                            float rotation,long tick,ShieldFxTimeline shieldFx) {
        if(ship.maxShields<=0||ship.shields<=0)return;
        Texture[] sectors=shieldSections.get(original);
        if(sectors==null)throw new IllegalStateException("Missing actual shield texture overlay");
        for(DirectionalDefense.Facing face:DirectionalDefense.Facing.values()){
            double maximum=ship.defense.maxShields(face);
            if(maximum<=0)continue;
            float strength=(float)Math.min(1,ship.defense.shields(face)/maximum);
            float opacity=ShieldSurfaceModel.opacity(strength,tick,ship.id,face.ordinal());
            if(shieldFx!=null)opacity=shieldFx.opacity(ship,face,opacity);
            if(opacity<=0f)continue;
            ShieldSurfaceModel.Style type=ShieldSurfaceModel.style(ship.shieldFieldBranch);
            float red=ShieldSurfaceModel.channel(type,strength,0);
            float green=ShieldSurfaceModel.channel(type,strength,1);
            float blue=ShieldSurfaceModel.channel(type,strength,2);
            // The outer field is the actual blue hex-energy sprite, NOT a
            // procedural ellipse. Geometry only masks its outside pixels and
            // chooses the live powered face; the original texture is preserved.
            if(shieldShell!=null){
                batch.setColor(red,green,blue,Math.min(.85f,opacity*1.35f));
                drawWhole(batch,shieldShell[face.ordinal()],pose,
                        width*1.31f,height*1.27f,rotation);
            }
            // Keep the existing on-hull material for shield contact feedback.
            batch.setColor(red,green,blue,opacity);
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
        HullLayouts.useSpriteProjector(null);
        shipyardAnchors.clear();alphaClouds.clear();
        java.util.Set<Texture> disposed=java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Texture,Boolean>());
        for(var fleet:textures.values())
            for(Texture image:fleet.values())if(disposed.add(image))image.dispose();
        textures.clear();
        for(Texture image:bossTextures.values())if(disposed.add(image))image.dispose();
        bossTextures.clear();
        for(Texture image:resident.values())if(disposed.add(image))image.dispose();
        resident.clear();lastUsedFrame.clear();assetPaths.clear();unavailable.clear();
        resetPeelCache();activeBattle=null;
        for(Texture[] sections:shieldSections.values())
            for(Texture sector:sections)if(sector!=null)sector.dispose();
        shieldSections.clear();
        if(shieldShell!=null){
            for(Texture sector:shieldShell)if(sector!=null)sector.dispose();
            shieldShell=null;
        }
        for(Pixmap pixels:sourcePixels.values())pixels.dispose();
        sourcePixels.clear();
        armorAnchors.clear();
        for(Texture image:damagedLayers.values())image.dispose();
        for(Texture image:severeLayers.values())image.dispose();
        damagedLayers.clear();severeLayers.clear();originals.clear();
        surfaces.dispose();
        if(shieldInk!=null)shieldInk.dispose();
        damagedMaterial.dispose();
        destroyedMaterial.dispose();
        shieldMaterial.dispose();
    }
}
