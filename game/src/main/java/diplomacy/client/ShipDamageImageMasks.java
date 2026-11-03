package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import diplomacy.sim.Battle;
import java.util.Locale;

/**
 * Reads individually authored, FIXED 24x24 RGBA damage decals.
 * Asset: damage_masks/<weapon>.png — 6 adjacent 24x24 frames, each a
 * different hand-shaped wound. No shape synthesis, rotation, per-pixel
 * noise, or stand-in sprite is used in the live renderer.
 *
 * Channels: R soot, G residual heat, B exposed metal/laser oxide;
 * A is the intact exterior coefficient (255 intact, 0 fully missing).
 * A wound is applied ONLY around one registered 16x16 defense tile,
 * never scaled to fill the ship.
 */
final class ShipDamageImageMasks {
    static final int SIZE=24, COUNT=6, MASK_RADIUS=12;
    // Radius: the actual 24 pixels project onto 2.667 defense cells
    // total diameter. In the 96x144 per-ship compositing mask that means
    // no more than 16x24 of its pixels per isolated impact.
    private static final float PIXELS_PER_ARMOR_TILE=9f;
    private final int[][] atlases=new int[Battle.DamageType.values().length][];

    static int pick(int seed,Battle.DamageType kind){
        return ShipDamageVariants.variant(seed,kind);
    }

    private int[] atlas(Battle.DamageType kind){
        int k=kind.ordinal();
        int[] cached=atlases[k];
        if(cached!=null)return cached;
        String path="damage_masks/"+
                kind.name().toLowerCase(Locale.ROOT)+".png";
        if(!Gdx.files.internal(path).exists())
            throw new IllegalStateException("Missing authored 6-variant damage PNG: "+path);
        Pixmap art=new Pixmap(Gdx.files.internal(path));
        try{
            if(art.getWidth()!=SIZE*COUNT||art.getHeight()!=SIZE)
                throw new IllegalStateException("Wrong damage atlas dimensions: "+path);
            int[] pixels=new int[art.getWidth()*SIZE];
            for(int y=0;y<SIZE;y++)for(int x=0;x<art.getWidth();x++)
                pixels[y*art.getWidth()+x]=art.getPixel(x,y);
            atlases[k]=pixels;
            return pixels;
        }finally{art.dispose();}
    }

    private int lookup(Battle.DamageType kind,float dx,float dy,int seed){
        int x=(int)StrictMath.floor(MASK_RADIUS+dx*PIXELS_PER_ARMOR_TILE);
        int y=(int)StrictMath.floor(MASK_RADIUS+dy*PIXELS_PER_ARMOR_TILE);
        if(x<0||x>=SIZE||y<0||y>=SIZE)return 0x000000ff;
        return atlas(kind)[y*(SIZE*COUNT)+pick(seed,kind)*SIZE+x];
    }

    int material(Battle.DamageType kind,float dx,float dy,
                 float armor,float internal,int seed){
        if(kind==null)return 0;
        float intensity=ShipDamageVariants.clamp(
                Math.max(armor,internal*.65f));
        if(intensity<=0)return 0;
        int color=lookup(kind,dx,dy,seed);
        int r=Math.round((color>>>24&255)*intensity);
        int g=Math.round((color>>>16&255)*intensity);
        int b=Math.round((color>>>8&255)*intensity);
        return (r<<16)|(g<<8)|b;
    }

    float opening(Battle.DamageType kind,float dx,float dy,
                  float armor,float internal,int seed){
        if(kind==null)return 0f;
        float wear=ShipDamageVariants.clamp(armor);
        float inside=ShipDamageVariants.clamp(internal);
        if(wear<=.70f&&inside<=.00001f)return 0f;
        float peeled=ShipDamageVariants.clamp((wear-.70f)/.20f);
        float structural=(float)StrictMath.sqrt(inside);
        float depth=Math.max(peeled,Math.min(.8f,structural*.8f));
        return depth*(255-(lookup(kind,dx,dy,seed)&255))/255f;
    }

    void clear(){
        for(int i=0;i<atlases.length;i++)atlases[i]=null;
    }
}
