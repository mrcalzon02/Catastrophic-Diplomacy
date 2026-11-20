package diplomacy.client;

import java.nio.ByteBuffer;
import java.util.Random;

/** Pixel-exact reference comparison for the production fog encoding path. */
public final class FogMaskEncodingTest {
    private static final int PIXELS=320*180;
    private static void check(float[] revealed,String scene){
        ByteBuffer baseline=ByteBuffer.allocate(PIXELS*4);
        ByteBuffer updated=ByteBuffer.allocate(PIXELS*4);
        FogMaskEncoding.initialize(updated,PIXELS);
        // Rebuild twice: fog pixels must not retain stale RGB or alpha.
        for(int pass=0;pass<2;pass++){
            for(int i=0;i<PIXELS;i++){
                int p=i*4;
                baseline.put(p,(byte)3);
                baseline.put(p+1,(byte)7);
                baseline.put(p+2,(byte)16);
                baseline.put(p+3,(byte)Math.round(255*(1-revealed[i])));
            }
            FogMaskEncoding.updateAlpha(updated,revealed);
            for(int i=0;i<PIXELS*4;i++){
                if(baseline.get(i)!=updated.get(i))
                    throw new AssertionError(scene+" pass="+pass+" byte="+i);
            }
            // Changed visibility in the second pass should overwrite every alpha.
            for(int i=0;i<PIXELS;i++)
                revealed[i]=1f-revealed[i];
        }
    }
    public static void main(String[] args){
        float[] values=new float[PIXELS];
        check(values,"fully concealed");
        java.util.Arrays.fill(values,1f);
        check(values,"fully revealed");
        for(int i=0;i<PIXELS;i++)values[i]=(i%320)/319f;
        check(values,"horizontal gradient");
        Random seeded=new Random(0xCADA570L);
        for(int i=0;i<PIXELS;i++)values[i]=seeded.nextFloat();
        check(values,"seeded mixed visibility");
        System.out.println("PASS fog byte-exact RGBA 4 scenes x 2 rebuilds x 230400 bytes");
    }
}
