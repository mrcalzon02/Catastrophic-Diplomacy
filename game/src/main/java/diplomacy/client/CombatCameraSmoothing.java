package diplomacy.client;

/** Render-only critically bounded camera approach with a frame-rate
 * independent response. Never changes simulation position or targeting. */
final class CombatCameraSmoothing {
    private static final double RESPONSE_PER_SECOND=6.0;
    private CombatCameraSmoothing(){}
    static float toward(float current,float target,float wallSeconds){
        if(!Float.isFinite(target))return current;
        if(!Float.isFinite(current)||!Float.isFinite(wallSeconds)||wallSeconds<=0)
            return target;
        float step=(float)(-Math.expm1(-RESPONSE_PER_SECOND*wallSeconds));
        return current+(target-current)*Math.min(1f,Math.max(0f,step));
    }
}
