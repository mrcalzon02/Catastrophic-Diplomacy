package diplomacy.client;

import diplomacy.sim.Battle;

public final class CombatAudioCueTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Battle.VisualEvent sample(Battle.VisualPhase phase, String weapon) {
        return new Battle.VisualEvent(1, 1, 3, phase, weapon, 0, 1, 2, 0, 0, 4, 5);
    }
    public static void main(String[] args) {
        require(CombatAudioCue.from(sample(Battle.VisualPhase.FIRE, "LASER")) == CombatAudioCue.Cue.LASER, "laser");
        require(CombatAudioCue.from(sample(Battle.VisualPhase.FIRE, "PLASMA")) == CombatAudioCue.Cue.PLASMA, "plasma");
        require(CombatAudioCue.from(sample(Battle.VisualPhase.FIRE, "PARTICLE")) == CombatAudioCue.Cue.PARTICLE, "particle");
        require(CombatAudioCue.from(sample(Battle.VisualPhase.FIRE, "KINETIC")) == CombatAudioCue.Cue.KINETIC, "kinetic");
        require(CombatAudioCue.from(sample(Battle.VisualPhase.FIRE, "TORPEDO")) == CombatAudioCue.Cue.TORPEDO, "torpedo");
        require(CombatAudioCue.from(sample(Battle.VisualPhase.FIRE, "MISSILE")) == CombatAudioCue.Cue.MISSILE, "missile");
        require(CombatAudioCue.from(sample(Battle.VisualPhase.FIRE, "POINT_DEFENSE")) == CombatAudioCue.Cue.LASER, "pd");
        require(CombatAudioCue.from(sample(Battle.VisualPhase.IMPACT, "KINETIC")) == CombatAudioCue.Cue.HULL, "hull");
        require(CombatAudioCue.from(sample(Battle.VisualPhase.INTERCEPT, "MISSILE")) == CombatAudioCue.Cue.SHIELD, "intercept");
        require(CombatAudioCue.from(sample(Battle.VisualPhase.TIMEOUT, "MISSILE")) == null, "timeout silent");
        require(CombatAudioCue.from(sample(Battle.VisualPhase.FIRE, "UNKNOWN")) == null, "unknown silent");
        require(CombatAudioCue.cooldownTicks(CombatAudioCue.Cue.LASER) > 0, "throttle active");
        require(CombatAudioCue.cooldownTicks(CombatAudioCue.Cue.UI) == 0, "ui immediate");
        System.out.println("PASS shared-platform audio cue classification");
    }
}
