package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;

/**
 * Central game-announcer voice, not ship or pilot radio chatter.
 * Streaming one recorded line at a time avoids loading 30 decoded tracks and
 * keeps these client-only effects outside deterministic combat simulation.
 */
public final class AnnouncerAudio implements AutoCloseable {
    public enum Cue {
        TITLE("01_title"),
        WELCOME("02_welcome_back"),
        FLEET_COMMAND("03_fleet_command_online"),
        TACTICAL_SIM("04_preparing_tactical_simulation"),
        BATTLE_START("05_battle_commencing"),
        ALL_FORCES_DEPLOYED("06_all_forces_deployed"),
        REINFORCEMENTS("07_reinforcements_available"),
        CRITICAL_SITUATION("08_critical_situation"),
        FLEET_WITHDRAWAL("09_fleet_withdrawal_confirmed"),
        VICTORY("10_victory_is_ours"),
        DECISIVE_VICTORY("11_decisive_victory"),
        MISSION_ACCOMPLISHED("12_mission_accomplished"),
        MISSION_FAILED("13_mission_failed"),
        FORCES_DEFEATED("14_forces_defeated"),
        BATTLE_LOST("15_battle_lost_war_continues"),
        NEW_TECHNOLOGY("16_new_technology_available"),
        FLEET_CONSTRUCTION("17_fleet_construction_complete"),
        PREPARATIONS_COMPLETE("18_all_preparations_complete"),
        ENEMY_FLEET("19_enemy_fleet_detected"),
        DIPLOMACY_BEGIN("20_let_the_diplomacy_begin"),
        NEGOTIATIONS_FAILED("21_negotiations_failed_spectacularly"),
        CHOSEN_VIOLENCE("22_chosen_violence"),
        EXPENSIVE("23_going_to_be_expensive"),
        MADE_AN_ENEMY("24_you_have_made_an_enemy"),
        PEACE_NOT_OPTION("25_peace_was_never_an_option"),
        INSURANCE("26_insurance_premiums_increased"),
        PERSUASIVE_ARGUMENT("27_exceptionally_persuasive_argument"),
        SURRENDER_DECLINED("28_surrender_offer_declined"),
        COULD_BE_BETTER("29_could_have_gone_better"),
        EMPHASIS_CATASTROPHIC("30_emphasis_on_catastrophic");

        private final String basename;
        Cue(String basename) { this.basename = basename; }
        public String assetPath() { return "audio/announcer/" + basename + ".ogg"; }
    }

    private Music active;
    private boolean suspended;
    private boolean resumeVoice;
    private long nextAllowedNs;

    public void play(Cue cue, boolean interrupt) {
        if (cue == null || suspended || Gdx.audio == null || Gdx.files == null) return;
        long now = System.nanoTime();
        if (active != null) {
            if (active.isPlaying() && !interrupt) return;
            closeActive();
        }
        if (!interrupt && now < nextAllowedNs) return;
        try {
            FileHandle source = Gdx.files.internal(cue.assetPath());
            if (!source.exists()) throw new IllegalStateException("Missing recorded announcement: " + source.path());
            active = Gdx.audio.newMusic(source);
            active.setLooping(false);
            active.setVolume(0.63f);
            active.play();
            nextAllowedNs = now + 2_000_000_000L;
        } catch (RuntimeException error) {
            if (Gdx.app != null) Gdx.app.error("ANNOUNCER", "Voice cue unavailable: " + cue, error);
            closeActive();
        }
    }

    /** Reclaim a finished streaming track; never overlap announcer lines. */
    public void advance() {
        if (!suspended && active != null && !active.isPlaying()) {
            closeActive();
            nextAllowedNs = System.nanoTime() + 1_200_000_000L;
        }
    }

    public void stop() { closeActive(); }
    public void pause() {
        suspended = true;
        resumeVoice = active != null && active.isPlaying();
        if (resumeVoice) active.pause();
    }
    public void resume() {
        suspended = false;
        if (resumeVoice && active != null) active.play();
        resumeVoice = false;
    }
    private void closeActive() {
        if (active == null) return;
        try { active.stop(); active.dispose(); }
        catch (RuntimeException error) {
            if (Gdx.app != null) Gdx.app.error("ANNOUNCER", "Voice cleanup failed", error);
        } finally { active = null; }
    }
    @Override public void close() { closeActive(); }
}
