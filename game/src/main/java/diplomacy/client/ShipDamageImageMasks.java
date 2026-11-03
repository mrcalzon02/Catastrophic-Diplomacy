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
 * No per-frame randomness, shape synthesis, procedural texture noise,
 * or stand-in sprite. Seeded micro-rotation/scale samples the same authored
 * frame and remains clipped to the legacy 2.667-cell physical extent.
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
    // The authored 96px R/G/B artwork remains untouched. The replacement
    // 96px atlases had only 40-80 physically-open cut pixels per 9,216px
    // frame, leaving just 2-8 pixels of structural backing at ship-mask LOD.
    // Precompute ONLY a wider version of the existing irregular A cutout,
    // retaining every fissure/puncture's shape and the original hull outline.
    private final int[][] cutoutAlpha=new int[Battle.DamageType.values().length][];
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

    /** Per-family edge recovery is smaller than the original impact extent.
     * Long laser oxide discoloration and the R/G/B material response are NEVER
     * broadened here: this affects only pixels revealing structural metal. */
    static int cutoutRecoveryRadius(Battle.DamageType kind){
        return switch(kind){
            case LASER -> 5;
            case KINETIC, PLASMA, MISSILE -> 7;
            case TORPEDO -> 8;
            case PARTICLE, FIGHTER -> 4;
        };
    }
    /**
     * Offline/once-per-atlas alpha dilation. It follows the ACTUAL authored
     * cutout topology (including branches/holes), not a new oval or explosion.
     * The last 1.5px feathers the edge to avoid another hard transparency halo.
     * Each frame is isolated: neighboring variants can never bleed together.
     */
    static int[] recoverCutoutAlpha(int[] rgba,int size,int frames,int radius){
        if(rgba==null||size<4||frames<1||radius<0||
                rgba.length!=size*size*frames)
            throw new IllegalArgumentException("Invalid RGBA cutout atlas");
        int[] recovered=new int[rgba.length];
        int width=size*frames;
        for(int variant=0;variant<frames;variant++){
            int origin=variant*size;
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){
                int original=rgba[y*width+origin+x]&255;
                if(original==0){recovered[y*width+origin+x]=0;continue;}
                int nearestSquared=Integer.MAX_VALUE;
                int cut=255;
                for(int dy=-radius;dy<=radius;dy++){
                    int sy=y+dy;
                    if(sy<0||sy>=size)continue;
                    for(int dx=-radius;dx<=radius;dx++){
                        int distance=dx*dx+dy*dy;
                        if(distance>radius*radius || distance>=nearestSquared)
                            continue;
                        int sx=x+dx;
                        if(sx<0||sx>=size)continue;
                        int candidate=rgba[sy*width+origin+sx]&255;
                        if(candidate>=243)continue;
                        nearestSquared=distance;cut=candidate;
                    }
                }
                if(nearestSquared==Integer.MAX_VALUE){
                    recovered[y*width+origin+x]=original;
                    continue;
                }
                float proximity=(float)StrictMath.sqrt(nearestSquared);
                float opacity=Math.max(0f,Math.min(1f,
                        (radius+.5f-proximity)/1.5f));
                int expanded=255-Math.round((255-cut)*opacity);
                recovered[y*width+origin+x]=Math.min(original,expanded);
            }
        }
        return recovered;
    }
    private int[] alphaAtlas(Battle.DamageType kind){
        int family=kind.ordinal();
        if(cutoutAlpha[family]!=null)return cutoutAlpha[family];
        cutoutAlpha[family]=recoverCutoutAlpha(
                atlas(kind),sampleSize,COUNT,cutoutRecoveryRadius(kind));
        return cutoutAlpha[family];
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
        // Hard clip to the approved physical footprint, independently of
        // small rotation/scale variations. Legacy comparison assets keep
        // their original untransformed sampling behavior.
        float bound=sampleSize*.5f/pixelsPerCell;
        if(Math.abs(dx)>=bound||Math.abs(dy)>=bound)return 0x000000ff;
        if(!use24&&!use48){
            int angle=ShipDamageVariants.maskAngleDegrees(seed,kind);
            float cosine=ShipDamageVariants.maskCos(angle);
            float sine=ShipDamageVariants.maskSin(angle);
            float inv=1f/ShipDamageVariants.maskScale(seed,kind);
            float x=dx,y=dy;
            dx=(x*cosine+y*sine)*inv;
            dy=(y*cosine-x*sine)*inv;
        }
        int x=(int)StrictMath.floor(sampleSize*.5f+dx*pixelsPerCell);
        int y=(int)StrictMath.floor(sampleSize*.5f+dy*pixelsPerCell);
        if(x<0||x>=sampleSize||y<0||y>=sampleSize)return 0x000000ff;
        int row=use48?kind.ordinal()*sampleSize+y:y;
        return atlas(kind)[row*(sampleSize*COUNT)+pick(seed,kind)*sampleSize+x];
    }

    private int openingAlpha(Battle.DamageType kind,float dx,float dy,int seed){
        // Both material and opening must use precisely the SAME deterministic
        // seed/rotation/scale. Reuse the existing lookup's registered UV, but
        // sample the preprocessed A channel rather than the color atlas.
        if(use24||use48)return lookup(kind,dx,dy,seed)&255;
        float bound=sampleSize*.5f/pixelsPerCell;
        if(Math.abs(dx)>=bound||Math.abs(dy)>=bound)return 255;
        int angle=ShipDamageVariants.maskAngleDegrees(seed,kind);
        float cosine=ShipDamageVariants.maskCos(angle);
        float sine=ShipDamageVariants.maskSin(angle);
        float inv=1f/ShipDamageVariants.maskScale(seed,kind);
        float ox=dx,oy=dy;
        dx=(ox*cosine+oy*sine)*inv;
        dy=(oy*cosine-ox*sine)*inv;
        int x=(int)StrictMath.floor(sampleSize*.5f+dx*pixelsPerCell);
        int y=(int)StrictMath.floor(sampleSize*.5f+dy*pixelsPerCell);
        if(x<0||x>=sampleSize||y<0||y>=sampleSize)return 255;
        return alphaAtlas(kind)[y*sampleSize*COUNT+
                pick(seed,kind)*sampleSize+x];
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
        return depth*(255-openingAlpha(kind,dx,dy,seed))/255f;
    }

    void clear(){
        for(int i=0;i<atlases.length;i++)atlases[i]=null;
        shared48=null;
        for(int i=0;i<cutoutAlpha.length;i++)cutoutAlpha[i]=null;
    }
}
