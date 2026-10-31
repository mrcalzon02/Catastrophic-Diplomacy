package diplomacy.client;

/** Smooth camera-proximity envelope for a SINGLE recorded ship radio channel. */
public final class RadioVoiceProximity {
    private RadioVoiceProximity() { }
    private static float clamp(double n) { return (float)Math.max(0,Math.min(1,n)); }
    /**
     * Smoothstep fade across camera-relative screen distance. Zoom expands
     * world-space audibility but never makes far-off ships speak at full level.
     */
    public static float target(double cameraX,double cameraY,double zoom,
                               double shipX,double shipY) {
        if(!Double.isFinite(cameraX)||!Double.isFinite(cameraY)
                ||!Double.isFinite(zoom)||zoom<=0
                ||!Double.isFinite(shipX)||!Double.isFinite(shipY))return 0;
        double cameraScale=Math.max(0.18,zoom);
        double dx=(shipX-cameraX)/(640*cameraScale);
        double dy=(shipY-cameraY)/(340*cameraScale);
        double d=Math.hypot(dx,dy);
        float t=clamp((d-0.12)/1.48);
        return 1f-t*t*(3f-2f*t);
    }
    /** An uninterrupted audio line fades instead of teleporting in gain. */
    public static float step(float current,float target,float deltaSeconds) {
        if(!Float.isFinite(current)||!Float.isFinite(target))return 0;
        float dt=Float.isFinite(deltaSeconds)?Math.max(0,Math.min(.10f,deltaSeconds)):0;
        float rate=4.8f;
        return clamp(current+(clamp(target)-clamp(current))*Math.min(1f,dt*rate));
    }
}