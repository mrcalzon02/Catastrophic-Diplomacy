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
    private final Pixmap damagedMaterial, destroyedMaterial;
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
        // Legacy factions share the approved source images with their derivative
        // civilizations. Reuse GPU textures by full asset path, not by faction.
        java.util.Map<String,Texture> uniqueTextures=new java.util.HashMap<>();
        for(Battle.Faction faction:Battle.Faction.values()){
            EnumMap<Shipyard.Hull,Texture> fleet=new EnumMap<>(Shipyard.Hull.class);
            for(Shipyard.Hull hull:Shipyard.Hull.values()){
                String file=faction==Battle.Faction.TERRAN
                        ?"ships/terran/"+spriteName(hull)+".png"
                        :FactionArt.runtimePath(faction,hull);
                if(file==null||!Gdx.files.internal(file).exists())continue;
                // Carrier/dreadnought share one atlas ship until an eleventh
                // capital image exists. Avoid loading duplicate GPU textures.
                if(hull.spriteHull()==Shipyard.Hull.DREADNOUGHT &&
                        fleet.containsKey(Shipyard.Hull.CARRIER)){
                    fleet.put(hull,fleet.get(Shipyard.Hull.CARRIER));
                    continue;
                }
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
            for(Shipyard.Hull hull:Shipyard.Hull.values())
                if(texture(faction,hull)==null)
                    throw new IllegalStateException(
                            "Approved combat ship sprite missing from packaged assets: "+
                            faction+"/"+hull);
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

    public int loaded() {
        int count=0;
        for(var fleet:textures.values())count+=fleet.size();
        return count;
    }
    private Texture texture(Battle.Faction faction,Shipyard.Hull hull) {
        var fleet=textures.get(faction);
        return fleet==null?null:fleet.get(hull);
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
    public void render(Battle battle,SpriteBatch batch) {
        if(textures.isEmpty())return;
        // Texture uploads must complete OUTSIDE SpriteBatch's active GL draw.
        // Most undamaged ships never allocate a second texture.
        for(Battle.Ship ship:battle.ships())if(hasSprite(ship)&&breached(ship))
            exposedInterior(texture(ship.faction,ship.hullClass),
                    ship.hull<ship.maxHull*0.30);
        batch.begin();
        for(Battle.Ship ship:battle.ships()) {
            if(!hasSprite(ship))continue;
            Texture tex=texture(ship.faction,ship.hullClass);
            if(tex==null)continue;
            float height=(float)(34*ship.visualScale());
            float width=height*tex.getWidth()/(float)tex.getHeight();
            float rotation=(float)(StrictMath.toDegrees(ship.facing)-90);
            boolean damaged=breached(ship);
            if(!damaged) {
                // Ship PNGs already contain the approved civilization palette.
                // Multiply by white: class tint belongs to geometry fallback only.
                batch.setColor(1,1,1,1);
                drawWhole(batch,tex,ship,width,height,rotation);
                drawShield(batch,ship,tex,width,height,rotation);
                continue;
            }
            batch.setColor(1,1,1,1);
            // The understructure uses the ORIGINAL sprite's alpha/UV mask.
            // Damaged hulls never become opaque gray ovals.
            drawWhole(batch,exposedInterior(tex,ship.hull<ship.maxHull*0.30),
                    ship,width,height,rotation);
            // One shared UV/grid layout for exterior and internal structure.
            // Only breached ships need the more expensive 16x16 texture draws.
            final float radians=(float)StrictMath.toRadians(rotation);
            final float cos=(float)StrictMath.cos(radians),sin=(float)StrictMath.sin(radians);
            for(int row=0;row<GRID;row++)for(int col=0;col<GRID;col++) {
                var tile=ship.defense.tile(row,col);
                if(!tile.occupied || tile.exposure()>=0.999)continue;
                int sx=col*tex.getWidth()/GRID,ex=(col+1)*tex.getWidth()/GRID;
                int sy=row*tex.getHeight()/GRID,ey=(row+1)*tex.getHeight()/GRID;
                float tileW=width*(ex-sx)/tex.getWidth();
                float tileH=height*(ey-sy)/tex.getHeight();
                float offX=width*((sx+ex)*0.5f/tex.getWidth()-0.5f);
                float offY=height*(0.5f-(sy+ey)*0.5f/tex.getHeight());
                float midX=(float)ship.x+offX*cos-offY*sin;
                float midY=(float)ship.y+offX*sin+offY*cos;
                batch.setColor(1,1,1,(float)(1-tile.exposure()));
                batch.draw(tex,midX-tileW/2,midY-tileH/2,
                        tileW/2,tileH/2,tileW,tileH,1,1,rotation,
                        sx,sy,ex-sx,ey-sy,false,false);
            }
            drawShield(batch,ship,tex,width,height,rotation);
        }
        batch.setColor(1,1,1,1);
        batch.end();
    }
    private void drawShield(SpriteBatch batch,Battle.Ship ship,Texture original,
                            float width,float height,float rotation) {
        if(ship.maxShields<=0 || ship.shields<=0)return;
        float strength=(float)Math.min(1,ship.shields/ship.maxShields);
        // Shield sheen also follows the true hull mask, not a stretched oval.
        batch.setColor(0.70f,0.88f,1f,0.10f*strength);
        drawWhole(batch,original,ship,width,height,rotation);
        batch.setColor(1,1,1,1);
    }
    private static void drawWhole(SpriteBatch batch,Texture tex,Battle.Ship ship,
                                  float width,float height,float rotation) {
        batch.draw(tex,(float)ship.x-width/2,(float)ship.y-height/2,
                width/2,height/2,width,height,1,1,rotation,
                0,0,tex.getWidth(),tex.getHeight(),false,false);
    }
    @Override public void dispose() {
        java.util.Set<Texture> disposed=java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<Texture,Boolean>());
        for(var fleet:textures.values())
            for(Texture image:fleet.values())if(disposed.add(image))image.dispose();
        textures.clear();
        for(Texture image:damagedLayers.values())image.dispose();
        for(Texture image:severeLayers.values())image.dispose();
        damagedLayers.clear();severeLayers.clear();originals.clear();
        damagedMaterial.dispose();
        destroyedMaterial.dispose();
    }
}
