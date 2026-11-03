package diplomacy.sim;

/**
 * Deterministic, no-RNG encounter termination. This policy never decides who
 * won a battle: survivors and actual extraction still decide field control.
 * STALEMATE/TIME_LIMIT are unresolved outcomes, never fabricated casualties.
 */
public final class BattleTermination {
    private BattleTermination(){}

    public enum Reason { NONE, STALEMATE, TIME_LIMIT }
    public static final long TICKS_PER_SECOND=30;
    /** Give even depleted fleets time for delayed munitions and withdrawal. */
    public static final long DEPLETED_MIN_TICKS=120*TICKS_PER_SECOND;
    public static final long DEPLETED_QUIET_TICKS=90*TICKS_PER_SECOND;
    /** Still-armed patrols may search the whole fixed arena before drawing. */
    public static final long STALLED_MIN_TICKS=20*60*TICKS_PER_SECOND;
    public static final long STALLED_QUIET_TICKS=6*60*TICKS_PER_SECOND;
    /** Absolute maximum: a battle never simulates indefinitely. */
    public static final long HARD_LIMIT_TICKS=60*60*TICKS_PER_SECOND;

    /**
     * @param lastProgressTick last real hull impact, destruction or extraction
     * @param bothDisarmed neither side has ANY usable or replenishable
     *   offensive resource; if uncertain, the caller MUST pass false
     * @param transientThreats in-flight ordnance, pending salvo, craft or
     *   timed flak event that might still affect a ship
     * @param extractionMoving a committed withdrawal with a viable route to
     *   its own rear exit; never interrupt physical escape countdown
     */
    public static Reason evaluate(long tick,long lastProgressTick,
                                  boolean bothDisarmed,boolean transientThreats,
                                  boolean extractionMoving){
        if(tick<0||lastProgressTick<0||lastProgressTick>tick)
            throw new IllegalArgumentException("Invalid battle termination clock");
        if(tick>=HARD_LIMIT_TICKS)return Reason.TIME_LIMIT;
        if(transientThreats||extractionMoving)return Reason.NONE;
        long quiet=tick-lastProgressTick;
        if(bothDisarmed&&tick>=DEPLETED_MIN_TICKS
                &&quiet>=DEPLETED_QUIET_TICKS)return Reason.STALEMATE;
        if(tick>=STALLED_MIN_TICKS&&quiet>=STALLED_QUIET_TICKS)
            return Reason.STALEMATE;
        return Reason.NONE;
    }
}
