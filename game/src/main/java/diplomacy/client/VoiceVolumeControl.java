package diplomacy.client;

/** Touch/keyboard control math, independent of rendering and audio device. */
final class VoiceVolumeControl {
    static final float LEFT=427f, WIDTH=426f;
    private VoiceVolumeControl() {}
    static float clamp(float value){
        if(Float.isNaN(value))return 1f;
        return Math.max(0f,Math.min(1f,value));
    }
    static boolean hit(float x,float y){
        return x>=405f&&x<=875f&&y>=544f&&y<=616f;
    }
    static float valueAt(float x){return clamp((x-LEFT)/WIDTH);}
    static float thumbX(float gain){return LEFT+clamp(gain)*WIDTH;}
    static float step(float gain,int direction){
        int pct=Math.round(clamp(gain)*100f);
        return Math.max(0,Math.min(100,pct+Integer.signum(direction)*5))/100f;
    }
}
