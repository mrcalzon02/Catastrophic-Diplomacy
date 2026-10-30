package diplomacy.client;

import java.util.Locale;

/**
 * Real-time presentation pace for the deterministic 30 Hz Battle simulation.
 * A slow tactical default gives ships, firing cycles, ordnance and damage
 * effects time to be observed together without changing their relative physics.
 * Test/verification runners may explicitly request a faster simulation pace.
 */
public final class CombatPace {
    public static final float DEFAULT=0.20f;
    public static final float MINIMUM=0.05f;
    public static final float MAXIMUM=16f;

    private CombatPace() {}

    public static float slower(float current){
        return Math.max(MINIMUM,current*0.5f);
    }
    public static float faster(float current){
        return Math.min(MAXIMUM,current*2f);
    }
    public static String label(float current){
        return String.format(Locale.ROOT,"x%.2f",current);
    }
}
