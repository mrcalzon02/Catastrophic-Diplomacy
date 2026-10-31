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
import java.util.EnumMap;

/**
 * First Terran concept fleet atlas, with a generic exposed internal hull beneath.
 * Battle never depends on sprite pixels; tile transparency reflects core simulation.
 */
public final class TerranPlaceholderSprites implements Disposable {
    private static final int GRID=DirectionalDefense.GRID;
    private final EnumMap<Battle.Faction,EnumMap<Shipyard.Hull,Texture>> textures=
            new EnumMap<>(Battle.Faction.class);
    /** Campaign flagships use separate art, never overwrite faction hulls. */
    private final java.util.Map<String,Texture> bossTextures=new java.util.HashMap<>();
    // Shipyard hardpoints are aligned once to each approved transparent PNG.
    // Cached normalized anchors are shared by mouse, touch and rendering.
    record ArtAnchor(float u,float v) {}
    private final java.util.Map<String,ArtAnchor[]> shipyardAnchors=new java.util.HashMap<>();

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
        // Optional dedicated flagship art: load once; all weapons remain the
        // actual fitted modules and are rendered separately by the game.
        String bossPath="ships/bosses/warrant_spire.png";
        if(Gdx.files.internal(bossPath).exists()){
            try{
                Texture boss=new Texture(Gdx.files.internal(bossPath));
                boss.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
                bossTextures.put("warrant_spire",boss);
                originals.put(boss,bossPath);
            }catch(RuntimeException error){
                Gdx.app.error("SPRITES","Unable to load Warrant Spire sprite: "+bossPath,error);
            }
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
        Pixmap source=sourcePixels(original);
        // Snapshot only 256 actual protection cells once per changed vessel.
        // Avoid dereferencing defenses and recomputing integrity for every
        // mask pixel (and all 9 neighboring tiles) in mass engagements.
        float[][] wear=new float[GRID][GRID],internal=new float[GRID][GRID];
        for(int r=0;r<GRID;r++)for(int c=0;c<GRID;c++){
            var tile=ship.defense.tile(r,c);
            if(!tile.occupied)continue;
            wear[r][c]=(float)tile.exposure();
            internal[r][c]=(float)tile.internalDamage();
        }
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
                // Wear darkens source plating; only localized physically
                // damaged cells cut alpha. Neighbor lookups catch crater lips.
                float damage=surfaceDamage(wear,tileRow,tileCol);
                float crater=0f;
                int centerR=(int)StrictMath.floor(tileRow+.5);
                int centerC=(int)StrictMath.floor(tileCol+.5);
                for(int r=Math.max(0,centerR-1);r<=Math.min(GRID-1,centerR+1);r++)
                    for(int c=Math.max(0,centerC-1);c<=Math.min(GRID-1,centerC+1);c++){
                        if(wear[r][c]<=.70f&&internal[r][c]<=.00001f)continue;
                        crater=Math.max(crater,ShipArmorStamps.openingAt(
                                (float)tileRow,(float)tileCol,r,c,
                                wear[r][c],internal[r][c],ship.id));
                    }
                float rough=noise(x/4,y/4,ship.id)*0.70f+
                        noise(x,y,ship.id*31)*0.30f;
                output.drawPixel(x,y,ShipSurfaceDamageVisual.stampedPixel(
                        originalPixel,damage,crater,rough));
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
    /**
     * Physical-looking fitting anchors for the original faction sprite, not a
     * replacement drawing. Map the stable HullLayouts socket positions into
     * the source PNG and snap each to the nearest opaque hull-interior sample.
     * Source image coordinates run downward; shipyard coordinates run upward.
     * The PNG is decoded only on first use for a particular faction/class.
     */
    private ArtAnchor[] buildShipyardAnchors(Battle.Faction faction,Shipyard.Hull hull) {
        Texture tex=texture(faction,hull);
        if(tex==null)return new ArtAnchor[0];
        var outline=HullLayouts.outline(faction,hull);
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE;
        int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        for(var point:outline){
            minX=Math.min(minX,point.x());maxX=Math.max(maxX,point.x());
            minY=Math.min(minY,point.y());maxY=Math.max(maxY,point.y());
        }
        int width=tex.getWidth(),height=tex.getHeight();
        var sockets=HullLayouts.slots(faction,hull);
        ArtAnchor[] result=new ArtAnchor[sockets.size()];
        // Precompute opaque core samples once, rather than reading every pixel
        // of a source PNG for every socket, or creating a GPU texture per socket.
        int[] candidates=new int[((width+3)/4)*((height+3)/4)];
        int count=0;
        Pixmap image=null;
        try {
            image=new Pixmap(Gdx.files.internal(originals.get(tex)));
            for(int y=8;y<height-8;y+=4)for(int x=8;x<width-8;x+=4){
                if((image.getPixel(x,y)&255)<180)continue;
                // Avoid anti-aliased transparent edges and detached exhaust.
                if((image.getPixel(x-6,y)&255)<70 ||
                   (image.getPixel(x+6,y)&255)<70 ||
                   (image.getPixel(x,y-6)&255)<70 ||
                   (image.getPixel(x,y+6)&255)<70)continue;
                candidates[count++]=y*width+x;
            }
        } catch(RuntimeException ex) {
            if(Gdx.app!=null)Gdx.app.error("SHIPYARD",
                    "Could not scan sprite mount anchors: "+faction+"/"+hull,ex);
        } finally {
            if(image!=null)image.dispose();
        }
        for(var socket:sockets){
            float u=(socket.x()-minX)/(float)Math.max(1,maxX-minX);
            float v=(socket.y()-minY)/(float)Math.max(1,maxY-minY);
            // Reserve 8% edge clearance inside the approved PNG crop.
            int idealX=Math.round((.08f+.84f*u)*(width-1));
            int idealY=Math.round((.08f+.84f*(1-v))*(height-1));
            int pickedX=idealX,pickedY=idealY;
            long best=Long.MAX_VALUE;
            for(int n=0;n<count;n++){
                int x=candidates[n]%width,y=candidates[n]/width;
                long dx=x-idealX,dy=y-idealY,d2=dx*dx+dy*dy;
                if(d2<best){best=d2;pickedX=x;pickedY=y;}
            }
            result[socket.index()]=new ArtAnchor(
                    (pickedX+.5f)/width,1f-(pickedY+.5f)/height);
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
        return texture(faction,hull)!=null;
    }
    public boolean drawPreview(SpriteBatch batch,Battle.Faction faction,Shipyard.Hull hull,
                               float centerX,float centerY,float maxWidth,float maxHeight) {
        if(!hasHull(faction,hull))return false;
        Texture texture=texture(faction,hull);
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
        if(textures.isEmpty())return;
        if(battle!=activeBattle){resetPeelCache();activeBattle=battle;}
        // Upload damage masks outside the SpriteBatch; only changed quantized
        // armor cells rebuild. One damaged ship draws two hull quads, not 256.
        for(Battle.Ship ship:battle.ships())if(hasSprite(ship)){
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
            if(breached(ship)){
                exposedInterior(tex,ship.hull<ship.maxHull*0.30);
                erodedExterior(ship,tex);
            }
        }
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
        java.util.Set<Texture> disposed=java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Texture,Boolean>());
        for(var fleet:textures.values())
            for(Texture image:fleet.values())if(disposed.add(image))image.dispose();
        textures.clear();
        for(Texture image:bossTextures.values())if(disposed.add(image))image.dispose();
        bossTextures.clear();
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
