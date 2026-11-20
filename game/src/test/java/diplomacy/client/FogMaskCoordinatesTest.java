package diplomacy.client;

import java.util.Random;

/** Byte-for-byte coordinate equivalence with the original fog projection. */
public final class FogMaskCoordinatesTest {
    private static void match(double expected,double actual,String label){
        if(Double.doubleToLongBits(expected)!=Double.doubleToLongBits(actual))
            throw new AssertionError(label+" expected="+expected+" actual="+actual);
    }

    public static void main(String[] args){
        FogMaskCoordinates lookup=new FogMaskCoordinates();
        Random random=new Random(0xF09C0A57L);
        int checks=0;
        // Include panning, subpixel motion, strategic and close zoom.
        float[] zooms={0.05f,0.35f,0.5f,1f,2f,3f,8f};
        for(int scene=0;scene<32;scene++){
            float cx=scene==0?0:(random.nextFloat()-.5f)*500000f;
            float cy=scene==0?0:(random.nextFloat()-.5f)*500000f;
            float zoom=zooms[scene%zooms.length];
            lookup.update(cx,cy,zoom);
            float pxWorld=1280f*zoom/320;
            float pyWorld=720f*zoom/180;
            for(int x=0;x<320;x++){
                double expected=cx+(x+.5-320*.5)*pxWorld;
                match(expected,lookup.x(x),"scene="+scene+" x="+x);
                checks++;
            }
            for(int y=0;y<180;y++){
                double expected=cy+(180*.5-y-.5)*pyWorld;
                match(expected,lookup.y(y),"scene="+scene+" y="+y);
                checks++;
            }
        }
        System.out.println("PASS "+checks+" fog projection coordinates, 32 camera views");
    }
}
