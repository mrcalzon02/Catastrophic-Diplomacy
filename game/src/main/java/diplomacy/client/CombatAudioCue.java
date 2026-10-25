package diplomacy.client;

import diplomacy.sim.Battle;

/** Pure, deterministic mapping from simulation visual events to audible cues. */
public final class CombatAudioCue {
    public enum Cue {
        LASER, KINETIC, PLASMA, PARTICLE, MISSILE, TORPEDO, SHIELD,
        HULL, EXPLOSION, UI
    }
    private CombatAudioCue() { }

    public static Cue from(Battle.VisualEvent event) {
        if (event == null) return null;
        if (event.phase() == Battle.VisualPhase.INTERCEPT) return Cue.SHIELD;
        if (event.phase() == Battle.VisualPhase.TIMEOUT) return null;
        if (event.phase() == Battle.VisualPhase.IMPACT) {
            return switch (event.weapon()) {
                case "KINETIC", "TORPEDO", "MISSILE", "FIGHTER",
                 "FIGHTER_CANNON", "FIGHTER_MISSILE", "FIGHTER_TORPEDO" -> Cue.HULL;
                default -> Cue.SHIELD;
            };
        }
        if (event.phase() != Battle.VisualPhase.FIRE) return null;
        return switch (event.weapon()) {
            case "LASER", "POINT_DEFENSE", "FIGHTER_LASER" -> Cue.LASER;
            case "FIGHTER_CANNON" -> Cue.KINETIC;
            case "FIGHTER_PARTICLE" -> Cue.PARTICLE;
            case "FIGHTER_MISSILE" -> Cue.MISSILE;
            case "FIGHTER_TORPEDO" -> Cue.TORPEDO;
            case "KINETIC" -> Cue.KINETIC;
            case "PLASMA" -> Cue.PLASMA;
            case "PARTICLE" -> Cue.PARTICLE;
            case "MISSILE", "DRONE", "FIGHTER" -> Cue.MISSILE;
            case "TORPEDO" -> Cue.TORPEDO;
            default -> null;
        };
    }

    /** Cooldown in authoritative simulation ticks; 30 ticks = one simulated second. */
    public static int cooldownTicks(Cue cue) {
        if (cue == null) return Integer.MAX_VALUE;
        return switch (cue) {
            case LASER, KINETIC, PARTICLE -> 5;
            case PLASMA, MISSILE, SHIELD -> 7;
            case TORPEDO, HULL -> 10;
            case EXPLOSION -> 14;
            case UI -> 0;
        };
    }
}
