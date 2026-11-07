package diplomacy.client;

/** Touch/keyboard control math, independent of rendering and audio device. */
final class VoiceVolumeControl {
    private VoiceVolumeControl() {}
    static float clamp(float value){
        if(Float.isNaN(value))return 1f;
        return Math.max(0f,Math.min(1f,value));
    }
    static float step(float gain,int direction){
        int pct=Math.round(clamp(gain)*100f);
        return Math.max(0,Math.min(100,pct+Integer.signum(direction)*5))/100f;
    }
}
