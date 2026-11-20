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

    static void updateAlpha(ByteBuffer pixels,float[] revealed){
        for(int i=0;i<revealed.length;i++)
            pixels.put(i*4+3,(byte)Math.round(255*(1-revealed[i])));
    }
}
