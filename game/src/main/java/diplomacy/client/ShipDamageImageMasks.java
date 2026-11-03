package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import diplomacy.sim.Battle;
import java.util.Locale;

/**
 * Reads six distinct, FIXED native 96x96 RGBA damage decals per weapon.
 * Runtime asset: damage_masks/hires96/<weapon>.png (576x96).
 * Diagnostic CD_ARMOR_MASK_SET=legacy24, upscaled48 or authored48
 * selects previously committed comparison images. All resolutions span
 * exactly the same 2.667 defense-cell diameter and do not grow the wound.
 * No shape synthesis, rotation, per-pixel
 * noise, or stand-in sprite is used in the live renderer.
 *
 * Channels: R soot, G residual heat, B exposed metal/laser oxide;
 * A is the intact exterior coefficient (255 intact, 0 fully missing).
 * A wound is applied ONLY around one registered 16x16 defense tile,
 * never scaled to fill the ship.
 */
final class ShipDamageImageMasks {
    static final int SIZE=96, COUNT=6, MASK_RADIUS=48;
    // 24 / 9 == 48 / 18 == 96 / 36: fixed 2.667-cell footprint.
    private final int[][] atlases=new int[Battle.DamageType.values().length][];
    /** Absent/unknown diagnostic override uses the new native 96px artwork. */
    private final String testSet=System.getenv("CD_ARMOR_MASK_SET");
    private final boolean use48="upscaled48".equals(testSet)||"authored48".equals(testSet);
    private final boolean use24="legacy24".equals(testSet);
    private final int sampleSize=use48?48:(use24?24:SIZE);
    private final float pixelsPerCell=sampleSize*(9f/24f);
    private int[] shared48;

    private int[] shared48(){
        if(shared48!=null)return shared48;
        String path="damage_masks/ab/"+testSet+".png";
        if(!Gdx.files.internal(path).exists())
            throw new IllegalStateException("Missing real A/B PNG: "+path);
        Pixmap art=new Pixmap(Gdx.files.internal(path));
        try{
            int width=48*COUNT, height=48*Battle.DamageType.values().length;
            if(art.getWidth()!=width||art.getHeight()!=height)
                throw new IllegalStateException("Wrong 48px A/B atlas dimensions: "+path);
            int[] pixels=new int[width*height];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)
                pixels[y*width+x]=art.getPixel(x,y);
            shared48=pixels;
            return pixels;
        }finally{art.dispose();}
    }

    static int pick(int seed,Battle.DamageType kind){
        return ShipDamageVariants.variant(seed,kind);
    }

    private int[] atlas(Battle.DamageType kind){
        if(use48)return shared48();
        int k=kind.ordinal();
        int[] cached=atlases[k];
        if(cached!=null)return cached;
        String path=(use24?"damage_masks/":"damage_masks/hires96/")+
                kind.name().toLowerCase(Locale.ROOT)+".png";
        if(!Gdx.files.internal(path).exists())
            throw new IllegalStateException("Missing authored 6-variant damage PNG: "+path);
        Pixmap art=new Pixmap(Gdx.files.internal(path));
        try{
            if(art.getWidth()!=sampleSize*COUNT||art.getHeight()!=sampleSize)
                throw new IllegalStateException("Wrong authored damage atlas dimensions: "+
                        path+" (expected "+(sampleSize*COUNT)+"x"+sampleSize+")");
            int[] pixels=new int[art.getWidth()*sampleSize];
            for(int y=0;y<sampleSize;y++)for(int x=0;x<art.getWidth();x++)
                pixels[y*art.getWidth()+x]=art.getPixel(x,y);
            atlases[k]=pixels;
            return pixels;
        }finally{art.dispose();}
    }

    private int lookup(Battle.DamageType kind,float dx,float dy,int seed){
        int x=(int)StrictMath.floor(sampleSize*.5f+dx*pixelsPerCell);
        int y=(int)StrictMath.floor(sampleSize*.5f+dy*pixelsPerCell);
        if(x<0||x>=sampleSize||y<0||y>=sampleSize)return 0x000000ff;
        int row=use48?kind.ordinal()*sampleSize+y:y;
        return atlas(kind)[row*(sampleSize*COUNT)+pick(seed,kind)*sampleSize+x];
    }

    int material(Battle.DamageType kind,float dx,float dy,
                 float armor,float internal,int seed){
        if(kind==null)return 0;
        float intensity=ShipDamageVariants.clamp(
                Math.max(armor,internal*.65f));
        if(intensity<=0)return 0;
        int color=lookup(kind,dx,dy,seed);
        int rawBlue=color>>>8&255;
        int r=Math.round((color>>>24&255)*intensity);
        int g=Math.round((color>>>16&255)*intensity);
        int b=Math.round(rawBlue*intensity);
        // In the native-96 atlas B>=164 is a MATERIAL TAG, not simply a
        // silver intensity. Preserve that oxide marker even while shallow
        // laser heating ramps up; G is a hue phase and must not fade toward
        // a different oxide color when damage severity changes.
        // Legacy 24/48 A/B frame behavior is deliberately unchanged.
        if(kind==Battle.DamageType.LASER&&!use24&&!use48
                &&rawBlue>=164&&intensity>.08f){
            b=Math.min(255,164+Math.round((rawBlue-164)*intensity));
            g=color>>>16&255;
        }
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
        shared48=null;
    }
}
