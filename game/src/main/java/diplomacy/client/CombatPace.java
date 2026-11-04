package diplomacy.client;

import java.util.Locale;

/**
 * Player-facing tactical speed levels. The original .20 accumulator multiplier
 * is now labeled 2.0x, leaving the 30-Hz authoritative Battle.DT unchanged.
 * The simulation multiplier is deliberately separate from the displayed label.
 */
public final class CombatPace {
    public static final float DEFAULT=1f;
    public static final float MINIMUM=.5f;
    public static final float MAXIMUM=3f;
    private static final float[] LEVELS={.5f,1f,1.5f,2f,2.5f,3f};
    private CombatPace() {}

    public static float slower(float current){
        for(int i=LEVELS.length-1;i>=0;i--)
            if(LEVELS[i]<current-.001f)return LEVELS[i];
        return MINIMUM;
    }
    public static float faster(float current){
        for(float level:LEVELS)
            if(level>current+.001f)return level;
        return MAXIMUM;
    }
    public static float clamp(float value){
        if(!Float.isFinite(value))return DEFAULT;
        float nearest=LEVELS[0],distance=Float.MAX_VALUE;
        for(float candidate:LEVELS){
            float diff=Math.abs(candidate-value);
            if(diff<distance){distance=diff;nearest=candidate;}
        }
        return nearest;
    }
    /** Retains the prior perceived combat cadence at the new displayed 2.0x. */
    public static float simulationMultiplier(float displayedSpeed){
        return clamp(displayedSpeed)*.10f;
    }
    public static String label(float speed){
        return String.format(Locale.ROOT,"x%.1f",speed);
    }
}
