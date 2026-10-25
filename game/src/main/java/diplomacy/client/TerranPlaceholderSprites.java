package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.Battle;
import diplomacy.sim.DirectionalDefense;
import java.util.EnumMap;

/**
 * First Terran concept fleet atlas, with a generic exposed internal hull beneath.
 * Battle never depends on sprite pixels; tile transparency reflects core simulation.
 */
public final class TerranPlaceholderSprites implements Disposable {
    private static final int GRID=DirectionalDefense.GRID;
    private final EnumMap<Shipyard.Hull,Texture> textures = new EnumMap<>(Shipyard.Hull.class);
    private final Texture innerHull;
    private static String spriteName(Shipyard.Hull hull) {
        return switch(hull) {
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
        };
    }
    public TerranPlaceholderSprites() {
        innerHull=createGenericStructure();
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
    /** Procedural generic plating, lattice ribs and longitudinal inner structure. */
    private static Texture createGenericStructure() {
        Pixmap pix=new Pixmap(64,96,Pixmap.Format.RGBA8888);
        pix.setColor(0,0,0,0);pix.fill();
        for(int y=0;y<96;y++)for(int x=0;x<64;x++) {
            double lx=(48-y)/48.0,ly=(32-x)/32.0;
            if(lx*lx+ly*ly/0.61>1)continue;
            boolean rib=y%13<3,rail=x%15<3;
            boolean conduit=StrictMath.abs(x-32)<4;
            int shade=rib?105:rail?86:conduit?148:50+((x*17+y*7)%24);
            pix.setColor(shade/255f,(shade+6)/255f,(shade+14)/255f,1);
            pix.drawPixel(x,y);
        }
        Texture result=new Texture(pix);
        result.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        pix.dispose();
        return result;
    }
    public int loaded() { return textures.size(); }
    /** True only when this living vessel has an actual image, never for unapproved alien sheets. */
    public boolean hasSprite(Battle.Ship ship) {
        return ship != null && ship.alive() && ship.faction == Battle.Faction.TERRAN
                && ship.hullClass != null && textures.containsKey(ship.hullClass);
    }
    public void render(Battle battle,SpriteBatch batch) {
        if(textures.isEmpty())return;
        batch.begin();
        for(Battle.Ship ship:battle.ships()) {
            if(!hasSprite(ship))continue;
            Texture tex=textures.get(ship.hullClass);
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
                batch.setColor(1,1,1,1);
                drawWhole(batch,tex,ship,width,height,rotation);
                continue;
            }
            batch.setColor(1,1,1,1);
            drawWhole(batch,innerHull,ship,width,height,rotation);
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
        }
        batch.setColor(1,1,1,1);
        batch.end();
    }
    private static void drawWhole(SpriteBatch batch,Texture tex,Battle.Ship ship,
                                  float width,float height,float rotation) {
        batch.draw(tex,(float)ship.x-width/2,(float)ship.y-height/2,
                width/2,height/2,width,height,1,1,rotation,
                0,0,tex.getWidth(),tex.getHeight(),false,false);
    }
    @Override public void dispose() {
        for(Texture texture:textures.values())texture.dispose();
        textures.clear();
        innerHull.dispose();
    }
}
