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
    private final Texture innerHull;
    private final Texture severeHull;
    private final Texture shieldHull;
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
        innerHull=createGenericStructure("materials/structure_damaged.png");
        severeHull=createGenericStructure("materials/structure_destroyed.png");
        shieldHull=Gdx.files.internal("materials/shield_energy_blue.png").exists()
                ? createGenericStructure("materials/shield_energy_blue.png") : null;
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
                    }
                    fleet.put(hull,tex);
                }catch(RuntimeException ex){
                    Gdx.app.error("SPRITES","Unable to load selected faction sprite: "+file,ex);
                }
            }
            if(!fleet.isEmpty())textures.put(faction,fleet);
        }
    }
    /** Procedural generic plating, lattice ribs and longitudinal inner structure. */
    private static Texture createGenericStructure(String sourcePath) {
        Pixmap pix=new Pixmap(64,96,Pixmap.Format.RGBA8888);
        Pixmap source=null;
        if(Gdx.files.internal(sourcePath).exists()){
            try{source=new Pixmap(Gdx.files.internal(sourcePath));}
            catch(RuntimeException bad){Gdx.app.error("MATERIAL","Falling back from "+sourcePath,bad);}
        }
        pix.setColor(0,0,0,0);pix.fill();
        for(int y=0;y<96;y++)for(int x=0;x<64;x++) {
            double lx=(48-y)/48.0,ly=(32-x)/32.0;
            if(lx*lx+ly*ly/0.61>1)continue;
            if(source!=null){
                pix.drawPixel(x,y,source.getPixel(
                        x*source.getWidth()/64,y*source.getHeight()/96));
                continue;
            }
            boolean rib=y%13<3,rail=x%15<3;
            boolean conduit=StrictMath.abs(x-32)<4;
            int shade=rib?105:rail?86:conduit?148:50+((x*17+y*7)%24);
            pix.setColor(shade/255f,(shade+6)/255f,(shade+14)/255f,1);
            pix.drawPixel(x,y);
        }
        Texture result=new Texture(pix);
        result.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        pix.dispose();
        if(source!=null)source.dispose();
        return result;
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
        batch.begin();
        for(Battle.Ship ship:battle.ships()) {
            if(!hasSprite(ship))continue;
            Texture tex=texture(ship.faction,ship.hullClass);
            if(tex==null)continue;
            float height=(float)(34*ship.visualScale());
            float width=height*tex.getWidth()/(float)tex.getHeight();
            float rotation=(float)(StrictMath.toDegrees(ship.facing)-90);
            boolean damaged=false;
            for(int row=0;row<GRID&&!damaged;row++)for(int col=0;col<GRID;col++) {
                var tile=ship.defense.tile(row,col);
                if(tile.occupied && tile.exposure()>0.001) {damaged=true;break;}
            }
            if(!damaged) {
                // Ship PNGs already contain the approved civilization palette.
                // Multiply by white: class tint belongs to geometry fallback only.
                batch.setColor(1,1,1,1);
                drawWhole(batch,tex,ship,width,height,rotation);
                drawShield(batch,ship,width,height,rotation);
                continue;
            }
            batch.setColor(1,1,1,1);
            drawWhole(batch,ship.hull<ship.maxHull*0.30?severeHull:innerHull,
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
            drawShield(batch,ship,width,height,rotation);
        }
        batch.setColor(1,1,1,1);
        batch.end();
    }
    private void drawShield(SpriteBatch batch,Battle.Ship ship,
                            float width,float height,float rotation) {
        if(shieldHull==null || ship.maxShields<=0 || ship.shields<=0)return;
        float strength=(float)Math.min(1,ship.shields/ship.maxShields);
        batch.setColor(0.70f,0.88f,1f,0.11f*strength);
        drawWhole(batch,shieldHull,ship,width*1.14f,height*1.14f,rotation);
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
        innerHull.dispose();
        severeHull.dispose();
        if(shieldHull!=null)shieldHull.dispose();
    }
}
