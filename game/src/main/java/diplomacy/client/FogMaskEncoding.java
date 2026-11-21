package diplomacy.client;

import java.nio.ByteBuffer;

/** Shared, allocation-free fog mask encoding; retains the original exact RGBA bytes. */
final class FogMaskEncoding {
    private FogMaskEncoding(){}

    static void initialize(ByteBuffer pixels,int count){
        for(int i=0;i<count;i++){
            int p=i*4;
            pixels.put(p,(byte)3);
            pixels.put(p+1,(byte)7);
            pixels.put(p+2,(byte)16);
        }
    }

    /** Return true only when at least one encoded alpha byte actually changes.
     * A mask rebuild that quantizes to identical bytes needs no GL texture upload. */
    static boolean updateAlpha(ByteBuffer pixels,float[] revealed){
        boolean changed=false;
        for(int i=0;i<revealed.length;i++){
            int offset=i*4+3;
            byte alpha=(byte)Math.round(255*(1-revealed[i]));
            if(pixels.get(offset)!=alpha){
                pixels.put(offset,alpha);
                changed=true;
            }
        }
        return changed;
    }
}
