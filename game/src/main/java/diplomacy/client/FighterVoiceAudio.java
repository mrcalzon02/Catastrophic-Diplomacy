package diplomacy.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import diplomacy.shipyard.Shipyard;
import diplomacy.sim.CrewChatter;
import diplomacy.sim.HullVoiceChatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Original user-recorded fighter/interceptor voice: 26 OGG cues, 13 events.
 * One clip at a time, with a global cooldown suitable for 200v200 engagements.
 * All routing is cosmetic; no simulation RNG, no synthetic pilot impersonation.
 */
public final class FighterVoiceAudio implements AutoCloseable {
    private static final Map<String, String> SUBTITLE_TO_ASSET = buildIndex();
    private static final long MIN_SPACING_TICKS = 150L; // 5 sec at 30Hz
    private Music active;
    private long nextTick = Long.MIN_VALUE;
    private boolean suspended;
    private boolean resumeVoice;

    private static Map<String,String> buildIndex() {
        Map<String,String> result = new HashMap<>();
        for (CrewChatter.Event event : CrewChatter.Event.values()) {
            CrewChatter.Tone[] registers = {
                    CrewChatter.Tone.PROFESSIONAL, CrewChatter.Tone.VETERAN
            };
            for (int i = 0; i < registers.length; i++) {
                String subtitle = HullVoiceChatter.line(Shipyard.Hull.INTERCEPTOR,
                        event, 0L, 0, registers[i]);
                String cue = "interceptor_" + event.name().toLowerCase(java.util.Locale.ROOT)
                        + "_0" + (i + 1);
                String path = "audio/fighter/" + cue + ".ogg";
                if (result.putIfAbsent(subtitle, path) != null)
                    throw new IllegalStateException("Duplicate fighter subtitle: " + subtitle);
            }
        }
        if (result.size() != 26) throw new IllegalStateException("Incomplete fighter voice bank");
        return Collections.unmodifiableMap(result);
    }

    /** Only the exact recorded dialogue is voiced; other radio remains text + bleep. */
    public static String assetPathForRadio(String eventLine) {
        if (eventLine == null || !eventLine.startsWith("RADIO ")) return null;
        int colon = eventLine.indexOf(": ");
        if (colon < 0) return null;
        return SUBTITLE_TO_ASSET.get(eventLine.substring(colon + 2));
    }
    public static int registeredCues() { return SUBTITLE_TO_ASSET.size(); }

    public boolean play(String eventLine, long tick) {
        String path = assetPathForRadio(eventLine);
        if (path == null || suspended || tick < nextTick || Gdx.audio == null || Gdx.files == null)
            return false;
        if (active != null) {
            if (active.isPlaying()) return false;
            closeActive();
        }
        try {
            var file = Gdx.files.internal(path);
            if (!file.exists()) throw new IllegalStateException("Missing recorded fighter voice: " + path);
            active = Gdx.audio.newMusic(file);
            active.setLooping(false);
            active.setVolume(0.44f);
            active.play();
            nextTick = tick + MIN_SPACING_TICKS;
            return true;
        } catch (RuntimeException error) {
            if (Gdx.app != null) Gdx.app.error("FIGHTER-VOICE", "Playback failed: " + path, error);
            closeActive();
            nextTick = tick + MIN_SPACING_TICKS;
            return false;
        }
    }
    public void advance() {
        if (!suspended && active != null && !active.isPlaying()) closeActive();
    }
    public void stop() { closeActive(); resumeVoice = false; }
    public void reset() { stop(); nextTick = Long.MIN_VALUE; }
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
            if (Gdx.app != null) Gdx.app.error("FIGHTER-VOICE","Cleanup failed",error);
        }
        finally { active = null; }
    }
    @Override public void close() { stop(); }
}
