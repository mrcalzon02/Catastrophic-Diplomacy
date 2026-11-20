package diplomacy.client;

import java.util.Random;

/** Byte-for-byte coordinate equivalence with the original fog projection. */
public final class FogMaskCoordinatesTest {
    private static void match(double expected,double actual,String label){
        if(Double.doubleToLongBits(expected)!=Double.doubleToLongBits(actual))
            throw new AssertionError(label+" expected="+expected+" actual="+actual);
    }

    // Advisory microprofile, NOT a release gate or whole-client FPS claim.
    private static volatile double consumed;
    private static long sample(boolean cached){
        FogMaskCoordinates coordinates=new FogMaskCoordinates();
        double sum=0;
        long start=System.nanoTime();
        for(int frame=0;frame<48;frame++){
            float cx=1175.25f+frame*.125f;
            float cy=-812.5f+frame*.25f;
            float zoom=.35f+(frame%3)*.15f;
            float dx=1280f*zoom/320;
            float dy=720f*zoom/180;
            if(cached)coordinates.update(cx,cy,zoom);
            // 120 simulated sources, bounded rectangles akin to mask patches.
            for(int ship=0;ship<120;ship++){
                double sx=ship*7.25;
                double sy=ship*11.75;
                for(int x=65;x<225;x++){
                    double world=cached?coordinates.x(x):cx+(x+.5-320*.5)*dx;
                    sum+=world-sx;
                }
                for(int y=35;y<145;y++){
                    double world=cached?coordinates.y(y):cy+(180*.5-y-.5)*dy;
                    sum+=world-sy;
                }
            }
        }
        consumed=sum; // prevent dead-code elimination in both treatments
        return System.nanoTime()-start;
    }

    private static void advisoryProfile(){
        for(int i=0;i<4;i++){sample(false);sample(true);}
        long[] original=new long[9],cached=new long[9];
        for(int i=0;i<9;i++){
            if((i&1)==0){
                original[i]=sample(false);
                double baseline=consumed;
                cached[i]=sample(true);
                match(baseline,consumed,"paired profile checksum "+i);
            }else{
                cached[i]=sample(true);
                double candidate=consumed;
                original[i]=sample(false);
                match(candidate,consumed,"paired profile checksum "+i);
            }
        }
        java.util.Arrays.sort(original);
        java.util.Arrays.sort(cached);
        System.out.printf(java.util.Locale.ROOT,
                "FOG_COORDINATE_PROFILE advisory 48 frames x 120 sources, 9 balanced pairs; "+
                "baseline p50=%.3fms p95=%.3fms; cached p50=%.3fms p95=%.3fms%n",
                original[4]/1e6,original[8]/1e6,cached[4]/1e6,cached[8]/1e6);
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
        advisoryProfile();
    }
}
