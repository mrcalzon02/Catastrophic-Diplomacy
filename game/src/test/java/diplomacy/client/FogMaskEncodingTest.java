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
            boolean expectedChange=false;
            for(int i=0;i<PIXELS;i++){
                int p=i*4;
                baseline.put(p,(byte)3);
                baseline.put(p+1,(byte)7);
                baseline.put(p+2,(byte)16);
                byte alpha=(byte)Math.round(255*(1-revealed[i]));
                baseline.put(p+3,alpha);
                if(updated.get(p+3)!=alpha)expectedChange=true;
            }
            boolean actualChange=FogMaskEncoding.updateAlpha(updated,revealed);
            if(actualChange!=expectedChange)
                throw new AssertionError(scene+" pass="+pass+" dirty="+actualChange+
                        " expected="+expectedChange);
            for(int i=0;i<PIXELS*4;i++){
                if(baseline.get(i)!=updated.get(i))
                    throw new AssertionError(scene+" pass="+pass+" byte="+i);
            }
            if(FogMaskEncoding.updateAlpha(updated,revealed))
                throw new AssertionError(scene+" pass="+pass+" identical mask requested redundant upload");
            // Changed visibility in the second pass should overwrite every alpha.
            for(int i=0;i<PIXELS;i++)
                revealed[i]=1f-revealed[i];
        }
    }
    private static void subByteChanges(){
        ByteBuffer updated=ByteBuffer.allocate(4);
        FogMaskEncoding.initialize(updated,1);
        float[] values={0.5001f};
        if(!FogMaskEncoding.updateAlpha(updated,values))
            throw new AssertionError("Initial fog alpha change missed");
        values[0]=0.5002f; // same 8-bit alpha even though float changed
        if(FogMaskEncoding.updateAlpha(updated,values))
            throw new AssertionError("Sub-byte fog variation triggered redundant upload");
        values[0]=0f;
        if(!FogMaskEncoding.updateAlpha(updated,values)||updated.get(3)!=(byte)255)
            throw new AssertionError("Real fog change was not encoded");
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
        subByteChanges();
        System.out.println("PASS fog byte-exact RGBA + no-op upload detection: 4 scenes x 2 rebuilds x 230400 bytes");
    }
}
