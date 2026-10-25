package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import diplomacy.sim.Battle;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * One libGDX audio implementation for Android, Windows and Linux.
 * Pure combat data supplies the cues; this class never affects combat or RNG.
 * Limits polyphony aggressively for 200v200 engagements.
 */
public final class CombatAudio implements AutoCloseable {
    private static final int MAX_CUES_PER_FRAME = 4;
    private static final String PREF_NAME = "catastrophic-diplomacy-audio";
    private final Map<CombatAudioCue.Cue, Sound> cues = new EnumMap<>(CombatAudioCue.Cue.class);
    private final long[] lastPlayedTick = new long[CombatAudioCue.Cue.values().length];
    private Preferences prefs;
    private Music battleMusic, ambientMusic, activeMusic;
    private long seenSequence;
    private int seenDestructions;
    private boolean musicOn = true, effectsOn = true, useBattleMusic = true;
    private boolean suspended;
    private float musicVolume = 0.19f, effectsVolume = 0.42f;

    public CombatAudio() {
        Arrays.fill(lastPlayedTick, -1000000L);
        try {
            prefs = Gdx.app.getPreferences(PREF_NAME);
            musicOn = prefs.getBoolean("music", true);
            effectsOn = prefs.getBoolean("effects", true);
            useBattleMusic = prefs.getBoolean("battleTrack", true);
        } catch (RuntimeException error) {
            Gdx.app.error("AUDIO", "Audio settings unavailable; using defaults", error);
        }
        load(CombatAudioCue.Cue.LASER, "audio/sfx/laser_zap.wav");
        load(CombatAudioCue.Cue.KINETIC, "audio/sfx/metal_wonk.wav");
        load(CombatAudioCue.Cue.PLASMA, "audio/sfx/plasma_pulse.wav");
        load(CombatAudioCue.Cue.PARTICLE, "audio/sfx/particle_arc.wav");
        load(CombatAudioCue.Cue.MISSILE, "audio/sfx/missile_launch.wav");
        load(CombatAudioCue.Cue.TORPEDO, "audio/sfx/torpedo_launch.wav");
        load(CombatAudioCue.Cue.SHIELD, "audio/sfx/shield_hit.wav");
        load(CombatAudioCue.Cue.HULL, "audio/sfx/hollow_metal.wav");
        load(CombatAudioCue.Cue.EXPLOSION, "audio/sfx/grenade.wav");
        load(CombatAudioCue.Cue.UI, "audio/sfx/ui_confirm.wav");
        ambientMusic = loadMusic("audio/music/cold_unresolved_drone.mp3");
        battleMusic = loadMusic("audio/music/final_surge.mp3");
        switchTrack();
    }

    private void load(CombatAudioCue.Cue cue, String path) {
        try {
            if (!Gdx.files.internal(path).exists())
                throw new IllegalStateException("Missing audio asset: " + path);
            cues.put(cue, Gdx.audio.newSound(Gdx.files.internal(path)));
        } catch (RuntimeException error) {
            Gdx.app.error("AUDIO", "Unable to load " + path, error);
        }
    }
    private Music loadMusic(String path) {
        try {
            if (!Gdx.files.internal(path).exists())
                throw new IllegalStateException("Missing soundtrack: " + path);
            Music music = Gdx.audio.newMusic(Gdx.files.internal(path));
            music.setLooping(true);
            music.setVolume(musicVolume);
            return music;
        } catch (RuntimeException error) {
            Gdx.app.error("AUDIO", "Unable to load " + path, error);
            return null;
        }
    }

    private void switchTrack() {
        Music wanted = useBattleMusic ? battleMusic : ambientMusic;
        if (wanted == null) wanted = useBattleMusic ? ambientMusic : battleMusic;
        if (activeMusic != wanted && activeMusic != null) activeMusic.stop();
        activeMusic = wanted;
        if (activeMusic != null) {
            activeMusic.setVolume(musicVolume);
            if (musicOn && !suspended && !activeMusic.isPlaying()) {
                try { activeMusic.play(); }
                catch (RuntimeException error) { Gdx.app.error("AUDIO", "Music playback failed", error); }
            }
        }
    }

    private void persist() {
        if (prefs == null) return;
        try {
            prefs.putBoolean("music", musicOn);
            prefs.putBoolean("effects", effectsOn);
            prefs.putBoolean("battleTrack", useBattleMusic);
            prefs.flush();
        } catch (RuntimeException error) {
            Gdx.app.error("AUDIO", "Cannot persist audio settings", error);
        }
    }

    public boolean musicEnabled() { return musicOn; }
    public boolean effectsEnabled() { return effectsOn; }
    public boolean battleTrack() { return useBattleMusic; }
    public void toggleMusic() {
        musicOn = !musicOn;
        if (!musicOn && activeMusic != null) activeMusic.pause();
        else switchTrack();
        persist();
    }
    public void toggleEffects() { effectsOn = !effectsOn; persist(); }
    public void toggleTrack() { useBattleMusic = !useBattleMusic; switchTrack(); persist(); }

    public void resetForBattle() {
        seenSequence = 0;
        seenDestructions = 0;
        Arrays.fill(lastPlayedTick, -1000000L);
    }

    /** Reads each immutable visual event once; no simulation modification. */
    public void advance(Battle battle) {
        if (battle == null) return;
        if (battle.destructions > seenDestructions) {
            if (effectsOn && !suspended) play(CombatAudioCue.Cue.EXPLOSION, battle.tick, 0.72f, 1f);
        }
        seenDestructions = battle.destructions;
        int voices = 0;
        for (Battle.VisualEvent event : battle.visualEvents()) {
            if (event.sequence() <= seenSequence) continue;
            seenSequence = event.sequence();
            if (!effectsOn || suspended || voices >= MAX_CUES_PER_FRAME) continue;
            CombatAudioCue.Cue cue = CombatAudioCue.from(event);
            if (cue == null) continue;
            // Deterministic pitch variation, independent of Battle's Random.
            float pitch = 0.91f + (float)(Math.floorMod(event.shotId(), 7) * 0.027);
            float volume = event.phase() == Battle.VisualPhase.FIRE ? 0.70f : 0.90f;
            if (play(cue, event.tick(), volume, pitch)) voices++;
        }
    }

    private boolean play(CombatAudioCue.Cue cue, long tick, float volume, float pitch) {
        if (!effectsOn || suspended) return false;
        Sound sound = cues.get(cue);
        if (sound == null) return false;
        int index = cue.ordinal();
        if (tick - lastPlayedTick[index] < CombatAudioCue.cooldownTicks(cue)) return false;
        lastPlayedTick[index] = tick;
        try {
            sound.play(Math.max(0f, Math.min(1f, effectsVolume * volume)), pitch, 0f);
            return true;
        } catch (RuntimeException error) {
            Gdx.app.error("AUDIO", "Disabling failed cue " + cue, error);
            cues.remove(cue);
            sound.dispose();
            return false;
        }
    }

    public void ui() {
        // UI events have no simulation tick; they intentionally bypass battle cooldown.
        if (!effectsOn || suspended) return;
        Sound click = cues.get(CombatAudioCue.Cue.UI);
        if (click != null) {
            try { click.play(effectsVolume * 0.42f); }
            catch (RuntimeException error) { Gdx.app.error("AUDIO", "UI cue failed", error); }
        }
    }

    public void pause() {
        suspended = true;
        if (activeMusic != null) activeMusic.pause();
    }
    public void resume() { suspended = false; switchTrack(); }

    @Override public void close() {
        if (activeMusic != null) activeMusic.stop();
        for (Sound sound : cues.values()) sound.dispose();
        cues.clear();
        if (battleMusic != null) battleMusic.dispose();
        if (ambientMusic != null && ambientMusic != battleMusic) ambientMusic.dispose();
    }
}
