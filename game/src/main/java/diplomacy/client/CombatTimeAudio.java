package diplomacy.client;

/**
 * A bounded perceptual audio response to tactical presentation speed.
 * Relative speed 1x preserves the authored duration and pitch. Combat cues
 * change moderately at other speeds so 3x does not sound like a chipmunk.
 * UI, music, radio and intelligible dialogue intentionally do not use this.
 */
final class CombatTimeAudio {
    private CombatTimeAudio(){}
    static float combatPitch(float speed,float sourcePitch){
        float pace=CombatPace.clamp(speed);
        float multiplier=(float)Math.pow(pace,.20);
        return Math.max(.65f,Math.min(1.6f,sourcePitch*multiplier));
    }
    static float enginePitch(float speed,float original){
        float pace=CombatPace.clamp(speed);
        float multiplier=(float)Math.pow(pace,.08);
        return Math.max(.5f,Math.min(1.6f,original*multiplier));
    }
}
